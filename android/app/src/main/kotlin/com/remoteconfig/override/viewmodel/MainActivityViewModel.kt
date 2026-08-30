package com.remoteconfig.override.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.remoteconfig.override.settings.SettingsRepository
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.settings.settingsAppContext
import com.remoteconfig.override.ui.theme.AppSettings
import com.remoteconfig.override.ui.theme.ThemeController

/**
 * 根 Activity 主题状态 — 对齐 KernelSU MainActivityUiState。
 */
data class MainActivityUiState(
    val appSettings: AppSettings,
    val miuixMonet: Boolean = true,
    val pageScale: Float = 1.0f,
    val enableBlur: Boolean = false,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val uiMode: UiMode = UiMode.Miuix,
)

/**
 * 根 Activity ViewModel — 对齐 KernelSU MainActivityViewModel：
 * SharedPreferences.OnSharedPreferenceChangeListener 监听主题键，
 * 命中即整体重读并推送 StateFlow；MainActivity 经 collectAsStateWithLifecycle
 * 订阅，驱动根主题与各 CompositionLocal。无 Compose 快照参与。
 */
class MainActivityViewModel : ViewModel() {

    private val prefs: SharedPreferences = settingsAppContext.getSharedPreferences(
        SettingsRepositoryImpl.SETTINGS_PREFS_FILE, Context.MODE_PRIVATE
    )
    private val settingRepo: SettingsRepository = SettingsRepositoryImpl()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in OBSERVED_KEYS) {
            _uiState.value = readUiState()
        }
    }

    private val _uiState = MutableStateFlow(readUiState())
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun readUiState(): MainActivityUiState {
        return MainActivityUiState(
            appSettings = ThemeController.getAppSettings(settingRepo),
            miuixMonet = settingRepo.miuixMonet,
            pageScale = settingRepo.pageScale,
            enableBlur = settingRepo.enableBlur,
            enableFloatingBottomBar = settingRepo.enableFloatingBottomBar,
            enableFloatingBottomBarBlur = settingRepo.enableFloatingBottomBarBlur,
            uiMode = UiMode.fromValue(settingRepo.uiMode),
        )
    }

    private companion object {
        val OBSERVED_KEYS = setOf(
            SettingsRepositoryImpl.KEY_UI_MODE,
            SettingsRepositoryImpl.KEY_COLOR_MODE,
            SettingsRepositoryImpl.KEY_MIUIX_MONET,
            SettingsRepositoryImpl.KEY_KEY_COLOR,
            SettingsRepositoryImpl.KEY_COLOR_STYLE,
            SettingsRepositoryImpl.KEY_COLOR_SPEC,
            SettingsRepositoryImpl.KEY_PAGE_SCALE,
            SettingsRepositoryImpl.KEY_ENABLE_BLUR,
            SettingsRepositoryImpl.KEY_ENABLE_FLOATING_BOTTOM_BAR,
            SettingsRepositoryImpl.KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR,
            SettingsRepositoryImpl.KEY_ENABLE_NAVIGATION_BADGE,
        )
    }
}
