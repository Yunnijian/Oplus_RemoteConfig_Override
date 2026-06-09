package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.model.GameConfigSummary
import com.remoteconfig.override.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigListContent(
    viewModel: MainViewModel,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
    onBack: () -> Unit = {},
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val gameList by viewModel.gameList.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasDbData by viewModel.hasDbData.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showNewDialog by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    var showResultDialog by remember { mutableStateOf(false) }

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
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { isSearching = false })
                        )
                    } else {
                        Text("云控配置")
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
                                DropdownMenuItem(text = { Text("重启应用增强服务") }, onClick = { showOverflow = false; showRestartConfirm = true })
                                DropdownMenuItem(text = { Text("清除应用增强服务数据") }, onClick = { showOverflow = false; showClearConfirm = true })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Filled.Add, "新建配置", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    ) { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (filteredGames.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (!hasDbData) "数据库中暂无配置记录" else "未找到匹配的应用",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!hasDbData && systemStatus.isRooted) {
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
                        Card(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                                    onClick = { onGameClick(summary.packageName) })
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        var newPkg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建配置") },
            text = { OutlinedTextField(value = newPkg, onValueChange = { newPkg = it }, label = { Text("应用包名") }, placeholder = { Text("例如 com.example.game") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (newPkg.isNotBlank()) { showNewDialog = false; onNewConfig(newPkg.trim()); searchQuery = newPkg.trim() } }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } }
        )
    }
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            icon = { Icon(if (resultMsg.contains("成功") || resultMsg.contains("已")) Icons.Default.CheckCircle else Icons.Default.Info, null) },
            title = { Text(if (resultMsg.contains("成功") || resultMsg.contains("已")) "操作成功" else "操作失败") },
            text = { Text(resultMsg) },
            confirmButton = { TextButton(onClick = { showResultDialog = false }) { Text("确定") } }
        )
    }
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("重启应用增强服务") },
            text = { Text("确定要重启应用增强服务吗？") },
            confirmButton = { TextButton(onClick = { showRestartConfirm = false; viewModel.restartGameService(); resultMsg = "应用增强服务已重启"; showResultDialog = true }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showRestartConfirm = false }) { Text("取消") } }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除应用增强服务数据") },
            text = { Text("确定要清除应用增强服务数据吗？\n清除后游戏配置将恢复默认。") },
            confirmButton = { TextButton(onClick = { showClearConfirm = false; viewModel.clearGameData(); resultMsg = "应用增强服务数据已清除"; showResultDialog = true }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}
