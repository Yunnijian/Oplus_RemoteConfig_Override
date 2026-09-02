package com.remoteconfig.override.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.remoteconfig.override.settings.ColorMode

/**
 * Material 风格主题 — 对齐 KernelSU MaterialKernelSUTheme（裁剪 MonetColorsProvider）。
 * Material 模式始终走 materialkolor 取色：keyColor ≠ 0 用强调色种子；
 * keyColor == 0 用系统动态色 primary 作种子（Color.Unspecified 由
 * [rememberRemoteConfigColorScheme] 解析）——色彩风格/标准在默认色块下同样生效。
 * colorMode 六态（含 AMOLED 纯黑）。
 *
 * 主题壳用 MaterialExpressiveTheme + MotionScheme.expressive()（与 KernelSU 一致），
 * 主题切换时整套颜色方案由 [rememberRemoteConfigColorScheme] 的 animateAsState()
 * 做 spring 平滑过渡（对齐 KernelSU ThemeExt.animateAsState），不做生硬跳变。
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

    // 预热整套强调色方案（17 套后台并行）：用户进「主题设置」时色块首帧即现，
    // 不再出现空白占位等待取色（见 RemoteConfigSchemeCache）。
    PrewarmRemoteConfigSchemes(
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    val colorScheme = rememberRemoteConfigColorScheme(
        seedColor = if (appSettings.keyColor == 0) Color.Unspecified else Color(appSettings.keyColor),
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}
