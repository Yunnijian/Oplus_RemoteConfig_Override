package com.remoteconfig.override.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.SettingsRepository
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 设置页/取色屏状态 — 对齐 KernelSU SettingsUiState。
 * 纯数据快照，由 [SettingsViewModel] 的 StateFlow 驱动更新。
 */
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.SPEC_2025.name,
    val enableBlur: Boolean = false,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val enablePredictiveBack: Boolean = false,
    val pageScale: Float = 1.0f,
)

/**
 * 设置 ViewModel — 对齐 KernelSU SettingsViewModel：
 * 写入 = repo 落盘 + StateFlow 同步更新（取色屏/设置列表经
 * collectAsStateWithLifecycle 订阅）。无 Compose 快照参与。
 */
class SettingsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(readUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun readUiState() = SettingsUiState(
        uiMode = repo.uiMode,
        themeMode = repo.themeMode,
        miuixMonet = repo.miuixMonet,
        keyColor = repo.keyColor,
        colorStyle = repo.colorStyle,
        colorSpec = repo.colorSpec,
        enableBlur = repo.enableBlur,
        enableFloatingBottomBar = repo.enableFloatingBottomBar,
        enableFloatingBottomBarBlur = repo.enableFloatingBottomBarBlur,
        enablePredictiveBack = repo.enablePredictiveBack,
        pageScale = repo.pageScale,
    )

    /** 对齐 KernelSU setUiMode：Miuix↔Material 切换时同步转换 colorMode 的 Monet 变体。 */
    fun setUiMode(mode: String) {
        val oldMode = repo.uiMode
        val currentThemeMode = repo.themeMode

        val newThemeMode = when (oldMode) {
            UiMode.Material.value -> if (mode == UiMode.Miuix.value) {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                val baseMode = if (colorMode == ColorMode.DARK_AMOLED) 2 else currentThemeMode
                if (repo.miuixMonet && !colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toMonetMode()
                } else if (!repo.miuixMonet && colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toNonMonetMode()
                } else baseMode
            } else currentThemeMode

            UiMode.Miuix.value -> if (mode == UiMode.Material.value) {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
            } else currentThemeMode

            else -> currentThemeMode
        }

        repo.uiMode = mode
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(uiMode = mode, themeMode = newThemeMode) }
    }

    /** 对齐 KernelSU setThemeMode：miuix + Monet 开启时基值 +3 转 Monet 变体。 */
    fun setThemeMode(mode: Int) {
        val currentUiMode = repo.uiMode
        val effectiveMode = if (currentUiMode == UiMode.Miuix.value && _uiState.value.miuixMonet) {
            mode + 3
        } else {
            mode
        }
        repo.themeMode = effectiveMode
        _uiState.update { it.copy(themeMode = effectiveMode) }
    }

    fun setColorMode(mode: ColorMode) {
        repo.themeMode = mode.value
        _uiState.update { it.copy(themeMode = mode.value) }
    }

    /** 对齐 KernelSU setMiuixMonet：开关 Monet 时在 themeMode 的 Monet/非 Monet 变体间转换。 */
    fun setMiuixMonet(enabled: Boolean) {
        val currentThemeMode = repo.themeMode
        val colorMode = ColorMode.fromValue(currentThemeMode)
        val newThemeMode = if (enabled) {
            if (!colorMode.isMonet) colorMode.toMonetMode() else currentThemeMode
        } else {
            if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
        }
        repo.miuixMonet = enabled
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(miuixMonet = enabled, themeMode = newThemeMode) }
    }

    fun setKeyColor(color: Int) {
        repo.keyColor = color
        _uiState.update { it.copy(keyColor = color) }
    }

    fun setColorStyle(style: String) {
        repo.colorStyle = style
        _uiState.update { it.copy(colorStyle = style) }
    }

    fun setColorSpec(spec: String) {
        repo.colorSpec = spec
        _uiState.update { it.copy(colorSpec = spec) }
    }

    fun setEnableBlur(enabled: Boolean) {
        repo.enableBlur = enabled
        _uiState.update { it.copy(enableBlur = enabled) }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        repo.enableFloatingBottomBar = enabled
        _uiState.update { it.copy(enableFloatingBottomBar = enabled) }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        repo.enableFloatingBottomBarBlur = enabled
        _uiState.update { it.copy(enableFloatingBottomBarBlur = enabled) }
    }


    fun setEnablePredictiveBack(enabled: Boolean) {
        repo.enablePredictiveBack = enabled
        _uiState.update { it.copy(enablePredictiveBack = enabled) }
    }

    fun setPageScale(scale: Float) {
        repo.pageScale = scale
        _uiState.update { it.copy(pageScale = scale) }
    }
}
