package com.remoteconfig.override.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.remoteconfig.override.ui.component.bottombar.MainPagerState

/**
 * 当前 MainPagerState（tab/pager 协调器）— 对齐 KernelSU MainActivity.kt:222。
 * 由 MainScreen 显式 provide，BottomBar/SideRail 系列组件读取。
 */
val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> {
    error("LocalMainPagerState not provided")
}
