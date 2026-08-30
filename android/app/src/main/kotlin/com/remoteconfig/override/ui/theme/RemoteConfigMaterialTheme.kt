package com.remoteconfig.override.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.remoteconfig.override.settings.ColorMode

/**
 * Material 风格主题 — 对齐 KernelSU MaterialKernelSUTheme（裁剪 MonetColorsProvider）。
 * 读 [AppSettings]：
 * - keyColor ≠ 0 → 静态强调色（materialkolor 取色，缓存预热，keyColor 优先）；
 * - keyColor == 0 且 miuixMonet 开启（Material 模式读同一 miuixMonet 门控）→ 系统动态色
 *   （dynamicDarkColorScheme / dynamicLightColorScheme）；
 * - 否则 → 静态色表（lightColorScheme / darkColorScheme）。
 * colorMode 六态（含 AMOLED 纯黑）。
 *
 * 主题切换不做颜色弹簧动画（perfetto 实证：48 路 animateAsState 在动画窗口内
 * 逐帧失效全树 11 个 composition，每帧 70-120ms，单次点色块级联 4-5 个长帧；
 * 直接切换收敛为单波重组，点击卡顿大幅缩短）。
 *
 * 注：KernelSU 用 MaterialExpressiveTheme/MotionScheme.expressive()，但它们在锁定版
 * material3 1.4.0 中为 internal（KernelSU 用 1.5.0-alpha26 才公开）——为保留 1.4.0 锁定
 * （R0/R1 硬约束），这里回落到标准 MaterialTheme。
 */
@Composable
fun RemoteConfigMaterialTheme(
    appSettings: AppSettings,
    miuixMonet: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    val dynamicColor = appSettings.keyColor == 0 && miuixMonet

    // 预热整套强调色方案（17 套后台并行）：用户进「主题设置」时色块首帧即现，
    // 不再出现空白占位等待取色（见 RemoteConfigSchemeCache）。
    PrewarmRemoteConfigSchemes(
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    val colorScheme = if (dynamicColor) {
        // 系统动态取色（Monet）——不应用 materialkolor 的 paletteStyle/colorSpec
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (appSettings.keyColor != 0) {
        // 自定义强调色优先：materialkolor 静态种子方案
        rememberRemoteConfigColorScheme(
            seedColor = Color(appSettings.keyColor),
            isDark = darkTheme,
            isAmoled = amoledMode,
            paletteStyle = appSettings.paletteStyle,
            colorSpec = appSettings.colorSpec,
        )
    } else {
        // 静态色表（非 Monet）
        (if (darkTheme) darkColorScheme() else lightColorScheme()).amoledBackground(amoledMode)
    }

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
