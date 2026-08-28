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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.Dp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.LocalMainPagerState
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
fun MainScreen(viewModel: MainViewModel) {
    val uiMode = LocalUiMode.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val contentReady = rememberContentReady()
    val navigator = LocalNavigator.current
    val expanded = isExpandedWidth()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(pagerState)

    // 手势滑动 → 实时同步 tab（isNavigating=false 时才生效，点击滚动中被协调器屏蔽）
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }
    // 内容门控用 settledPage（停稳才算当前页）
    val settledPage = pagerState.settledPage

    // 配置页双窗选中（宽屏 list-detail）：null = 未选；窄屏不使用（恒为 null）
    var dualPaneSelected by rememberSaveable { mutableStateOf<String?>(null) }

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
                        0 -> if (isCurrentPage || contentReady) HomePage(viewModel, bottomInnerPadding, isCurrentPage)
                        1 -> if (isCurrentPage || contentReady) ConfigListPage(
                            viewModel = viewModel,
                            bottomInnerPadding = bottomInnerPadding,
                            isCurrentPage = isCurrentPage,
                            onGameClick = { pkg ->
                                if (expanded) {
                                    // 宽屏双窗：只选中，右侧窗格即时切换，不 push 路由
                                    dualPaneSelected = pkg
                                } else {
                                    viewModel.loadConfig(pkg)
                                    navigator.push(Route.ConfigEditor(pkg))
                                }
                            },
                            onNewConfig = { pkg ->
                                viewModel.createNewConfig(pkg)
                                if (expanded) dualPaneSelected = pkg
                                else navigator.push(Route.ConfigEditor(pkg))
                            },
                            dualPaneSelected = if (expanded) dualPaneSelected else null,
                            onDualPaneSelect = { dualPaneSelected = it },
                        )

                        2 -> if (isCurrentPage || contentReady) SettingsContent(bottomInnerPadding)
                    }
                }
            }
        }

        if (useRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Miuix -> MiuixScaffold { _ ->
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Material -> MaterialScaffold(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
