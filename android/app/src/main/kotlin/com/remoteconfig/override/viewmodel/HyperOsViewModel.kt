package com.remoteconfig.override.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.data.MigtCodec
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.ui.util.PinyinUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * HyperOS 云控 ViewModel —— 应用列表 / 应用功能页 / 通用配置（布尔开关）三块状态。
 * 数据源为 [JoyoseManager]（root shell + libcosa.so joyose-* 命令族），
 * 所有 shell 调用均置于 IO 调度器；写路径带并发守护（防 root shell 慢时重复触发）。
 */
class HyperOsViewModel(application: Application) : AndroidViewModel(application) {

    private val joyose = JoyoseManager(application)

    /** 排序选择等持久化设置（对齐 KernelSU：ViewModel 直接读写仓库，观察性由各自的 StateFlow 承担）。 */
    private val settings = SettingsRepositoryImpl()

    // ── 首页状态卡 ───────────────────────────────────────────

    private val _statState = MutableStateFlow<JoyoseManager.Stat?>(null)
    val statState = _statState.asStateFlow()

    /** 轻量刷新：仅 stat（双库存在性 + 冻结状态），首页状态卡用。 */
    fun refreshStat() {
        viewModelScope.launch {
            _statState.value = withContext(Dispatchers.IO) { joyose.stat() }
        }
    }

    // ── 设备能力（功能页 v2：编辑器档位数据源，进程内采集一次）──

    private val _deviceCapsState = MutableStateFlow<JoyoseManager.DeviceCaps?>(null)
    val deviceCapsState = _deviceCapsState.asStateFlow()
    private var deviceCapsLoading = false

    /** 幂等采集：详情页进入时调用；null = 未采集或采集失败（编辑器档位退化为手输）。 */
    fun ensureDeviceCaps() {
        if (_deviceCapsState.value != null || deviceCapsLoading) return
        deviceCapsLoading = true
        viewModelScope.launch {
            _deviceCapsState.value = withContext(Dispatchers.IO) { joyose.deviceCaps() }
            deviceCapsLoading = false
        }
    }

    /** Joyose 应用版本（PackageManager 查询，无 root 开销）。 */
    val joyoseVersion: String get() = joyose.joyoseVersion()

