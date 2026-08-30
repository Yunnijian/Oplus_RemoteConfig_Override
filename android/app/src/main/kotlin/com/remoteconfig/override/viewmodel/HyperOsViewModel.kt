package com.remoteconfig.override.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remoteconfig.override.data.JoyoseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HyperOS 云控 ViewModel —— 应用列表 / 应用功能页 / 通用配置（布尔开关）三块状态。
 * 数据源为 [JoyoseManager]（root shell + libcosa.so joyose-* 命令族），
 * 所有 shell 调用均置于 IO 调度器；写路径带并发守护（防 root shell 慢时重复触发）。
 */
class HyperOsViewModel(application: Application) : AndroidViewModel(application) {

    private val joyose = JoyoseManager(application)

    // ── 首页状态卡 ───────────────────────────────────────────

    private val _statState = MutableStateFlow<JoyoseManager.Stat?>(null)
    val statState = _statState.asStateFlow()

    /** 轻量刷新：仅 stat（双库存在性 + 冻结状态），首页状态卡用。 */
    fun refreshStat() {
        viewModelScope.launch {
            _statState.value = withContext(Dispatchers.IO) { joyose.stat() }
        }
    }

    /** Joyose 应用版本（PackageManager 查询，无 root 开销）。 */
    val joyoseVersion: String get() = joyose.joyoseVersion()

    // ── 应用图标与应用名预加载（对齐 MainViewModel 的 ColorOS 实测做法）──
    // 解码/绘制全部在 IO 线程完成，UI 只读缓存（组合期零 PackageManager 调用）；
    // 异常包（图标损坏 / 未安装）记入 missingIconPackages 跳过重复尝试。

    /** 图标缓存 — 包名 → Bitmap（IO 线程写、UI 线程读，并发容器保证安全） */
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    /** 已确认无图标/图标损坏的包名集合 —— 跳过每轮 refresh 的重复尝试。 */
    private val missingIconPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val appLabels = _appLabels.asStateFlow()

    /** 公开给 UI 层读取缓存图标（缓存未命中 = 占位）。 */
    fun getCachedIcon(pkg: String): Bitmap? = iconCache[pkg]

    private fun preloadIconsAndLabels(
        apps: List<JoyoseManager.AppIndexEntry>,
    ): Map<String, String> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val densityDpi = context.resources.displayMetrics.density
        if (iconCache.size > MAX_CACHED_ICONS) iconCache.clear()
        val labels = HashMap<String, String>()
        for (app in apps) {
            val pkg = app.pkg
            if (missingIconPackages.contains(pkg)) continue
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                labels[pkg] = pm.getApplicationLabel(info).toString()
                if (!iconCache.containsKey(pkg)) {
                    val drawable = pm.getApplicationIcon(info)
                    val px = (44 * densityDpi).toInt().coerceAtLeast(48)
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    drawable.setBounds(0, 0, px, px)
                    drawable.draw(c)
                    iconCache[pkg] = bmp
                }
                missingIconPackages.remove(pkg)
            } catch (_: Exception) {
                // 图标损坏 / 未安装：跳过重复尝试，UI 走占位
                iconCache.remove(pkg)
                missingIconPackages.add(pkg)
            }
        }
        return labels
    }

    // ── 应用列表 ─────────────────────────────────────────────

    data class ListState(
        val loading: Boolean = false,
        val unavailable: Boolean = false,
        val apps: List<JoyoseManager.AppIndexEntry> = emptyList(),
        val labels: Map<String, String> = emptyMap(),
        val frozen: Boolean = false,
        val error: String? = null,
    )

    private val _listState = MutableStateFlow(ListState())
    val listState = _listState.asStateFlow()

    fun refreshList() {
        if (_listState.value.loading) return
        viewModelScope.launch {
            _listState.update { it.copy(loading = true, error = null) }
            val state = withContext(Dispatchers.IO) {
                val stat = joyose.stat()
                if (stat == null || (!stat.smartp.exists && !stat.teg.exists)) {
                    ListState(unavailable = true)
                } else {
                    val apps = joyose.apps()
                    // 图标与应用名在 IO 线程预加载（对齐 ColorOS 路径），UI 只读缓存
                    val labels = preloadIconsAndLabels(apps)
                    ListState(apps = apps, labels = labels, frozen = stat.sp.frozen)
                }
            }
            _listState.value = state
        }
    }

    // ── 应用功能页 ───────────────────────────────────────────

    data class DetailState(
        val loading: Boolean = false,
        val view: JoyoseManager.AppView? = null,
        val error: String? = null,
    )

    private val _detailState = MutableStateFlow(DetailState())
    val detailState = _detailState.asStateFlow()

    fun loadDetail(packageName: String) {
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

    companion object {
        private const val BOOSTER = JoyoseManager.CONFIG_BOOSTER

        /** 图标缓存容量上限（超过整体清空，下一轮 refresh 只重建当前列表） */
        private const val MAX_CACHED_ICONS = 500
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
