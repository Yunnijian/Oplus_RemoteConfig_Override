package com.remoteconfig.override.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Material 风格主题 — 对齐 KernelSU MaterialKernelSUTheme（裁剪 MonetColorsProvider）。
 * 读 [AppSettings]：keyColor=0 → 动态取色（materialkolor rememberDynamicColorScheme）；
 * colorMode 六态（含 AMOLED 纯黑）；主题色切换带 spring 动画（ColorScheme.animateAsState）。
 *
 * 注：KernelSU 用 MaterialExpressiveTheme/MotionScheme.expressive()，但它们在锁定版
 * material3 1.4.0 中为 internal（KernelSU 用 1.5.0-alpha26 才公开）——为保留 1.4.0 锁定
 * （R0/R1 硬约束），这里回落到标准 MaterialTheme，动画取色不变。
 */
@Composable
fun RemoteConfigMaterialTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    val dynamicColor = appSettings.keyColor == 0

    val colorScheme = rememberRemoteConfigColorScheme(
        seedColor = if (dynamicColor) Color.Unspecified else Color(appSettings.keyColor),
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val animatedColorScheme = colorScheme.animateAsState()

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content,
    )
}
