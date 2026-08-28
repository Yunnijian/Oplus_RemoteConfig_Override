package com.remoteconfig.override.settings

/**
 * 设置仓库 — 对齐 KernelSU SettingsRepository，只保留主题相关字段。
 *
 * 字段语义与 KernelSU 完全一致：
 * - [uiMode]：UI 风格（"miuix" / "material"，见 [UiMode]）
 * - [themeMode]：主题模式（[ColorMode.value]，0-6 六态）
 * - [miuixMonet]：Miuix 动态取色开关（仅 miuix 模式生效，[ThemeController] 据此在 Monet/非 Monet 间转换）
 * - [keyColor]：自定义强调色（ARGB Int，0 = 跟随默认）
 * - [colorStyle]：调色板风格（[com.materialkolor.PaletteStyle].name）
 * - [colorSpec]：颜色规范（[com.materialkolor.dynamiccolor.ColorSpec.SpecVersion].name）
 * - [enableBlur]：液态玻璃/模糊总开关
 * - [enableFloatingBottomBar]：悬浮底栏开关
 * - [enableFloatingBottomBarBlur]：悬浮底栏实时模糊
 */
interface SettingsRepository {
    var uiMode: String
    var themeMode: Int
    var miuixMonet: Boolean
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
}
