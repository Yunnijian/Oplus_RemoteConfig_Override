package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode

/**
 * 预设强调色板（keyColor ARGB Int）。
 *
 * 注意：这些十六进制值 > Int.MAX_VALUE，Kotlin 会按 Long 推断，
 * 因此显式用 `.toInt()` 转回 Int（`setKeyColor(Int)` / `Color(Int)` 需要）。
 * 首项「跟随默认」用 keyColor 0 表示，不入此表（见取色屏色卡组）。
 */
internal val PresetKeyColors: List<Int> = listOf(
    0xFF3482FF.toInt(), // Miuix 默认蓝
    0xFFBA1A1A.toInt(),
    0xFFE8590C.toInt(),
    0xFFF08C00.toInt(),
    0xFF2B8A3E.toInt(),
    0xFF0B7285.toInt(),
    0xFF1971C2.toInt(),
    0xFF6741D9.toInt(),
    0xFFC2255C.toInt(),
)

/** materialkolor 5.0.0 PaletteStyle 全部枚举名（TonalSpot/Neutral/Vibrant/Expressive/Rainbow/FruitSalad/Monochrome/Fidelity/Content）。 */
internal val PaletteStyleNames: List<String> = PaletteStyle.entries.map { it.name }

/** 颜色规范枚举名（SPEC_2021 / SPEC_2025）。Default 为库内部常量，过滤掉。 */
internal val ColorSpecNames: List<String> =
    ColorSpec.SpecVersion.entries.map { it.name }.filter { it != "Default" }

/** PaletteStyle 枚举名 → 中英标签（Miuix / Material 双实现共用）。 */
private val PaletteStyleLabelMap: Map<String, String> = mapOf(
    "TonalSpot" to "主色调 (TonalSpot)",
    "Neutral" to "中性色 (Neutral)",
    "Vibrant" to "活力色 (Vibrant)",
    "Expressive" to "表现色 (Expressive)",
    "Rainbow" to "彩虹 (Rainbow)",
    "FruitSalad" to "水果沙拉 (FruitSalad)",
    "Monochrome" to "单色 (Monochrome)",
    "Fidelity" to "保真色 (Fidelity)",
    "Content" to "内容色 (Content)",
)

/** 颜色规范枚举名 → 中英标签。 */
private val ColorSpecLabelMap: Map<String, String> = mapOf(
    "SPEC_2021" to "规范 2021 (SPEC_2021)",
    "SPEC_2025" to "规范 2025 (SPEC_2025)",
)

internal fun paletteStyleLabel(name: String): String = PaletteStyleLabelMap[name] ?: name

internal fun colorSpecLabel(name: String): String = ColorSpecLabelMap[name] ?: name

/**
 * 主题取色屏 — 分发器。
 *
 * 按 [LocalUiMode] 选择 Miuix / Material 实现（对齐 KernelSU
 * `ColorPaletteScreen.kt` 的分发结构）。返回统一经 [LocalNavigator] pop。
 */
@Composable
fun ColorPaletteScreen() {
    // LocalNavigator.current 是 @Composable getter，不能放进 onBack 非组合 lambda，提前提升（对齐 Task 7 修复）。
    val navigator = LocalNavigator.current
    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorPaletteContentMiuix(onBack = { navigator.pop() })
        UiMode.Material -> ColorPaletteContentMaterial(onBack = { navigator.pop() })
    }
}
