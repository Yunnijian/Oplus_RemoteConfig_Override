package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.ui.component.ScrollToTopOnChange
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.SearchAppBar
import com.remoteconfig.override.ui.component.material.SegmentedItem
import com.remoteconfig.override.ui.component.material.SegmentedListItem
import com.remoteconfig.override.viewmodel.AppSortConfig
import com.remoteconfig.override.viewmodel.AppSortType
import com.remoteconfig.override.viewmodel.AppRow
import com.remoteconfig.override.viewmodel.HyperOsViewModel

/** 排序菜单条目（文案与 KernelSU zh-rCN 的 sort_by_* 逐字一致）。 */
private val materialSortEntries = listOf(
    AppSortType.NAME to "应用名",
    AppSortType.PACKAGE_NAME to "包名",
    AppSortType.INSTALL_TIME to "安装时间",
    AppSortType.UPDATE_TIME to "更新时间",
)

/**
 * HyperOS 应用列表 — Material 3 实现。
 *
 * 框架结构直接复用 KernelSU 超级用户页（`SuperUserPagerMaterial`）：
 * `ExpressiveScaffold + SearchAppBar`（LargeFlexibleTopAppBar + ContainedSearchBar，
 * 展开后整屏接管）+ `DropdownMenuPopup/MenuDefaults.groupShape` 分组排序菜单
 * （带 VirtualKey 触感）+ `PullToRefreshBox` + `PullToRefreshDefaults.LoadingIndicator`
 * 下拉刷新 + `ScrollToTopOnChange` 换序/刷新时安静回顶。
 *
 * [bottomInnerPadding]：底栏占位高度，由本页在列表末尾 Spacer 消费（对齐 ConfigListMaterial）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperOsAppListMaterial(
    viewModel: HyperOsViewModel,
    bottomInnerPadding: Dp = 0.dp,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val haptic = LocalHapticFeedback.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    var searchText by remember { mutableStateOf("") }
    val filtered by remember {
        derivedStateOf {
            val query = searchText.trim()
            if (query.isEmpty()) state.apps
            else state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.pkg.contains(query, ignoreCase = true) ||
                    it.pinyin.contains(query.lowercase(), ignoreCase = true)
            }
        }
    }

    val latestApps = rememberUpdatedState(filtered)
    val latestRefreshing = rememberUpdatedState(state.refreshing)
    ScrollToTopOnChange(
        listState,
        state.sortConfig,
        refreshTick.intValue,
        isBusy = { latestRefreshing.value },
    ) { latestApps.value }

    /** 一行应用（图标 + 名称 + 组别名/功能数），折叠列表与搜索结果共用。 */
    val appRow: @Composable (Int, Int, AppRow) -> Unit = { index, count, app ->
        SegmentedItem(index = index, count = count) {
            SegmentedListItem(
                selected = false,
                onClick = { navigator.push(Route.HyperOsAppDetail(app.pkg)) },
                headlineContent = {
                    Text(
                        text = app.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = materialAppSubtitle(app.group, app.features),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    // 应用图标：IO 线程预加载缓存，组合期零 PackageManager 调用
                    val icon = viewModel.getCachedIcon(app.pkg)
                    if (icon != null) {
                        Image(
                            bitmap = icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                },
            )
        }
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                title = { Text("应用配置") },
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onClearClick = { searchText = "" },
                actions = {
                    AppFilterPopupMaterial(
                        showInstalledOnly = state.showInstalledOnly,
                        onShowInstalledOnlyChange = viewModel::setShowInstalledOnly,
                    )
                    var showSortMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序",
                        )

                        DropdownMenuPopup(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            val config = state.sortConfig
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(index = 0, count = 2),
                            ) {
                                materialSortEntries.onEachIndexed { index, (type, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        selected = config.sortType == type,
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            viewModel.updateSortConfig(config.withType(type))
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = materialSortEntries.size,
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(index = 1, count = 2),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("倒序") },
                                    checked = config.reversed,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        viewModel.updateSortConfig(config.toggleReversed())
                                        showSortMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { _, _ ->
                    // 展开搜索即隐藏列表，仅保留输入区（对齐 KernelSU）：
                    // 未输入查询词时不渲染结果，输入后才在 searchContent 槽显示命中行。
                },
                searchContent = { bottomPadding, _ ->
                    LaunchedEffect(searchText) {
                        searchListState.scrollToItem(0)
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "无匹配应用",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = searchListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 0.dp,
                                bottom = 16.dp + bottomPadding,
                            ),
                        ) {
                            itemsIndexed(filtered, key = { _, item -> item.pkg }) { index, app ->
                                appRow(index, filtered.size, app)
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = state.refreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                viewModel.refreshFromPull()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = state.refreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            when {
                // spinner 仅在无数据可显示时出现（真正的首屏加载），同 Miuix 版
                state.loading && state.apps.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.unavailable ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.error ?: "Joyose 云控不可读",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.refreshList() }) { Text("重试") }
                        }
                    }

                state.apps.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无云控应用",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 16.dp + bottomInnerPadding,
                    ),
                ) {
                    itemsIndexed(filtered, key = { _, item -> item.pkg }) { index, app ->
                        appRow(index, filtered.size, app)
                    }
                }
            }
        }
    }
}

/**
 * 右上角筛选弹层（KernelSU 第二个弹层的形制：MoreVert + DropdownMenuPopup + 勾选行，
 * 带 VirtualKey 触感）。选项语义按本项目改为"所有应用配置 / 只显示已安装应用配置"。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppFilterPopupMaterial(
    showInstalledOnly: Boolean,
    onShowInstalledOnlyChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showFilterMenu by remember { mutableStateOf(false) }

    IconButton(onClick = { showFilterMenu = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "筛选",
        )

        DropdownMenuPopup(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                FilterRowMaterial(
                    label = "所有应用配置",
                    checked = !showInstalledOnly,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onShowInstalledOnlyChange(false)
                        showFilterMenu = false
                    },
                )
                FilterRowMaterial(
                    label = "只显示已安装应用配置",
                    checked = showInstalledOnly,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onShowInstalledOnlyChange(true)
                        showFilterMenu = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRowMaterial(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        checked = checked,
        checkedLeadingIcon = {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
            )
        },
        onCheckedChange = { onClick() },
        shapes = MenuDefaults.itemShape(index = 0, count = 1),
    )
}

/** 应用行次级文本：group 别名（如有）+ 功能数。 */
private fun materialAppSubtitle(group: String?, features: Int): String =
    if (group.isNullOrEmpty()) "${features} 项功能" else "$group · ${features} 项功能"
