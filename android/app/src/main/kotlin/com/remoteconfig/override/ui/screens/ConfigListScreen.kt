package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
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
 * 双窗（Task 12）：宽屏 [isExpandedWidth]（≥840dp）下编辑器由 MainScreen 作为覆盖层
 * 渲染在 Pager 外（不参与 Pager 测量 → IME 弹出不影响 currentPage）；本页只渲染
 * 列表（铺满整页），选中项高亮通过 [dualPaneSelected]（可选参数，默认 null = 窄屏不传入）下发。
 *
 * [bottomInnerPadding]：底栏（悬浮液态玻璃底栏 / 宽屏系统导航栏）占位高度。Pager 不加
 * bottom padding（内容需铺到底栏下方供玻璃采样折射），留白由本页在 LazyColumn 末尾
 * item Spacer + FAB bottom padding 消费。
 */
@Composable
fun ConfigListPage(
    viewModel: MainViewModel,
    bottomInnerPadding: Dp = 0.dp,
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

    // 宽屏：列表铺满整页（编辑器覆盖层由 MainScreen 在 Pager 外渲染，避免 IME 干扰 Pager 测量）
    ConfigListContentImpl(viewModel, bottomInnerPadding, onGameClick, onNewConfig, dualPaneSelected, onDualPaneSelect)
}

/** 按当前 UI 风格选择列表实现；[dualPaneSelected] 用于宽屏选中行高亮。 */
@Composable
private fun ConfigListContentImpl(
    viewModel: MainViewModel,
    bottomInnerPadding: Dp,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
    dualPaneSelected: String? = null,
    onDualPaneSelect: (String) -> Unit = {},
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigListContentMiuix(viewModel, bottomInnerPadding, onGameClick, onNewConfig, dualPaneSelected, onDualPaneSelect)
        UiMode.Material -> ConfigListContentMaterial(viewModel, bottomInnerPadding, onGameClick, onNewConfig, dualPaneSelected, onDualPaneSelect)
    }
}

/** 双窗右侧空态提示（MainScreen 双窗布局用，按 UI 模式取主题色）。 */
@Composable
internal fun EmptyPaneHint() {
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

/**
 * 宽屏双窗编辑器路由页：左配置列表 + 右编辑器同框。
 *
 * 这是 [Route.ConfigEditor] 在 Expanded 宽度下的渲染（MainActivity entry 分发）。
 * 编辑器在路由页（Pager 外）→ IME 弹出不干扰 Pager currentPage（修复横屏跳设置页）；
 * 与列表同框，保持"配置 + 编辑"双窗体验；Navigation3 管理转场（无闪烁）。
 */
@Composable
fun DualPaneEditorScreen(
    viewModel: MainViewModel,
    packageName: String,
    onBack: () -> Unit,
) {
    // 当前选中的编辑包（初始为路由参数，后续点列表切换）
    var selected by rememberSaveable(packageName) { mutableStateOf(packageName) }
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(0.42f)) {
            ConfigListPage(
                viewModel = viewModel,
                bottomInnerPadding = navBarBottomPadding,
                isCurrentPage = true,
                onGameClick = { pkg ->
                    viewModel.loadConfig(pkg)
                    selected = pkg
                },
                onNewConfig = { pkg ->
                    viewModel.createNewConfig(pkg)
                    selected = pkg
                },
                dualPaneSelected = selected,
                onDualPaneSelect = { selected = it },
            )
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ConfigEditorPane(
                viewModel = viewModel,
                packageName = selected,
                bottomInnerPadding = navBarBottomPadding,
                onClosed = onBack,
            )
        }
    }
}
