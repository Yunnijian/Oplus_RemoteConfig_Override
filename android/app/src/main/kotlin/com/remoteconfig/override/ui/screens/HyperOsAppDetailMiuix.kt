package com.remoteconfig.override.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.ui.component.DiscardChangesDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * HyperOS 应用功能页 v2 — Miuix 实现：顶部应用卡 + 功能入口列表。
 *
 * 入口按片段实际键 + 设备能力动态显隐（[visibleFeatureEntries]）；点入进入
 * 该功能的专用子屏（子屏与作用域编辑器共享同一份草稿，本页顶栏保留保存与
 * 高级编辑入口）。未保存修改时返回（顶栏返回键 + 系统返回）→ 确认弹窗。
 */
@Composable
fun HyperOsAppDetailMiuix(
    view: JoyoseManager.AppView?,
    header: DetailHeaderInfo?,
    loading: Boolean,
    error: String?,
    editError: String?,
    document: kotlinx.serialization.json.JsonObject?,
    dirty: Boolean,
    saving: Boolean,
    caps: JoyoseManager.DeviceCaps?,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onRevert: () -> Unit,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenFeature: (HyperOsFeatureEntry) -> Unit,
) {
    // 上滑大标题自动缩放（对齐 HyperOsAppListMiuix）：TopAppBar 拿 scrollBehavior。
    val scrollBehavior = MiuixScrollBehavior()
    var showDiscard by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { showDiscard = true }
    if (showDiscard) {
        // 确认丢弃：先重置脏草稿再退栈（否则幂等守卫命中，残留修改复活）
        DiscardChangesDialog(onConfirm = { showDiscard = false; onRevert(); onBack() }, onDismiss = { showDiscard = false })
    }
    val entries = remember(document, caps) { visibleFeatureEntries(document, caps) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = "应用功能",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { if (dirty) showDiscard = true else onBack() }) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = colorScheme.onSurface)
                    }
                },
                actions = {
                    // 保存：任一功能子屏产生的未保存草稿（joyose-scoped-write 链路）
                    TextButton(
                        text = if (saving) "保存中…" else "保存",
                        onClick = onSave,
                        enabled = dirty && !saving,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    IconButton(onClick = onOpenEditor) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "高级编辑", tint = colorScheme.onSurface)
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
                        Text(text = error, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(12.dp))
                        TextButton(text = "重试", onClick = onRetry, colors = ButtonDefaults.textButtonColorsPrimary())
                    }
                }
                view == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "该应用无 per-app 云控配置", fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 编辑链路错误条（片段读取/保存失败）：入口仍可用，不整页劫持
                    editError?.let { errorText ->
                        item(key = "edit_error") { ErrorBannerMiuix(errorText) }
                    }
                    item(key = "header") { HeaderCardMiuix(view, header) }
                    item(key = "entries") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                entries.forEachIndexed { index, entry ->
                                    BasicComponent(
                                        modifier = Modifier.combinedClickable(
                                            interactionSource = null,
                                            indication = ripple(),
                                            onClick = { onOpenFeature(entry) },
                                        ),
                                        title = entry.title,
                                        summary = entry.summary,
                                        startAction = {
                                            Icon(
                                                imageVector = entry.icon(),
                                                contentDescription = entry.title,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.padding(end = 6.dp),
                                            )
                                        },
                                        endActions = {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowRight,
                                                contentDescription = "打开",
                                                tint = colorScheme.onSurfaceVariantActions,
                                            )
                                        },
                                    )
                                    if (index != entries.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp, end = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 入口图标（material-icons-extended；主题色渲染）。 */
internal fun HyperOsFeatureEntry.icon(): ImageVector = when (this) {
    HyperOsFeatureEntry.THERMAL_FPS -> Icons.Rounded.DeviceThermostat
    HyperOsFeatureEntry.PERF_SCHEDULE -> Icons.Rounded.Speed
    HyperOsFeatureEntry.FISR -> Icons.Rounded.MovieFilter
    HyperOsFeatureEntry.DYN_RES -> Icons.Rounded.AspectRatio
    HyperOsFeatureEntry.GPU_TUNER -> Icons.Rounded.Tune
    HyperOsFeatureEntry.MIGT -> Icons.Rounded.Bolt
}

/**
 * 头部卡：包名（粗体大字）+ 徽标行（group 别名 / common 成员状态，未出现时省略）
 * + conflicts 警告条（列出冲突路径）。
 */
@Composable
private fun HeaderCardMiuix(view: JoyoseManager.AppView, header: DetailHeaderInfo?) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = view.packageName,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            // 徽标行：group 别名（如 SGAME）+ common 成员状态 + 云控版本/冻结态
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
                header?.cloudVersion?.takeIf { it.isNotBlank() }?.let {
                    BadgeMiuix("云控版本 $it", colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
                }
                header?.let {
                    if (it.frozen) {
                        BadgeMiuix("云控已冻结", colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
                    } else {
                        BadgeMiuix("未冻结（会被云覆盖）", colorScheme.errorContainer, colorScheme.onErrorContainer)
                    }
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
