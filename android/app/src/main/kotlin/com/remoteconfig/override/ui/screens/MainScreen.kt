package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Navigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.component.glass.GlassBottomBar
import com.remoteconfig.override.ui.component.glass.LocalGlassTabScale
import com.remoteconfig.override.ui.component.glass.LocalGlassTabSelect
import com.remoteconfig.override.ui.component.glass.rememberGlassBackdrop
import com.remoteconfig.override.ui.component.rememberContentReady
import com.remoteconfig.override.ui.theme.LocalEnableGlass
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isExpandedWidth
import com.remoteconfig.override.ui.theme.isInDarkTheme
import com.remoteconfig.override.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState as rememberMiuixNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGE_COUNT = 3

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })
    val contentReady = rememberContentReady()
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    // settledPage 本身就是 state，直接读取即可（只在值变化时重组，拖动中不逐帧）
    val settledPage = pagerState.settledPage
    val expanded = isExpandedWidth()

    // 配置页双窗选中（宽屏 list-detail）：null = 未选；窄屏不使用（恒为 null）
    var dualPaneSelected by rememberSaveable { mutableStateOf<String?>(null) }

    if (expanded) {
        // ── 宽屏（Expanded ≥840dp）：左侧导航 rail + 右侧 pager（无底栏）──
        Row(Modifier.fillMaxSize()) {
            if (LocalUiMode.current == UiMode.Miuix) {
                MiuixNavRail(selectedPage = settledPage, onSelect = { scope.launch { pagerState.animateScrollToPage(it) } })
            } else {
                MaterialNavRail(selectedPage = settledPage, onSelect = { scope.launch { pagerState.animateScrollToPage(it) } })
            }
            MainPager(
                pagerState = pagerState,
                contentReady = contentReady,
                viewModel = viewModel,
                navigator = navigator,
                isCurrentPageFor = { page -> page == settledPage },
                expanded = true,
                dualPaneSelected = dualPaneSelected,
                onDualPaneSelect = { dualPaneSelected = it },
            )
        }
    } else {
        // ── 窄屏（Compact/Medium）：保持既有 3 tab Pager + 底栏 ──
        val glassBackdrop = rememberGlassBackdrop(enabled = LocalEnableGlass.current)
        Scaffold(
            bottomBar = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    GlassBottomBar(
                        selectedIndex = { settledPage },
                        onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        backdrop = glassBackdrop,
                    ) {
                        GlassTab(0, Icons.Filled.Home, "首页")
                        GlassTab(1, Icons.AutoMirrored.Filled.List, "配置")
                        GlassTab(2, Icons.Filled.Settings, "设置")
                    }
                }
            },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                beyondViewportPageCount = if (contentReady) 1 else 0,
                overscrollEffect = null,
            ) { page ->
                MainPagerPage(
                    page = page,
                    isCurrentPage = page == settledPage,
                    contentReady = contentReady,
                    viewModel = viewModel,
                    navigator = navigator,
                    expanded = false,
                    dualPaneSelected = null,
                    onDualPaneSelect = {},
                )
            }
        }
    }
}

/** 宽屏 Miuix 侧边导航 rail（对齐 KernelSU NavigationRailMiuix.kt，3 项：首页/配置/设置）。 */
@Composable
private fun MiuixNavRail(selectedPage: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("首页", MiuixIcons.Home, 0),
        Triple("配置", MiuixIcons.ListView, 1),
        Triple("设置", MiuixIcons.Settings, 2),
    )
    MiuixNavigationRail(
        modifier = Modifier.fillMaxHeight(),
        state = rememberMiuixNavigationRailState(),
        color = MiuixTheme.colorScheme.surface,
        expandContentDescription = "展开导航",
        collapseContentDescription = "收起导航",
    ) {
        items.forEach { (label, icon, index) ->
            MiuixNavigationRailItem(
                selected = selectedPage == index,
                onClick = { onSelect(index) },
                icon = icon,
                label = label,
            )
        }
    }
}

/** 宽屏 Material 3 侧边导航 rail（3 项：首页/配置/设置）。 */
@Composable
private fun MaterialNavRail(selectedPage: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("首页", Icons.Filled.Home, 0),
        Triple("配置", Icons.AutoMirrored.Filled.List, 1),
        Triple("设置", Icons.Filled.Settings, 2),
    )
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        items.forEach { (label, icon, index) ->
            NavigationRailItem(
                selected = selectedPage == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

/** Pager 内容（宽屏）——直接铺满右侧，无 bottom bar padding。 */
@Composable
private fun MainPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    contentReady: Boolean,
    viewModel: MainViewModel,
    navigator: Navigator,
    isCurrentPageFor: (Int) -> Boolean,
    expanded: Boolean,
    dualPaneSelected: String?,
    onDualPaneSelect: (String) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = if (contentReady) 1 else 0,
        overscrollEffect = null,
    ) { page ->
        MainPagerPage(
            page = page,
            isCurrentPage = isCurrentPageFor(page),
            contentReady = contentReady,
            viewModel = viewModel,
            navigator = navigator,
            expanded = expanded,
            dualPaneSelected = dualPaneSelected,
            onDualPaneSelect = onDualPaneSelect,
        )
    }
}

@Composable
private fun MainPagerPage(
    page: Int,
    isCurrentPage: Boolean,
    contentReady: Boolean,
    viewModel: MainViewModel,
    navigator: Navigator,
    expanded: Boolean,
    dualPaneSelected: String?,
    onDualPaneSelect: (String) -> Unit,
) {
    when (page) {
        0 -> if (isCurrentPage || contentReady) HomePage(viewModel, isCurrentPage)
        1 -> if (isCurrentPage || contentReady) ConfigListPage(
            viewModel, isCurrentPage,
            onGameClick = { pkg ->
                if (expanded) {
                    // 宽屏双窗：只选中，右侧窗格即时切换，不 push 路由
                    onDualPaneSelect(pkg)
                } else {
                    viewModel.loadConfig(pkg)
                    navigator.push(Route.ConfigEditor(pkg))
                }
            },
            onNewConfig = { pkg ->
                viewModel.createNewConfig(pkg)
                if (expanded) onDualPaneSelect(pkg)
                else navigator.push(Route.ConfigEditor(pkg))
            },
            dualPaneSelected = if (expanded) dualPaneSelected else null,
            onDualPaneSelect = onDualPaneSelect,
        )
        2 -> if (isCurrentPage || contentReady) SettingsContent()
    }
}

/**
 * 底栏 tab（对应 catalog LiquidBottomTab + KernelSU FloatingBottomBarItem）：
 * 只做视觉，点击经 LocalGlassTabSelect 汇入 GlassBottomBar 的 currentIndex，
 * 由其 snapshotFlow 驱动 onSelected（避免点击→pager→selectedIndex 的双跳）。
 * 缩放值只在 graphicsLayer lambda 中读取（draw-phase 观测，不逐帧重组）。
 * 比简报签名多一个 index 参数：tab 需要知道自己点击后应选中哪一项。
 */
@Composable
fun RowScope.GlassTab(index: Int, icon: ImageVector, label: String) {
    val select = LocalGlassTabSelect.current
    val scale = LocalGlassTabScale.current
    // 任务 6 先用固定兜底内容色（避免主题依赖），Task 7 接入真实主题色后替换
    val contentColor = if (isInDarkTheme()) Color(0xFFE6E6E6) else Color(0xFF1A1A1A)
    Column(
        Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = { select(index) },
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}
