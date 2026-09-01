package com.remoteconfig.override.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.ui.component.DiscardChangesDialog
import com.remoteconfig.override.ui.component.material.SegmentedListItem

/**
 * HyperOS 应用功能页 v2 — Material 3 实现：顶部应用卡 + 功能入口列表。
 *
 * 入口显隐与 Miuix 皮肤同源（[visibleFeatureEntries]）；编辑即改即存，顶栏
 * 只保留高级编辑入口，无保存按钮、返回不做未保存守卫。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperOsAppDetailMaterial(
    view: JoyoseManager.AppView?,
    header: DetailHeaderInfo?,
    loading: Boolean,
    error: String?,
    editError: String?,
    document: kotlinx.serialization.json.JsonObject?,
    caps: JoyoseManager.DeviceCaps?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenFeature: (HyperOsFeatureEntry) -> Unit,
) {
    // 上滑大标题自动缩放（对齐 HyperOsAppListMaterial）。
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val entries = remember(document, caps) { visibleFeatureEntries(document, caps) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            LargeTopAppBar(
                title = { Text("应用功能", style = MaterialTheme.typography.headlineLarge) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenEditor) {
                        Icon(Icons.Filled.Edit, contentDescription = "高级编辑")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
                view == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "该应用无 per-app 云控配置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 编辑链路错误条（片段读取/保存失败）：入口仍可用，不整页劫持
                    editError?.let { errorText ->
                        item(key = "edit_error") { ErrorBannerMaterial(errorText) }
                    }
                    item(key = "header") { HeaderCardMaterial(view, header) }
                    item(key = "entries") {
                        // 分段卡：入口行合并进一张 SegmentedColumn（行自带分段 shape）
                        HyperOsSectionCard(
                            rows = entries.map { entry ->
                                @Composable {
                                    SegmentedListItem(
                                        onClick = { onOpenFeature(entry) },
                                        leadingContent = {
                                            Icon(
                                                imageVector = entry.icon(),
                                                contentDescription = entry.title,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        headlineContent = {
                                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        },
                                        supportingContent = { Text(entry.summary, style = MaterialTheme.typography.bodySmall) },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 头部卡：包名（粗体大字）+ 徽标行（group 别名 / common 成员状态，未出现时省略）
 * + conflicts 警告条（列出冲突路径）。
 */
@Composable
private fun HeaderCardMaterial(view: JoyoseManager.AppView, header: DetailHeaderInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                header?.icon?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = header?.label ?: view.packageName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = view.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 徽标行：group 别名（如 SGAME）+ common 成员状态 + 云控版本/冻结态
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                view.group?.takeIf { it.isNotBlank() }?.let {
                    BadgeMaterial(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (view.common.inGameList == true) {
                    BadgeMaterial("已纳入优化", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (view.common.inSupportApp == true) {
                    BadgeMaterial("在支持列表", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                header?.cloudVersion?.takeIf { it.isNotBlank() }?.let {
                    BadgeMaterial("云控版本 $it", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                header?.let {
                    if (it.frozen) {
                        BadgeMaterial("云控已冻结", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        BadgeMaterial("未冻结（会被云覆盖）", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (view.conflicts.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "检测到规则冲突",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        view.conflicts.forEach { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 小徽标（pill）：container/content 均取主题令牌，不硬编码颜色。 */
@Composable
private fun BadgeMaterial(text: String, container: Color, content: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
