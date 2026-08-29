package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.model.GameConfigSummary
import com.remoteconfig.override.viewmodel.MainViewModel

/**
 * 配置列表 — Material 实现。
 *
 * 由原 GameListScreen.kt 的 ConfigListContent 迁入，仅改名并去掉 MainScreen 不再使用的
 * `onBack`/`isActive`/`modifier` 参数；`onGameClick`/`onNewConfig` 只回调不内部导航
 * （导航由 MainScreen 的 ConfigListPage 回调完成，见 Step 4）。
 *
 * [bottomInnerPadding]：底栏占位高度。Pager 不加 bottom padding（内容需铺到底栏下方供
 * 悬浮玻璃采样折射），留白由 LazyColumn 末尾 `item { Spacer }` 与 FAB 的 bottom padding 消费。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val hasDbData by viewModel.hasDbData.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showNewDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    // 显式成败布尔：不再靠消息文案嗅探（含“已”的失败文案会被误判为成功）。
    var resultSuccess by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    var isSearching by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
            LargeTopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                            placeholder = { Text("搜索应用包名...") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Close, "清除") }
                                }
                            },
                            singleLine = true, maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                // 与取消路径对齐：隐藏搜索框的同时清空过滤词，
                                // 否则列表继续被隐藏条件过滤而用户无从得知。
                                isSearching = false; searchQuery = ""
                            })
                        )
                    } else {
                        Text("云控配置", style = MaterialTheme.typography.headlineLarge)
                    }
                },
                navigationIcon = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) { Icon(Icons.Default.ArrowBack, "取消搜索") }
                    }
                },
                actions = {
                    if (isSearching) {
                        // no extra actions while searching
                    } else {
                        IconButton(onClick = { isSearching = true }) { Icon(Icons.Filled.Search, "搜索") }
                        var showOverflow by remember { mutableStateOf(false) }
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, "更多操作")
                            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                DropdownMenuItem(text = { Text("刷新配置") }, onClick = { showOverflow = false; viewModel.refreshAll() })
                                DropdownMenuItem(text = { Text("清除应用增强服务数据") }, onClick = { showOverflow = false; showClearConfirm = true })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                // FAB 抬到底栏上方（Pager 已不加 bottom padding）
                modifier = Modifier.padding(bottom = bottomInnerPadding),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Filled.Add, "新建配置", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // spinner 仅在无数据可显示时出现（真正的首屏加载）；已有数据时
            // refreshAll 是后台刷新，整体换 spinner 会让切 tab 时列表“闪一下”。
            if (isLoading && filteredGames.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (filteredGames.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Bug 7: 先判搜索词——有搜索词时命中为空显示"未找到匹配的应用"；
                        // 数据库不可访问（未授 Root/检测失败）与“库为空”分开提示，
                        // 避免把“打不开库”误报成“暂无记录”误导新建。
                        Text(
                            when {
                                qt.isNotEmpty() -> "未找到匹配的应用"
                                !systemStatus.checked -> "正在检测..."
                                !systemStatus.dbAvailable -> "无法访问数据库，请先授予 Root 权限"
                                !hasDbData -> "数据库中暂无配置记录"
                                else -> "未找到匹配的应用"
                            },
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (qt.isEmpty() && !hasDbData && systemStatus.dbAvailable) {
                            Spacer(Modifier.height(8.dp))
                            Text("点击右下角 + 按钮新建配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Text("共 ${filteredGames.size} 个应用", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(filteredGames, key = { it.packageName }) { summary ->
                        val iconPainter: Painter? = remember(summary.packageName) {
                            val bmp = viewModel.getCachedIcon(summary.packageName) ?: return@remember null
                            BitmapPainter(bmp.asImageBitmap())
                        }
                        val isSelected = summary.packageName == dualPaneSelected
                        Card(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(
                                // 双窗选中行高亮：secondaryContainer（区别于普通行 surfaceVariant 半透明）
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = {
                                    Box(modifier = Modifier.padding(4.dp)) {
                                        if (iconPainter != null) {
                                            androidx.compose.foundation.Image(
                                                painter = iconPainter, contentDescription = null,
                                                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = if (summary.isInstalled) summary.appName else summary.packageName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(text = summary.packageName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    Icon(Icons.Default.KeyboardArrowRight, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                modifier = Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = androidx.compose.material3.ripple(),
                                    onClick = { onGameClick(summary.packageName) },
                                    onLongClick = { showDeleteConfirm = summary.packageName },
                                    // 读屏无障碍：让 TalkBack 用户知道长按是删除操作。
                                    onLongClickLabel = "删除",
                                )
                            )
                        }
                    }
                    // 底栏留白：由页面自己消费（对齐 KernelSU 的末尾 Spacer 模式）
                    item { Spacer(Modifier.height(bottomInnerPadding)) }
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
                    OutlinedTextField(value = newPkg,
                        onValueChange = { newPkg = it; pkgError = false },
                        label = { Text("应用包名") },
                        placeholder = { Text("例如 com.example.game") },
                        singleLine = true,
                        isError = pkgError,
                        modifier = Modifier.fillMaxWidth())
                    // Bug 2: 包名非法时对话框内红字提示，不关闭对话框、不创建
                    if (pkgError) {
                        Text("包名格式无效", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val trimmed = newPkg.trim()
                if (isValidPackageName(trimmed)) {
                    showNewDialog = false; onNewConfig(trimmed); searchQuery = trimmed
                } else {
                    pkgError = true
                }
            }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } }
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
            confirmButton = { TextButton(onClick = { showResultDialog = false }) { Text("确定") } }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除应用增强服务数据") },
            text = { Text("确定要清除应用增强服务数据吗？\n清除后游戏配置将恢复默认。") },
            confirmButton = { TextButton(onClick = {
                showClearConfirm = false
                viewModel.clearGameData { success, msg ->
                    // Bug 4：清除成功后右窗格指向的包已被清 → 清空选中
                    if (success && dualPaneSelected != null) onDualPaneSelect("")
                    resultSuccess = success; resultMsg = msg; showResultDialog = true
                }
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除配置") },
            text = { Text("确定要从数据库删除 ${showDeleteConfirm} 的配置吗？") },
            confirmButton = { TextButton(onClick = {
                val pkg = showDeleteConfirm ?: ""; showDeleteConfirm = null
                viewModel.deleteConfig(pkg) { success, msg ->
                    // Bug 4：删除成功且右窗格正显示该包 → 清空选中，避免残留已删 JSON
                    if (success && pkg == dualPaneSelected) onDualPaneSelect("")
                    resultSuccess = success; resultMsg = msg; showResultDialog = true
                }
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }
}
