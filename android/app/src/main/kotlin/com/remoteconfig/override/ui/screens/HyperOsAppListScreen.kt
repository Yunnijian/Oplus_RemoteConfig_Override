package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.platform.Platform
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.HyperOsViewModel

/**
 * HyperOS 应用列表页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 *
 * 平台门控（P2.0）：仅 [LocalPlatform] == HyperOS 时渲染。ColorOS 分页不含本页
 * （MainScreen 按平台分支页数），其余平台的调用点安全空渲染。
 *
 * 粘性首刷：首次成为当前页才触发一次 [HyperOsViewModel.refreshList]
 * （性能规格 9.2，对齐 HomePage / ConfigListPage 的粘性激活模式），
 * 切换 tab 返回不重复刷新；手动重刷由列表实现的"重试"按钮触发。
 *
 * [bottomInnerPadding]：底栏（悬浮液态玻璃底栏 / 系统导航栏）占位高度。Pager 不加
 * bottom padding（内容需铺到底栏下方供玻璃采样折射），留白由列表实现末尾 Spacer 消费。
 */
@Composable
fun HyperOsAppListScreen(
    bottomInnerPadding: Dp = 0.dp,
    isCurrentPage: Boolean = true,
) {
    if (LocalPlatform.current != Platform.HyperOS) return

    val viewModel: HyperOsViewModel = viewModel()

    // 粘性激活：首次成为当前页才触发刷新（对齐 HomePage / ConfigListPage）
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshList()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> HyperOsAppListMiuix(viewModel, bottomInnerPadding)
        UiMode.Material -> HyperOsAppListMaterial(viewModel, bottomInnerPadding)
    }
}
