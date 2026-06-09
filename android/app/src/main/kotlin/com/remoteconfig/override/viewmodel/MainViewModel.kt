package com.remoteconfig.override.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remoteconfig.override.data.DatabaseManager
import com.remoteconfig.override.model.GameConfigSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主 ViewModel — 所有配置以原始 JSON 字符串形式处理。
 *
 * 数据流：
 *   数据库 → loadRawConfig() → 原始 JSON → UI 编辑 → writeRawConfig() → 数据库
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

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasDbData = MutableStateFlow(false)
    val hasDbData: StateFlow<Boolean> = _hasDbData.asStateFlow()

    /** 预加载的应用图标缓存 — 包名 → Bitmap */
    private val iconCache = mutableMapOf<String, Bitmap?>()

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
        val isRooted = try { dbManager.checkRoot() } catch (_: Exception) { false }
        val dbAvailable = try { isRooted && dbManager.checkDatabase() } catch (_: Exception) { false }
        val configuredCount = if (dbAvailable) {
            try { dbManager.countConfiguredPackages() } catch (_: Exception) { 0 }
        } else 0

        _systemStatus.value = SystemStatus(isRooted, dbAvailable, configuredCount)

        // 获取 cosa 版本
        if (isRooted) {
            _cosaVersion.value = try { dbManager.getCosaVersion() } catch (_: Exception) { "未知" }
        }
    }

    private suspend fun loadGameList() {
        val context = getApplication<Application>()
        val configuredPkgs = try {
            if (_systemStatus.value.dbAvailable) dbManager.listConfiguredPackages()
            else emptyList()
        } catch (_: Exception) { emptyList() }

        _hasDbData.value = configuredPkgs.isNotEmpty()

        // 后台线程预加载：包名 → (appLabel, iconBitmap, isInstalled)
        val results = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val densityDpi = context.resources.displayMetrics.density
            configuredPkgs.map { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(info).toString()
                    val drawable = pm.getApplicationIcon(info)
                    val px = (44 * densityDpi).toInt().coerceAtLeast(48)
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    drawable.setBounds(0, 0, px, px)
                    drawable.draw(c)
                    iconCache[pkg] = bmp
                    Triple(pkg, label, true)
                } catch (_: Exception) {
                    iconCache[pkg] = null
                    Triple(pkg, pkg, false)
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

    /**
     * 从数据库加载指定包名的原始 JSON 配置。
     */
    fun loadConfig(packageName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _editingPackageName.value = packageName
            try {
                _editingJson.value = dbManager.loadRawConfig(packageName)
            } catch (_: Exception) {
                _editingJson.value = null
            } finally {
                _isLoading.value = false
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

    /**
     * 从数据库重新加载（放弃本地编辑）。
     */
    fun reloadFromDb() {
        val pkg = _editingPackageName.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _editingJson.value = dbManager.loadRawConfig(pkg)
                _toastMessage.value = "已从数据库重新加载"
            } catch (e: Exception) {
                _toastMessage.value = "重新加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 将当前 JSON 写入数据库。
     */
    fun writeToDatabase(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val pkg = _editingPackageName.value ?: return
        val json = _editingJson.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = dbManager.writeRawConfig(pkg, json)
                _toastMessage.value = result.message
                onComplete(result.success, result.message)
                if (result.success) refreshAll()
            } catch (e: Exception) {
                _toastMessage.value = "写入失败: ${e.message}"
                onComplete(false, e.message ?: "未知错误")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearToast() { _toastMessage.value = null }

    /** 重启应用增强服务 */
    fun restartGameService() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = dbManager.restartGameService()
            _toastMessage.value = result.message
            _isLoading.value = false
        }
    }

    /** 清除应用增强服务数据 */
    fun clearGameData() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = dbManager.clearGameData()
            _toastMessage.value = result.message
            _isLoading.value = false
        }
    }

    /** 导出当前配置到内部存储 */
    fun exportConfig(onComplete: (Boolean, String) -> Unit) {
        val pkg = _editingPackageName.value ?: run { onComplete(false, "未选择应用"); return }
        val json = _editingJson.value ?: run { onComplete(false, "无可导出的配置"); return }
        viewModelScope.launch {
            _isLoading.value = true
            val result = dbManager.exportConfig(pkg, json)
            _toastMessage.value = result.message
            onComplete(result.success, result.message)
            _isLoading.value = false
        }
    }

    /** 从本地文件导入配置并覆盖当前编辑内容 */
    fun importConfig(onComplete: (Boolean, String) -> Unit) {
        val pkg = _editingPackageName.value ?: run { onComplete(false, "未选择应用"); return }
        viewModelScope.launch {
            _isLoading.value = true
            val json = dbManager.importConfig(pkg)
            if (json != null) {
                _editingJson.value = json
                val msg = "已导入配置"
                _toastMessage.value = msg
                onComplete(true, msg)
            } else {
                val msg = "未找到本地配置文件"
                _toastMessage.value = msg
                onComplete(false, msg)
            }
            _isLoading.value = false
        }
    }

    // ── 辅助 ─────────────────────────────────────────────────

    data class SystemStatus(
        val isRooted: Boolean = false,
        val dbAvailable: Boolean = false,
        val configuredCount: Int = 0
    )
}
