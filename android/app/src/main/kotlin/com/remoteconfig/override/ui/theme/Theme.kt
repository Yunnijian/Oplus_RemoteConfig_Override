package com.remoteconfig.override.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.SettingsRepository
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode

/**
 * 主题设置聚合 — 对齐 KernelSU Theme.kt 的 AppSettings。
 * 由 [ThemeController.getAppSettings] 从 [SettingsRepository] 读取并解析。
 */
data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    /** 平台选择原始值（"auto" / "coloros" / "hyperos"），由 MainActivity 经 resolvePlatform 解析生效平台。 */
    val platformMode: String,
)

/** 仅这些样式支持 2025 动态取色规范（对齐 KernelSU Theme.kt supportsSpec2025）。 */
val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

/** 不支持 2025 规范的样式自动降级到 SPEC_2021（对齐 KernelSU Theme.kt effectiveFor）。 */
fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

/**
 * 主题控制器 — 对齐 KernelSU ThemeController.getAppSettings。
 * 读取仓库原始字段并解析为 [AppSettings]；miuix 模式下按 [SettingsRepository.miuixMonet]
 * 在 Monet/非 Monet 间转换 colorMode（Material 模式不做转换）。
 */
object ThemeController {
    fun getAppSettings(repo: SettingsRepository = SettingsRepositoryImpl()): AppSettings {
        val uiMode = repo.uiMode
        var colorModeValue = repo.themeMode

        if (uiMode == UiMode.Miuix.value) {
            val miuixMonet = repo.miuixMonet
            val colorMode = ColorMode.fromValue(colorModeValue)
            colorModeValue = if (!miuixMonet && colorMode.isMonet) {
                colorMode.toNonMonetMode()
            } else if (miuixMonet && !colorMode.isMonet) {
                colorMode.toMonetMode()
            } else {
                colorModeValue
            }
        }

        val colorMode = ColorMode.fromValue(colorModeValue)
        val keyColor = repo.keyColor
        val paletteStyle = try {
            PaletteStyle.valueOf(repo.colorStyle)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        val colorSpec = try {
            ColorSpec.SpecVersion.valueOf(repo.colorSpec)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.SPEC_2025
        }

        return AppSettings(colorMode, keyColor, paletteStyle, colorSpec, repo.platformMode)
    }
}

/**
 * 双主题分发入口 — 对齐 KernelSU KernelSUTheme，按 [uiMode] 分发到
 * MiuixTheme / MaterialTheme。
 * [appSettings] 由 MainActivity 传入（数据源为
 * MainActivityViewModel 的 prefs 监听 StateFlow，对齐 KernelSU）。
 */
@Composable
fun RemoteConfigTheme(
    appSettings: AppSettings = ThemeController.getAppSettings(),
    uiMode: UiMode = LocalUiMode.current,
    content: @Composable () -> Unit
) {
    when (uiMode) {
        UiMode.Miuix -> RemoteConfigMiuixTheme(
            appSettings = appSettings,
            content = content,
        )

        UiMode.Material -> RemoteConfigMaterialTheme(
            appSettings = appSettings,
            content = content,
        )
    }
}

/**
 * 当前是否深色主题（读 [LocalColorMode]；SYSTEM/Monet-SYSTEM 时跟随系统）。
 * 对齐 KernelSU Theme.kt isInDarkTheme：1/4 强制浅色，2/5/6 强制深色。
 */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1, 4 -> false  // Force light mode
        2, 5, 6 -> true   // Force dark mode
        else -> isSystemInDarkTheme()  // Follow system (0 or default)
    }
}

/** 当前 colorMode（ColorMode.value，Int）。由 MainActivity 显式 provide。 */
val LocalColorMode = staticCompositionLocalOf { 0 }

/** 液态玻璃/模糊总开关。默认必须为 false，由 MainActivity 显式 provide。 */
val LocalEnableBlur = staticCompositionLocalOf { false }

/** 悬浮底栏开关。 */
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

/** 悬浮底栏实时模糊。 */
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

