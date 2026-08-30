package com.remoteconfig.override.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.data.JoyoseManager
import kotlinx.serialization.json.JsonElement
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * HyperOS 应用功能页 — Miuix 实现。
 *
 * 结构对齐 ConfigListMiuix：`Scaffold + TopAppBar + LazyColumn + Card`。
 * 头部卡（包名粗体大字 / group 别名徽标 / common 成员状态徽标 / conflicts 警告条）
 * → 逐个 FeatureHit 功能卡：label 标题 + category（mono 小字）+ source 徽标 +
 * gate 主开关状态 pill（右侧）+ params 键值行 + overrides 尾注；
 * gate.enabled == false 时整卡降透明度。features 为空的包显示空态。
 *
 * 注意（miuix 0.9.3 实际签名）：TopAppBar 的 title 为 String；返回键用
 * MiuixIcons.Back（对齐 ConfigListMiuix / ConfigEditor 惯例）；
 * 自定义内容（非 BasicComponent）在 Card 内自带 padding（对齐 HomeMiuix RootStatusCard）。
 *
 * 「高级编辑」入口暂以 Toast 提示（P2 后续接编辑器）。
 */
@Composable
fun HyperOsAppDetailMiuix(
    view: JoyoseManager.AppView?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = "应用功能",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Toast.makeText(context, "高级 JSON 编辑将在后续版本提供", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "高级编辑",
                            tint = colorScheme.onSurface,
                        )
                    }
                },
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
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            text = "重试",
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
                view == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "该应用无 per-app 云控配置",
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "header") { HeaderCardMiuix(view) }
                    if (view.features.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "该应用无 per-app 云控配置",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            )
                        }
                    } else {
                        // key 带 index 前缀：防个别数据下 path 重复导致 LazyColumn 崩溃
                        itemsIndexed(view.features, key = { index, feature -> "$index:${feature.path}" }) { _, feature ->
                            FeatureCardMiuix(feature)
                        }
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
private fun HeaderCardMiuix(view: JoyoseManager.AppView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = view.packageName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            // 徽标行：group 别名（如 SGAME）+ common 成员状态（字段未出现/false 时省略）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                view.group?.takeIf { it.isNotBlank() }?.let {
                    BadgeMiuix(it, colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
                }
                if (view.common.inGameList == true) {
                    BadgeMiuix("已纳入优化", colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
                }
                if (view.common.inSupportApp == true) {
                    BadgeMiuix("在支持列表", colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
                }
            }
            if (view.conflicts.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "检测到规则冲突",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onErrorContainer,
                        )
                    }
                    view.conflicts.forEach { path ->
                        Text(
                            text = path,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 功能卡：标题行（label + category/source / gate pill）→ params 键值行 → overrides 尾注。
 * gate.enabled == false 时整卡降透明度（内容仍可读，仅提示主开关未生效）。
 */
@Composable
private fun FeatureCardMiuix(feature: JoyoseManager.FeatureHit) {
    val dimmed = feature.gate?.enabled == false
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (dimmed) 0.45f else 1f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 标题行：label + category/source（左）；gate 主开关状态 pill（右）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = feature.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = feature.category,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        BadgeMiuix(
                            text = featureSourceBadge(feature),
                            container = colorScheme.surfaceVariant,
                            content = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                feature.gate?.let { gate ->
                    if (gate.enabled) {
                        BadgeMiuix("已启用", colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
                    } else {
                        BadgeMiuix("主开关已关 · ${gate.key}", colorScheme.errorContainer, colorScheme.onErrorContainer)
                    }
                }
            }
            // params 列表：name(mono) + value（原始量直接显示，对象/数组可折叠 mono JSON）
            feature.params.forEach { param ->
                ParamRowMiuix(param.name, param.value)
            }
            // overrides 尾注：每条一行小字
            if (feature.overrides.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    feature.overrides.forEach { path ->
                        Text(
                            text = path,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * params 键值行：name（mono 小字）+ value 渲染。
 * 原始量（字符串/数字/布尔）直接显示，长字符串 maxLines 截断、点击展开；
 * 对象/数组显示为可折叠 mono JSON（折叠态显示摘要：键数/项数）。
 */
@Composable
private fun ParamRowMiuix(name: String, value: JsonElement) {
    val summary = jsonCollapseSummary(value)
    var expanded by remember(name) { mutableStateOf(false) }
    if (summary == null) {
        Column(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = jsonScalarText(value).orEmpty(),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "收起" else summary,
                    fontSize = 11.sp,
                    color = colorScheme.primary,
                )
            }
            if (expanded) {
                Text(
                    text = jsonPrettyText(value),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

/** 小徽标（pill）：container/content 均取主题令牌，不硬编码颜色。 */
@Composable
private fun BadgeMiuix(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            maxLines = 1,
            color = content,
        )
    }
}
