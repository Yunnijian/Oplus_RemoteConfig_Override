package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.ui.component.ListPopupDefaults
import com.remoteconfig.override.ui.component.ScrollToTopOnChange
import com.remoteconfig.override.ui.component.SearchStatus
import com.remoteconfig.override.ui.component.miuix.SearchBarFake
import com.remoteconfig.override.ui.component.miuix.SearchBox
import com.remoteconfig.override.ui.component.miuix.SearchPager
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.util.BlurredBar
import com.remoteconfig.override.ui.util.rememberBlurBackdrop
import com.remoteconfig.override.viewmodel.AppSortConfig
import com.remoteconfig.override.viewmodel.AppSortType
import com.remoteconfig.override.viewmodel.AppRow
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 排序菜单条目（文案与 KernelSU zh-rCN 的 sort_by_* 逐字一致）。 */
private val sortEntries = listOf(
    AppSortType.NAME to "应用名",
    AppSortType.PACKAGE_NAME to "包名",
    AppSortType.INSTALL_TIME to "安装时间",
    AppSortType.UPDATE_TIME to "更新时间",
)

/**
 * HyperOS 应用列表 — Miuix 实现。
 *
 * 框架结构直接复用 KernelSU 超级用户页（`SuperUserPagerMiuix`）：
 * `BlurredBar + TopAppBarAnim + TopAppBar(bottomContent = SearchBarFake)` 折叠搜索条
 * + `Scaffold(popupHost = SearchPager)` 全屏展开搜索 + `OverlayListPopup` 右上角排序
 * 菜单 + `PullToRefresh` 下拉刷新 + `ScrollToTopOnChange` 换序/刷新时安静回顶。
 * 搜索状态用本地 `remember`（对齐 KernelSU `SulogMiuix` 的用法——列表数据已在内存，
 * 不需要 ViewModel 侧的 debounce 通道）。
 *
 * [bottomInnerPadding]：底栏占位高度，由本页在 LazyColumn 末尾的 Spacer 消费
 * （Pager 不扣底部，供悬浮玻璃采样折射，对齐 ConfigListMiuix 约定）。
 */
@Composable
fun HyperOsAppListMiuix(
    viewModel: HyperOsViewModel,
    bottomInnerPadding: Dp = 0.dp,
) {
    val state by viewModel.listState.collectAsState()
    // LocalNavigator.current 是 @Composable getter，提前提升（对齐 ColorPaletteScreen 的做法）。
    val navigator = LocalNavigator.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    var searchStatus by remember {
        mutableStateOf(SearchStatus(label = "搜索应用名或包名"))
    }
    val onSearchStatusChange: (SearchStatus) -> Unit = { searchStatus = it }

    // 搜索过滤：中文名片段、包名片段或拼音片段命中即可（拼音在 VM 里预计算）。
    val filtered by remember {
        derivedStateOf {
            val query = searchStatus.searchText.trim()
            if (query.isEmpty()) state.apps
            else state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.pkg.contains(query, ignoreCase = true) ||
                    it.pinyin.contains(query.lowercase(), ignoreCase = true)
            }
        }
    }

    // SearchPager 的结果区按 resultStatus 分支：DEFAULT 渲染 defaultResult、SHOW 渲染 result。
    // 不驱动它就永远停在 DEFAULT、搜不到任何结果——随查询词同步（KernelSU 由 VM 的
    // searchResultStatusFor 承担，我们本地过滤，直接在这里推导）。
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
    // 展开搜索时是另一个 LazyColumn，滚动状态必须独立（主列表状态留给 ScrollToTopOnChange）
    val searchListState = rememberLazyListState()
    val latestRefreshing = rememberUpdatedState(state.refreshing)
    val latestApps = rememberUpdatedState(filtered)
    val refreshTick = remember { mutableIntStateOf(0) }
    ScrollToTopOnChange(
        lazyListState,
        state.sortConfig,
        refreshTick.intValue,
        isBusy = { latestRefreshing.value },
    ) { latestApps.value }

    val pullToRefreshState = rememberPullToRefreshState()
    val refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新…", "刷新成功")

    /** 应用行列表：折叠态（带下拉刷新与顶栏联动）与展开搜索结果两处共用。 */
    val appRows: @Composable (List<AppRow>, PaddingValues, Boolean, LazyListState) -> Unit =
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
            items(apps, key = { it.pkg }, contentType = { "app" }) { app ->
                AppRowCardMiuix(
                    app = app,
                    icon = viewModel.getCachedIcon(app.pkg),
                    onClick = { navigator.push(Route.HyperOsAppDetail(app.pkg)) },
                )
            }
            item(key = "bottom-space") {
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = "应用配置",
                        actions = {
                            AppSortPopupMiuix(
                                sortConfig = state.sortConfig,
                                onSortConfigChange = viewModel::updateSortConfig,
                            )
                            AppFilterPopupMiuix(
                                showInstalledOnly = state.showInstalledOnly,
                                onShowInstalledOnlyChange = viewModel::setShowInstalledOnly,
                            )
                        },
                        scrollBehavior = scrollBehavior,
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
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = onSearchStatusChange,
                defaultResult = {
                    // 展开搜索即隐藏列表，仅保留输入区（对齐 KernelSU）：
                    // 未输入查询词时不渲染任何结果，输入后才在 result 槽显示命中行。
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
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                when {
                    // spinner 仅在无数据可显示时出现（真正的首屏加载）；下拉刷新不拆列表。
                    state.loading && state.apps.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    state.unavailable ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.error ?: "Joyose 云控不可读",
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(text = "重试", onClick = { viewModel.refreshList() })
                            }
                        }

                    state.apps.isEmpty() ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无云控应用",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }

                    else -> PullToRefresh(
                        isRefreshing = state.refreshing,
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
}

/** 右上角排序菜单（对齐 KernelSU：OverlayListPopup + ListPopupColumn + DropdownImpl + 反转分隔线）。 */
@Composable
private fun AppSortPopupMiuix(
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
                    val sortGroupSize = sortEntries.size + 1
                    sortEntries.forEachIndexed { index, (type, label) ->
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
                        index = sortEntries.size,
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

/**
 * 右上角筛选弹层（KernelSU 第二个弹层的形制：MoreCircle + OverlayListPopup + 两行
 * DropdownImpl）。选项语义按本项目改为"所有应用配置 / 只显示已安装应用配置"——
 * 云控库里常有已卸载应用的残留配置，需要能只看当前在装的。
 */
@Composable
private fun AppFilterPopupMiuix(
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
                imageVector = MiuixIcons.MoreCircle,
                tint = colorScheme.onSurface,
                contentDescription = "筛选",
            )
        }
    }
}

/** 应用行：图标 + 应用名 + 组别名/功能数 + 右箭头。 */
@Composable
private fun AppRowCardMiuix(
    app: AppRow,
    icon: android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        insideMargin = PaddingValues(12.dp, 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = appSubtitle(app.group, app.features),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "查看详情",
                tint = colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/** 应用行次级文本：group 别名（如有）+ 功能数。 */
private fun appSubtitle(group: String?, features: Int): String =
    if (group.isNullOrEmpty()) "${features} 项功能" else "$group · ${features} 项功能"
