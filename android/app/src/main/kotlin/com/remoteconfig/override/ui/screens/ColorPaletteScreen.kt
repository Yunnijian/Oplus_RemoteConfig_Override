package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.SettingsViewModel

/**
 * 取色屏状态 — 对齐 KernelSU `ColorPaletteUiState.kt`。
 * 字段来自 [com.remoteconfig.override.viewmodel.SettingsViewModel] 的 StateFlow。
 */
@Immutable
data class ColorPaletteUiState(
    val themeMode: Int,
    val miuixMonet: Boolean,
    val keyColor: Int,
    val colorStyle: String,
    val colorSpec: String,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val enableNavigationBadge: Boolean,
    val enablePredictiveBack: Boolean,
    val pageScale: Float,
    val currentColorMode: ColorMode,
    val currentPaletteStyle: PaletteStyle,
    val currentColorSpec: ColorSpec.SpecVersion,
)

/**
 * 取色屏动作 — 对齐 KernelSU `ColorPaletteScreenActions`。
 * 全部写入走 [SettingsViewModel] setter（repo 落盘 + StateFlow 更新）。
 */
@Immutable
data class ColorPaletteScreenActions(
    val onBack: () -> Unit,
    val onSetThemeMode: (Int) -> Unit,
    val onSetMiuixMonet: (Boolean) -> Unit,
    val onSetKeyColor: (Int) -> Unit,
    val onSetColorMode: (ColorMode) -> Unit,
    val onSetColorStyle: (String) -> Unit,
    val onSetColorSpec: (String) -> Unit,
    val onSetEnableBlur: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBar: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBarBlur: (Boolean) -> Unit,
    val onSetEnableNavigationBadge: (Boolean) -> Unit,
    val onSetEnablePredictiveBack: (Boolean) -> Unit,
    val onSetPageScale: (Float) -> Unit,
)

/**
 * 主题取色屏 — 分发器（对齐 KernelSU `ColorPaletteScreen.kt`）。
 *
 * 数据源为 [SettingsViewModel]（对齐 KernelSU：StateFlow 驱动，无快照观察），
 * 按 [LocalUiMode] 分发 Miuix / Material 实现。
 */
@Composable
fun ColorPaletteScreen() {
    // LocalNavigator.current 是 @Composable getter，提前提升进 actions（避免非组合 lambda 内访问）。
    val navigator = LocalNavigator.current
    val (uiState, actions) = rememberColorPaletteStateAndActions(onBack = { navigator.pop() })
    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorPaletteContentMiuix(uiState, actions)
        UiMode.Material -> ColorPaletteContentMaterial(uiState, actions)
    }
}

/**
 * 构建取色屏状态与动作（数据源 [SettingsViewModel]）。
 *
 * 全屏路由（[ColorPaletteScreen]）与宽屏双窗右侧 pane（SettingsScreen 内 ThemePane）共用。
 * [onBack] 由调用方注入：全屏 = navigator.pop()，pane = 空操作（pane 无返回按钮，
 * 由左侧设置列表选择控制）。
 */
@Composable
internal fun rememberColorPaletteStateAndActions(onBack: () -> Unit): Pair<ColorPaletteUiState, ColorPaletteScreenActions> {
    val viewModel: SettingsViewModel = viewModel()
    val settingsState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = ColorPaletteUiState(
        themeMode = settingsState.themeMode,
        miuixMonet = settingsState.miuixMonet,
        keyColor = settingsState.keyColor,
        colorStyle = settingsState.colorStyle,
        colorSpec = settingsState.colorSpec,
        enableBlur = settingsState.enableBlur,
        enableFloatingBottomBar = settingsState.enableFloatingBottomBar,
        enableFloatingBottomBarBlur = settingsState.enableFloatingBottomBarBlur,
        enableNavigationBadge = settingsState.enableNavigationBadge,
        enablePredictiveBack = settingsState.enablePredictiveBack,
        pageScale = settingsState.pageScale,
        currentColorMode = ColorMode.fromValue(settingsState.themeMode),
        currentPaletteStyle = try {
            PaletteStyle.valueOf(settingsState.colorStyle)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        },
        currentColorSpec = try {
            ColorSpec.SpecVersion.valueOf(settingsState.colorSpec)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.SPEC_2025
        },
    )
    val actions = remember(viewModel) {
        ColorPaletteScreenActions(
            onBack = onBack,
            onSetThemeMode = viewModel::setThemeMode,
            onSetMiuixMonet = viewModel::setMiuixMonet,
            onSetKeyColor = viewModel::setKeyColor,
            onSetColorMode = viewModel::setColorMode,
            onSetColorStyle = viewModel::setColorStyle,
            onSetColorSpec = viewModel::setColorSpec,
            onSetEnableBlur = viewModel::setEnableBlur,
            onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
            onSetEnableFloatingBottomBarBlur = viewModel::setEnableFloatingBottomBarBlur,
            onSetEnableNavigationBadge = viewModel::setEnableNavigationBadge,
            onSetEnablePredictiveBack = viewModel::setEnablePredictiveBack,
            onSetPageScale = viewModel::setPageScale,
        )
    }
    return uiState to actions
}
