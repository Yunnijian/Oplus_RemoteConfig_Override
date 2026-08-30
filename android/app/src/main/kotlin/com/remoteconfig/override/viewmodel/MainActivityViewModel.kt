package com.remoteconfig.override.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
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
class MainActivityViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val prefs: SharedPreferences = settingsAppContext.getSharedPreferences(
        SettingsRepositoryImpl.SETTINGS_PREFS_FILE, Context.MODE_PRIVATE
    )
    private val settingRepo: SettingsRepository = SettingsRepositoryImpl()
    private val mainPageState = MainPageState(savedStateHandle)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in OBSERVED_KEYS) {
            _uiState.value = readUiState()
        }
    }

    private val _uiState = MutableStateFlow(readUiState())
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    /**
     * 当前选中主页 tab — 对齐 KernelSU selectedMainPage（SavedStateHandle 持久化）。
     * 切换界面风格（uiMode）会使主题分支下的整棵子树丢弃重建，裸 remember 的
     * pagerState 随之重置；tab 选择外置到此，重建时经 initialPage 注回
     * （见 MainActivity/MainScreen），不再跳回首页。
     */
    val selectedMainPage: StateFlow<Int> = mainPageState.selectedPage

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun setSelectedMainPage(page: Int) {
        mainPageState.updateSelectedPage(page)
    }

    private fun readUiState(): MainActivityUiState {
        return MainActivityUiState(
            appSettings = ThemeController.getAppSettings(settingRepo),
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

private const val SELECTED_MAIN_PAGE_KEY = "selected_main_page"

/** 对齐 KernelSU MainPageState：选中 tab 存于 SavedStateHandle（进程重建亦保留）。 */
private class MainPageState(
    private val savedStateHandle: SavedStateHandle,
) {
    val selectedPage: StateFlow<Int> = savedStateHandle.getStateFlow(SELECTED_MAIN_PAGE_KEY, 0)

    fun updateSelectedPage(page: Int) {
        savedStateHandle[SELECTED_MAIN_PAGE_KEY] = MainPagerConfig.coercePage(page)
    }
}

/** 对齐 KernelSU MainPagerConfig（本项目 3 tab：首页/配置/设置）。 */
object MainPagerConfig {
    const val PAGE_COUNT = 3
    const val LAST_PAGE_INDEX = PAGE_COUNT - 1

    fun coercePage(page: Int): Int = page.coerceIn(0, LAST_PAGE_INDEX)
}
