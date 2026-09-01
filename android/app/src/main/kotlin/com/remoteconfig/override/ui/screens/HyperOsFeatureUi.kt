package com.remoteconfig.override.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ripple
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.data.CmdCodec
import com.remoteconfig.override.data.CurveCodec
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.data.NovatekCodec
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.component.DiscardChangesDialog
import com.remoteconfig.override.ui.component.material.SegmentedListItem
import com.remoteconfig.override.ui.theme.LocalUiMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixSpinner
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

// ── 片段定位辅助（作用域文档 = { "<JSON Pointer>": <片段> }）────────────────

/** 找第一个以 prefix 开头且值为对象的片段（返回 指针 to 片段）。 */
internal fun JsonObject?.findFragment(prefix: String): Pair<String, JsonObject>? {
    val pointer = this?.keys?.firstOrNull { it.startsWith(prefix) } ?: return null
    val fragment = this[pointer] as? JsonObject ?: return null
    return pointer to fragment
}

/** 找第一个以 prefix 开头的片段（任意类型：标量/数组/对象 —— novatek token 串、黑名单数组用）。 */
internal fun JsonObject?.findAnyFragment(prefix: String): Pair<String, kotlinx.serialization.json.JsonElement>? {
    val pointer = this?.keys?.firstOrNull { it.startsWith(prefix) } ?: return null
    return pointer to this[pointer]!!
}

/** 片段内命中任一前缀的键（温控/曲线类参数发现）。 */
internal fun JsonObject.keysMatching(vararg prefixes: String): List<String> =
    keys.filter { k -> prefixes.any { k.startsWith(it) } }

// ── 功能屏外壳（双皮肤：TopBar + 返回守卫 + 保存 + 错误条 + LazyColumn）──────

/**
 * 功能子屏统一外壳：独立路由页（Scaffold + safeDrawing Top+Horizontal insets）。
 *
 * - dirty 且提供 [onSave] 时：顶栏保存按钮 + 系统返回守卫（DiscardChangesDialog，
 *   确认丢弃先 [onRevert] 再退栈 —— 不重置的话幂等守卫会让脏草稿复活）；
 * - [error] 以列表首条横幅呈现（内容仍渲染，保存可重试，不整页劫持）；
 * - [loading] 占满居中 spinner。
 */
@Composable
fun HyperOsFeatureScaffold(
    title: String,
    dirty: Boolean,
    saving: Boolean,
    error: String?,
    loading: Boolean = false,
    diffBase: String? = null,
    diffEdited: String? = null,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    onRevert: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var showDiscard by remember { mutableStateOf(false) }
    val guarded = dirty && onSave != null
    BackHandler(enabled = guarded) { showDiscard = true }
    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onRevert?.invoke(); onBack() },
            onDismiss = { showDiscard = false },
        )
    }
    val requestBack = { if (guarded) showDiscard = true else onBack() }
    // 保存前 diff 预览（C3）：提供 base/edited 且确有变更时先展示变更清单
    val diffItems = remember(diffBase, diffEdited) { scopedDiffList(diffBase, diffEdited) }
    var showDiff by remember { mutableStateOf(false) }
    val requestSave: () -> Unit = {
        if (diffItems.isEmpty()) onSave?.invoke() else showDiff = true
    }
    if (showDiff) {
        ScopedDiffDialog(
            items = diffItems,
            onConfirm = { showDiff = false; onSave?.invoke() },
            onDismiss = { showDiff = false },
        )
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> FeatureScaffoldMiuix(title, dirty, saving, error, loading, requestBack, requestSave, content)
        UiMode.Material -> FeatureScaffoldMaterial(title, dirty, saving, error, loading, requestBack, requestSave, content)
    }
}

@Composable
private fun FeatureScaffoldMiuix(
    title: String,
    dirty: Boolean,
    saving: Boolean,
    error: String?,
    loading: Boolean,
    onBack: () -> Unit,
    onSave: (() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    MiuixScaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            MiuixTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = miuixColorScheme.onSurface)
                    }
                },
                actions = {
                    if (onSave != null) {
                        MiuixTextButton(
                            text = if (saving) "保存中…" else "保存",
                            onClick = onSave,
                            enabled = dirty && !saving,
                            colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MiuixSpinner() }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { item(key = "error") { ErrorBannerMiuix(it) } }
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureScaffoldMaterial(
    title: String,
    dirty: Boolean,
    saving: Boolean,
    error: String?,
    loading: Boolean,
    onBack: () -> Unit,
    onSave: (() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineLarge) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (onSave != null) {
                        TextButton(onClick = onSave, enabled = dirty && !saving) {
                            Text(if (saving) "保存中…" else "保存")
                        }
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
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    error?.let { item(key = "error") { ErrorBannerMaterial(it) } }
                    content()
                }
            }
        }
    }
}

