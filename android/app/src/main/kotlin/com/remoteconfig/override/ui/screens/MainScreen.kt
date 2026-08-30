package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.LocalMainPagerState
import com.remoteconfig.override.ui.component.DiscardChangesDialog
import com.remoteconfig.override.ui.component.bottombar.BottomBar
import com.remoteconfig.override.ui.component.bottombar.SideRail
import com.remoteconfig.override.ui.component.bottombar.rememberMainPagerState
import com.remoteconfig.override.ui.component.bottombar.useNavigationRail
import com.remoteconfig.override.ui.component.rememberContentReady
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBar
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBarBlur
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isExpandedWidth
import com.remoteconfig.override.ui.util.rememberBlurBackdrop
import com.remoteconfig.override.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGE_COUNT = 3

/**
 * 截断 Pager 各页内容向外层 HorizontalPager 的可见性请求（bringIntoView）：
 * 离屏组装的页面（编辑器文本域 / 设置页下拉锚点）的边界可落在相邻页范围内，
 * 聚焦、inset 变化等时机触发的请求会把 Pager 拽向相邻 tab（跳设置 / 闪烁回弹）。
 * tab 切换只经 MainPagerState（侧栏/底栏点击），无需 bringIntoView 参与。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val PagerBringIntoViewBarrier = object : BringIntoViewResponder {
    override fun calculateRectForParent(rect: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect =
        androidx.compose.ui.geometry.Rect.Zero
    override suspend fun bringChildIntoView(childRect: () -> androidx.compose.ui.geometry.Rect?) {
    }
}

/** 每页内容根部的可见性请求屏障。 */
@Composable
private fun PageBringIntoViewBarrier(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.bringIntoViewResponder(PagerBringIntoViewBarrier)
    ) {
        content()
    }
}

