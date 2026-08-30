package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.viewmodel.HyperOsViewModel

/**
 * HyperOS 应用列表 — Material 实现。
 *
 * 列表模式对齐 ConfigListMaterial.kt：`Scaffold(surfaceContainer) + LargeTopAppBar
 * (exitUntilCollapsedScrollBehavior) + LazyColumn + Card + ListItem`。
 *
 * 行为与文案对齐 Miuix 版：顶部云控状态徽标（冻结状态）+ 共 N 个应用 + 应用行
 * （包名粗体 / group 别名与功能数次级文本），点击行 push [Route.HyperOsAppDetail]
 * （导航惯例对齐 SettingsMaterial 内部取 [LocalNavigator] 的写法）。
 *
 * 状态：loading（无数据时整页 spinner）/ unavailable（Joyose 云控不可读 + 重试）/
 * 空态（暂无云控应用）均有处理。
 *
 * [bottomInnerPadding]：底栏占位高度。留白由 LazyColumn 末尾 `item { Spacer }` 消费
 * （对齐 ConfigListMaterial 的末尾 Spacer 模式）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperOsAppListMaterial(
    viewModel: HyperOsViewModel,
    bottomInnerPadding: Dp = 0.dp,
) {
    val state by viewModel.listState.collectAsState()
    // LocalNavigator.current 是 @Composable getter，提前提升（避免在 items 的点击 lambda 内访问）。
    val navigator = LocalNavigator.current
    val lazyListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        // 背景与配置列表页对齐（与 MainScreen 的 MaterialScaffold 一致）
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            LargeTopAppBar(
                title = { Text("应用配置", style = MaterialTheme.typography.headlineLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                                "Joyose 云控不可读",
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
                    state = lazyListState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 云控状态行：冻结徽标 + 应用计数
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FreezeStatusBadgeMaterial(frozen = state.frozen)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "共 ${state.apps.size} 个应用",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.apps, key = { it.pkg }) { app ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = {
                                    // 应用图标：ColorOS 路径预加载缓存（IO 线程解码），组合期零查询
                                    val icon = viewModel.getCachedIcon(app.pkg)
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(MaterialTheme.shapes.small),
                                        )
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = state.labels[app.pkg] ?: app.pkg,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = appSubtitle(app.group, app.features),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = "查看详情",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    navigator.push(Route.HyperOsAppDetail(app.pkg))
                                },
                            )
                        }
                    }
                    // 底栏留白：由页面自己消费（对齐 ConfigListMaterial 的末尾 Spacer 模式）
                    item { Spacer(Modifier.height(bottomInnerPadding)) }
                }
            }
        }
    }
}

/** 云控冻结状态徽标（Material 配色：胶囊底 secondaryContainer + 状态圆点）。 */
@Composable
private fun FreezeStatusBadgeMaterial(frozen: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (frozen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (frozen) "云控已冻结" else "云控未冻结",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 应用行次级文本：group 别名（如有）+ 功能数。 */
private fun appSubtitle(group: String?, features: Int): String =
    if (group.isNullOrEmpty()) "${features} 项功能" else "$group · ${features} 项功能"
