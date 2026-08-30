package com.remoteconfig.override.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
