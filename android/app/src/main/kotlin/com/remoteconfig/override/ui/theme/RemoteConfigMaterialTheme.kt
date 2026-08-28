package com.remoteconfig.override.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.remoteconfig.override.settings.AppSettingsRepository

internal val LightColors = lightColorScheme(
    primary = Color(0xFFCC0000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410000),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF705C2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE0A6),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BE)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFFDFC48C),
    onTertiary = Color(0xFF3C2E04),
    tertiaryContainer = Color(0xFF564419),
    onTertiaryContainer = Color(0xFFFCE0A6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF0DFDB),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF0DFDB),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341)
)

/** paletteStyle 字符串 → materialkolor [PaletteStyle]（非法值回退 TonalSpot）。 */
internal fun parsePaletteStyle(style: String): PaletteStyle =
    try {
        PaletteStyle.valueOf(style)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }

/** colorSpec 字符串 → materialkolor [ColorSpec.SpecVersion]（非法值回退 SPEC_2025）。 */
internal fun parseColorSpec(spec: String): ColorSpec.SpecVersion =
    try {
        ColorSpec.SpecVersion.valueOf(spec)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2025
    }

/** 仅这些样式支持 2025 动态取色规范（对齐 KernelSU Theme.kt supportsSpec2025）。 */
internal val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

/** 不支持 2025 规范的样式自动降级到 SPEC_2021（对齐 KernelSU Theme.kt effectiveFor）。 */
internal fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

/** Material 风格主题：迁移自旧 RemoteConfigTheme，darkTheme 改为参数传入。 */
@Composable
fun RemoteConfigMaterialTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val keyColor = AppSettingsRepository.keyColor
    val paletteStyle = parsePaletteStyle(AppSettingsRepository.paletteStyle)
    val colorSpec = parseColorSpec(AppSettingsRepository.colorSpec)

    val colorScheme = when {
        // 用户指定自定义取色种子 → 无条件 materialkolor 按 paletteStyle/spec 生成
        // （对齐 KernelSU dynamicColor = keyColor == 0 语义及 Miuix 侧：keyColor 优先于壁纸取色）
        keyColor != 0 -> rememberDynamicColorScheme(
            seedColor = Color(keyColor),
            isDark = darkTheme,
            style = paletteStyle,
            specVersion = colorSpec.effectiveFor(paletteStyle),
        )
        // keyColor==0 → 保持原行为：SDK≥S 壁纸取色，否则静态色表回退
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val surfaceArgb = colorScheme.surface.toArgb()
            window.statusBarColor = surfaceArgb
            window.navigationBarColor = surfaceArgb
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(surfaceArgb))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
