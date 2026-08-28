package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 主题取色屏 — Miuix 实现。
 *
 * 对齐 KernelSU `ColorPaletteScreenMiuix.kt` 结构：
 * `Scaffold + TopAppBar(返回键) + Card 分组`。四组内容：
 * 预设强调色卡（首项「跟随默认」keyColor=0，选中描边高亮）/ 调色板风格 /
 * 颜色规范 / 恢复默认。所有写入即时调 [com.remoteconfig.override.settings.SettingsRepositoryImpl] setter，主题即时响应。
 */
@Composable
fun ColorPaletteContentMiuix(onBack: () -> Unit) {
    // R1：数据源换为 KernelSU 风格 SettingsRepository（取色页 UI 不重写，R4 再做）
    val repo = remember { SettingsRepositoryImpl() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = "主题取色",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // ── 预设强调色卡组 ──
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Text(
                    text = "预设强调色",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceSecondary,
                )
                PresetColorGridMiuix(modifier = Modifier.padding(top = 12.dp))
            }

            // ── 调色板风格 ──
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                val styles = PaletteStyle.entries
                OverlayDropdownPreference(
                    title = "调色板风格",
                    summary = paletteStyleLabel(repo.colorStyle),
                    items = styles.map { paletteStyleLabel(it.name) },
                    selectedIndex = styles
                        .indexOfFirst { it.name == repo.colorStyle }
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        repo.colorStyle = styles[index].name
                    },
                )
            }

            // ── 颜色规范 ──
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                val specs = ColorSpec.SpecVersion.entries.filter { it.name != "Default" }
                OverlayDropdownPreference(
                    title = "颜色规范",
                    summary = colorSpecLabel(repo.colorSpec),
                    items = specs.map { colorSpecLabel(it.name) },
                    selectedIndex = specs
                        .indexOfFirst { it.name == repo.colorSpec }
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        repo.colorSpec = specs[index].name
                    },
                )
            }

            // ── 恢复默认 ──
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "一键恢复默认强调色、调色板风格与颜色规范",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceSecondary,
                    )
                    TextButton(
                        text = "恢复默认取色",
                        onClick = {
                            repo.keyColor = 0
                            repo.colorStyle = "TonalSpot"
                            repo.colorSpec = "SPEC_2025"
                        },
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

/** 强调色卡组：首项「跟随默认」(keyColor=0) + 预设色板，FlowRow 换行排布。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetColorGridMiuix(modifier: Modifier = Modifier) {
    val repo = remember { SettingsRepositoryImpl() }
    val currentKeyColor = repo.keyColor
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyColorSwatchMiuix(
            keyColor = 0,
            selected = currentKeyColor == 0,
            onClick = { repo.keyColor = 0 },
        )
        PresetKeyColors.forEach { color ->
            KeyColorSwatchMiuix(
                keyColor = color,
                selected = currentKeyColor == color,
                onClick = { repo.keyColor = color },
            )
        }
    }
}

/** 单个 40dp 圆形强调色卡；选中项用 primary 描边高亮；「跟随默认」显示当前主题强调色 + 勾选图标。 */
@Composable
private fun KeyColorSwatchMiuix(
    keyColor: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = if (keyColor == 0) colorScheme.primary else Color(keyColor)
    val borderColor = if (selected) {
        if (keyColor == 0) colorScheme.onPrimary else colorScheme.primary
    } else {
        colorScheme.outline.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (keyColor == 0) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "跟随默认",
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