    /** 单应用名查询（详情页应用卡；IO 线程调用，勿在组合期直接用）。 */
    fun appLabel(pkg: String): String = try {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    // ── 应用图标与应用名预加载（对齐 MainViewModel 的 ColorOS 实测做法）──
    // 解码/绘制全部在 IO 线程完成，UI 只读缓存（组合期零 PackageManager 调用）；
    // 异常包（图标损坏 / 未安装）记入 missingIconPackages 跳过重复尝试。

    /** 图标缓存 — 包名 → Bitmap（IO 线程写、UI 线程读，并发容器保证安全） */
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    /** 已确认无图标/图标损坏的包名集合 —— 跳过每轮 refresh 的重复尝试。 */
    private val missingIconPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 公开给 UI 层读取缓存图标（缓存未命中 = 占位）。 */
    fun getCachedIcon(pkg: String): Bitmap? = iconCache[pkg]

    /** 展示与排序所需的包元数据；一次 getPackageInfo 同时取到 label、时间戳与在装状态。 */
    private data class AppMeta(
        val label: String,
        val installTime: Long,
        val updateTime: Long,
        val installed: Boolean,
    )

    private fun preloadMeta(
        apps: List<JoyoseManager.AppIndexEntry>,
    ): Map<String, AppMeta> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val densityDpi = context.resources.displayMetrics.density
        if (iconCache.size > MAX_CACHED_ICONS) iconCache.clear()
        val meta = HashMap<String, AppMeta>()
        for (app in apps) {
            val pkg = app.pkg
            val info = try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: Exception) {
                continue // 已卸载：既无 label 也无图标
            }
            val applicationInfo = info.applicationInfo
            val label = if (applicationInfo != null) {
                pm.getApplicationLabel(applicationInfo).toString()
            } else {
                pkg // 未安装：以包名兜底展示，仍可编辑其云控片段
            }
            meta[pkg] = AppMeta(
                label = label,
                installTime = info.firstInstallTime,
                updateTime = info.lastUpdateTime,
                installed = applicationInfo != null,
            )
            if (applicationInfo == null) continue
            if (iconCache.containsKey(pkg) || missingIconPackages.contains(pkg)) continue
            try {
                val px = (44 * densityDpi).toInt().coerceAtLeast(48)
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val drawable = pm.getApplicationIcon(applicationInfo)
                drawable.setBounds(0, 0, px, px)
                drawable.draw(c)
                iconCache[pkg] = bmp
            } catch (_: Exception) {
                // 图标损坏：跳过重复尝试，UI 走占位（label 与时间戳仍可用）
                iconCache.remove(pkg)
                missingIconPackages.add(pkg)
            }
        }
        return meta
    }

    // ── 应用列表 ─────────────────────────────────────────────

    /** 一次刷新在 IO 线程产出的结果（避免往主线程回传三元组）。 */
    private data class Loaded(
        val rows: List<AppRow>,
        val frozen: Boolean,
        val sort: AppSortConfig,
        val installedOnly: Boolean,
    )

    data class ListState(
        val loading: Boolean = false,
        /** 下拉刷新中。与首屏 loading 分开：列表不清空，指示器由 PullToRefresh 驱动。 */
        val refreshing: Boolean = false,
        val unavailable: Boolean = false,
        /** 全部行（含未安装），已按 [sortConfig] 排序；[apps] 是它按可见性过滤后的结果。 */
        val allRows: List<AppRow> = emptyList(),
        val apps: List<AppRow> = emptyList(),
        val frozen: Boolean = false,
        val sortConfig: AppSortConfig = AppSortConfig(),
        /** 右上角筛选：true = 只显示已安装应用的配置。 */
        val showInstalledOnly: Boolean = false,
        val error: String? = null,
    )

    private val _listState = MutableStateFlow(ListState())
    val listState = _listState.asStateFlow()

    /** 可见性 + 排序的唯一出口（对齐 KernelSU updateVisibleApps 的单一职责）。 */
    private fun visibleRows(
        all: List<AppRow>,
        config: AppSortConfig,
        installedOnly: Boolean,
    ): List<AppRow> = sortApps(all.filter { !installedOnly || it.installed }, config)

    fun refreshList(pullToRefresh: Boolean = false) {
        val current = _listState.value
        if (current.loading || current.refreshing) return
        viewModelScope.launch {
            _listState.update {
                it.copy(
                    loading = !pullToRefresh && it.apps.isEmpty(),
                    refreshing = pullToRefresh,
                    error = null,
                )
            }
            val loaded: Loaded? = withContext(Dispatchers.IO) {
                val stat = joyose.stat()
                if (stat == null || (!stat.smartp.exists && !stat.teg.exists)) {
                    null
                } else {
                    val config = AppSortConfig.fromInt(settings.hyperOsAppSortOption)
                    val installedOnly = settings.hyperOsAppShowInstalledOnly
                    val entries = joyose.apps()
                    val meta = preloadMeta(entries)
                    val rows = entries.map { entry ->
                        val m = meta[entry.pkg]
                        val label = m?.label ?: entry.pkg
                        AppRow(
                            pkg = entry.pkg,
                            group = entry.group,
                            features = entry.features,
                            label = label,
                            firstInstallTime = m?.installTime ?: 0L,
                            lastUpdateTime = m?.updateTime ?: 0L,
                            installed = m?.installed ?: false,
                            pinyin = PinyinUtil.toPinyin(label),
                        )
                    }
                    Loaded(sortApps(rows, config), stat.sp.frozen, config, installedOnly)
                }
            }
            _listState.update {
                if (loaded == null) {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        unavailable = true,
                        allRows = emptyList(),
                        apps = emptyList(),
                    )
                } else {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        unavailable = false,
                        allRows = loaded.rows,
                        apps = visibleRows(loaded.rows, loaded.sort, loaded.installedOnly),
                        frozen = loaded.frozen,
                        sortConfig = loaded.sort,
                        showInstalledOnly = loaded.installedOnly,
                    )
                }
            }
        }
    }

    /** 下拉刷新（对齐 KernelSU onRefresh）：保留列表内容，只转刷新指示器。 */
    fun refreshFromPull() {
        refreshList(pullToRefresh = true)
    }

    /** 改排序：持久化选择 + 就地重排，不重新读库。 */
    fun updateSortConfig(config: AppSortConfig) {
        settings.hyperOsAppSortOption = config.toInt()
        _listState.update {
            it.copy(
                sortConfig = config,
                apps = visibleRows(it.allRows, config, it.showInstalledOnly),
            )
        }
    }

    /** 右上角筛选切换：所有应用配置 / 只显示已安装应用配置。 */
    fun setShowInstalledOnly(value: Boolean) {
        settings.hyperOsAppShowInstalledOnly = value
        _listState.update {
            it.copy(
                showInstalledOnly = value,
                apps = visibleRows(it.allRows, it.sortConfig, value),
            )
        }
    }

    // ── 应用功能页 ───────────────────────────────────────────

    data class DetailState(
        val loading: Boolean = false,
        val view: JoyoseManager.AppView? = null,
        val error: String? = null,
        /** 全局开关写入中（详情页/功能页共用开关写路径的并发守护）。 */
        val switchWriting: Boolean = false,
        /** 全局开关写入失败文案（仅失败显示；成功以开关状态为反馈）。 */
        val switchError: String? = null,
    )

    private val _detailState = MutableStateFlow(DetailState())
    val detailState = _detailState.asStateFlow()

    fun loadDetail(packageName: String) {
        // 片段文档与作用域编辑器共享（幂等）：参数编辑与 JSON 直编读写同一份状态
        loadScopedEditor(packageName)
        ensureDeviceCaps()
        viewModelScope.launch {
            _detailState.value = DetailState(loading = true)
            val state = withContext(Dispatchers.IO) {
                val view = joyose.appView(packageName)
                if (view == null) {
                    DetailState(error = "无法读取 $packageName 的云控配置")
                } else {
                    DetailState(view = view)
                }
            }
            _detailState.value = state
        }
    }

    // ── 功能页参数编辑（片段级）──────────────────────────────
    // 编辑 = 修改共享 ScopedEditorState.edited 里对应片段的 JSON（kotlinx API，
    // 只动片段内部，不手写全文档指针树）；保存复用 saveScopedEditor() 的 CLI
    // 补丁链路（joyose-scoped-write：双库镜像 + 回读校验 + 增/删/改名键报错）。

    /** 场景 booster 容器（booster / end / base_booster / booster#120 …）。 */
    data class SceneContainer(val key: String, val label: String, val entries: List<BoosterEntry>)
    data class BoosterEntry(val index: Int, val permission: String, val cmd: String)

    /** scene_ovrride 单个场景对象的解析结果（渲染用；编辑直接写 JSON）。 */
    data class SceneInfo(
        val index: Int,
        val sceneId: Long,
        val sceneName: String,
        val sceneNameLabel: String,
        val flags: List<Pair<String, Boolean>>,
        val containers: List<SceneContainer>,
        val reuseCmdConfig: List<String>,
    )

    /** 对指定片段做一次内存修改；返回 false = 状态未就绪或片段缺失（记日志留痕，UI 无感跳过）。 */
    private fun mutateScopedFragment(pointer: String, mutate: (JsonObject) -> JsonObject): Boolean {
        val st = _scopedEditorState.value
        val edited = st.edited
        if (edited == null) {
            Log.w(TAG, "scoped edit skipped: edited text not ready")
            return false
        }
        val doc = runCatching { strictJson.parseToJsonElement(edited).jsonObject }.getOrNull()
        if (doc == null) {
            Log.w(TAG, "scoped edit skipped: edited text is not a valid JSON object")
            return false
        }
        val frag = doc[pointer] as? JsonObject
        if (frag == null) {
            // 指针对不上 = Rust 侧指针与文档形状脱节（契约漂移），必须留痕排查
            Log.w(TAG, "scoped edit skipped: pointer $pointer missing or not an object")
            return false
        }
        val newFrag = mutate(frag)
        if (newFrag == frag) return false
        val newDoc = JsonObject(doc + (pointer to newFrag))
        _scopedEditorState.update {
            it.copy(edited = prettyJson.encodeToString(JsonObject.serializer(), newDoc), error = null)
        }
        return true
    }

    /** 片段内任意键值替换（标量/对象/数组均可；键必须已存在，新增键走作用域 JSON 编辑器）。 */
    fun updateFragmentValue(pointer: String, key: String, value: JsonElement) {
        mutateScopedFragment(pointer) { frag ->
            if (frag.containsKey(key)) JsonObject(frag + (key to value)) else frag
        }
    }

    /** 片段内嵌套路径替换（如 profile/键、数组下标；路径各段必须已存在）。 */
    fun updateFragmentNested(pointer: String, path: List<String>, value: JsonElement) {
        mutateScopedFragment(pointer) { frag -> setNested(frag, path, value) as JsonObject }
    }

    /** 片段本体替换（片段即标量/数组时使用，如 novatek token 串、gex 上限、黑名单数组）。 */
    fun updateFragmentSelf(pointer: String, value: JsonElement) {
        val st = _scopedEditorState.value
        val edited = st.edited
        if (edited == null) {
            Log.w(TAG, "scoped edit skipped: edited text not ready")
            return
        }
        val doc = runCatching { strictJson.parseToJsonElement(edited).jsonObject }.getOrNull()
        if (doc == null || !doc.containsKey(pointer)) {
            Log.w(TAG, "scoped edit skipped: pointer $pointer missing")
            return
        }
        val newDoc = JsonObject(doc + (pointer to value))
        _scopedEditorState.update {
            it.copy(edited = prettyJson.encodeToString(JsonObject.serializer(), newDoc), error = null)
        }
    }

    private fun setNested(element: JsonElement, path: List<String>, value: JsonElement): JsonElement {
        if (path.isEmpty()) return value
        val head = path.first()
        return when (element) {
            is JsonObject -> {
                val child = element[head] ?: return element
                JsonObject(element + (head to setNested(child, path.drop(1), value)))
            }
            is JsonArray -> {
                val idx = head.toIntOrNull() ?: return element
                if (idx !in element.indices) return element
                JsonArray(element.toMutableList().also { it[idx] = setNested(element[idx], path.drop(1), value) })
            }
            else -> element
        }
    }

    // ── 运行态 SP 回显（FisrScreen 等只读展示 Joyose 侧 SP 键值）──

    private val _spEchoState = MutableStateFlow<Map<String, String>>(emptyMap())
    val spEchoState = _spEchoState.asStateFlow()

    fun loadSpEcho(keys: List<String>) {
        viewModelScope.launch {
            _spEchoState.value = withContext(Dispatchers.IO) { joyose.readJoyoseSpKeys(keys) }
        }
    }

    /** 场景结构字段编辑（timeout/default_need/change_end/change_release_perflock_inner 等已有键）。 */
    fun updateSceneValue(pointer: String, sceneIndex: Int, key: String, value: JsonElement) {
        mutateScopedFragment(pointer) { frag ->
            val scenes = frag["scene_ovrride"] as? JsonArray ?: return@mutateScopedFragment frag
            val updated = scenes.mapIndexed { i, e ->
                val scene = e as? JsonObject
                if (i == sceneIndex && scene != null && scene.containsKey(key)) {
                    JsonObject(scene + (key to value))
                } else e
            }
            JsonObject(frag + ("scene_ovrride" to JsonArray(updated)))
        }
    }

    // ── 全局开关（详情页/功能页共用；与 CommonConfig 同一写路径）──

    /**
     * 翻转 game_booster 一级布尔开关（name 支持一层嵌套如 mivk_settings.enable）。
     * 乐观更新 detailState.view.globalSwitches + 失败回滚；成功不做回读（CLI 已回读校验）。
     */
    fun toggleGlobalSwitch(name: String, newValue: Boolean) {
        val st = _detailState.value
        if (st.view == null || st.switchWriting) return
        _detailState.update {
            it.copy(switchWriting = true, switchError = null, view = it.view?.withGlobalSwitch(name, newValue))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                joyose.toggleBoolean(listOf("game_booster") + name.split('.'), newValue)
            }
            _detailState.update {
                if (result.success) {
                    it.copy(switchWriting = false)
                } else {
                    it.copy(
                        switchWriting = false,
                        switchError = result.message,
                        view = it.view?.withGlobalSwitch(name, !newValue),
                    )
                }
            }
        }
    }

    private fun JoyoseManager.AppView.withGlobalSwitch(name: String, value: Boolean) = copy(
        globalSwitches = globalSwitches.map {
            if (it.name == name) it.copy(value = JsonPrimitive(value)) else it
        },
    )

    // ── migt 名单管理（专用命令路径：数组增删超出作用域编辑能力）──

    /** migt 功能屏状态：名单成员 + 可编辑参数包表单 + sysfs 运行态回显。 */
    data class MigtState(
        val pkg: String = "",
        val inList: Boolean = false,
        /** 当前条目解析结果（null = 不在名单或条目格式不识别 → 只读原始串展示）。 */
        val form: MigtCodec.Pack? = null,
        /** 原始条目串（格式不识别时兜底展示）。 */
        val raw: String? = null,
        val runtime: Map<String, String> = emptyMap(),
        val writing: Boolean = false,
        val error: String? = null,
        val loaded: Boolean = false,
    )

    private val _migtState = MutableStateFlow(MigtState())
    val migtState = _migtState.asStateFlow()

    /** migt 编辑器关注的 sysfs 参数（按 DeviceCaps 存在性回显）。 */
    private val MIGT_RUNTIME_PARAMS = listOf(
        "migt_freq", "migt_ms", "boost_policy", "fps_variance_ratio",
        "migt_ceiling_freq", "super_task_max_num", "target_fps", "glk_ms",
    )

    fun loadMigt(pkg: String) {
        if (_migtState.value.writing) return
        viewModelScope.launch {
            val doc = scopedDocument()
            val pointer = doc?.keys?.firstOrNull { it.startsWith("/game_booster/migt/") }
            val raw = pointer?.let { (doc?.get(it) as? JsonPrimitive)?.content }
            val runtime = withContext(Dispatchers.IO) {
                joyose.readMigtRuntimeParams(MIGT_RUNTIME_PARAMS)
            }
            _migtState.value = MigtState(
                pkg = pkg,
                inList = raw != null,
                form = raw?.let(MigtCodec::parse),
                raw = raw,
                runtime = runtime,
                loaded = true,
            )
        }
    }

    /** 保存参数包（joyose-migt-write：整条替换，一次落库）。 */
    fun saveMigtPack(pack: MigtCodec.Pack) {
        val st = _migtState.value
        if (st.writing || st.pkg.isEmpty()) return
        _migtState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { joyose.migtWrite(MigtCodec.serialize(pack)) }
            if (result.success) {
                _migtState.update {
                    it.copy(writing = false, inList = true, form = pack, raw = MigtCodec.serialize(pack))
                }
                reloadAfterMigtWrite(st.pkg)
            } else {
                _migtState.update { it.copy(writing = false, error = result.message) }
            }
        }
    }

    /** 名单进出：进 = 写入默认模板（DeviceCaps 推导）；出 = 移除条目。 */
    fun toggleMigtMembership(inList: Boolean) {
        val st = _migtState.value
        if (st.writing || st.pkg.isEmpty()) return
        _migtState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            val result = if (inList) {
                val template = st.form ?: defaultMigtTemplate(st.pkg)
                withContext(Dispatchers.IO) { joyose.migtWrite(MigtCodec.serialize(template)) }
            } else {
                withContext(Dispatchers.IO) { joyose.migtRemove(st.pkg) }
            }
            if (result.success) {
                if (inList) {
                    val template = st.form ?: defaultMigtTemplate(st.pkg)
                    _migtState.update {
                        it.copy(writing = false, inList = true, form = template, raw = MigtCodec.serialize(template))
                    }
                } else {
                    _migtState.update { it.copy(writing = false, inList = false, form = null, raw = null) }
                }
                reloadAfterMigtWrite(st.pkg)
            } else {
                _migtState.update { it.copy(writing = false, error = result.message) }
            }
        }
    }

    /**
     * migt 专用写后重载：数组重排/条目增删会使作用域草稿与详情视图过期，
     * 强制重读（草稿丢弃 —— migt 屏不与草稿体系混用，避免旧片段回写覆盖新条目）。
     */
    private fun reloadAfterMigtWrite(pkg: String) {
        _scopedEditorState.value = ScopedEditorState()
        loadDetail(pkg)
    }

    /** 默认参数包模板：每核取所在簇最低频率（DeviceCaps 缺失时退回 8 核常用值）。 */
    private fun defaultMigtTemplate(pkg: String): MigtCodec.Pack {
        val caps = _deviceCapsState.value
        val freqTable = caps?.cpuClusters
            ?.takeIf { it.isNotEmpty() }
            ?.flatMap { cluster -> cluster.cpus.map { cpu -> "$cpu:${cluster.frequencies.minOrNull() ?: 0}" } }
            ?.joinToString(" ")
            ?: "0:384000 1:384000 2:384000 3:384000 4:384000 5:384000 6:1017600 7:1017600"
        return MigtCodec.Pack(
            pkg = pkg,
            migtFreq = freqTable,
            migtMs = 30,
            fpsThresh = "90:15 60:18 40:30 30:40",
            boostPolicy = 2,
            fpsVarianceRatio = 10,
            superTaskMaxNum = 1,
            migtCeilingFreq = null,
        )
    }

    /** 当前作用域草稿的解析文档（未就绪/解析失败返回 null）。 */
    private fun scopedDocument(): JsonObject? {
        val edited = _scopedEditorState.value.edited ?: return null
        return runCatching { strictJson.parseToJsonElement(edited).jsonObject }.getOrNull()
    }

    /** 场景 booster 容器内单条 cmd 编辑。 */
    fun updateSceneCmd(pointer: String, sceneIndex: Int, containerKey: String, boosterIndex: Int, cmd: String) {
        mutateScopedFragment(pointer) { frag ->
            val scenes = frag["scene_ovrride"] as? JsonArray ?: return@mutateScopedFragment frag
            val updated = scenes.mapIndexed { i, e ->
                val scene = e as? JsonObject ?: return@mapIndexed e
                if (i != sceneIndex) return@mapIndexed e
                val container = scene[containerKey] as? JsonArray ?: return@mapIndexed e
                val newContainer = container.mapIndexed { bi, be ->
                    val entry = be as? JsonObject ?: return@mapIndexed be
                    if (bi == boosterIndex && entry["cmd"] is JsonPrimitive) {
                        JsonObject(entry + ("cmd" to JsonPrimitive(cmd)))
                    } else be
                }
                JsonObject(scene + (containerKey to JsonArray(newContainer)))
            }
            JsonObject(frag + ("scene_ovrride" to JsonArray(updated)))
        }
    }

    /** scene_ovrride 数组解析为 UI 渲染模型（不认识的容器键一律跳过，宁缺勿错）。 */
    fun parseSceneInfo(fragment: JsonObject): List<SceneInfo> {
        val scenes = fragment["scene_ovrride"] as? JsonArray ?: return emptyList()
        val metaKeys = setOf(
            "scene_id", "scene_name", "scene_id_list", "scene_id_reuse",
            "change_end", "change_release_perflock_inner", "reuse_cmd_config",
        )
        return scenes.mapIndexedNotNull { idx, e ->
            val scene = e as? JsonObject ?: return@mapIndexedNotNull null
            val containers = scene.mapNotNull { (key, v) ->
                if (key in metaKeys) return@mapNotNull null
                val list = v as? JsonArray ?: return@mapNotNull null
                val entries = list.mapIndexedNotNull { bi, be ->
                    val entry = be as? JsonObject ?: return@mapIndexedNotNull null
                    val cmd = (entry["cmd"] as? JsonPrimitive)?.content ?: return@mapIndexedNotNull null
                    val permission = (entry["permission"] as? JsonPrimitive)?.content ?: ""
                    if (entry.keys.any { it != "cmd" && it != "permission" }) return@mapIndexedNotNull null
                    BoosterEntry(bi, permission, cmd)
                }
                if (entries.size != list.size || entries.isEmpty()) return@mapNotNull null
                SceneContainer(key, sceneContainerLabel(key), entries)
            }
            val id = (scene["scene_id"] as? JsonPrimitive)?.content?.toLongOrNull() ?: -1L
            val name = (scene["scene_name"] as? JsonPrimitive)?.content ?: ""
            SceneInfo(
                index = idx,
                sceneId = id,
                sceneName = name,
                sceneNameLabel = sceneNameLabel(id, name),
                flags = listOf("change_release_perflock_inner", "scene_id_reuse", "change_end")
                    .mapNotNull { k -> (scene[k] as? JsonPrimitive)?.takeIf { it.booleanOrNull != null }?.boolean?.let { k to it } },
                containers = containers,
                reuseCmdConfig = (scene["reuse_cmd_config"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            )
        }
    }

    companion object {
        private const val TAG = "HyperOsViewModel"
        private const val BOOSTER = JoyoseManager.CONFIG_BOOSTER

        /** 图标缓存容量上限（超过整体清空，下一轮 refresh 只重建当前列表） */
        private const val MAX_CACHED_ICONS = 500

        /** kotlinx 默认实例即严格模式；作用域/整文档写回前都用它把关。 */
        val strictJson = Json

        /** 编辑器展示与载入统一 2 空格缩进（对齐应用功能页的 PrettyJson）。 */
        val prettyJson = Json { prettyPrint = true }

        /** 已观测的 scene_id 语义（booster_config 云控实测）；未知 id 回退原名。 */
        private val SCENE_NAMES = mapOf(
            10004L to "冷启动", 10001L to "前台", 1004L to "帧率切换",
            3L to "登录", 5L to "加载", 4L to "大厅", 10000L to "默认",
        )

        private fun sceneNameLabel(id: Long, name: String): String =
            SCENE_NAMES[id]?.let { "$it $name" } ?: name

        /** 容器键中文说明：booster=场景提频, end=结束恢复, base_booster=复用基准, booster#N=帧率档位。 */
        private fun sceneContainerLabel(key: String): String = when {
            key == "booster" -> "场景提频"
            key == "end" -> "场景结束恢复"
            key == "base_booster" -> "复用基准"
            key == "booster_config" -> "提频命令"
            key.startsWith("booster#") -> {
                val parts = key.removePrefix("booster#").split('#')
                val fps = parts.firstOrNull()?.toIntOrNull()
                val mods = parts.drop(1).joinToString("+")
                buildString {
                    if (fps != null) append("${fps}帧档位")
                    if (mods.isNotEmpty()) append(if (fps != null) " · $mods" else parts.joinToString("+"))
                }
            }
            else -> key
        }
    }

    // ── 高级 JSON 编辑 ───────────────────────────────────────

    data class EditorState(
        val loading: Boolean = false,
        val writing: Boolean = false,
        val configName: String? = null,
        /** 最近一次从 DB 载入的原文 —— 与 edited 比较得出保存可用性。 */
        val json: String? = null,
        /** 当前编辑文本（onTextChanged 实时更新）。 */
        val edited: String? = null,
        val error: String? = null,
    )

    private val _editorState = MutableStateFlow(EditorState())
    val editorState = _editorState.asStateFlow()

    fun loadEditor(configName: String) {
        if (_editorState.value.configName == configName && _editorState.value.json != null) return
        viewModelScope.launch {
            _editorState.value = EditorState(loading = true, configName = configName)
            val state = withContext(Dispatchers.IO) {
                val raw = joyose.readRaw(configName)
                if (raw == null) {
                    EditorState(configName = configName, error = "无法读取 $configName（SmartP 与 teg 均无此配置）")
                } else {
                    EditorState(configName = configName, json = raw, edited = raw)
                }
            }
            _editorState.value = state
        }
    }

    fun updateEdited(text: String) {
        _editorState.update { it.copy(edited = text, error = null) }
    }

    /** 保存：严格语法校验后走 joyose-write 双库镜像写（内部含回读校验）。 */
    fun saveEditor() {
        val st = _editorState.value
        val config = st.configName ?: return
        if (st.writing || st.edited == null || st.edited == st.json) return
        val edited = st.edited
        _editorState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            val error = writeValidated(edited) { joyose.writeConfig(config, it) }
            _editorState.update {
                if (error == null) it.copy(writing = false, json = edited)
                else it.copy(writing = false, error = error)
            }
        }
    }

    // ── 作用域编辑（App 专属片段）─────────────────────────────
    // 作用域文档 = { "<JSON Pointer>": <该 App 名下的原始片段> }，键即回写指针。
    // 指针解析、片段抽取与格式化全部在 libcosa.so 内完成（joyose-scoped /
    // joyose-scoped-write）：App 进程只搬运几十 KB 片段，既不必解析 346KB 整份
    // 文档（打开慢的根因），也不会因为"整文档重序列化"波及其他 App 与全局配置。
    // 新增/改名/删除键由 CLI 直接报错，不存在静默丢弃。

    data class ScopedEditorState(
        val loading: Boolean = false,
        val writing: Boolean = false,
        val packageName: String? = null,
        /** 载入时的原文 —— 与 edited 比较得出未保存标记。 */
        val base: String? = null,
        /** 当前编辑文本（onTextChanged 实时更新）。 */
        val edited: String? = null,
        val error: String? = null,
        /** CLI 未能解析的指针（正常为空；非空即 Rust 侧指针与文档形状脱节）。 */
        val warning: String? = null,
    )

    private val _scopedEditorState = MutableStateFlow(ScopedEditorState())
    val scopedEditorState = _scopedEditorState.asStateFlow()

    fun loadScopedEditor(packageName: String) {
        if (_scopedEditorState.value.packageName == packageName && _scopedEditorState.value.base != null) return
        viewModelScope.launch {
            _scopedEditorState.value = ScopedEditorState(loading = true, packageName = packageName)
            val state = withContext(Dispatchers.IO) {
                val scoped = joyose.scoped(packageName)
                when {
                    scoped == null ->
                        ScopedEditorState(packageName = packageName, error = "无法读取该应用的云控片段")
                    scoped.document.isEmpty() ->
                        ScopedEditorState(packageName = packageName, error = "该应用没有 per-app 云控片段")
                    else -> {
                        val text = prettyJson.encodeToString(JsonObject.serializer(), scoped.document)
                        ScopedEditorState(
                            packageName = packageName,
                            base = text,
                            edited = text,
                            warning = scoped.skipped.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        )
                    }
                }
            }
            _scopedEditorState.value = state
        }
    }

    fun updateScopedEdited(text: String) {
        _scopedEditorState.update { it.copy(edited = text, error = null) }
    }

    /** 放弃修改：edited 重置回 base（功能页与作用域编辑器的丢弃确认共用）。
     *  不重置的话 loadScopedEditor 的幂等守卫会直接命中，脏草稿在下次进入时复活。
     *  写入进行中不动 —— 写完成后 base/edited 自然对齐，避免与在途保存竞争。 */
    fun revertScopedEditor() {
        val st = _scopedEditorState.value
        if (st.writing || st.base == null || st.edited == st.base) return
        _scopedEditorState.update { it.copy(edited = st.base, error = null) }
    }

    /** 保存作用域编辑：CLI 按指针把片段补丁进当前库里的整份文档后双库镜像写。 */
    fun saveScopedEditor() {
        val st = _scopedEditorState.value
        val pkg = st.packageName ?: return
        if (st.writing || st.edited == null || st.edited == st.base) return
        val edited = st.edited
        _scopedEditorState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            val error = writeValidated(edited) { joyose.writeScoped(pkg, it) }
            _scopedEditorState.update {
                if (error == null) it.copy(writing = false, base = edited)
                else it.copy(writing = false, error = error)
            }
        }
    }

    /**
     * 严格 JSON 语法校验（不放过单引号/无引号键——写进云控库的必须是标准 JSON，
     * 失败时带上解析器给出的位置），通过后交给 [write] 落库。
     * 返回 null 表示成功，否则为可直接展示的错误文案。
     */
    private suspend fun writeValidated(
        text: String,
        write: (String) -> JoyoseManager.WriteResult,
    ): String? = withContext(Dispatchers.IO) {
        runCatching { strictJson.parseToJsonElement(text) }
            .exceptionOrNull()
            ?.let { return@withContext "JSON 语法错误：${it.message?.replace('\n', ' ')}" }
        write(text).let { if (it.success) null else it.message }
    }

    // ── 通用配置（布尔开关）──────────────────────────────────

    /** 一行开关：path 供写回定位（支持一层嵌套，如 mivk_settings.enable）。 */
    data class SwitchRow(
        val name: String,
        val label: String,
        val group: String,
        val value: Boolean,
        val path: List<String>,
    )

    data class CommonState(
        val loading: Boolean = false,
        val writing: Boolean = false,
        val switches: List<SwitchRow> = emptyList(),
        val version: String? = null,
        val frozen: Boolean = false,
        val error: String? = null,
    )

    private val _commonState = MutableStateFlow(CommonState())
    val commonState = _commonState.asStateFlow()

    fun refreshCommon() {
        if (_commonState.value.loading || _commonState.value.writing) return
        viewModelScope.launch {
            _commonState.update { it.copy(loading = true, error = null) }
            val state = withContext(Dispatchers.IO) {
                val stat = joyose.stat()
                val view = joyose.appView(BOOSTER)
                when {
                    stat == null || view == null ->
                        CommonState(error = "Joyose 云控不可读，请确认设备与 root 权限")
                    else -> CommonState(
                        switches = view.globalSwitches.map { it.toSwitchRow() },
                        version = joyose.list().cloudConfigVersion(),
                        frozen = stat.sp.frozen,
                    )
                }
            }
            _commonState.value = state
        }
    }

    /** 从 joyose-list 取 booster_config 的云控版本（version 列）。 */
    private fun JoyoseManager.ListResult?.cloudConfigVersion(): String? =
        this?.cloudConfig?.firstOrNull { it.configName == JoyoseManager.CONFIG_BOOSTER }?.version?.toString()

    /** 乐观更新 + 写后确认；失败回滚并呈现 CLI 错误文本。
     *  成功不做整页回读刷新（CLI 内部已回读校验），且不整页禁用——
     *  开关状态本身就是反馈；并发由 writing 守卫拦截（主线程同步置位）。 */
    fun toggleSwitch(row: SwitchRow) {
        if (_commonState.value.writing) return
        _commonState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            // 乐观翻转
            _commonState.update { state ->
                state.copy(switches = state.switches.map {
                    if (it.path == row.path) it.copy(value = !it.value) else it
                })
            }
            val result = withContext(Dispatchers.IO) {
                joyose.toggleBoolean(row.path, !row.value)
            }
            if (result.success) {
                _commonState.update { it.copy(writing = false) }
            } else {
                // 回滚乐观更新
                _commonState.update { state ->
                    state.copy(
                        writing = false,
                        error = result.message,
                        switches = state.switches.map {
                            if (it.path == row.path) it.copy(value = row.value) else it
                        },
                    )
                }
            }
        }
    }

    /** 冻结/解冻：乐观置位 frozen，失败回滚（frozen 即开关状态，无需整页刷新）。 */
    fun freeze() {
        if (_commonState.value.writing) return
        _commonState.update { it.copy(writing = true, error = null, frozen = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { joyose.freeze() }
            _commonState.update {
                if (result.success) it.copy(writing = false)
                else it.copy(writing = false, frozen = false, error = result.message)
            }
        }
    }

    fun unfreeze() {
        if (_commonState.value.writing) return
        _commonState.update { it.copy(writing = true, error = null, frozen = false) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { joyose.unfreeze() }
            _commonState.update {
                if (result.success) it.copy(writing = false)
                else it.copy(writing = false, frozen = true, error = result.message)
            }
        }
    }

    private fun JoyoseManager.ParamItem.toSwitchRow(): SwitchRow {
        // globalSwitches 的 name 是 game_booster 的顶层键（嵌套 enable 带点号），
        // 写回路径必须以 game_booster 为根前缀。
        val path = listOf("game_booster") + name.split('.')
        return SwitchRow(
            name = name,
            label = HyperOsSwitchCatalog.label(name),
            group = HyperOsSwitchCatalog.group(name),
            value = (value as? kotlinx.serialization.json.JsonPrimitive)?.content == "true",
            path = path,
        )
    }
}

