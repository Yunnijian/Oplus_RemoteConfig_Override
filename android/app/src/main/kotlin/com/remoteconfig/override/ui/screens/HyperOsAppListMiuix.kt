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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * HyperOS 应用列表 — Miuix 实现。
 *
 * 列表模式对齐 ConfigListMiuix.kt：`Scaffold + TopAppBar(MiuixScrollBehavior) +
 * LazyColumn + Card`，带 `items(key = { it.pkg }, contentType = { "app" })`（性能规格 9.5）。
 *
 * 内容：顶部云控状态徽标（冻结状态）+ 共 N 个应用 + 应用行（包名粗体 / group 别名与
 * 功能数次级文本），点击行 push [Route.HyperOsAppDetail]（导航惯例对齐 SettingsMiuix
 * 内部取 [LocalNavigator] 的写法）。
 *
 * 状态：loading（无数据时整页 spinner）/ unavailable（Joyose 云控不可读 + 重试）/
 * 空态（暂无云控应用）均有处理。
 *
 * [bottomInnerPadding]：底栏占位高度。Pager 不加 bottom padding（内容需铺到底栏下方供
 * 悬浮玻璃采样折射），留白由 LazyColumn 末尾 `item { Spacer }` 消费（对齐 ConfigListMiuix）。
 */
@Composable
fun HyperOsAppListMiuix(
    viewModel: HyperOsViewModel,
    bottomInnerPadding: Dp = 0.dp,
) {
    val state by viewModel.listState.collectAsState()
    // LocalNavigator.current 是 @Composable getter，提前提升（对齐 ColorPaletteScreen 的做法），
    // 避免在 items 的点击 lambda（非组合上下文）内访问。
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = "应用配置",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                // spinner 仅在无数据可显示时出现（真正的首屏加载）；refreshList 重复进入
                // 不重刷（粘性首刷），整体换 spinner 会把列表拆掉重建。
                state.loading && state.apps.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.unavailable ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Joyose 云控不可读",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(text = "重试", onClick = { viewModel.refreshList() })
                        }
                    }
                state.apps.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "暂无云控应用",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 云控状态行：冻结徽标 + 应用计数
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FreezeStatusBadgeMiuix(frozen = state.frozen)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "共 ${state.apps.size} 个应用",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    items(state.apps, key = { it.pkg }, contentType = { "app" }) { app ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = null,
                                        indication = ripple(),
                                    ) {
                                        navigator.push(Route.HyperOsAppDetail(app.pkg))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = app.pkg,
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
                    // 底栏留白：由页面自己消费（对齐 ConfigListMiuix 末尾 Spacer 模式）
                    item {
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

/** 云控冻结状态徽标（Miuix 配色：胶囊底 secondaryContainer + 状态圆点）。 */
@Composable
private fun FreezeStatusBadgeMiuix(frozen: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (frozen) colorScheme.primary else colorScheme.onSurfaceVariantSummary),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (frozen) "云控已冻结" else "云控未冻结",
            fontSize = 12.sp,
            color = colorScheme.onSecondaryContainer,
        )
    }
}

/** 应用行次级文本：group 别名（如有）+ 功能数。 */
private fun appSubtitle(group: String?, features: Int): String =
    if (group.isNullOrEmpty()) "${features} 项功能" else "$group · ${features} 项功能"
