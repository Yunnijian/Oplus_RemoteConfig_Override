package com.remoteconfig.override.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.settings.AppSettingsRepository
import com.remoteconfig.override.settings.ColorMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * Miuix 风格主题：按 ColorMode/enableMonet 选择 ColorSchemeMode，
 * 并消费 Task 2 仓库的取色参数（keyColor/paletteStyle/colorSpec）。
 * ThemeController 具名参数写法对齐 KernelSU MiuixTheme.kt:54-67（miuix 0.9.3）。
 */
@Composable
fun RemoteConfigMiuixTheme(
    colorMode: ColorMode,
    enableMonet: Boolean,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val mode = when {
        enableMonet && colorMode == ColorMode.SYSTEM -> ColorSchemeMode.MonetSystem
        enableMonet && colorMode == ColorMode.LIGHT -> ColorSchemeMode.MonetLight
        enableMonet && colorMode == ColorMode.DARK -> ColorSchemeMode.MonetDark
        colorMode == ColorMode.LIGHT -> ColorSchemeMode.Light
        colorMode == ColorMode.DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    // 取色参数消费（对齐 KernelSU MiuixTheme.kt:33-43 的 valueOf+try-catch 模式）
    val paletteStyle = parsePaletteStyle(AppSettingsRepository.paletteStyle)
    val colorSpec = parseColorSpec(AppSettingsRepository.colorSpec)

    // ThemePaletteStyle 与 materialkolor PaletteStyle 枚举名一致，valueOf 失败时回退 TonalSpot
    val miuixPaletteStyle = try {
        ThemePaletteStyle.valueOf(paletteStyle.name)
    } catch (_: Exception) {
        ThemePaletteStyle.TonalSpot
    }

    val miuixColorSpec = if (colorSpec.effectiveFor(paletteStyle) == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    // 0 = 使用 Miuix 默认蓝（传 null 由库内部取默认）
    val resolvedKeyColor: Color? = AppSettingsRepository.keyColor
        .takeIf { it != 0 }
        ?.let { Color(it) }

    val controller = ThemeController(
        mode,
        keyColor = resolvedKeyColor,
        isDark = darkTheme,
        paletteStyle = miuixPaletteStyle,
        colorSpec = miuixColorSpec,
    )

    MiuixTheme(controller = controller) {
        val context = LocalContext.current
        LaunchedEffect(darkTheme) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        content()
    }
}