/**
 * 布尔开关中文名与分组目录（业务含义来自 HyperOS 云控调研，实测 songyuan OS3.0）。
 * 未来云控新增的未知键落入「其他」组原样展示，不丢失。
 */
object HyperOsSwitchCatalog {

    private val labels: Map<String, String> = mapOf(
        "booster_enable" to "游戏加速总开关",
        "cpuset_enable" to "CPU 集群绑定调度",
        "tuner_enable" to "性能调谐器",
        "qsync_enable" to "QSync 同步刷新",
        "action_key_optimized" to "按键操作优化",
        "fisr_mqs_v2" to "FISR MQS v2 增强",
        "key_mivk_gputuner_select_enable" to "MIVK GPU 调谐档位选择",
        "support_new_gpu_tunermode" to "新一代 GPU 调谐模式",
        "game_group_mapping_enable" to "游戏分组映射",
        "cgame_enable" to "云控游戏接管（锁帧总闸）",
        "SOC_enable" to "SOC 平台调度",
        "self_gpu_tuner_enable" to "自研 GPU 调谐器",
        "affinity_enable" to "CPU 绑核",
        "support_ysre" to "YSRE 超分",
        "scene_id_reuse" to "场景 ID 复用",
        "GameSight" to "GameSight 画面识别",
        "scene_id_sender_enable" to "场景 ID 上报",
        "predownload_enable" to "游戏资源预下载",
        "multi_windows_v2" to "多窗口优化 v2",
        "background_freeze_enable" to "后台冻结",
        "scale_app_enable" to "分辨率缩放",
        "qfps_enable" to "QFPS 帧率调度",
        "force_set_drr_path" to "强制 DRR 动态分辨率通路",
        "mivk_settings.enable" to "MIVK（Vulkan 过渲染）",
        "migl_settings.enable" to "MIGL（OpenGL 过渲染）",
    )

