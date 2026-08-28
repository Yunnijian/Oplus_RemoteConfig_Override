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
 * 配置列表页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 *
 * 粘性首刷：首次成为当前页才触发一次 [MainViewModel.refreshAll]
 * （性能规格 9.2，对齐 HomePage 的粘性激活模式），每次重新进入 tab 不重复刷新。
 *
 * 导航回调：onGameClick / onNewConfig 由 MainScreen 传入（内部做
 * viewModel.loadConfig / createNewConfig + navigator.push(Route.ConfigEditor(pkg))）。
 */
@Composable
fun ConfigListPage(
    viewModel: MainViewModel,
    isCurrentPage: Boolean,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
) {
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshAll()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigListContentMiuix(viewModel, onGameClick, onNewConfig)
        UiMode.Material -> ConfigListContentMaterial(viewModel, onGameClick, onNewConfig)
    }
}
