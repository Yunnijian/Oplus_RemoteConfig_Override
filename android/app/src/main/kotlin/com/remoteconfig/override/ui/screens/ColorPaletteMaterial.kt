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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.settings.AppSettingsRepository

/**
 * 主题取色屏 — Material 3 实现。
 *
 * 与 [ColorPaletteContentMiuix] 行为一致、读同一 [AppSettingsRepository]：
 * `Scaffold + LargeTopAppBar(返回键) + Column(verticalScroll) + ElevatedCard`。
 * 四组内容：预设强调色卡（跟随默认 + 色板，选中描边）/ 调色板风格（ExposedDropdownMenuBox）/
 * 颜色规范（ExposedDropdownMenuBox）/ 恢复默认。写入即生效，主题即时响应。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteContentMaterial(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("主题取色") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // ── 预设强调色卡组 ──
            ElevatedCard(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("预设强调色", style = MaterialTheme.typography.titleMedium)
                    PresetColorGridMaterial(modifier = Modifier.padding(top = 12.dp))
                }
            }

            // ── 调色板风格 ──
            ElevatedCard(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val styles = PaletteStyle.entries
                    PaletteStyleDropdown(
                        items = styles.map { paletteStyleLabel(it.name) },
                        selectedIndex = styles
                            .indexOfFirst { it.name == AppSettingsRepository.paletteStyle }
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            AppSettingsRepository.setPaletteStyle(styles[index].name)
                        },
                    )
                }
            }

            // ── 颜色规范 ──
            ElevatedCard(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val specs = ColorSpec.SpecVersion.entries.filter { it.name != "Default" }
                    ColorSpecDropdown(
                        items = specs.map { colorSpecLabel(it.name) },
                        selectedIndex = specs
                            .indexOfFirst { it.name == AppSettingsRepository.colorSpec }
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            AppSettingsRepository.setColorSpec(specs[index].name)
                        },
                    )
                }
            }

            // ── 恢复默认 ──
            ElevatedCard(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "一键恢复默认强调色、调色板风格与颜色规范",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            AppSettingsRepository.setKeyColor(0)
                            AppSettingsRepository.setPaletteStyle("TonalSpot")
                            AppSettingsRepository.setColorSpec("SPEC_2025")
                        },
                    ) {
                        Text("恢复默认取色")
                    }
                }
            }
        }
    }
}

/** 强调色卡组：首项「跟随默认」(keyColor=0) + 预设色板，FlowRow 换行排布。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetColorGridMaterial(modifier: Modifier = Modifier) {
    val currentKeyColor = AppSettingsRepository.keyColor
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyColorSwatchMaterial(
            keyColor = 0,
            selected = currentKeyColor == 0,
            onClick = { AppSettingsRepository.setKeyColor(0) },
        )
        PresetKeyColors.forEach { color ->
            KeyColorSwatchMaterial(
                keyColor = color,
                selected = currentKeyColor == color,
                onClick = { AppSettingsRepository.setKeyColor(color) },
            )
        }
    }
}

/** 单个 40dp 圆形强调色卡；选中项用 primary 描边高亮；「跟随默认」显示当前主题强调色 + 勾选图标。 */
@Composable
private fun KeyColorSwatchMaterial(
    keyColor: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = if (keyColor == 0) MaterialTheme.colorScheme.primary else Color(keyColor)
    val borderColor = if (selected) {
        if (keyColor == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
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
                imageVector = Icons.Filled.Check,
                contentDescription = "跟随默认",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 调色板风格下拉（ExposedDropdownMenuBox + 只读 OutlinedTextField）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteStyleDropdown(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    StyleDropdown(
        label = "调色板风格",
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/** 颜色规范下拉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSpecDropdown(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    StyleDropdown(
        label = "颜色规范",
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/** 通用 ExposedDropdownMenuBox 只读下拉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleDropdown(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = items.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        expanded = false
                        onSelectedIndexChange(index)
                    },
                )
            }
        }
    }
}
