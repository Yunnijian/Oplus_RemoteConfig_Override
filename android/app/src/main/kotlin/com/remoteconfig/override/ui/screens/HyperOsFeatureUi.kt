package com.remoteconfig.override.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.remoteconfig.override.ui.component.material.ExpressiveSwitch
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.expressiveTopAppBarColors
import com.remoteconfig.override.ui.component.material.SegmentedColumn
import com.remoteconfig.override.ui.component.material.SegmentedDropdownItem
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
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
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
 * 功能屏「默认即生效」：所有编辑在 ViewModel 内即改即存（写入中自动排队），
 * 顶栏不设保存按钮、返回不做未保存守卫 —— 状态本身就是反馈。
 * [error] 以列表首条横幅呈现（内容仍渲染，不整页劫持）；[loading] 占满居中 spinner。
 */
@Composable
fun HyperOsFeatureScaffold(
    title: String,
    error: String?,
    loading: Boolean = false,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> FeatureScaffoldMiuix(title, error, loading, onBack, content)
        UiMode.Material -> FeatureScaffoldMaterial(title, error, loading, onBack, content)
    }
}

@Composable
private fun FeatureScaffoldMiuix(
    title: String,
    error: String?,
    loading: Boolean,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    // 底部不消费 inset：内容延伸到导航条后面（对齐主页沉浸），列表自补 navigationBars padding
    MiuixScaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            MiuixTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = miuixColorScheme.onSurface)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
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
    error: String?,
    loading: Boolean,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ExpressiveScaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let { item(key = "error") { ErrorBannerMaterial(it) } }
                    content()
                }
            }
        }
    }
}

// ── 行组件（双皮肤分发）────────────────────────────────────────────────────

/**
 * 分组卡（对齐通用配置页基线）：一组行合并进**一张卡**，标题在卡外。
 * - Miuix：卡外标题（[HyperOsSectionLabel] 同款排版）+ `Card` 内行无缝堆叠（无行间距、无内边距）；
 * - Material：[SegmentedColumn]（自带卡外标题 + 逐行分段 shape，行合并成一体的圆角卡）。
 *
 * 行以 `List<@Composable () -> Unit>` 传入（非 ColumnScope lambda）：Material 侧需要
 * 已知行数才能给每段算首/末大圆角，这是分段卡合并的前提。
 */
@Composable
fun HyperOsSectionCard(
    title: String? = null,
    rows: List<@Composable () -> Unit>,
) {
    if (rows.isEmpty()) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                // 卡内标题：组间间距由 scaffold spacedBy 提供，这里只留标题与卡的 6dp
                MiuixText(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = miuixColorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    rows.forEach { it() }
                }
            }
        }
        UiMode.Material -> SegmentedColumn(
            modifier = Modifier.fillMaxWidth(),
            title = title.orEmpty(),
            content = rows,
        )
    }
}

/**
 * 独立分组标题（不带卡片；后紧跟 [HyperOsSectionCard] 无标题变体或裸行时用）。
 * 与通用配置页 header 逐字对齐（Miuix start=4/top=12/bottom=6）。
 */
@Composable
fun HyperOsSectionLabel(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SmallTitle(
            text = text,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        )
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
    }
}

/**
 * 等宽文本块行（超长原文展示专用，如 novatek token 串）。
 *
 * 不用 summary 槽：超长摘要会撑破行高（固定 maxLines 截断更安全）。
 */
