package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.model.GameConfigSummary
import com.remoteconfig.override.ui.component.ScrollToTopOnChange
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.SearchAppBar
import com.remoteconfig.override.viewmodel.AppSortConfig
import com.remoteconfig.override.viewmodel.AppSortType
import com.remoteconfig.override.viewmodel.MainViewModel

/** 排序菜单条目（文案与 KernelSU zh-rCN 的 sort_by_* 逐字一致）。 */
private val colorosMaterialSortEntries = listOf(
    AppSortType.NAME to "应用名",
    AppSortType.PACKAGE_NAME to "包名",
    AppSortType.INSTALL_TIME to "安装时间",
    AppSortType.UPDATE_TIME to "更新时间",
)

/**
 * 配置列表 — Material 实现（对齐 KernelSU 超级用户页框架）。
 *
 * 搜索/排序/筛选/下拉刷新复用与 HyperOS 页同一套移植自 KernelSU 的组件：
 * `ExpressiveScaffold + SearchAppBar`（展开即整屏接管、隐藏列表）、
 * `DropdownMenuPopup` 排序与筛选弹层（带 VirtualKey 触感）、
 * `PullToRefreshBox + LoadingIndicator` 下拉、`ScrollToTopOnChange` 安静回顶。
 * 保留本页原有的新建配置、长按删除、清除数据、双窗选中高亮。
 *
 * [bottomInnerPadding]：底栏占位高度，由列表末尾 Spacer 与 FAB 的 bottom padding 消费。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfigListContentMaterial(
    viewModel: MainViewModel,
    bottomInnerPadding: Dp = 0.dp,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
    dualPaneSelected: String? = null,
    onDualPaneSelect: (String) -> Unit = {},
) {
    val gameList by viewModel.gameList.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val sortConfig by viewModel.sortConfig.collectAsState()
    val showInstalledOnly by viewModel.showInstalledOnly.collectAsState()
    val hasDbData by viewModel.hasDbData.collectAsState()

    val haptic = LocalHapticFeedback.current

    var showNewDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    var searchText by remember { mutableStateOf("") }
    val filtered by remember {
        derivedStateOf {
            val query = searchText.trim()
            if (query.isEmpty()) gameList
            else gameList.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true) ||
                    it.pinyin.contains(query.lowercase(), ignoreCase = true)
            }
        }
    }

    val latestApps = rememberUpdatedState(filtered)
    val latestRefreshing = rememberUpdatedState(refreshing)
    ScrollToTopOnChange(
        listState,
        sortConfig,
        refreshTick.intValue,
        isBusy = { latestRefreshing.value },
    ) { latestApps.value }

    /** 一行应用（图标 + 名称 + 包名 + 箭头），折叠列表与搜索结果共用。 */
    val appRow: @Composable (GameConfigSummary) -> Unit = { summary ->
        val iconPainter: Painter? = remember(summary.packageName) {
            val bmp = viewModel.getCachedIcon(summary.packageName) ?: return@remember null
            BitmapPainter(bmp.asImageBitmap())
        }
        val isSelected = summary.packageName == dualPaneSelected
        Card(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(modifier = Modifier.padding(4.dp)) {
                        if (iconPainter != null) {
                            Image(
                                painter = iconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Box(
                                Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                },
                headlineContent = {
                    Text(
                        text = if (summary.isInstalled) summary.appName else summary.packageName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = summary.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(),
                    onClick = { onGameClick(summary.packageName) },
                    onLongClick = { showDeleteConfirm = summary.packageName },
                    onLongClickLabel = "删除",
                ),
            )
        }
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                title = { Text("云控配置") },
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onClearClick = { searchText = "" },
                actions = {
                    // 排序
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
                            val config = sortConfig
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(index = 0, count = 2),
                            ) {
                                colorosMaterialSortEntries.onEachIndexed { index, (type, label) ->
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
                                            count = colorosMaterialSortEntries.size,
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
                    // 筛选：所有应用配置 / 只显示已安装应用配置
                    var showFilterMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "筛选",
                        )
                        DropdownMenuPopup(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                DropdownMenuItem(
                                    text = { Text("所有应用配置") },
                                    checked = !showInstalledOnly,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        viewModel.setShowInstalledOnly(false)
                                        showFilterMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                                )
                                DropdownMenuItem(
                                    text = { Text("只显示已安装应用配置") },
                                    checked = showInstalledOnly,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        viewModel.setShowInstalledOnly(true)
                                        showFilterMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                                )
                            }
                        }
                    }
                    // 溢出：刷新配置 / 清除应用增强服务数据
                    var showOverflow by remember { mutableStateOf(false) }
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                        )
                        DropdownMenuPopup(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                DropdownMenuItem(
                                    text = { Text("刷新配置") },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.refreshAll()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("清除应用增强服务数据") },
                                    onClick = {
                                        showOverflow = false
                                        showClearConfirm = true
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { _, _ ->
                    // 展开搜索即隐藏列表，仅保留输入区（对齐 KernelSU）。
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
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 0.dp,
                                bottom = 16.dp + bottomPadding,
                            ),
                        ) {
                            items(filtered, key = { it.packageName }) { summary ->
                                appRow(summary)
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                modifier = Modifier.padding(bottom = bottomInnerPadding),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Filled.Add,
                    "新建配置",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = refreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                viewModel.refreshFromPull()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = refreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            when {
                // spinner 仅在无数据可显示时出现（真正的首屏加载）。
                isLoading && filtered.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                filtered.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when {
                                    searchText.trim().isNotEmpty() -> "无匹配应用"
                                    !systemStatus.checked -> "正在检测..."
                                    !systemStatus.dbAvailable -> "无法访问数据库，请先授予 Root 权限"
                                    !hasDbData -> "数据库中暂无配置记录"
                                    else -> "无匹配应用"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (searchText.trim().isEmpty() && !hasDbData && systemStatus.dbAvailable) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "点击右下角 + 按钮新建配置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(filtered, key = { it.packageName }) { summary ->
                        appRow(summary)
                    }
                    // 底栏留白：由页面自己消费（对齐 KernelSU 的末尾 Spacer 模式）
                    item(key = "bottom-space") {
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        var newPkg by remember { mutableStateOf("") }
        var pkgError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建配置") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPkg,
                        onValueChange = { newPkg = it; pkgError = false },
                        label = { Text("应用包名") },
                        placeholder = { Text("例如 com.example.game") },
                        singleLine = true,
                        isError = pkgError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pkgError) {
                        Text(
                            "包名格式无效",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newPkg.trim()
                    if (isValidPackageName(trimmed)) {
                        showNewDialog = false; onNewConfig(trimmed)
                    } else {
                        pkgError = true
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } },
        )
    }
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            icon = {
                Icon(
                    if (resultSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                    null,
                    tint = if (resultSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(if (resultSuccess) "操作成功" else "操作失败") },
            text = { Text(resultMsg) },
            confirmButton = { TextButton(onClick = { showResultDialog = false }) { Text("确定") } },
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除应用增强服务数据") },
            text = { Text("确定要清除应用增强服务数据吗？\n清除后游戏配置将恢复默认。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearGameData { success, msg ->
                        if (success && dualPaneSelected != null) onDualPaneSelect("")
                        resultSuccess = success; resultMsg = msg; showResultDialog = true
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除配置") },
            text = { Text("确定要从数据库删除 ${showDeleteConfirm} 的配置吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val pkg = showDeleteConfirm ?: ""; showDeleteConfirm = null
                    viewModel.deleteConfig(pkg) { success, msg ->
                        if (success && pkg == dualPaneSelected) onDualPaneSelect("")
                        resultSuccess = success; resultMsg = msg; showResultDialog = true
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } },
        )
    }
}
