package com.remoteconfig.override.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.remoteconfig.override.ui.component.DiscardChangesDialog
import com.remoteconfig.override.viewmodel.HyperOsViewModel

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
 * 顶栏「高级编辑」跳转作用域编辑器（Route.HyperOsScopedEditor，仅本 App 片段）。
 */
@Composable
fun HyperOsAppDetailMiuix(
    view: JoyoseManager.AppView?,
    loading: Boolean,
    error: String?,
    edit: HyperOsDetailEdit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    // 上滑大标题自动缩放（对齐 HyperOsAppListMiuix / KernelSU AppProfileMiuix）：
    // TopAppBar 拿 scrollBehavior，列表挂 nestedScrollConnection。
    val scrollBehavior = MiuixScrollBehavior()
    // 未保存修改时返回（TopBar 返回键 + 系统返回手势同路）→ 确认弹窗
    //（对齐编辑器页 DiscardChangesDialog + BackHandler 交互）
    var showDiscard by remember { mutableStateOf(false) }
    // 参数编辑弹窗目标（null = 关闭）
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    BackHandler(enabled = edit.dirty) { showDiscard = true }
    if (showDiscard) {
        // 确认丢弃：先重置脏草稿再退栈（否则 loadScopedEditor 幂等守卫命中，残留修改复活）
        DiscardChangesDialog(onConfirm = { showDiscard = false; edit.onRevert(); onBack() }, onDismiss = { showDiscard = false })
    }
    editTarget?.let { target ->
        EditValueDialogMiuix(
            title = target.title,
            initial = target.initial,
            isNumber = target.isNumber,
            onCommit = { value -> editTarget = null; target.onCommit(value) },
            onDismiss = { editTarget = null },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = "应用功能",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        if (edit.dirty) showDiscard = true else onBack()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    val navigator = LocalNavigator.current
                    // 保存：片段文档有未保存修改时可用（走 joyose-scoped-write CLI 链路）
                    TextButton(
                        text = if (edit.writing) "保存中…" else "保存",
                        onClick = edit.onSave,
                        enabled = edit.dirty && !edit.writing,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    IconButton(onClick = {
                        // 作用域编辑：仅编辑当前 App 名下的云控片段（秒开 + 作用域隔离）
                        view?.packageName?.let { pkg -> navigator.push(Route.HyperOsScopedEditor(pkg)) }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 编辑链路错误条（保存失败/片段读取失败）：内容仍渲染（未就绪=只读），
                    // 保存按钮保留可重试 —— 不做整页劫持
                    edit.error?.let { errorText ->
                        item(key = "edit_error") { EditErrorBannerMiuix(errorText) }
                    }
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
                            // 片段 = 编辑文档里该功能对应的 JSON（编辑真值来源；未就绪为 null → 只读）
                            val fragment = edit.document?.get(feature.path) as? JsonObject
                            // 解析结果按片段实例缓存：避免每次重组重跑 parseSceneInfo
                            val scenes = remember(fragment) { fragment?.let(edit.parseScenes) ?: emptyList() }
                            FeatureCardMiuix(feature, fragment, scenes, edit) { editTarget = it }
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
private fun FeatureCardMiuix(
    feature: JoyoseManager.FeatureHit,
    fragment: JsonObject?,
    scenes: List<HyperOsViewModel.SceneInfo>,
    edit: HyperOsDetailEdit,
    onEditRequest: (EditTarget) -> Unit,
) {
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
            // params 列表：可编辑（片段就绪时）——bool 行内开关；数字/字符串点行弹窗；
            // 片段未就绪或对象/数组保持只读渲染（对象/数组走 scene 卡或高级编辑）。
            feature.params.forEach { param ->
                // scene_ovrride[N].* 是 appview 展平的虚拟参数：片段就绪时已由下方
                // 场景卡结构化呈现，跳过原始行避免双份渲染；片段未就绪时保留兜底展示
                if (fragment != null && param.name.startsWith("scene_ovrride[")) return@forEach
                val live = fragment?.get(param.name)
                val livePrim = live as? JsonPrimitive
                when {
                    livePrim != null && livePrim.booleanOrNull != null ->
                        ParamSwitchRowMiuix(param.name, livePrim.boolean) { v ->
                            edit.onScalar(feature.path, param.name, JsonPrimitive(v))
                        }
                    livePrim != null && edit.document != null -> {
                        val isNumber = livePrim.isString.not()
                        ParamEditableRowMiuix(
                            name = param.name,
                            value = livePrim.content,
                            isNumber = isNumber,
                            onEditRequest = onEditRequest,
                        ) { text ->
                            val prim = when {
                                isNumber && text.toLongOrNull() != null -> JsonPrimitive(text.toLong())
                                isNumber && text.toDoubleOrNull() != null -> JsonPrimitive(text.toDouble())
                                else -> JsonPrimitive(text)
                            }
                            edit.onScalar(feature.path, param.name, prim)
                        }
                    }
                    else -> ParamRowMiuix(param.name, live ?: param.value)
                }
            }
            // 场景卡：scene_ovrride 结构化呈现（场景 → booster 容器 → cmd）
            scenes.forEach { scene ->
                SceneCardMiuix(feature.path, scene, edit, onEditRequest = onEditRequest)
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

// ── 功能页参数编辑（P2.4）────────────────────────────────

/**
 * 编辑链路错误条（对齐 CommonConfigMiuix 的失败提示条）：仅失败呈现
 * （保存失败 / 片段读取失败），页面内容与保存按钮保持可用，不做整页劫持。
 */
@Composable
private fun EditErrorBannerMiuix(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "错误",
                tint = colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = colorScheme.onErrorContainer,
            )
        }
    }
}

/** 一次编辑弹窗的目标：标题 / 初始文本 / 是否数字 / 确认回调。 */
internal data class EditTarget(
    val title: String,
    val initial: String,
    val isNumber: Boolean,
    val onCommit: (String) -> Unit,
)

/** bool 参数行：行内即时开关（编辑写进共享片段文档，保存统一落库）。 */
@Composable
private fun ParamSwitchRowMiuix(name: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = if (value) "true" else "false",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurface,
            )
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/** 数字/字符串参数行：点击弹编辑框，确认后写回（保持原 JSON 类型）。 */
@Composable
private fun ParamEditableRowMiuix(
    name: String,
    value: String,
    isNumber: Boolean,
    onEditRequest: (EditTarget) -> Unit,
    onCommit: (String) -> Unit,
) {
    val expanded = value.length > 40
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onEditRequest(EditTarget(name, value, isNumber, onCommit)) }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            Text(text = "编辑", fontSize = 11.sp, color = colorScheme.primary)
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurface,
            maxLines = if (expanded) 2 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 场景卡：场景名（中文+原名+id）→ 标志开关 → booster 容器（cmd 可编辑）→ 复用映射。 */
@Composable
private fun SceneCardMiuix(
    pointer: String,
    scene: HyperOsViewModel.SceneInfo,
    edit: HyperOsDetailEdit,
    onEditRequest: (EditTarget) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = scene.sceneNameLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                BadgeMiuix(
                    text = "scene_id ${scene.sceneId}",
                    container = colorScheme.surfaceVariant,
                    content = colorScheme.onSurfaceVariantSummary,
                )
            }
            scene.flags.forEach { (flag, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = flag,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = value, onCheckedChange = { v ->
                        edit.onSceneFlag(pointer, scene.index, flag, v)
                    })
                }
            }
            scene.containers.forEach { container ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${container.key} · ${container.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                    )
                    container.entries.forEach { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceVariant)
                                .clickable {
                                    onEditRequest(
                                        EditTarget(
                                            title = "${scene.sceneNameLabel} · ${container.key} [${entry.index}] cmd",
                                            initial = entry.cmd,
                                            isNumber = false,
                                        ) { text ->
                                            edit.onSceneCmd(pointer, scene.index, container.key, entry.index, text)
                                        },
                                    )
                                }
                                .padding(8.dp),
                        ) {
                            if (entry.permission.isNotBlank()) {
                                Text(
                                    text = "permission: ${entry.permission}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Text(
                                text = entry.cmd,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (scene.reuseCmdConfig.isNotEmpty()) {
                Text(
                    text = "复用映射: ${scene.reuseCmdConfig.joinToString(", ")}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 参数/命令编辑弹窗（Miuix WindowDialog + TextField；数字校验后按原类型写回）。 */
@Composable
private fun EditValueDialogMiuix(
    title: String,
    initial: String,
    isNumber: Boolean,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(title) { mutableStateOf(initial) }
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
            if (isNumber && text.toLongOrNull() == null && text.toDoubleOrNull() == null) {
                Text(
                    text = "请输入有效数字",
                    fontSize = 11.sp,
                    color = colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                Modifier.align(Alignment.End).padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = "确定",
                    onClick = { onCommit(text) },
                    enabled = !isNumber || text.toLongOrNull() != null || text.toDoubleOrNull() != null,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
