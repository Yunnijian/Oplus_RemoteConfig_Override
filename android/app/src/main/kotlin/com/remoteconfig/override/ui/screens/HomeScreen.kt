package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.MainViewModel

/**
 * 首页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 *
 * 粘性激活：首次成为当前页才触发刷新（性能规格 9.2，对齐 KernelSU HomeScreen.kt:50-56），
 * 页面首次进入 tab 0 时执行一次 [MainViewModel.refreshAll]。
 *
 * [bottomInnerPadding]：底栏（悬浮液态玻璃底栏 / 系统导航栏）占位高度。对齐 KernelSU
 * HomePager(navController, bottomInnerPadding, isCurrentPage)：Pager **不加** bottom padding，
 * 内容铺满到底栏下方（玻璃 backdrop 才有内容可采样折射），底部留白由页面自己在
 * 滚动内容末尾用 Spacer 消费。
 */
@Composable
fun HomePage(
    viewModel: MainViewModel,
    bottomInnerPadding: Dp = 0.dp,
    isCurrentPage: Boolean = true,
) {
    // 粘性激活：首次成为当前页才触发刷新（性能规格 9.2，对齐 KernelSU HomeScreen.kt:50-56）
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshAll()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomeContentMiuix(viewModel, bottomInnerPadding)
        UiMode.Material -> HomeContentMaterial(viewModel, bottomInnerPadding)
    }
}
