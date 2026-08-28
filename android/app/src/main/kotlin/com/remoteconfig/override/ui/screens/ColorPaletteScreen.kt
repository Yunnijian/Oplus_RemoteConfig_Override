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

/** 强调色下拉预设名（与 keyColorOptions 一一对应；首项「跟随默认」= keyColor 0）。 */
internal val KeyColorNames: List<String> = listOf(
    "跟随默认",
    "红色", "粉色", "紫色", "深紫色", "靛蓝色", "蓝色", "青色", "蓝绿色",
    "绿色", "黄色", "琥珀色", "橙色", "棕色", "蓝灰色", "樱花色",
)

/** PaletteStyle 枚举名 → 中文标签（Miuix / Material 双实现共用）。 */
private val PaletteStyleLabelMap: Map<String, String> = mapOf(
    "TonalSpot" to "主色调",
    "Neutral" to "中性色",
    "Vibrant" to "活力色",
    "Expressive" to "表现色",
    "Rainbow" to "彩虹",
    "FruitSalad" to "水果沙拉",
    "Monochrome" to "单色",
    "Fidelity" to "保真色",
    "Content" to "内容色",
)

/** 颜色规范枚举名 → 中文标签。 */
private val ColorSpecLabelMap: Map<String, String> = mapOf(
    "SPEC_2021" to "规范 2021",
    "SPEC_2025" to "规范 2025",
)

internal fun paletteStyleLabel(name: String): String = PaletteStyleLabelMap[name] ?: name

internal fun colorSpecLabel(name: String): String = ColorSpecLabelMap[name] ?: name

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
    val repo = remember { SettingsRepositoryImpl() }
    val uiState = ColorPaletteUiState(
        themeMode = repo.themeMode,
        miuixMonet = repo.miuixMonet,
        keyColor = repo.keyColor,
        colorStyle = repo.colorStyle,
        colorSpec = repo.colorSpec,
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
            onBack = { navigator.pop() },
            onSetThemeMode = { repo.themeMode = it },
            onSetMiuixMonet = { repo.miuixMonet = it },
            onSetKeyColor = { repo.keyColor = it },
            onSetColorMode = { repo.themeMode = it.value },
            onSetColorStyle = { repo.colorStyle = it },
            onSetColorSpec = { repo.colorSpec = it },
        )
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorPaletteContentMiuix(uiState, actions)
        UiMode.Material -> ColorPaletteContentMaterial(uiState, actions)
    }
}
