package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.MainViewModel

/**
 * 首页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 *
 * 粘性激活：首次成为当前页才触发刷新（性能规格 9.2，对齐 KernelSU HomeScreen.kt:50-56），
 * 页面首次进入 tab 0 时执行一次 [MainViewModel.refreshAll]。
 */
@Composable
fun HomePage(viewModel: MainViewModel, isCurrentPage: Boolean) {
    // 粘性激活：首次成为当前页才触发刷新（性能规格 9.2，对齐 KernelSU HomeScreen.kt:50-56）
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshAll()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomeContentMiuix(viewModel)
        UiMode.Material -> HomeContentMaterial(viewModel)
    }
}
