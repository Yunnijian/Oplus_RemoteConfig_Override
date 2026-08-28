package com.remoteconfig.override.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remoteconfig.override.data.DatabaseManager
import com.remoteconfig.override.model.GameConfigSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** 当前编辑的原始 JSON 文本 */
    private val _editingJson = MutableStateFlow<String?>(null)
    val editingJson: StateFlow<String?> = _editingJson.asStateFlow()

    private val _editingPackageName = MutableStateFlow<String?>(null)
    val editingPackageName: StateFlow<String?> = _editingPackageName.asStateFlow()

    /** 列表刷新 loading —— 仅 refreshAll 置位（列表型操作共用） */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 编辑器/窗格加载 loading —— 仅 loadConfig 置位（与列表刷新分离，避免双窗串扰） */
    private val _isEditorLoading = MutableStateFlow(false)
    val isEditorLoading: StateFlow<Boolean> = _isEditorLoading.asStateFlow()

    private val _hasDbData = MutableStateFlow(false)
    val hasDbData: StateFlow<Boolean> = _hasDbData.asStateFlow()

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
            checkSystemStatus()
            loadGameList()
            _isLoading.value = false
        }
    }

    private suspend fun checkSystemStatus() {
        // 所有 Root Shell / 数据库调用放到 IO 线程，避免阻塞主线程导致 ANR。
        val (status, version) = withContext(Dispatchers.IO) {
            val isRooted = try { dbManager.checkRoot() } catch (_: Exception) { false }
            val dbAvailable = try { isRooted && dbManager.checkDatabase() } catch (_: Exception) { false }
            val configuredCount = if (dbAvailable) {
                try { dbManager.countConfiguredPackages() } catch (_: Exception) { 0 }
            } else 0
            val ver = if (isRooted) {
                try { dbManager.getCosaVersion() } catch (_: Exception) { "未知" }
            } else ""
            SystemStatus(isRooted, dbAvailable, configuredCount) to ver
        }
        _systemStatus.value = status
        if (status.isRooted) {
            _cosaVersion.value = version
        }
    }

    private suspend fun loadGameList() {
        val context = getApplication<Application>()
        val configuredPkgs = try {
            if (_systemStatus.value.dbAvailable) {
                withContext(Dispatchers.IO) { dbManager.listConfiguredPackages() }
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        _hasDbData.value = configuredPkgs.isNotEmpty()

        // 后台线程预加载：包名 → (appLabel, iconBitmap, isInstalled)
        val results = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val densityDpi = context.resources.displayMetrics.density
            // Bug 3: 容量上限保护——条目超过上限整体清空（下一轮 refresh 只重建当前配置包）
            if (iconCache.size > MAX_CACHED_ICONS) iconCache.clear()
            configuredPkgs.map { pkg ->
                if (missingIconPackages.contains(pkg)) {
                    // Bug 3: 已知未安装（无图标）——跳过重复解码尝试，直接走占位图标
                    Triple(pkg, pkg, false)
                } else {
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(info).toString()
                        // Bug 3: 已缓存图标跳过重建（解码/绘 Bitmap 是主要内存与耗时来源），
                        // refreshAll 只补新增包；label 查询是廉价 Binder 调用，保持列表名最新
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
                        Triple(pkg, label, true)
                    } catch (_: Exception) {
                        // ConcurrentHashMap 不接受 null 值：移除旧缓存让 UI 走占位图标
                        iconCache.remove(pkg)
                        missingIconPackages.add(pkg)
                        Triple(pkg, pkg, false)
                    }
                }
            }
        }

        val sorted = results
            .sortedByDescending { it.third }
            .map { (pkg, name, installed) ->
                GameConfigSummary(packageName = pkg, appName = name, hasConfig = true, isInstalled = installed)
            }
        _gameList.value = sorted
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
                }
            } catch (_: Exception) {
                if (_editingPackageName.value == packageName) {
                    _editingJson.value = null
                }
            } finally {
                _isEditorLoading.value = false
            }
        }
    }

    /**
     * 创建新的空白配置。
     */
    fun createNewConfig(packageName: String) {
        _editingJson.value = """{"package_name":"$packageName"}"""
        _editingPackageName.value = packageName
    }

    fun updateEditingJson(json: String) {
        _editingJson.value = json
    }

    fun clearEditingConfig() {
        _editingJson.value = null
        _editingPackageName.value = null
    }

    fun injectConfig(onComplete: (Boolean, String) -> Unit) {
        val pkg = _editingPackageName.value ?: run { onComplete(false, "未选择应用"); return }
        val json = _editingJson.value ?: run { onComplete(false, "无编辑内容"); return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = if (_systemStatus.value.isRooted) {
                    withContext(Dispatchers.IO) { dbManager.writeConfig(pkg, json) }
                } else {
                    DatabaseManager.WriteResult(false, "未授予 Root 权限")
                }
                if (result.success) refreshAll()
                onComplete(result.success, result.message)
            } catch (e: Exception) { onComplete(false, "注入失败: ${e.message}") }
            finally { _isLoading.value = false }
        }
    }

    fun deleteConfig(packageName: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
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
                refreshAll(); onComplete(true, "配置 $packageName 已删除")
            } catch (e: Exception) { onComplete(false, "删除失败: ${e.message}") }
            finally { _isLoading.value = false }
        }
    }

    /** 清除应用增强服务数据 */
    fun clearGameData(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
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
            }
        }
    }

    /** 导出当前编辑器文本到内部存储（Bug 2：导出 _editingJson 而非 DB 旧值） */
    fun exportConfig(onComplete: (Boolean, String) -> Unit) {
        val pkg = _editingPackageName.value ?: run { onComplete(false, "未选择应用"); return }
        val json = _editingJson.value ?: run { onComplete(false, "无可导出的配置"); return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { dbManager.exportConfig(pkg, json) }
            onComplete(result.success, result.message)
        }
    }

    data class SystemStatus(
        val isRooted: Boolean = false,
        val dbAvailable: Boolean = false,
        val configuredCount: Int = 0
    )
}