// ── 行组件（双皮肤分发）────────────────────────────────────────────────────

/** 分组卡：Miuix = Card 容器；Material = 裸列（行自带分段样式）。 */
@Composable
fun HyperOsSectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (title != null) {
                    MiuixText(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = miuixColorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                content()
            }
        }
        UiMode.Material -> Column {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                )
            }
            Column(content = content)
        }
    }
}

/** 分组标题（无卡片的裸小节标题，双皮肤）。 */
@Composable
fun HyperOsSectionLabel(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = miuixColorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        )
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
        )
    }
}

/** 布尔开关行（Miuix 必须 SwitchPreference，禁 basic.Switch 手拼 —— 项目规范）。 */
@Composable
fun HyperOsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
        )
        UiMode.Material -> SegmentedListItem(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = summary?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            },
        )
    }
}

/** 可点击动作行（title + summary + 右侧自定义槽，如值/箭头）。 */
@Composable
fun HyperOsActionRow(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> BasicComponent(
            modifier = if (onClick != null) {
                Modifier.combinedClickable(interactionSource = null, indication = ripple(), onClick = onClick)
            } else {
                Modifier
            },
            title = title,
            summary = summary,
            endActions = end,
        )
        UiMode.Material -> SegmentedListItem(
            onClick = onClick ?: {},
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = summary?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            },
            trailingContent = end?.let { content -> { Row { content() } } },
        )
    }
}

/** 值编辑行：title + 当前值（mono，右对齐）+ 箭头，点击弹编辑框。 */
@Composable
fun HyperOsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> BasicComponent(
            modifier = Modifier.combinedClickable(interactionSource = null, indication = ripple(), onClick = onClick),
            title = title,
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiuixText(
                        text = value,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = miuixColorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    MiuixIcon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "编辑",
                        tint = miuixColorScheme.onSurfaceVariantActions,
                    )
                }
            },
        )
        UiMode.Material -> SegmentedListItem(
            onClick = onClick,
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "编辑")
                }
            },
        )
    }
}

// ── 错误横幅（仅失败呈现；成功以状态为反馈）────────────────────────────────

@Composable
internal fun ErrorBannerMiuix(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = miuixColorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixText(text = text, fontSize = 14.sp, color = miuixColorScheme.onErrorContainer)
        }
    }
}

@Composable
internal fun ErrorBannerMaterial(text: String) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

// ── 编辑弹窗（数值/字符串）──────────────────────────────────────────────────

/** 一次编辑弹窗的目标：标题 / 初始文本 / 是否数字 / 确认回调。 */
internal data class EditTarget(
    val title: String,
    val initial: String,
    val isNumber: Boolean,
    val onCommit: (String) -> Unit,
)