/**
 * 主页面 — 完整对齐 KernelSU MainScreen（MainActivity.kt:224-388）。
 *
 * - [MainPagerState]（rememberMainPagerState）：tab/pager 协调核心。点击 tab →
 *   [androidx.compose.foundation.pager.PagerState.scroll] + PagerNavigationSpring 弹簧滚动
 *   （springAnimateToPage，替代 animateScrollToPage——点击与手势滑动协调的关键）；
 *   手势滑动 → LaunchedEffect(currentPage) 调 syncPage() 实时跟随。
 * - [BottomBar]/[SideRail]：按 LocalUiMode 分发 Miuix/Material 双模式；
 *   Miuix 底栏读 LocalEnableFloatingBottomBar/LocalEnableFloatingBottomBarBlur
 *   走 FloatingBottomBar（悬浮液态玻璃）或普通 NavigationBar。
 * - 保留：3 tab（首页/配置/设置）、双窗（Expanded rail + 配置 list-detail）、
 *   rememberContentReady 延迟组装、isCurrentPage（settledPage）门控。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
) {
    val uiMode = LocalUiMode.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val contentReady = rememberContentReady()
    val navigator = LocalNavigator.current
    val expanded = isExpandedWidth()

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(pagerState)

    // 手势滑动 → 实时同步 tab（isNavigating=false 时才生效，点击滚动中被协调器屏蔽）
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }
    // 内容门控用 settledPage（停稳才算当前页）
    val settledPage = pagerState.settledPage
    // tab 选择上报持久化（对齐 KernelSU onPageChanged）：切换界面风格会重建整棵
    // 子树，裸 remember 的 pagerState 被重置，靠外置的选中页经 initialPage 恢复。
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    // 配置页双窗选中（宽屏 list-detail）：null = 未选；窄屏不使用（恒为 null）
    var dualPaneSelected by rememberSaveable { mutableStateOf<String?>(null) }
    // 脏检查待切换目标：编辑中有未保存修改时切换/新建先弹“放弃修改”确认。
    var pendingDualPaneSelect by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingNewConfig by rememberSaveable { mutableStateOf<String?>(null) }

    /** 宽屏切换目标包：脏状态下先弹确认，否则直接切换。 */
    val selectDualPane: (String) -> Unit = { pkg ->
        if (pkg != dualPaneSelected && viewModel.isEditingDirty() && pkg.isNotEmpty()) {
            pendingDualPaneSelect = pkg
        } else {
            dualPaneSelected = pkg
        }
    }
    if (pendingDualPaneSelect != null) {
        DiscardChangesDialog(
            onConfirm = {
                val pkg = pendingDualPaneSelect.orEmpty()
                pendingDualPaneSelect = null
                dualPaneSelected = pkg
            },
            onDismiss = { pendingDualPaneSelect = null },
        )
    }
    if (pendingNewConfig != null) {
        DiscardChangesDialog(
            onConfirm = {
                val pkg = pendingNewConfig.orEmpty()
                pendingNewConfig = null
                viewModel.createNewConfig(pkg)
                dualPaneSelected = pkg
            },
            onDismiss = { pendingNewConfig = null },
        )
    }

    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface // Blur is not used in Material, this is just a placeholder
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val useRail = useNavigationRail()

    // 锁定 Pager 拖拽（宽屏双窗编辑器嵌在第 1 页内）时，必须同时替换默认的
    // pageNestedScrollConnection：foundation 1.12 的默认实现在
    // currentPageOffsetFraction ≠ 0 时经 onPreScroll 截走子组件派发的 UserInput
    // 横向位移，且不检查 userScrollEnabled——子像素残留（见 springAnimateToPage 的
    // 无条件 snap）即触发“首次滑动不落到编辑区”，且 Pager 被挪离页界后抢夺会自我
    // 维持，直到下一个整页边界才停（无 settle 动画，冻结在半路）。
    val defaultPagerScrollConnection = PagerDefaults.pageNestedScrollConnection(
        pagerState,
        Orientation.Horizontal,
    )
    val lockedPagerScrollConnection = remember { object : NestedScrollConnection {} }
    val pageNestedScrollConnection =
        if (expanded && dualPaneSelected != null) lockedPagerScrollConnection else defaultPagerScrollConnection

    CompositionLocalProvider(LocalMainPagerState provides mainPagerState) {
        val pagerContent: @Composable (Dp) -> Unit = { bottomInnerPadding ->
            Box(
                modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier
            ) {
                // Pager 不加 bottom padding（对齐 KernelSU）：内容必须铺满到底栏下方，
                // 悬浮底栏的玻璃才有内容可采样折射。底部留白由各页面自己消费
                // （bottomInnerPadding 透传给页面的 contentPadding）。
                HorizontalPager(
                    state = pagerState,
                    // 宽屏双窗时 JSON 编辑器嵌在第 1 页内：禁用 Pager 自身的手势拖拽，
                    // 编辑器内部的横向滚动不会被 Pager 抢走（首次滑动尤其如此）；
                    // 配合 ConfigEditorPane 根部 NestedScrollConnection 拦截内部滚动
                    // 抵达边缘后的剩余位移，双通道都堵死。此模式下 tab 切换走侧栏。
                    userScrollEnabled = !(expanded && dualPaneSelected != null),
                    pageNestedScrollConnection = pageNestedScrollConnection,
                    modifier = if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                        Modifier.layerBackdrop(backdrop)
                    } else {
                        Modifier
                    },
                    beyondViewportPageCount = if (contentReady) 3 else 0,
                    overscrollEffect = null,
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (isCurrentPage || contentReady) PageBringIntoViewBarrier {
                            HomePage(viewModel, bottomInnerPadding, isCurrentPage)
                        }
                        1 -> if (isCurrentPage || contentReady) PageBringIntoViewBarrier {
                            ConfigListPage(
                                viewModel = viewModel,
                                bottomInnerPadding = bottomInnerPadding,
                                isCurrentPage = isCurrentPage,
                                onGameClick = { pkg ->
                                    if (expanded) {
                                        // 宽屏双窗：只选中，右侧窗格即时切换，不 push 路由
                                        selectDualPane(pkg)
                                    } else {
                                        viewModel.loadConfig(pkg)
                                        navigator.push(Route.ConfigEditor(pkg))
                                    }
                                },
                                onNewConfig = { pkg ->
                                    // 新建配置同样会重置编辑缓冲区：脏状态下先确认。
                                    if (expanded && viewModel.isEditingDirty()) {
                                        pendingNewConfig = pkg
                                    } else {
                                        viewModel.createNewConfig(pkg)
                                        if (expanded) dualPaneSelected = pkg
                                        else navigator.push(Route.ConfigEditor(pkg))
                                    }
                                },
                                dualPaneSelected = if (expanded) dualPaneSelected else null,
                                onDualPaneSelect = selectDualPane,
                            )
                        }

                        2 -> if (isCurrentPage || contentReady) PageBringIntoViewBarrier {
                            SettingsContent(bottomInnerPadding)
                        }
                    }
                }
            }
        }

        if (useRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Miuix -> MiuixScaffold { innerPadding ->
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                // rail 模式外层 Scaffold 无 topBar，其 top inset 即状态栏高度；
                                // 而三个 Pager 页面各自带 TopAppBar 已自行避让，此处再叠加会多出一条
                                // 状态栏高度的空白，故不再应用（与非 rail 分支一致）。
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Material -> MaterialScaffold(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) { innerPadding ->
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                // 同上：不叠加状态栏避让，避免双重顶栏空白。
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            val bottomBar: @Composable () -> Unit = {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Miuix -> MiuixScaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Material -> MaterialScaffold(
                    bottomBar = bottomBar,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}
