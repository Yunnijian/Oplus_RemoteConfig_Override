package com.remoteconfig.override.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * 仅在导航转场动画结束后 + 1 帧才返回 true。
 * 转场期间返回 false → 页面显示轻量占位（动画流畅）；
 * 动画结束 + 1 帧 → 重内容开始组装（此时页面已静止，卡顿不可见）。
 * 值是粘性的 —— 一旦 true 永不回退，退出转场时内容不闪。
 */
@Composable
fun rememberContentReady(): Boolean {
    val scope = LocalNavAnimatedContentScope.current
    val transitionRunning = scope.transition.isRunning
    val ready = remember { mutableStateOf(false) }

    LaunchedEffect(transitionRunning) {
        if (!transitionRunning && !ready.value) {
            withFrameNanos { }
            ready.value = true
        }
    }

    return ready.value
}
