package com.remoteconfig.override.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.ExpressiveSwitch
import com.remoteconfig.override.ui.component.material.SegmentedColumn
import com.remoteconfig.override.ui.component.material.SegmentedListItem
import com.remoteconfig.override.ui.component.material.expressiveTopAppBarColors
import com.remoteconfig.override.viewmodel.HyperOsSwitchCatalog
import com.remoteconfig.override.viewmodel.HyperOsViewModel

/**
 * HyperOS 通用配置页 — Material 3 实现（P2.2）。
 *
 * 脚手架对齐 [SettingsContentMaterial] / [ColorPaletteContentMaterial]：
 * `ExpressiveScaffold` + `LargeFlexibleTopAppBar`（返回按钮 + expressiveTopAppBarColors
 * + exitUntilCollapsed 滚动行为）。分组容器用 [SegmentedColumn]（组标题 = titleSmall +
 * primary，对齐 Material 设置页外观组的排版），组内开关行复刻 SegmentedList.kt 的
 * SegmentedSwitchItem 写法（整行点击切换 + [ExpressiveSwitch] trailing 共享
 * interactionSource），label 用 SemiBold、name 用 mono（bodySmall + Monospace，
 * 对齐 Type.kt 的 labelSmall 令牌思路）。
 *
 * 结构：云控状态卡（版本 + 冻结 pill + 冻结/解冻按钮 + 冻结语义小字）→ message/error
 * 提示条（primaryContainer / errorContainer，对齐 ConfigListMaterial 结果弹窗的成败配色）
 * → 按 [HyperOsSwitchCatalog.orderedGroups] 分组的开关行（writing 时禁用）。
 * loading（无数据可显示时整页 spinner）与空态（提示 + 重试）对齐 ConfigListMaterial。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperOsCommonConfigMaterial(
    state: HyperOsViewModel.CommonState,
    bottomInnerPadding: Dp = 0.dp,
    onToggle: (HyperOsViewModel.SwitchRow) -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onRetry: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current

    // 分组展示：SwitchRow.group 已由 ViewModel 按 HyperOsSwitchCatalog.group(name) 填好，
    // 这里只按 orderedGroups 的顺序取组内行（「其他」恒排最后，未知键不丢失）。
    val orderedGroups = remember(state.switches) {
        HyperOsSwitchCatalog.orderedGroups(state.switches.map { it.name })
    }
    val grouped = remember(state.switches, orderedGroups) {
        val byGroup = state.switches.groupBy { it.group }
        orderedGroups.associateWith { group -> byGroup[group].orEmpty() }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("HyperOS 通用配置") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                // spinner 仅在无数据可显示时出现（真正的首屏加载）
                state.loading && state.switches.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                // 空态：无开关可渲染（读不到 Joyose / 配置为空）——错误已由提示条/文案承载，给重试入口
                state.switches.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "暂无可配置的开关",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onRetry) { Text("重试") }
                        }
                    }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── 云控状态卡 ──
                    item(key = "status") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceBright,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "云控版本",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = state.version ?: "未知",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // 冻结状态 pill
                                    Surface(
                                        shape = CircleShape,
                                        color = if (state.frozen) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Text(
                                            text = if (state.frozen) "云控已冻结" else "未冻结",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (state.frozen) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = FREEZE_HINT,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = if (state.frozen) onUnfreeze else onFreeze,
                                    enabled = !state.writing,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(if (state.frozen) "解冻" else "冻结")
                                }
                            }
                        }
                    }

                    // ── 提示条：仅 error（成功以开关状态本身为反馈，不打断列表）──
                    val errorText = state.error
                    if (errorText != null) {
                        item(key = "error") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = "错误",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }

                    // ── 分组开关（SegmentedColumn 自带组标题排版：titleSmall + primary）──
                    grouped.forEach { (group, rows) ->
                        item(key = "group_$group") {
                            SegmentedColumn(
                                modifier = Modifier.fillMaxWidth(),
                                title = group,
                                content = rows.map { row -> { SwitchItemMaterial(row, !state.writing, onToggle) } },
                            )
                        }
                    }

                    // 底部留白：消费底栏占位高度（对齐 SettingsContentMaterial）。
                    item(key = "bottomSpacer") {
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

/**
 * 单行开关（Material）— 复刻 SegmentedList.kt `SegmentedSwitchItem` 的现成写法：
 * 整行点击切换（带 haptic）+ [ExpressiveSwitch] trailing 共享 interactionSource
 * （switch 本身 onCheckedChange 为 null，由行点击承担切换）。[enabled] = !writing。
 * label（headline，SemiBold）+ name（supporting，mono 小字）。
 */
@Composable
private fun SwitchItemMaterial(
    row: HyperOsViewModel.SwitchRow,
    enabled: Boolean,
    onToggle: (HyperOsViewModel.SwitchRow) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onToggle(row)
        },
        enabled = enabled,
        interactionSource = interactionSource,
        headlineContent = {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        },
        trailingContent = {
            ExpressiveSwitch(
                checked = row.value,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
    )
}
