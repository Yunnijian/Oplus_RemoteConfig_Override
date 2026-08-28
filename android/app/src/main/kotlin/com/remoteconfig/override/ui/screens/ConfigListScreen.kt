package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isExpandedWidth
import com.remoteconfig.override.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 配置列表页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 *
 * 粘性首刷：首次成为当前页才触发一次 [MainViewModel.refreshAll]
 * （性能规格 9.2，对齐 HomePage 的粘性激活模式），每次重新进入 tab 不重复刷新。
 *
 * 导航回调：onGameClick / onNewConfig 由 MainScreen 传入（内部做
 * viewModel.loadConfig / createNewConfig + navigator.push(Route.ConfigEditor(pkg))）。
 *
 * 双窗（Task 12）：宽屏 [isExpandedWidth]（≥840dp）渲染 list-detail 双窗格
 * （左列表 + 右 [ConfigEditorPane]）；窄屏保持整页路由模式。列表实现不感知双窗，
 * 选中项高亮通过 [dualPaneSelected]（可选参数，默认 null = 窄屏不传入）下发。
 */
@Composable
fun ConfigListPage(
    viewModel: MainViewModel,
    isCurrentPage: Boolean,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
    dualPaneSelected: String? = null,
    onDualPaneSelect: (String) -> Unit = {},
) {
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshAll()
    }

    val expanded = isExpandedWidth()

    if (expanded) {
        // 宽屏：左列表 + 右编辑器双窗格
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth(0.42f)) {
                ConfigListContentImpl(viewModel, onGameClick, onNewConfig, dualPaneSelected)
            }
            VerticalDivider()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (dualPaneSelected.isNullOrEmpty()) {
                    EmptyPaneHint()
                } else {
                    ConfigEditorPane(
                        viewModel = viewModel,
                        packageName = dualPaneSelected,
                        onClosed = { onDualPaneSelect("") },
                    )
                }
            }
        }
    } else {
        ConfigListContentImpl(viewModel, onGameClick, onNewConfig, dualPaneSelected)
    }
}

/** 双窗右侧空态提示（按 UI 模式取主题色）。 */
@Composable
private fun EmptyPaneHint() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(
            text = "选择左侧应用查看配置",
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
        UiMode.Material -> Text(
            text = "选择左侧应用查看配置",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 按当前 UI 风格选择列表实现；[dualPaneSelected] 用于宽屏选中行高亮。 */
@Composable
private fun ConfigListContentImpl(
    viewModel: MainViewModel,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
    dualPaneSelected: String? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigListContentMiuix(viewModel, onGameClick, onNewConfig, dualPaneSelected)
        UiMode.Material -> ConfigListContentMaterial(viewModel, onGameClick, onNewConfig, dualPaneSelected)
    }
}
