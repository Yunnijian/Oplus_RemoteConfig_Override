package com.remoteconfig.override.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.remoteconfig.override.settings.UiMode

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }

// 性能开关：默认必须为 false，由 MainActivity 显式 provide（对齐 KernelSU Theme.kt:135-139）
val LocalEnableGlass = staticCompositionLocalOf { false }
val LocalEnableGlassBlur = staticCompositionLocalOf { false }
