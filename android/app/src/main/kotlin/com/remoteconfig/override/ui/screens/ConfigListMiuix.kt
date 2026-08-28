package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 配置列表 — Miuix 实现。
 *
 * 列表模式对齐 KernelSU SuperUserMiuix.kt：`Scaffold + TopAppBar(MiuixScrollBehavior) +
 * LazyColumn + Card + BasicComponent`，并带性能规格 9.5 的
 * `items(key = { it.packageName }, contentType = { "app" })`。
 *
 * 行为与文案逐字对齐 Material 版：搜索过滤（appName/packageName contains）、
 * 空态（"数据库中暂无配置记录"/"未找到匹配的应用"）、顶部"共 N 个应用"、
 * 新建对话框、长按删除、清除数据确认、结果弹窗。
 *
 * 注意（miuix 0.9.3 实际签名）：
 * - TopAppBar 的 `title` 为 String（无 composable 槽），搜索框放在 `bottomContent` 槽位
 *   （对齐 KernelSU SearchBarFake 的放法），搜索时 navigationIcon 显示返回箭头取消搜索
 * - BasicComponent 无 onLongClick 槽，长按删除用 `Modifier.combinedClickable` 包裹
 *   （不传 BasicComponent 的 onClick，避免与 combinedClickable 双重触发；indication 用 null）
 * - 对话框全部用 WindowDialog + TextButton 双按钮（对齐 KernelSU DialogMiuix.kt 用法）
 * - 溢出菜单（刷新配置 / 清除数据）用 OverlayListPopup + ListPopupColumn + DropdownImpl
 *
 * [bottomInnerPadding]：底栏占位高度。Pager 不加 bottom padding（内容需铺到底栏下方供
 * 悬浮玻璃采样折射），留白由 LazyColumn 末尾 `item { Spacer }`（对齐 KernelSU
 * SuperUserMiuix.kt:481）与 FAB 的 bottom padding（对齐 ModuleMiuix.kt:448）消费。
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
    val hasDbData by viewModel.hasDbData.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var resultMsg by remember { mutableStateOf("") }
    var showResultDialog by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()

    val qt = searchQuery.trim().lowercase()
    val filteredGames by remember(gameList, qt) {
        derivedStateOf {
            if (qt.isEmpty()) gameList
            else gameList.filter { it.appName.lowercase().contains(qt) || it.packageName.lowercase().contains(qt) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = "云控配置",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "取消搜索",
                                tint = colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = MiuixIcons.Basic.Search,
                                contentDescription = "搜索",
                                tint = colorScheme.onSurface,
                            )
                        }
                        // 溢出菜单：刷新配置 / 清除应用增强服务数据（对齐 KernelSU ListPopup 用法）
                        val showOverflow = remember { mutableStateOf(false) }
                        Box {
                            OverlayListPopup(
                                show = showOverflow.value,
                                popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(),
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showOverflow.value = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "刷新配置",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = { showOverflow.value = false; viewModel.refreshAll() },
                                    )
                                    DropdownImpl(
                                        text = "清除应用增强服务数据",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = { showOverflow.value = false; showClearConfirm = true },
                                    )
                                }
                            }
                            IconButton(onClick = { showOverflow.value = true }) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多操作",
                                    tint = colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                bottomContent = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "搜索应用包名",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = MiuixIcons.Basic.Close,
                                        contentDescription = "清除",
                                        modifier = Modifier.clickable(interactionSource = null, indication = null) { searchQuery = "" },
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { isSearching = false }),
                        )
                    }
                },
            )
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
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filteredGames.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Bug 7: 先判搜索词——有搜索词时命中为空显示"未找到匹配的应用"，
                        // DB 无数据才显示"数据库中暂无配置记录"
                        Text(
                            text = when {
                                qt.isNotEmpty() -> "未找到匹配的应用"
                                !hasDbData -> "数据库中暂无配置记录"
                                else -> "未找到匹配的应用"
                            },
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        if (qt.isEmpty() && !hasDbData && systemStatus.isRooted) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "点击右下角 + 按钮新建配置",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "共 ${filteredGames.size} 个应用",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    items(filteredGames, key = { it.packageName }, contentType = { "app" }) { summary ->
                        val isSelected = summary.packageName == dualPaneSelected
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            // 双窗选中行高亮（容器色 = secondaryContainer，对齐 Material 侧高亮语义）
                            colors = if (isSelected) {
                                CardDefaults.defaultColors(color = colorScheme.secondaryContainer)
                            } else {
                                CardDefaults.defaultColors()
                            },
                        ) {
                            BasicComponent(
                                // BasicComponent 无 onLongClick 槽：用 combinedClickable 包裹实现长按删除
                                // （不传 onClick 避免与 combinedClickable 双重触发）
                                modifier = Modifier.combinedClickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = { onGameClick(summary.packageName) },
                                    onLongClick = { showDeleteConfirm = summary.packageName },
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
                    item {
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }

    // ── 弹窗组（Miuix 用 WindowDialog，文案与 Material 版逐字一致）──

    // 新建配置
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
                // Bug 2: 包名非法时对话框内红字提示，不关闭对话框、不创建
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
                                searchQuery = trimmed
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

    // 结果弹窗
    if (showResultDialog) {
        val success = resultMsg.contains("成功") || resultMsg.contains("已")
        WindowDialog(
            show = showResultDialog,
            title = if (success) "操作成功" else "操作失败",
            onDismissRequest = { showResultDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (success) MiuixIcons.Ok else Icons.Default.Info,
                        contentDescription = null,
                        tint = colorScheme.primary,
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

    // 清除应用增强服务数据确认
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
                                // Bug 4：清除成功后右窗格指向的包已被清 → 清空选中
                                if (success && dualPaneSelected != null) onDualPaneSelect("")
                                resultMsg = msg; showResultDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    // 长按删除确认
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
                                // Bug 4：删除成功且右窗格正显示该包 → 清空选中，避免残留已删 JSON
                                if (success && pkg == dualPaneSelected) onDualPaneSelect("")
                                resultMsg = msg; showResultDialog = true
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
            // miuix Icon 默认 tint = LocalContentColor.current（浅色 = 深色 onBackground），
            // 会把彩色 App 图标整体染黑。传 Unspecified 取消着色（对齐 Material 版 Image 无 tint）。
            tint = Color.Unspecified,
            modifier = Modifier.size(40.dp),
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(colorScheme.secondaryContainer),
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
