package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.ui.component.ListPopupDefaults
import com.remoteconfig.override.ui.component.ScrollToTopOnChange
import com.remoteconfig.override.ui.component.SearchStatus
import com.remoteconfig.override.ui.component.miuix.SearchBarFake
import com.remoteconfig.override.ui.component.miuix.SearchBox
import com.remoteconfig.override.ui.component.miuix.SearchPager
import com.remoteconfig.override.viewmodel.AppSortConfig
import com.remoteconfig.override.viewmodel.AppSortType
import com.remoteconfig.override.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

/** 排序菜单条目（文案与 KernelSU zh-rCN 的 sort_by_* 逐字一致）。 */
private val colorosSortEntries = listOf(
    AppSortType.NAME to "应用名",
    AppSortType.PACKAGE_NAME to "包名",
    AppSortType.INSTALL_TIME to "安装时间",
    AppSortType.UPDATE_TIME to "更新时间",
)

/**
 * 配置列表 — Miuix 实现（对齐 KernelSU 超级用户页框架）。
 *
 * 搜索/排序/筛选/下拉刷新全部复用与 HyperOS 页同一套移植自 KernelSU 的组件：
 * `SearchStatus + SearchBarFake + SearchPager + SearchBox`（展开即隐藏列表）、
 * `OverlayListPopup` 排序与筛选弹层、`PullToRefresh` 下拉、`ScrollToTopOnChange`
 * 换序/刷新安静回顶。保留本页原有的新建配置、长按删除、清除数据、双窗选中高亮。
 *
 * [bottomInnerPadding]：底栏占位高度，由列表末尾 Spacer 与 FAB 的 bottom padding 消费。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigListContentMiuix(
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

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    var showNewDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var resultMsg by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    var searchStatus by remember {
        mutableStateOf(SearchStatus(label = "搜索应用名或包名"))
    }
    val onSearchStatusChange: (SearchStatus) -> Unit = { searchStatus = it }

    // 搜索过滤：中文名片段、包名片段或拼音片段命中即可（拼音在 VM 里预计算）。
    val filtered by remember {
        derivedStateOf {
            val query = searchStatus.searchText.trim()
            if (query.isEmpty()) gameList
            else gameList.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true) ||
                    it.pinyin.contains(query.lowercase(), ignoreCase = true)
            }
        }
    }

    // SearchPager 的结果区按 resultStatus 分支：DEFAULT 渲染 defaultResult、SHOW 渲染 result。
    LaunchedEffect(searchStatus.searchText, filtered) {
        val target = if (searchStatus.searchText.trim().isEmpty()) {
            SearchStatus.ResultStatus.DEFAULT
        } else {
            SearchStatus.ResultStatus.SHOW
        }
        if (searchStatus.resultStatus != target) {
            onSearchStatusChange(searchStatus.copy(resultStatus = target))
        }
    }

    val lazyListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val latestRefreshing = rememberUpdatedState(refreshing)
    val latestApps = rememberUpdatedState(filtered)
    val refreshTick = remember { mutableIntStateOf(0) }
    ScrollToTopOnChange(
        lazyListState,
        sortConfig,
        refreshTick.intValue,
        isBusy = { latestRefreshing.value },
    ) { latestApps.value }

    val pullToRefreshState = rememberPullToRefreshState()
    val refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新…", "刷新成功")

    /** 应用行列表：折叠态（带下拉刷新与顶栏联动）与展开搜索结果两处共用。 */
    val appRows: @Composable (List<com.remoteconfig.override.model.GameConfigSummary>, PaddingValues, Boolean, LazyListState) -> Unit =
    { apps, padding, interactive, listState ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (interactive) {
                        Modifier
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier.overScrollVertical()
                    },
                ),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            items(apps, key = { it.packageName }, contentType = { "app" }) { summary ->
                val isSelected = summary.packageName == dualPaneSelected
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = if (isSelected) {
                        CardDefaults.defaultColors(color = colorScheme.secondaryContainer)
                    } else {
                        CardDefaults.defaultColors()
                    },
                ) {
                    BasicComponent(
                        modifier = Modifier.combinedClickable(
                            interactionSource = null,
                            indication = ripple(),
                            onClick = { onGameClick(summary.packageName) },
                            onLongClick = { showDeleteConfirm = summary.packageName },
                            onLongClickLabel = "删除",
                        ),
                        title = if (summary.isInstalled) summary.appName else summary.packageName,
                        summary = summary.packageName,
                        startAction = { AppIcon(summary.packageName, viewModel) },
                        endActions = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "编辑",
                                tint = colorScheme.onSurfaceVariantActions,
                            )
                        },
                    )
                }
            }
            // 底栏留白：由页面自己消费（对齐 KernelSU SuperUserMiuix.kt:481）
            item(key = "bottom-space") {
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }

    Scaffold(
        topBar = {
            searchStatus.TopAppBarAnim {
                TopAppBar(
                    title = "云控配置",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppSortPopupMiuixColor(
                            sortConfig = sortConfig,
                            onSortConfigChange = viewModel::updateSortConfig,
                        )
                        AppFilterPopupMiuixColor(
                            showInstalledOnly = showInstalledOnly,
                            onShowInstalledOnlyChange = viewModel::setShowInstalledOnly,
                        )
                        // 溢出菜单：刷新配置 / 清除应用增强服务数据
                        val showOverflow = remember { mutableStateOf(false) }
                        Box {
                            OverlayListPopup(
                                show = showOverflow.value,
                                popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showOverflow.value = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "刷新配置",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showOverflow.value = false
                                            viewModel.refreshAll()
                                        },
                                    )
                                    DropdownImpl(
                                        text = "清除应用增强服务数据",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showOverflow.value = false
                                            showClearConfirm = true
                                        },
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showOverflow.value = true },
                                holdDownState = showOverflow.value,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.MoreCircle,
                                    contentDescription = "更多操作",
                                    tint = colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    bottomContent = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    with(density) {
                                        val newOffsetY = coordinates.positionInWindow().y.toDp()
                                        if (searchStatus.offsetY != newOffsetY) {
                                            onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                        }
                                    }
                                }
                                .then(
                                    if (searchStatus.isCollapsed()) {
                                        Modifier.pointerInput(Unit) {
                                            detectTapGestures {
                                                onSearchStatusChange(
                                                    searchStatus.copy(
                                                        current = SearchStatus.Status.EXPANDING,
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            SearchBarFake(searchStatus.label, dynamicTopPadding)
                        }
                    },
                )
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = onSearchStatusChange,
                defaultResult = {
                    // 展开搜索即隐藏列表，仅保留输入区（对齐 KernelSU）。
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                if (filtered.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "无匹配应用",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    appRows(
                        filtered,
                        PaddingValues(top = 6.dp, bottom = maxOf(bottomInnerPadding, imeBottom)),
                        false,
                        searchListState,
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                // FAB 抬到底栏上方（对齐 KernelSU ModuleMiuix.kt:448）
                modifier = Modifier.padding(bottom = bottomInnerPadding),
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "新建配置",
                    tint = colorScheme.onPrimary,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(Modifier.fillMaxSize()) {
                when {
                    // spinner 仅在无数据可显示时出现（真正的首屏加载）。
                    isLoading && filtered.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    filtered.isEmpty() ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when {
                                        searchStatus.searchText.trim().isNotEmpty() -> "无匹配应用"
                                        !systemStatus.checked -> "正在检测..."
                                        !systemStatus.dbAvailable -> "无法访问数据库，请先授予 Root 权限"
                                        !hasDbData -> "数据库中暂无配置记录"
                                        else -> "无匹配应用"
                                    },
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                if (searchStatus.searchText.trim().isEmpty() && !hasDbData && systemStatus.dbAvailable) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "点击右下角 + 按钮新建配置",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }

                    else -> PullToRefresh(
                        isRefreshing = refreshing,
                        pullToRefreshState = pullToRefreshState,
                        onRefresh = {
                            viewModel.refreshFromPull()
                            refreshTick.intValue++
                        },
                        refreshTexts = refreshTexts,
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                        ),
                    ) {
                        appRows(
                            filtered,
                            PaddingValues(
                                top = innerPadding.calculateTopPadding() + 6.dp,
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                            ),
                            true,
                            lazyListState,
                        )
                    }
                }
            }
        }
    }

    // ── 弹窗组（Miuix 用 WindowDialog，文案与 Material 版逐字一致）──

    if (showNewDialog) {
        var newPkg by remember { mutableStateOf("") }
        var pkgError by remember { mutableStateOf(false) }
        WindowDialog(
            show = showNewDialog,
            title = "新建配置",
            onDismissRequest = { showNewDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = newPkg,
                    onValueChange = { newPkg = it; pkgError = false },
                    label = "应用包名",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pkgError) {
                    Text(
                        text = "包名格式无效",
                        fontSize = 12.sp,
                        color = colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showNewDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "创建",
                        onClick = {
                            val trimmed = newPkg.trim()
                            if (isValidPackageName(trimmed)) {
                                showNewDialog = false
                                onNewConfig(trimmed)
                            } else {
                                pkgError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    if (showResultDialog) {
        WindowDialog(
            show = showResultDialog,
            title = if (resultSuccess) "操作成功" else "操作失败",
            onDismissRequest = { showResultDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (resultSuccess) MiuixIcons.Ok else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (resultSuccess) colorScheme.primary else colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(resultMsg, fontSize = 14.sp)
                }
                TextButton(
                    text = "确定",
                    onClick = { showResultDialog = false },
                    modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    if (showClearConfirm) {
        WindowDialog(
            show = showClearConfirm,
            title = "清除应用增强服务数据",
            onDismissRequest = { showClearConfirm = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("确定要清除应用增强服务数据吗？\n清除后游戏配置将恢复默认。", fontSize = 14.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showClearConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            showClearConfirm = false
                            viewModel.clearGameData { success, msg ->
                                if (success && dualPaneSelected != null) onDualPaneSelect("")
                                resultSuccess = success; resultMsg = msg; showResultDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        WindowDialog(
            show = true,
            title = "删除配置",
            onDismissRequest = { showDeleteConfirm = null },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("确定要从数据库删除 ${showDeleteConfirm} 的配置吗？", fontSize = 14.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showDeleteConfirm = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除",
                        onClick = {
                            val pkg = showDeleteConfirm ?: ""; showDeleteConfirm = null
                            viewModel.deleteConfig(pkg) { success, msg ->
                                if (success && pkg == dualPaneSelected) onDualPaneSelect("")
                                resultSuccess = success; resultMsg = msg; showResultDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(textColor = colorScheme.error),
                    )
                }
            }
        }
    }
}

/** 排序弹层（KernelSU 形制，选项文案对齐 zh-rCN）。 */
@Composable
private fun AppSortPopupMiuixColor(
    sortConfig: AppSortConfig,
    onSortConfigChange: (AppSortConfig) -> Unit,
) {
    Box {
        val showSortPopup = remember { mutableStateOf(false) }
        OverlayListPopup(
            show = showSortPopup.value,
            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { showSortPopup.value = false },
            content = {
                ListPopupColumn {
                    val sortGroupSize = colorosSortEntries.size + 1
                    colorosSortEntries.forEachIndexed { index, (type, label) ->
                        DropdownImpl(
                            text = label,
                            optionSize = sortGroupSize,
                            isSelected = sortConfig.sortType == type,
                            index = index,
                            onSelectedIndexChange = {
                                onSortConfigChange(sortConfig.withType(type))
                                showSortPopup.value = false
                            },
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        thickness = 1.5.dp,
                    )
                    DropdownImpl(
                        text = "倒序",
                        optionSize = sortGroupSize,
                        isSelected = sortConfig.reversed,
                        index = colorosSortEntries.size,
                        onSelectedIndexChange = {
                            onSortConfigChange(sortConfig.toggleReversed())
                            showSortPopup.value = false
                        },
                    )
                }
            },
        )
        IconButton(
            onClick = { showSortPopup.value = true },
            holdDownState = showSortPopup.value,
        ) {
            Icon(
                imageVector = MiuixIcons.Sort,
                tint = colorScheme.onSurface,
                contentDescription = "排序",
            )
        }
    }
}

/** 筛选弹层：所有应用配置 / 只显示已安装应用配置。 */
@Composable
private fun AppFilterPopupMiuixColor(
    showInstalledOnly: Boolean,
    onShowInstalledOnlyChange: (Boolean) -> Unit,
) {
    Box {
        val showFilterPopup = remember { mutableStateOf(false) }
        OverlayListPopup(
            show = showFilterPopup.value,
            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { showFilterPopup.value = false },
            content = {
                ListPopupColumn {
                    DropdownImpl(
                        text = "所有应用配置",
                        optionSize = 2,
                        isSelected = !showInstalledOnly,
                        index = 0,
                        onSelectedIndexChange = {
                            onShowInstalledOnlyChange(false)
                            showFilterPopup.value = false
                        },
                    )
                    DropdownImpl(
                        text = "只显示已安装应用配置",
                        optionSize = 2,
                        isSelected = showInstalledOnly,
                        index = 1,
                        onSelectedIndexChange = {
                            onShowInstalledOnlyChange(true)
                            showFilterPopup.value = false
                        },
                    )
                }
            },
        )
        IconButton(
            onClick = { showFilterPopup.value = true },
            holdDownState = showFilterPopup.value,
        ) {
            Icon(
                imageVector = MiuixIcons.More,
                tint = colorScheme.onSurface,
                contentDescription = "筛选",
            )
        }
    }
}

/**
 * 应用图标：读 [MainViewModel.getCachedIcon] 缓存（Material 版同款逻辑），
 * 无缓存时显示占位图标。
 */
@Composable
private fun AppIcon(pkg: String, viewModel: MainViewModel) {
    val iconBitmap = remember(pkg) { viewModel.getCachedIcon(pkg)?.asImageBitmap() }
    if (iconBitmap != null) {
        Icon(
            bitmap = iconBitmap,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
