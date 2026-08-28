package com.remoteconfig.override.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.remoteconfig.override.settings.UiMode

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }

// 性能开关（LocalEnableBlur / LocalEnableFloatingBottomBar / LocalEnableFloatingBottomBarBlur /
// LocalEnableNavigationBadge）定义在 Theme.kt（对齐 KernelSU Theme.kt:133-141），
// 默认必须为 false，由 MainActivity 显式 provide。

/**
 * 窗口宽度尺寸类（Google 标准 WindowSizeClass，Task 12）。
 *
 * 由 MainActivity 在 setContent 内计算并 provide（`calculateWindowSizeClass(this)`，
 * 每次重组读取以保证分屏/平行视窗/自由窗口重布局时即时响应）。
 * 默认 Compact：任何未显式 provide 的调用点（如预览/测试）都安全走窄屏路径。
 */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

/** 宽屏判定：≥840dp（Expanded）。用于平板双窗 / 左侧导航 rail。 */
@Composable
fun isExpandedWidth(): Boolean =
    LocalWindowWidthClass.current == WindowWidthSizeClass.Expanded
