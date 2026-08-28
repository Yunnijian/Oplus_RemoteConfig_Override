package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode

/**
 * 取色屏状态 — 对齐 KernelSU `ColorPaletteUiState.kt`。
 * 字段来自 [com.remoteconfig.override.settings.SettingsRepository]（SharedPreferences 即时读写）。
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
 * 全部写入走 [com.remoteconfig.override.settings.SettingsRepositoryImpl] setter，
 * 主题即时响应（MainActivity 注册的 prefs 监听 → settingsVersion++ → 重组）。
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
 * 数据源为 [SettingsRepositoryImpl]，每次重组读仓库最新值（MainActivity 的
 * settingsVersion 状态驱动重组），按 [LocalUiMode] 分发 Miuix / Material 实现。
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
 * 构建取色屏状态与动作（数据源 [SettingsRepositoryImpl]）。
 *
 * 全屏路由（[ColorPaletteScreen]）与宽屏双窗右侧 pane（SettingsScreen 内 ThemePane）共用。
 * [onBack] 由调用方注入：全屏 = navigator.pop()，pane = 空操作（pane 无返回按钮，
 * 由左侧设置列表选择控制）。
 */
@Composable
internal fun rememberColorPaletteStateAndActions(onBack: () -> Unit): Pair<ColorPaletteUiState, ColorPaletteScreenActions> {
    val repo = remember { SettingsRepositoryImpl() }
    val uiState = ColorPaletteUiState(
        themeMode = repo.themeMode,
        miuixMonet = repo.miuixMonet,
        keyColor = repo.keyColor,
        colorStyle = repo.colorStyle,
        colorSpec = repo.colorSpec,
        enableBlur = repo.enableBlur,
        enableFloatingBottomBar = repo.enableFloatingBottomBar,
        enableFloatingBottomBarBlur = repo.enableFloatingBottomBarBlur,
        enableNavigationBadge = repo.enableNavigationBadge,
        enablePredictiveBack = repo.enablePredictiveBack,
        pageScale = repo.pageScale,
        currentColorMode = ColorMode.fromValue(repo.themeMode),
        currentPaletteStyle = try {
            PaletteStyle.valueOf(repo.colorStyle)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        },
        currentColorSpec = try {
            ColorSpec.SpecVersion.valueOf(repo.colorSpec)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.SPEC_2025
        },
    )
    val actions = remember(repo) {
        ColorPaletteScreenActions(
            onBack = onBack,
            onSetThemeMode = { repo.themeMode = it },
            onSetMiuixMonet = { repo.miuixMonet = it },
            onSetKeyColor = { repo.keyColor = it },
            onSetColorMode = { repo.themeMode = it.value },
            onSetColorStyle = { repo.colorStyle = it },
            onSetColorSpec = { repo.colorSpec = it },
            onSetEnableBlur = { repo.enableBlur = it },
            onSetEnableFloatingBottomBar = { repo.enableFloatingBottomBar = it },
            onSetEnableFloatingBottomBarBlur = { repo.enableFloatingBottomBarBlur = it },
            onSetEnableNavigationBadge = { repo.enableNavigationBadge = it },
            onSetEnablePredictiveBack = { repo.enablePredictiveBack = it },
            onSetPageScale = { repo.pageScale = it },
        )
    }
    return uiState to actions
}
