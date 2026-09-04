package com.remoteconfig.override.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remoteconfig.override.data.DatabaseManager
import com.remoteconfig.override.model.GameConfigSummary
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bug 3: 图标缓存容量上限——超过后整体清空，下一轮 refresh 只重建当前配置包（内存上限保护）。 */
private const val MAX_CACHED_ICONS = 200

/**
 * 主 ViewModel — 所有配置以原始 JSON 字符串形式处理。
 *
 * 数据流：
 *   数据库 → Rust/rusqlite → JSON → UI 编辑 → Rust/rusqlite → 数据库
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dbManager = DatabaseManager(application)

    // ── 状态 ─────────────────────────────────────────────────

    private val _gameList = MutableStateFlow<List<GameConfigSummary>>(emptyList())
    val gameList: StateFlow<List<GameConfigSummary>> = _gameList.asStateFlow()

    private val _systemStatus = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    /** 已配置且当前已安装的应用数（供首页"已安装应用配置"计数卡）。 */
    private val _installedConfigCount = MutableStateFlow(0)
    val installedConfigCount: StateFlow<Int> = _installedConfigCount.asStateFlow()

    /** 当前编辑的原始 JSON 文本 */
    private val _editingJson = MutableStateFlow<String?>(null)
    val editingJson: StateFlow<String?> = _editingJson.asStateFlow()

    private val _editingPackageName = MutableStateFlow<String?>(null)
    val editingPackageName: StateFlow<String?> = _editingPackageName.asStateFlow()

    /** 列表刷新 loading —— 仅 refreshAll 置位（列表型操作共用） */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 编辑器写入/删除/清除守护 —— 防止 root shell 慢（最长 10s）时重复触发并发操作，
     * 且不再驱动列表的全屏 spinner（避免双窗下编辑器操作把左列表盖成 loading）。
     */
    private val _isWriting = MutableStateFlow(false)
    val isWriting: StateFlow<Boolean> = _isWriting.asStateFlow()

    /** 编辑器/窗格加载 loading —— 仅 loadConfig 置位（与列表刷新分离，避免双窗串扰） */
    private val _isEditorLoading = MutableStateFlow(false)
    val isEditorLoading: StateFlow<Boolean> = _isEditorLoading.asStateFlow()

    private val _hasDbData = MutableStateFlow(false)
    val hasDbData: StateFlow<Boolean> = _hasDbData.asStateFlow()

    // ── 列表排序/可见性（对齐 HyperOsViewModel，组件对齐 KernelSU）──
    private val settings = com.remoteconfig.override.settings.SettingsRepositoryImpl()

    /** 下拉刷新中（与首屏 isLoading 分开：不清空列表，只转指示器）。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _sortConfig = MutableStateFlow(
        AppSortConfig.fromInt(settings.colorosAppSortOption),
    )
    val sortConfig: StateFlow<AppSortConfig> = _sortConfig.asStateFlow()

    private val _showInstalledOnly = MutableStateFlow(settings.colorosAppShowInstalledOnly)
    val showInstalledOnly: StateFlow<Boolean> = _showInstalledOnly.asStateFlow()

    /** 全部行（含未安装）；[gameList] 是它按可见性 + 排序过滤后的结果。 */
    private var allRows: List<GameConfigSummary> = emptyList()

    private fun visibleRows(
        all: List<GameConfigSummary>,
        config: AppSortConfig,
        installedOnly: Boolean,
    ): List<GameConfigSummary> =
        sortSummaries(all.filter { !installedOnly || it.isInstalled }, config)

    /** 排序实现与 AppSort.sortApps 一致（NAME 走本地化 Collator），作用于摘要行。 */
    private fun sortSummaries(
        rows: List<GameConfigSummary>,
        config: AppSortConfig,
    ): List<GameConfigSummary> {
        val collator = java.text.Collator.getInstance(java.util.Locale.getDefault())
        val base: Comparator<GameConfigSummary> = when (config.sortType) {
            AppSortType.PACKAGE_NAME -> compareBy { it.packageName }
            AppSortType.INSTALL_TIME -> compareBy { it.installTime }
            AppSortType.UPDATE_TIME -> compareBy { it.updateTime }
            AppSortType.NAME -> Comparator { a, b -> collator.compare(a.appName, b.appName) }
        }
        return rows.sortedWith(if (config.reversed) base.reversed() else base)
    }

    /**
     * 编辑基准文本（最近一次从数据库/模板载入的原文）——与 [editingJson] 比较
     * 得出脏标记，用于退出/切换/导入前的“未保存修改”拦截，避免静默丢稿。
     */
    private val _baselineJson = MutableStateFlow<String?>(null)
    val baselineJson: StateFlow<String?> = _baselineJson.asStateFlow()

    /** 预加载的应用图标缓存 — 包名 → Bitmap（IO 线程写、UI 线程读，用并发容器保证安全） */
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    /** Bug 3: 确认未安装（无图标）的包名集合 —— 跳过每轮 refresh 的重复解码尝试。 */
    private val missingIconPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 公开给 UI 层读取缓存图标 */
    fun getCachedIcon(pkg: String): Bitmap? = iconCache[pkg]

    private val _cosaVersion = MutableStateFlow("")
    val cosaVersion: StateFlow<String> = _cosaVersion.asStateFlow()

    // ── 初始化 ───────────────────────────────────────────────

    init { refreshAll() }

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 单次 `list` 同时供系统状态（数量）与游戏列表使用，不再各查一遍 shell
                val configuredPkgs = checkSystemStatus()
                loadGameList(configuredPkgs)
            } finally {
                // 必须 finally 复位：loadGameList 里的图标解码会抛 Error（OOM 不被内层
                // catch(_:Exception) 捕获），漏一次就让列表永久转圈且再也刷不动。
                _isLoading.value = false
            }
        }
    }

    /** 下拉刷新（对齐 KernelSU onRefresh）：保留列表内容，只转刷新指示器。 */
    fun refreshFromPull() {
        if (_refreshing.value || _isLoading.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val configuredPkgs = checkSystemStatus()
                loadGameList(configuredPkgs)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** 改排序：持久化选择 + 就地重排，不重新读库。 */
    fun updateSortConfig(config: AppSortConfig) {
        settings.colorosAppSortOption = config.toInt()
        _sortConfig.value = config
        _gameList.value = visibleRows(allRows, config, _showInstalledOnly.value)
    }

    /** 右上角筛选：所有应用配置 / 只显示已安装应用配置。 */
    fun setShowInstalledOnly(value: Boolean) {
        settings.colorosAppShowInstalledOnly = value
        _showInstalledOnly.value = value
        _gameList.value = visibleRows(allRows, _sortConfig.value, value)
    }

    /** 检测 Root/数据库状态并返回配置包名列表（数据库不可访问时为空表）。 */
    private suspend fun checkSystemStatus(): List<String> {
        // 所有 Root Shell / 数据库调用放到 IO 线程，避免阻塞主线程导致 ANR。
        val (status, configuredPkgs, version) = withContext(Dispatchers.IO) {
            val isRooted = try { dbManager.checkRoot() } catch (_: Exception) { false }
            val pkgs = if (isRooted) {
                try { dbManager.listConfiguredPackagesOrNull() } catch (_: Exception) { null }
            } else null
            val ver = if (isRooted) {
                try { dbManager.getCosaVersion() } catch (_: Exception) { "未知" }
            } else ""
            Triple(
                SystemStatus(isRooted, pkgs != null, pkgs?.size ?: 0),
                pkgs.orEmpty(),
                ver,
            )
        }
        _systemStatus.value = status.copy(checked = true)
        if (status.isRooted) {
            _cosaVersion.value = version
        }
        return configuredPkgs
    }

    private suspend fun loadGameList(configuredPkgs: List<String>) {
        _hasDbData.value = configuredPkgs.isNotEmpty()
        val context = getApplication<Application>()

        // 后台线程预加载：包名 → 摘要（含 label / 图标 / 在装状态 / 时间戳 / 拼音）
        val results = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val densityDpi = context.resources.displayMetrics.density
            // Bug 3: 容量上限保护——条目超过上限整体清空（下一轮 refresh 只重建当前配置包）
            if (iconCache.size > MAX_CACHED_ICONS) iconCache.clear()
            configuredPkgs.map { pkg ->
                if (missingIconPackages.contains(pkg)) {
                    // Bug 3: 已知未安装（无图标）——跳过重复解码尝试，直接走占位图标
                    GameConfigSummary(
                        packageName = pkg,
                        appName = pkg,
                        hasConfig = true,
                        isInstalled = false,
                        pinyin = com.remoteconfig.override.ui.util.PinyinUtil.toPinyin(pkg),
                    )
                } else {
                    try {
                        val info = pm.getPackageInfo(pkg, 0)
                        val applicationInfo = info.applicationInfo
                        val installed = applicationInfo != null
                        val label = if (applicationInfo != null) {
                            pm.getApplicationLabel(applicationInfo).toString()
                        } else {
                            pkg
                        }
                        // Bug 3: 已缓存图标跳过重建（解码/绘 Bitmap 是主要内存与耗时来源），
                        // refreshAll 只补新增包；label 查询是廉价 Binder 调用，保持列表名最新
                        if (installed && !iconCache.containsKey(pkg)) {
                            val drawable = pm.getApplicationIcon(applicationInfo!!)
                            val px = (44 * densityDpi).toInt().coerceAtLeast(48)
                            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                            val c = Canvas(bmp)
                            drawable.setBounds(0, 0, px, px)
                            drawable.draw(c)
                            iconCache[pkg] = bmp
                        }
                        missingIconPackages.remove(pkg)
                        GameConfigSummary(
                            packageName = pkg,
                            appName = label,
                            hasConfig = true,
                            isInstalled = installed,
                            installTime = info.firstInstallTime,
                            updateTime = info.lastUpdateTime,
                            pinyin = com.remoteconfig.override.ui.util.PinyinUtil.toPinyin(label),
                        )
                    } catch (_: Exception) {
                        // ConcurrentHashMap 不接受 null 值：移除旧缓存让 UI 走占位图标
                        iconCache.remove(pkg)
                        missingIconPackages.add(pkg)
                        GameConfigSummary(
                            packageName = pkg,
                            appName = pkg,
                            hasConfig = true,
                            isInstalled = false,
                            pinyin = com.remoteconfig.override.ui.util.PinyinUtil.toPinyin(pkg),
                        )
                    }
                }
            }
        }

        allRows = results
        _installedConfigCount.value = results.count { it.isInstalled }
        _gameList.value = visibleRows(results, _sortConfig.value, _showInstalledOnly.value)
    }

    // ── 搜索 ─────────────────────────────────────────────────
    // ── 配置编辑 ─────────────────────────────────────────────

    // 上次 loadConfig 的任务：快速切换包时取消上一次，避免并发覆盖（P1-2 竞态）
    private var loadConfigJob: Job? = null

    /**
     * 从数据库加载指定包名的 JSON 配置，字段顺序由 Rust 工具按表结构保留。
     */
    fun loadConfig(packageName: String) {
        loadConfigJob?.cancel()
        loadConfigJob = viewModelScope.launch {
            _isEditorLoading.value = true
            _editingPackageName.value = packageName
            try {
                val json = withContext(Dispatchers.IO) { dbManager.loadConfig(packageName) }
                // 校验包名：若加载期间用户又切到别的包，本次结果丢弃（防 A 配置写入 B 包）
                if (_editingPackageName.value == packageName) {
                    _editingJson.value = json
                    _baselineJson.value = json
                }
            } catch (_: Exception) {
                if (_editingPackageName.value == packageName) {
                    _editingJson.value = null
                    _baselineJson.value = null
                }
            } finally {
                if (isActive) _isEditorLoading.value = false
            }
        }
    }

    /**
     * 创建新的空白配置。
     * 必须先取消在途的 loadConfig，否则它的回调守卫只按包名判重，
     * 会用数据库结果（不存在的包为 null）静默覆盖刚写入的模板。
     * 若该包已有配置，改为加载真实数据，避免空白模板写入时静默部分覆盖旧列。
     */
    fun createNewConfig(packageName: String) {
        loadConfigJob?.cancel()
        val existing = allRows.any { it.packageName == packageName }
        if (existing) {
            loadConfig(packageName)
            return
        }
        val template = """{"package_name":"$packageName"}"""
        _editingJson.value = template
        _baselineJson.value = template
        _editingPackageName.value = packageName
    }

    /** 当前编辑内容相对基准是否有未保存修改（供退出/切换/导入前拦截）。 */
    fun isEditingDirty(): Boolean =
        _editingPackageName.value != null && _editingJson.value != _baselineJson.value

    fun updateEditingJson(json: String) {
        _editingJson.value = json
    }

    fun clearEditingConfig() {
        _editingJson.value = null
        _baselineJson.value = null
        _editingPackageName.value = null
    }

    fun injectConfig(onComplete: (Boolean, String) -> Unit) {
        // 写入守护：防止慢速 root shell 期间重复触发并发写入。
        if (_isWriting.value) return
        val pkg = _editingPackageName.value ?: run { onComplete(false, "未选择应用"); return }
        val json = _editingJson.value ?: run { onComplete(false, "无编辑内容"); return }
        viewModelScope.launch {
            _isWriting.value = true
            try {
                val result = if (_systemStatus.value.isRooted) {
                    withContext(Dispatchers.IO) { dbManager.writeConfig(pkg, json) }
                } else {
                    DatabaseManager.WriteResult(false, "未授予 Root 权限")
                }
                if (result.success) {
                    // 写入成功后编辑内容已落库，同步基准消除脏标记。
                    _baselineJson.value = _editingJson.value
                    refreshAll()
                }
                onComplete(result.success, result.message)
            } catch (e: Exception) { onComplete(false, "注入失败: ${e.message}") }
            finally { _isWriting.value = false }
        }
    }

    fun deleteConfig(packageName: String, onComplete: (Boolean, String) -> Unit) {
        if (_isWriting.value) return
        viewModelScope.launch {
            _isWriting.value = true
            _isLoading.value = true
            try {
                if (!_systemStatus.value.isRooted) {
                    onComplete(false, "未授予 Root 权限")
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { dbManager.deleteConfig(packageName) }
                if (!result.success) {
                    onComplete(false, result.message)
                    return@launch
                }
                // 删除成功后若正在编辑被删包，清空编辑态避免残留旧 JSON。
                if (_editingPackageName.value == packageName) clearEditingConfig()
                refreshAll(); onComplete(true, "配置 $packageName 已删除")
            } catch (e: Exception) { onComplete(false, "删除失败: ${e.message}") }
            finally {
                _isLoading.value = false
                _isWriting.value = false
            }
        }
    }

    /** 清除应用增强服务数据 */
    fun clearGameData(onComplete: (Boolean, String) -> Unit) {
        if (_isWriting.value) return
        viewModelScope.launch {
            _isWriting.value = true
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) { dbManager.clearGameData() }
                if (result.success) {
                    // Bug 1：清除成功后刷新列表/状态，避免 gameList/hasDbData/configuredCount 残留旧值
                    refreshAll()
                    // pm clear 清空整个 cosa 库：当前编辑包（若存在）已被清除 → 清空编辑态，
                    // 避免编辑器/双窗窗格残留已删配置的旧 JSON
                    if (_editingPackageName.value != null) clearEditingConfig()
                }
                onComplete(result.success, result.message)
            } catch (e: Exception) {
                onComplete(false, "清除失败: ${e.message ?: "未知错误"}")
            } finally {
                _isLoading.value = false
                _isWriting.value = false
            }
        }
    }

    /** 导出当前编辑器文本到内部存储（Bug 2：导出 _editingJson 而非 DB 旧值） */
    fun exportFileName(): String =
        (_editingPackageName.value ?: "config") + ".json"

    fun exportConfig(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        val json = _editingJson.value ?: run { onComplete(false, "无可导出的配置"); return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { dbManager.exportConfig(uri, json) }
            onComplete(result.success, result.message)
        }
    }

    data class SystemStatus(
        val isRooted: Boolean = false,
        val dbAvailable: Boolean = false,
        val configuredCount: Int = 0,
        /** 检测是否已完成：冷启动时 Root/数据库检测需数秒，完成前 UI 不应据此报错 */
        val checked: Boolean = false
    )
}