    private val groups: Map<String, String> = mapOf(
        "booster_enable" to "基础加速",
        "cpuset_enable" to "基础加速",
        "affinity_enable" to "基础加速",
        "tuner_enable" to "基础加速",
        "SOC_enable" to "基础加速",
        "self_gpu_tuner_enable" to "基础加速",
        "support_new_gpu_tunermode" to "基础加速",
        "key_mivk_gputuner_select_enable" to "基础加速",
        "cgame_enable" to "游戏接管与帧率",
        "qfps_enable" to "游戏接管与帧率",
        "qsync_enable" to "游戏接管与帧率",
        "force_set_drr_path" to "游戏接管与帧率",
        "support_ysre" to "渲染与超分",
        "fisr_mqs_v2" to "渲染与超分",
        "mivk_settings.enable" to "渲染与超分",
        "migl_settings.enable" to "渲染与超分",
        "game_group_mapping_enable" to "显示与场景",
        "scene_id_reuse" to "显示与场景",
        "scene_id_sender_enable" to "显示与场景",
        "GameSight" to "显示与场景",
        "action_key_optimized" to "显示与场景",
        "multi_windows_v2" to "显示与场景",
        "background_freeze_enable" to "系统行为",
        "scale_app_enable" to "系统行为",
        "predownload_enable" to "系统行为",
    )

    private const val FALLBACK_GROUP = "其他"

    fun label(name: String): String = labels[name] ?: name

    fun group(name: String): String = groups[name] ?: FALLBACK_GROUP

    /** 分组展示顺序；「其他」恒排最后。 */
    fun orderedGroups(names: List<String>): List<String> {
        val present = names.map(::group).toSet()
        val known = listOf("基础加速", "游戏接管与帧率", "渲染与超分", "显示与场景", "系统行为")
        return known.filter { it in present } + listOf(FALLBACK_GROUP).filter { it in present }
    }
}
