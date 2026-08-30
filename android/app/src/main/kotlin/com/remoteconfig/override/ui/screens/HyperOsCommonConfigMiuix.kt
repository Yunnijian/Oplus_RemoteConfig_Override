package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.util.BlurredBar
import com.remoteconfig.override.ui.util.rememberBlurBackdrop
import com.remoteconfig.override.viewmodel.HyperOsSwitchCatalog
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * HyperOS 通用配置页 — Miuix 实现（P2.2）。
 *
 * 脚手架对齐 [SettingsContentMiuix]：`Scaffold` 顶栏用 [BlurredBar]（backdrop 由
 * [rememberBlurBackdrop] 按 `LocalEnableBlur` 惰性创建）+ `MiuixScrollBehavior`；内容
 * `LazyColumn` 用 `scrollEndHaptic() + overScrollVertical() + nestedScroll + overscrollEffect = null`，
 * 分组 `Card`（组标题在 Card 外、Card 内排开关行）。返回按钮对齐 [ColorPaletteContentMiuix]
 * （`MiuixIcons.Back` + RTL 翻转）。
 *
 * 结构：云控状态卡（版本 + 冻结 pill + 冻结/解冻按钮 + 冻结语义小字）→ message/error 提示条
 * → 按 [HyperOsSwitchCatalog.orderedGroups] 分组的开关行（label 粗体 + name mono 小字 +
 * [Switch]，writing 时禁用）。loading（无数据可显示时整页 spinner）与空态（提示 + 重试）
 * 对齐 ConfigListMiuix 的 when 分支模式。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HyperOsCommonConfigMiuix(
    state: HyperOsViewModel.CommonState,
    bottomInnerPadding: Dp = 0.dp,
    onToggle: (HyperOsViewModel.SwitchRow) -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onRetry: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
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

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "HyperOS 通用配置",
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        // popupHost 用默认 MiuixPopupHost()（不传 {}，对齐 SettingsContentMiuix / ColorPaletteContentMiuix）。
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            when {
                // spinner 仅在无数据可显示时出现（真正的首屏加载）
                state.loading && state.switches.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                // 空态：无开关可渲染（读不到 Joyose / 配置为空）——错误已由提示条/文案承载，给重试入口
                state.switches.isEmpty() ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "暂无可配置的开关",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                text = "重试",
                                onClick = onRetry,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = 12.dp),
                    contentPadding = innerPadding,
                    overscrollEffect = null,
                ) {
                    // ── 云控状态卡：冻结状态即开关（SwitchPreference 原生行），版本号入 summary ──
                    item(key = "status") {
                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            SwitchPreference(
                                title = "冻结 Joyose 云控",
                                summary = "云控版本 ${state.version ?: "未知"} · " +
                                    if (state.frozen) "已冻结（云端不会覆盖）" else "未冻结（云端会周期性覆盖）",
                                checked = state.frozen,
                                enabled = !state.writing,
                                onCheckedChange = { frozen ->
                                    if (frozen) onFreeze() else onUnfreeze()
                                },
                            )
                            Text(
                                text = FREEZE_HINT,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }

                    // ── 提示条：仅 error（成功以开关状态本身为反馈，不打断列表）──
                    val errorText = state.error
                    if (errorText != null) {
                        item(key = "error") {
                            Card(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(),
                                colors = CardDefaults.defaultColors(color = colorScheme.errorContainer),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
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
                                        text = errorText,
                                        fontSize = 14.sp,
                                        color = colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }

                    // ── 分组开关 ──
                    grouped.forEach { (group, rows) ->
                        item(key = "header_$group") {
                            Text(
                                text = group,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
                            )
                        }
                        item(key = "group_$group") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                rows.forEach { row ->
                                    SwitchPreference(
                                        title = row.label,
                                        summary = row.name,
                                        checked = row.value,
                                        enabled = !state.writing,
                                        onCheckedChange = { onToggle(row) },
                                    )
                                }
                            }
                        }
                    }

                    // 底部留白：消费底栏（悬浮液态玻璃底栏 / 系统导航栏）占位高度，
                    // 否则最后的开关行会被底栏永久遮挡（对齐 SettingsContentMiuix）。
                    item(key = "bottomSpacer") {
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