@Composable
internal fun HyperOsMonoBlockRow(title: String, body: String, maxLines: Int = 6) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            MiuixText(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = miuixColorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            MiuixText(
                text = body,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = miuixColorScheme.onSurfaceVariantSummary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        UiMode.Material -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 作用域草稿是否真的有修改：**结构化比较**（解析后按 JSON 值相等），
 * 字符串层格式差异（重排序/空白）不误报“未保存的修改”。
 */
internal fun scopedIsDirty(base: String?, edited: String?): Boolean {
    if (base == null || edited == null || base == edited) return false
    val b = runCatching { Json.parseToJsonElement(base) }.getOrNull() ?: return true
    val e = runCatching { Json.parseToJsonElement(edited) }.getOrNull() ?: return true
    return b != e
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
        // Material：整行点击切换（haptic）+ trailing 可见开关（ExpressiveSwitch 共享
        // interactionSource）—— 对齐 CommonConfig 的 SwitchItemMaterial 既有样式。
        UiMode.Material -> {
            val haptic = LocalHapticFeedback.current
            val interactionSource = remember { MutableInteractionSource() }
            SegmentedListItem(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onChange(!checked)
                },
                enabled = enabled,
                interactionSource = interactionSource,
                headlineContent = {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = summary?.let {
                    { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                },
                trailingContent = {
                    ExpressiveSwitch(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = enabled,
                        interactionSource = interactionSource,
                    )
                },
            )
        }
    }
}

/**
 * 可点击动作行（title + summary + 右侧自定义槽，如值/箭头）。
 *
 * 参数顺序是承重的：[onClick] 必须排在最后。尾随 lambda 绑定到最后一个参数，若
 * [end]（`@Composable` 内容槽）在后，`HyperOsActionRow(...) { 改草稿 }` 会被当作
 * 组合期内容执行 —— 组合期写 StateFlow → 自激重组 → 每帧改写 edited（列表持续闪烁）。
 */
@Composable
fun HyperOsActionRow(
    title: String,
    summary: String? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val effClick: () -> Unit = if (enabled) (onClick ?: {}) else ({})
    val row: @Composable () -> Unit = {
        when (LocalUiMode.current) {
            UiMode.Miuix -> BasicComponent(
                onClick = effClick,
                title = title,
                summary = summary,
                endActions = end,
            )
            UiMode.Material -> SegmentedListItem(
                onClick = effClick,
                headlineContent = {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = summary?.let {
                    { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                },
                trailingContent = end?.let { content -> { Row { content() } } },
            )
        }
    }
    if (enabled) {
        row()
    } else {
        Box(Modifier.alpha(0.4f)) { row() }
    }
}

/** 值编辑行：title + 当前值（mono，右对齐）+ 箭头，点击弹编辑框。[enabled]=false 灰置且不可点。 */
@Composable
fun HyperOsValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val effClick: () -> Unit = if (enabled) onClick else ({})
    val row: @Composable () -> Unit = {
        when (LocalUiMode.current) {
            UiMode.Miuix -> BasicComponent(
                onClick = effClick,
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
                onClick = effClick,
                headlineContent = {
                    Text(title, style = MaterialTheme.typography.titleMedium)
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
    if (enabled) {
        row()
    } else {
        Box(Modifier.alpha(0.4f)) { row() }
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

// ── 多档帧率曲线（档位 = 独立功能页入口；纯解析/序列化助手）──────────────

/** 一个档位：档位值 + 触发点列表（温度 → 限帧）。 */
internal typealias FpsBand = Pair<String, MutableList<Pair<String, String>>>

/** `165#10:0,45:120;120#…` → 档位结构（带 key 的段 = 档首）。 */
internal fun parseFpsBands(value: String): List<FpsBand> {
    val segs = CurveCodec.parse(value, CurveCodec.FPS_TARGET_BAND) ?: return emptyList()
    val bands = ArrayList<FpsBand>()
    var current: String? = null
    for (s in segs) {
        val key = s.key
        if (key != null || current == null) {
            current = key ?: segs.first().x
            bands.add(current!! to mutableListOf())
        }
        bands.last().second.add(s.x to s.y)
    }
    return bands
}

/** 档位结构 → 曲线串（每档恒带 `key#` 前缀）。 */
internal fun formatFpsBands(bands: List<FpsBand>): String =
    bands.joinToString(";") { (key, pts) -> "$key#" + pts.joinToString(",") { p -> "${p.first}:${p.second}" } }

/**
 * 单层曲线（无档位，如温控限帧 dynamic_fps `43.5:60,45:40`）→ 触发点列表。
 * 与 [parseFpsBands] 不同：不入档位结构，直接 `[x,y]` 对，x = 温度，y = 限帧。
 */
internal fun parseSimpleCurve(value: String, format: CurveCodec.Format): List<Pair<String, String>> {
    val segs = CurveCodec.parse(value, format) ?: return emptyList()
    return segs.map { it.x to it.y }
}

/** 触发点列表 → 单层曲线串（`x:y,...`）。 */
internal fun formatSimpleCurve(points: List<Pair<String, String>>, format: CurveCodec.Format): String =
    points.joinToString(format.separator) { p -> "${p.first}:${p.second}" }

/** 限帧下拉选项：不限(0) + 设备刷新率档（升序），已有值不在表内时并入。 */
internal fun fpsOptions(refreshRates: List<Int>, existing: List<String>): List<String> {
    val values = (listOf(0) + refreshRates + existing.mapNotNull { it.toIntOrNull() }).distinct().sorted()
    return values.map { if (it == 0) "不限" else "$it" }
}

internal fun fpsIndexOf(options: List<String>, fps: String): Int =
    options.indexOf(fps).takeIf { it >= 0 } ?: 0

/** 温度串格式化：整数值去掉小数点（0.5℃ 步进）。 */
internal fun tempStr(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

/** 温度关键点（滑轨视觉参考点，0–80℃ 每 10℃）。 */
private val TEMP_KEY_POINTS = (0..80 step 10).map { it.toFloat() }

/** 温控阈值滑条行（0.5℃ 一档：显示与提交均量化；关键点 + 步进震动）。 */
@Composable
internal fun BandTempSliderRow(temp: String, enabled: Boolean, onCommit: (String) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val v = (dragging ?: temp.toFloatOrNull() ?: 0f).coerceIn(0f, 80f)
    val snapped = (v * 2).toInt() / 2f
    val shown = "${tempStr(snapped)}℃"
    when (LocalUiMode.current) {
        UiMode.Miuix -> SliderPreference(
            value = v,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { d -> onCommit(tempStr((d * 2).toInt() / 2f)) }
                dragging = null
            },
            title = "温控阈值",
            valueText = shown,
            enabled = enabled,
            valueRange = 0f..80f,
            showKeyPoints = true,
            keyPoints = TEMP_KEY_POINTS,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
        )
        // Material：标题行（左标题右数值）+ 滑条占满整行（对齐「界面缩放」既有滑条卡片布局）
        UiMode.Material -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "温控阈值",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = v,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { d -> onCommit(tempStr((d * 2).toInt() / 2f)) }
                    dragging = null
                },
                valueRange = 0f..80f,
                steps = 159,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 触发后限帧下拉行（选项 = 不限 + 设备刷新率档）。 */
@Composable
internal fun BandFpsDropdownRow(fps: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDropdownPreference(
            items = options,
            selectedIndex = fpsIndexOf(options, fps),
            title = "触发后限帧",
            enabled = enabled,
            onSelectedIndexChange = { idx -> onSelect(if (options[idx] == "不限") "0" else options[idx]) },
        )
        UiMode.Material -> SegmentedDropdownItem(
            title = "触发后限帧",
            items = options,
            selectedIndex = fpsIndexOf(options, fps),
            enabled = enabled,
            onItemSelected = { idx -> onSelect(if (options[idx] == "不限") "0" else options[idx]) },
        )
    }
}

/** 触发后限频下拉行（选项 = 设备 CPU 频率，MHz 显示 / Hz 存储）。 */
@Composable
internal fun BandFreqDropdownRow(freq: String, cpuFreqs: List<Long>, enabled: Boolean, onSelect: (String) -> Unit) {
    val labels = cpuFreqs.map { "${it / 1000} MHz" }
    val options = cpuFreqs.map { it.toString() }
    // 已有值不在设备表内（云控配置可能含设备未枚举频率）→ 并入保持可显示
    val labelOf: (String) -> String = { f -> f.toLongOrNull()?.let { "${it / 1000} MHz" } ?: f }
    val merged = if (options.contains(freq)) options else options + freq
    val mergedLabels = if (options.contains(freq)) labels else labels + labelOf(freq)
    val idx = merged.indexOf(freq).takeIf { it >= 0 } ?: 0
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDropdownPreference(
            items = mergedLabels,
            selectedIndex = idx,
            title = "触发后限频",
            enabled = enabled,
            onSelectedIndexChange = { i -> onSelect(merged[i]) },
        )
        UiMode.Material -> SegmentedDropdownItem(
            title = "触发后限频",
            items = mergedLabels,
            selectedIndex = idx,
            enabled = enabled,
            onItemSelected = { i -> onSelect(merged[i]) },
        )
    }
}

/** 频率串 → 显示 label：`1804800` → `1804 MHz`。 */
internal fun freqLabel(freq: String): String =
    freq.toLongOrNull()?.let { "${it / 1000} MHz" } ?: freq

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (format.hasKey) {
                            CurveFieldMiuixCompat(row.key, { row.key = it }, modifier = Modifier.weight(0.8f), label = format.keyLabel ?: "")
                        }
                        CurveFieldMiuixCompat(row.x, { row.x = it }, modifier = Modifier.weight(1f), label = format.xLabel)
                        CurveFieldMiuixCompat(row.y, { row.y = it }, modifier = Modifier.weight(1.4f), label = format.yLabel)
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String = "",
) {
    val modifier = modifier.padding(top = if (label.isNotEmpty()) 2.dp else 0.dp)
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
 * 目标帧率档 chips（多档曲线档首：dynamic_targetfps / _fan / by_battery / cpufreq）。
 * 档首必须与当前目标帧率完全相等才命中，用设备刷新率档预填 key 列。
 */
internal fun targetFpsBandChips(caps: JoyoseManager.DeviceCaps?): List<CurveChip> =
    caps?.refreshRates.orEmpty().map { CurveChip(label = "${it} fps 档", key = it.toString()) }

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
                                    row.key, { row.key = it }, modifier = Modifier.weight(1f),
                                    label = if (isGlk) "占位符(${CmdCodec.GLK_PLACEHOLDERS.joinToString("/")})" else "键",
                                )
                                CurveFieldMiuixCompat(row.value, { row.value = it }, modifier = Modifier.weight(1f), label = "值")
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

// ── Novatek 功能下拉行（原生组件，2026-09-02 v2：标题 + 右侧下拉，选中即生效）─

/** 插帧方案下拉行：选项 = 预制方案，选中即回调该方案（当前值命中则回显）。 */
@Composable
internal fun NovatekPresetDropdownRow(
    title: String,
    segment: NovatekCodec.Segment,
    enabled: Boolean,
    onPick: (NovatekCodec.FpsPreset) -> Unit,
) {
    val labels = NovatekCodec.FPS_PRESETS.map { it.label }
    val first = segment.levels.firstOrNull()
    val idx = NovatekCodec.FPS_PRESETS.indexOfFirst {
        first != null && it.dynamicFps == first.dynamicFps && it.targetFps == first.targetFps
    }.coerceAtLeast(0)
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDropdownPreference(
            items = labels,
            selectedIndex = idx,
            title = title,
            enabled = enabled,
            onSelectedIndexChange = { i -> NovatekCodec.FPS_PRESETS.getOrNull(i)?.let(onPick) },
        )
        UiMode.Material -> SegmentedDropdownItem(
            items = labels,
            selectedIndex = idx,
            title = title,
            enabled = enabled,
            onItemSelected = { i -> NovatekCodec.FPS_PRESETS.getOrNull(i)?.let(onPick) },
        )
    }
}

/** 温度档位下拉行：选项 = 原始/+10/+20/+30/+40℃（相对基线绝对值），选中即回调档位。 */
@Composable
internal fun NovatekTempDropdownRow(
    title: String,
    tier: Int,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val labels = NovatekCodec.TEMP_OFFSETS.map { NovatekCodec.tempLabel(it) }
    val idx = NovatekCodec.TEMP_OFFSETS.indexOfFirst { it == tier }.coerceAtLeast(0)
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDropdownPreference(
            items = labels,
            selectedIndex = idx,
            title = title,
            enabled = enabled,
            onSelectedIndexChange = { i -> NovatekCodec.TEMP_OFFSETS.getOrNull(i)?.let(onPick) },
        )
        UiMode.Material -> SegmentedDropdownItem(
            items = labels,
            selectedIndex = idx,
            title = title,
            enabled = enabled,
            onItemSelected = { i -> NovatekCodec.TEMP_OFFSETS.getOrNull(i)?.let(onPick) },
        )
    }
}