/** 数字/字符串编辑弹窗（双皮肤；数字校验 gate 确定，保持原 JSON 类型由调用方处理）。 */
@Composable
internal fun EditValueDialog(
    title: String,
    initial: String,
    isNumber: Boolean,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(title) { mutableStateOf(initial) }
    // 所有数字字段的提交端都是 toLongOrNull —— 校验口径必须一致，否则小数会静默 no-op
    val numericOk = !isNumber || text.toLongOrNull() != null
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth()) {
                MiuixTextField(value = text, onValueChange = { text = it }, singleLine = true)
                if (!numericOk) {
                    MiuixText(
                        text = "请输入有效整数（不支持小数）",
                        fontSize = 11.sp,
                        color = miuixColorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(Modifier.align(Alignment.End).padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    MiuixTextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))
                    MiuixTextButton(
                        text = "确定",
                        onClick = { onCommit(text) },
                        enabled = numericOk,
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                    if (!numericOk) {
                        Text(
                            text = "请输入有效整数（不支持小数）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onCommit(text) }, enabled = numericOk) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

// ── 曲线编辑器（S4：`x:y[,…]` 串 ↔ 行表格双向绑定）────────────────────────

/** 快速填充 chip：点击追加一行并预填非空列。 */
data class CurveChip(val label: String, val key: String? = null, val x: String? = null, val y: String? = null)

/** 行字段必须走 snapshot 状态：否则输入只改 plain var，error/canCommit 不随输入刷新。 */
private class CurveRow(key: String, x: String, y: String) {
    var key by mutableStateOf(key)
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
}

/**
 * 曲线串 ↔ 表格行 双向绑定（双皮肤）。
 *
 * - 已有值解析失败 → 退回原始文本模式（仍按格式校验 gate 确定）；
 * - 行内按 [CurveCodec.Format] 逐行校验，非法 / 半填行 → 错误文案 + 确定禁用；
 * - [chips] 由调用方按 DeviceCaps 生成（刷新率档 / CPU/GPU 频率档），点击快速追加行。
 */
@Composable
fun CurveEditorDialog(
    title: String,
    value: String,
    format: CurveCodec.Format,
    chips: List<CurveChip> = emptyList(),
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember(title, value) { CurveEditorState(value, format) }
    val error = state.error
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
            CurveEditorBody(state, format, chips, error, onCommit, onDismiss) { t, s ->
                MiuixText(text = t, fontSize = s, color = miuixColorScheme.onSurfaceVariantSummary)
            }
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = {
                CurveEditorBody(state, format, chips, error, onCommit, onDismiss) { t, s ->
                    Text(text = t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { onCommit(state.commitValue()) }, enabled = state.canCommit) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

@Composable
private fun CurveEditorBody(
    state: CurveEditorState,
    format: CurveCodec.Format,
    chips: List<CurveChip>,
    error: String?,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: @Composable (String, androidx.compose.ui.unit.TextUnit) -> Unit,
) {
    // 共享体（状态在 state 里）：提示 + 表格/原始文本 + chips + 错误
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            hint(format.hint, 11.sp)
            if (state.rawMode) {
                CurveFieldMiuixCompat(state.rawText, { state.rawText = it }, singleLine = false)
            } else {
                state.rows.forEachIndexed { i, row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (format.hasKey) {
                            CurveFieldMiuixCompat(row.key, { row.key = it }, weight = 1f, label = format.keyLabel ?: "")
                        }
                        CurveFieldMiuixCompat(row.x, { row.x = it }, weight = 1f, label = format.xLabel)
                        CurveFieldMiuixCompat(row.y, { row.y = it }, weight = 1f, label = format.yLabel)
                        IconButtonCompat(onClick = { state.removeAt(i) }) {
                            when (LocalUiMode.current) {
                                UiMode.Miuix -> MiuixIcon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = miuixColorScheme.onSurfaceVariantActions,
                                )
                                UiMode.Material -> Icon(Icons.Filled.Close, contentDescription = "删除")
                            }
                        }
                    }
                }
                TextButtonCompat(text = "+ 添加一行") { state.addRow() }
            }
            if (chips.isNotEmpty() && !state.rawMode) {
                Row(
                    Modifier.fillMaxWidth().chipsScroll(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { chip ->
                        TextButtonCompat(text = chip.label, primary = false) {
                            state.addRow(chip.key, chip.x, chip.y)
                        }
                    }
                }
            }
            error?.let { err ->
                when (LocalUiMode.current) {
                    UiMode.Miuix -> MiuixText(text = err, fontSize = 11.sp, color = miuixColorScheme.error)
                    UiMode.Material -> Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        // Miuix 的 WindowDialog 没有 confirm 槽位 —— 按钮行放共享体尾部
        if (LocalUiMode.current == UiMode.Miuix) {
            Row(Modifier.align(Alignment.End).padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                MiuixTextButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                MiuixTextButton(
                    text = "确定",
                    onClick = { onCommit(state.commitValue()) },
                    enabled = state.canCommit,
                    colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 双皮肤输入框（曲线行内 / 原始文本模式共用）。 */
@Composable
private fun CurveFieldMiuixCompat(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    weight: Float? = null,
    label: String = "",
) {
    val modifier = if (weight != null) {
        Modifier.fillMaxWidth(weight).padding(top = if (label.isNotEmpty()) 2.dp else 0.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    if (LocalUiMode.current == UiMode.Miuix) {
        Column(modifier) {
            if (label.isNotEmpty()) {
                MiuixText(text = label, fontSize = 10.sp, color = miuixColorScheme.onSurfaceVariantSummary)
            }
            MiuixTextField(value = value, onValueChange = onValueChange, singleLine = singleLine)
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            label = if (label.isNotEmpty()) {
                { Text(label, style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun IconButtonCompat(onClick: () -> Unit, content: @Composable () -> Unit) {
    if (LocalUiMode.current == UiMode.Miuix) {
        MiuixIconButton(onClick = onClick) { content() }
    } else {
        androidx.compose.material3.IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun TextButtonCompat(text: String, primary: Boolean = true, onClick: () -> Unit) {
    if (LocalUiMode.current == UiMode.Miuix) {
        if (primary) {
            MiuixTextButton(text = text, onClick = onClick, colors = MiuixButtonDefaults.textButtonColorsPrimary())
        } else {
            MiuixTextButton(text = text, onClick = onClick)
        }
    } else {
        TextButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun Modifier.chipsScroll(): Modifier = horizontalScroll(rememberScrollState())

/** 曲线编辑器状态（rows 原地可变 —— mutableStateOf 列表整体替换触发重组）。 */
private class CurveEditorState(initial: String, val format: CurveCodec.Format) {
    var rawMode by mutableStateOf(initial.isNotBlank() && CurveCodec.parse(initial, format) == null)
    var rawText by mutableStateOf(initial)
    var rows by mutableStateOf(
        (CurveCodec.parse(initial, format) ?: emptyList()).map { CurveRow(it.key.orEmpty(), it.x, it.y) },
    )

    /** 填完整的行（整行空白被忽略；半填行进 [error]）。 */
    private val filled: List<CurveCodec.Segment>
        get() = rows
            .filter { it.x.isNotBlank() && it.y.isNotBlank() }
            .map { CurveCodec.Segment(it.key.takeIf { k -> format.hasKey && k.isNotBlank() }, it.x.trim(), it.y.trim()) }

    val error: String?
        get() {
            if (rawMode) return null
            val partial = rows.any { (it.x.isBlank() && it.y.isNotBlank()) || (it.x.isNotBlank() && it.y.isBlank()) }
            if (partial) return "存在未填完整的行（横纵值需成对填写）"
            return CurveCodec.validate(filled, format)
        }

    val canCommit: Boolean
        get() = if (rawMode) CurveCodec.parse(rawText, format) != null else error == null

    fun commitValue(): String = if (rawMode) rawText.trim() else CurveCodec.format(filled, format)

    fun addRow(key: String? = null, x: String? = null, y: String? = null) {
        rows = rows + CurveRow(key.orEmpty(), x.orEmpty(), y.orEmpty())
    }

    fun removeAt(index: Int) {
        rows = rows.toMutableList().apply { removeAt(index) }
    }
}

// ── 共享小辅助 ─────────────────────────────────────────────────────────────

/** 空态提示（LazyColumn item 内使用）。 */
@Composable
internal fun HyperOsEmptyHint(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(
            text = text,
            fontSize = 14.sp,
            color = miuixColorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        )
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        )
    }
}

/** fps 档 chips：TEMP_FPS 类预填 y 轴（帧率）；FPS_THRESH 类 x 轴才是帧率（onX=true）。 */
internal fun fpsChips(caps: JoyoseManager.DeviceCaps?, onX: Boolean = false): List<CurveChip> =
    caps?.refreshRates.orEmpty().map {
        if (onX) CurveChip(label = "$it fps", x = it.toString())
        else CurveChip(label = "$it fps", y = it.toString())
    }

/** CPU 频率档 chips（限频曲线 y 轴；MHz 展示，取最高 12 档防溢出）。 */
internal fun cpuFreqChips(caps: JoyoseManager.DeviceCaps?): List<CurveChip> =
    caps?.cpuFrequencies.orEmpty().takeLast(12).map { CurveChip(label = "${it / 1000} MHz", y = it.toString()) }

/**
 * 按当前 JSON 值类型写回：字符串保持字符串；数字尝试 Long→Double 回退文本。
 * （云控里 int 字段既有数字型也有字符串型，写回保持原类型不惊动消费端。）
 */
internal fun kotlinx.serialization.json.JsonElement.sameTypePrimitive(text: String): kotlinx.serialization.json.JsonPrimitive {
    val prim = this as? kotlinx.serialization.json.JsonPrimitive
        ?: return kotlinx.serialization.json.JsonPrimitive(text)
    if (prim.isString) return kotlinx.serialization.json.JsonPrimitive(text)
    text.toLongOrNull()?.let { return kotlinx.serialization.json.JsonPrimitive(it) }
    text.toDoubleOrNull()?.let { return kotlinx.serialization.json.JsonPrimitive(it) }
    return kotlinx.serialization.json.JsonPrimitive(text)
}

// ── 命令编辑器（C2：perfhint/setprop/glk 结构化 + 原文模式；perflock/end 由调用方过滤）──

/** 命令编辑器状态（全部 snapshot 状态，确定门随输入实时刷新）。 */
private class CmdEditorState(initial: String) {
    val parsed: CmdCodec.Parsed = CmdCodec.parse(initial)
    var rawMode by mutableStateOf(parsed is CmdCodec.Parsed.Raw)
    var rawText by mutableStateOf(initial)
    // perfhint 四字段
    var hint by mutableStateOf((parsed as? CmdCodec.Parsed.PerfHint)?.hint.orEmpty())
    var userData by mutableStateOf((parsed as? CmdCodec.Parsed.PerfHint)?.userData.orEmpty())
    var data1 by mutableStateOf((parsed as? CmdCodec.Parsed.PerfHint)?.data1.orEmpty())
    var data3 by mutableStateOf((parsed as? CmdCodec.Parsed.PerfHint)?.data3.orEmpty())
    // setprop / glk 共用 kv 行
    var rows by mutableStateOf(
        when (val p = parsed) {
            is CmdCodec.Parsed.SetProp -> p.pairs.map { CmdKvRow(it.first, it.second) }
            is CmdCodec.Parsed.Glk -> p.segments.map { CmdKvRow(it.first, it.second) }
            else -> emptyList()
        },
    )

    fun toParsed(): CmdCodec.Parsed = when (val p = parsed) {
        is CmdCodec.Parsed.PerfHint -> CmdCodec.Parsed.PerfHint(hint, userData, data1, data3)
        is CmdCodec.Parsed.SetProp -> CmdCodec.Parsed.SetProp(rows.map { it.key to it.value })
        is CmdCodec.Parsed.Glk -> CmdCodec.Parsed.Glk(rows.map { it.key to it.value })
        is CmdCodec.Parsed.Raw -> CmdCodec.Parsed.Raw(rawText)
    }

    val error: String?
        get() = when (val p = parsed) {
            is CmdCodec.Parsed.PerfHint ->
                if (hint.isBlank() || userData.isBlank() || data1.isBlank() || data3.isBlank()) "各字段不能为空" else null
            is CmdCodec.Parsed.SetProp ->
                if (rows.any { it.key.isBlank() }) "键不能为空" else null
            is CmdCodec.Parsed.Glk ->
                if (rows.any { it.key.isBlank() || it.value.isBlank() }) "占位符与值不能为空" else null
            is CmdCodec.Parsed.Raw -> null
        }

    val canCommit: Boolean get() = error == null
    fun commitValue(): String = if (rawMode) rawText else CmdCodec.serialize(toParsed())
}

/** setprop / glk 行（snapshot 字段）。 */
private class CmdKvRow(key: String, value: String) {
    var key by mutableStateOf(key)
    var value by mutableStateOf(value)
}

@Composable
internal fun CmdEditorDialog(
    title: String,
    cmd: String,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember(cmd) { CmdEditorState(cmd) }
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
            CmdEditorBody(state, onCommit, onDismiss)
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = { CmdEditorBody(state, onCommit, onDismiss) },
            confirmButton = {
                TextButton(onClick = { onCommit(state.commitValue()) }, enabled = state.canCommit) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

@Composable
private fun CmdEditorBody(
    state: CmdEditorState,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.rawMode) {
                CurveFieldMiuixCompat(state.rawText, { state.rawText = it }, singleLine = false)
            } else {
                when (val p = state.parsed) {
                    is CmdCodec.Parsed.PerfHint -> {
                        CurveFieldMiuixCompat(
                            state.hint, { state.hint = it },
                            label = "hint（${CmdCodec.PERF_HINTS.joinToString("/")}）",
                        )
                        CurveFieldMiuixCompat(state.userData, { state.userData = it }, label = "userData")
                        CurveFieldMiuixCompat(state.data1, { state.data1 = it }, label = "data1")
                        CurveFieldMiuixCompat(state.data3, { state.data3 = it }, label = "data3")
                    }
                    is CmdCodec.Parsed.SetProp, is CmdCodec.Parsed.Glk -> {
                        val isGlk = p is CmdCodec.Parsed.Glk
                        state.rows.forEachIndexed { i, row ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CurveFieldMiuixCompat(
                                    row.key, { row.key = it }, weight = 1f,
                                    label = if (isGlk) "占位符(${CmdCodec.GLK_PLACEHOLDERS.joinToString("/")})" else "键",
                                )
                                CurveFieldMiuixCompat(row.value, { row.value = it }, weight = 1f, label = "值")
                                IconButtonCompat(onClick = { state.rows = state.rows.toMutableList().apply { removeAt(i) } }) {
                                    when (LocalUiMode.current) {
                                        UiMode.Miuix -> MiuixIcon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "删除",
                                            tint = miuixColorScheme.onSurfaceVariantActions,
                                        )
                                        UiMode.Material -> Icon(Icons.Filled.Close, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                        TextButtonCompat(text = "+ 添加一对") { state.rows = state.rows + CmdKvRow("", "") }
                    }
                    is CmdCodec.Parsed.Raw -> {}
                }
            }
            state.error?.let { err ->
                when (LocalUiMode.current) {
                    UiMode.Miuix -> MiuixText(text = err, fontSize = 11.sp, color = miuixColorScheme.error)
                    UiMode.Material -> Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        // 原文模式切换 + Miuix 按钮行（WindowDialog 无 confirm 槽位）
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButtonCompat(text = if (state.rawMode) "结构化编辑" else "原文模式", primary = false) {
                if (!state.rawMode) state.rawText = CmdCodec.serialize(state.toParsed())
                state.rawMode = !state.rawMode
            }
            if (LocalUiMode.current == UiMode.Miuix) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextButton(text = "取消", onClick = onDismiss)
                    MiuixTextButton(
                        text = "确定",
                        onClick = { onCommit(state.commitValue()) },
                        enabled = state.canCommit,
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

// ── 保存前 diff 预览（C3）──────────────────────────────────────────────────

/** 一条变更：指针 | 旧值 | 新值。 */
internal data class ScopedDiffItem(val pointer: String, val oldText: String, val newText: String)

private val DiffPrettyJson = Json { prettyPrint = true }

/** base/edited 两份作用域文档逐指针比对（片段级 diff；perflock 命令未变更时自然不出现在清单）。 */
internal fun scopedDiffList(base: String?, edited: String?): List<ScopedDiffItem> {
    if (base == null || edited == null || base == edited) return emptyList()
    val b = runCatching { Json.parseToJsonElement(base).jsonObject }.getOrNull() ?: return emptyList()
    val e = runCatching { Json.parseToJsonElement(edited).jsonObject }.getOrNull() ?: return emptyList()
    fun text(x: kotlinx.serialization.json.JsonElement?): String = when (x) {
        null -> "（缺失）"
        is kotlinx.serialization.json.JsonPrimitive -> x.content
        else -> DiffPrettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), x)
    }
    return (b.keys + e.keys).distinct().mapNotNull { key ->
        val ov = b[key]; val nv = e[key]
        if (ov == nv) return@mapNotNull null
        ScopedDiffItem(key, text(ov), text(nv))
    }
}

@Composable
internal fun ScopedDiffDialog(
    items: List<ScopedDiffItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mono = FontFamily.Monospace
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(show = true, title = "保存前确认（${items.size} 处变更）", onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { d ->
                        Column {
                            MiuixText(text = d.pointer, fontSize = 11.sp, color = miuixColorScheme.onSurfaceVariantSummary, fontFamily = mono)
                            MiuixText(text = "旧：${d.oldText.take(160)}", fontSize = 11.sp, color = miuixColorScheme.onSurface, fontFamily = mono)
                            MiuixText(text = "新：${d.newText.take(160)}", fontSize = 11.sp, color = miuixColorScheme.primary, fontFamily = mono)
                        }
                    }
                }
                Row(Modifier.align(Alignment.End).padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    MiuixTextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))
                    MiuixTextButton(
                        text = "确认保存",
                        onClick = onConfirm,
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("保存前确认（${items.size} 处变更）", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { d ->
                        Column {
                            Text(d.pointer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = mono)
                            Text("旧：${d.oldText.take(160)}", style = MaterialTheme.typography.bodySmall, fontFamily = mono)
                            Text("新：${d.newText.take(160)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = mono)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("确认保存") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

// ── Novatek 等级编辑器（1.6：7 字段 + params 17 键命名表）──────────────────

/** Novatek 等级编辑状态（params 超过 17 token 时禁结构化，只允许原文编辑 params 串）。 */
private class NovatekLevelState(level: NovatekCodec.Level) {
    var dynamicFps by mutableStateOf(level.dynamicFps)
    var targetFps by mutableStateOf(level.targetFps)
    var tgTh by mutableStateOf(level.tgTh)
    var tgRec by mutableStateOf(level.tgRec)
    var mgTh by mutableStateOf(level.mgTh)
    var mgRec by mutableStateOf(level.mgRec)
    val structured = level.params.size <= NovatekCodec.PARAM_KEYS.size
    val paramValues = NovatekCodec.PARAM_KEYS.mapIndexed { i, _ -> mutableStateOf(level.params.getOrNull(i).orEmpty()) }
    var rawParams by mutableStateOf(level.params.joinToString(","))

    val error: String?
        get() = when {
            dynamicFps.isBlank() || targetFps.isBlank() -> "dynamicFps / targetFps 不能为空"
            else -> null
        }

    fun toLevel(): NovatekCodec.Level = NovatekCodec.Level(
        dynamicFps = dynamicFps,
        targetFps = targetFps,
        params = if (structured) paramValues.map { it.value } else rawParams.split(','),
        tgTh = tgTh, tgRec = tgRec, mgTh = mgTh, mgRec = mgRec,
    )
}

@Composable
internal fun NovatekLevelDialog(
    title: String,
    level: NovatekCodec.Level,
    refreshRates: List<Int>,
    onCommit: (NovatekCodec.Level) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember(title) { NovatekLevelState(level) }
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
            NovatekLevelBody(state, refreshRates, onCommit, onDismiss)
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = { NovatekLevelBody(state, refreshRates, onCommit, onDismiss) },
            confirmButton = {
                TextButton(onClick = { onCommit(state.toLevel()) }, enabled = state.error == null) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

@Composable
private fun NovatekLevelBody(
    state: NovatekLevelState,
    refreshRates: List<Int>,
    onCommit: (NovatekCodec.Level) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurveFieldMiuixCompat(state.dynamicFps, { state.dynamicFps = it }, label = "dynamicFps（插帧门限，低于 ceil(v)-1 降级 bypass）")
            CurveFieldMiuixCompat(state.targetFps, { state.targetFps = it }, label = "targetFps（输出刷新率${if (refreshRates.isNotEmpty()) "：${refreshRates.joinToString("/")}" else ""}）")
            if (state.structured) {
                MiuixHintLabel("params（17 键，留空 = 0）")
                state.paramValues.forEachIndexed { i, v ->
                    CurveFieldMiuixCompat(v.value, { v.value = it }, weight = 1f, label = NovatekCodec.PARAM_KEYS[i])
                }
            } else {
                MiuixHintLabel("params 超过 17 token —— 结构化编辑不可用，请原文修改（保真）")
                CurveFieldMiuixCompat(state.rawParams, { state.rawParams = it }, singleLine = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurveFieldMiuixCompat(state.tgTh, { state.tgTh = it }, weight = 1f, label = "TGAME 降档℃")
                CurveFieldMiuixCompat(state.tgRec, { state.tgRec = it }, weight = 1f, label = "TGAME 回档℃")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurveFieldMiuixCompat(state.mgTh, { state.mgTh = it }, weight = 1f, label = "MGAME 降档℃")
                CurveFieldMiuixCompat(state.mgRec, { state.mgRec = it }, weight = 1f, label = "MGAME 回档℃")
            }
            state.error?.let { err ->
                when (LocalUiMode.current) {
                    UiMode.Miuix -> MiuixText(text = err, fontSize = 11.sp, color = miuixColorScheme.error)
                    UiMode.Material -> Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (LocalUiMode.current == UiMode.Miuix) {
            Row(Modifier.align(Alignment.End).padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                MiuixTextButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                MiuixTextButton(
                    text = "确定",
                    onClick = { onCommit(state.toLevel()) },
                    enabled = state.error == null,
                    colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun MiuixHintLabel(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(text = text, fontSize = 10.sp, color = miuixColorScheme.onSurfaceVariantSummary)
        UiMode.Material -> Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
