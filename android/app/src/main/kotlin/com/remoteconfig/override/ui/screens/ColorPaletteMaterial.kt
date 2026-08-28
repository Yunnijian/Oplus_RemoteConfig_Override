package com.remoteconfig.override.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.R
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.SegmentedColumn
import com.remoteconfig.override.ui.component.material.SegmentedDropdownItem
import com.remoteconfig.override.ui.component.material.SegmentedSwitchItem
import com.remoteconfig.override.ui.component.material.expressiveTopAppBarColors
import com.remoteconfig.override.ui.theme.isExpandedWidth
import com.remoteconfig.override.ui.theme.keyColorOptions
import com.remoteconfig.override.ui.theme.rememberRemoteConfigColorScheme

/**
 * 强调色下拉预设名 — 完整对齐 KernelSU `ColorPaletteScreenMiuix.kt` 的 colorItems
 * （strings.xml 中文文案硬编码，顺序与 keyColorOptions 一一对应；首项「默认」= keyColor 0）。
 */
private val KeyColorNames: List<String> = listOf(
    "默认",
    "红色", "粉色", "紫色", "深紫", "靛青", "蓝色", "青色", "青绿",
    "绿色", "黄色", "琥珀", "橙色", "棕色", "灰蓝", "樱花",
)

/**
 * 主题取色屏 — Material 3 实现（与 Miuix 版同三组，组件对齐 KernelSU
 * `ColorPaletteScreenMaterial.kt`：SegmentedColumn / SegmentedDropdownItem /
 * SegmentedSwitchItem / ExpressiveSwitch）。
 *
 * 主题模式分段按钮 → 第一组（启用 Monet 颜色 / 强调色 / 色彩风格 / 色彩标准）
 * → 第二组（模糊 / 悬浮底栏 / 液态玻璃 / 导航栏角标）
 * → 第三组（预测性返回手势 / 界面缩放）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteContentMaterial(
    state: ColorPaletteUiState,
    actions: ColorPaletteScreenActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val uiState = state
    val currentColorMode = state.currentColorMode
    val currentKeyColor = uiState.keyColor
    val colorStyle = state.currentPaletteStyle
    val colorSpec = state.currentColorSpec
    val haptic = LocalHapticFeedback.current

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("主题设置") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isDark = currentColorMode.isDark || currentColorMode.isSystem && isSystemInDarkTheme()
            val isAmoled = currentColorMode.isAmoled
            ThemePreviewCard(
                keyColor = currentKeyColor,
                isDark = isDark,
                isAmoled = isAmoled,
                paletteStyle = colorStyle,
                colorSpec = colorSpec,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 主题模式（跟随系统/浅色/深色）
                val themeModes = listOf(
                    Triple(0, "跟随系统", Icons.Filled.Brightness4),
                    Triple(1, "浅色", Icons.Filled.Brightness7),
                    Triple(2, "深色", Icons.Filled.Brightness3),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeModes.forEachIndexed { index, (mode, label, icon) ->
                        SegmentedButton(
                            selected = (if (uiState.themeMode >= 3) uiState.themeMode - 3 else uiState.themeMode) == mode,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                actions.onSetThemeMode(mode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                            )
                        }
                    }
                }

                // 第一组：主题
                val colorValues = listOf(0) + keyColorOptions
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = {
                        item {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.Wallpaper,
                                title = "启用 Monet 颜色",
                                checked = uiState.miuixMonet,
                                onCheckedChange = actions.onSetMiuixMonet,
                            )
                        }
                        item(visible = uiState.miuixMonet) {
                            SegmentedDropdownItem(
                                icon = Icons.Rounded.Colorize,
                                title = "强调色",
                                items = KeyColorNames,
                                selectedIndex = colorValues.indexOf(uiState.keyColor).takeIf { it >= 0 } ?: 0,
                                onItemSelected = { index ->
                                    actions.onSetKeyColor(colorValues[index])
                                },
                            )
                        }
                        item(visible = uiState.miuixMonet && uiState.keyColor != 0) {
                            val styles = PaletteStyle.entries
                            SegmentedDropdownItem(
                                icon = Icons.Rounded.Style,
                                title = "色彩风格",
                                items = styles.map { it.name },
                                selectedIndex = styles.indexOfFirst { it.name == uiState.colorStyle }.coerceAtLeast(0),
                                onItemSelected = { index ->
                                    actions.onSetColorStyle(styles[index].name)
                                },
                            )
                        }
                        item(visible = uiState.miuixMonet && uiState.keyColor != 0) {
                            val specs = ColorSpec.SpecVersion.entries
                            SegmentedDropdownItem(
                                icon = Icons.Rounded.DesignServices,
                                title = "色彩标准",
                                items = specs.map { it.name },
                                selectedIndex = specs.indexOfFirst { it.name == uiState.colorSpec }.coerceAtLeast(0),
                                onItemSelected = { index ->
                                    actions.onSetColorSpec(specs[index].name)
                                },
                            )
                        }
                    }
                )

                // 第二组：效果
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = {
                        item(visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.BlurOn,
                                title = "模糊",
                                summary = "启用顶栏和底栏的模糊效果",
                                checked = uiState.enableBlur,
                                onCheckedChange = actions.onSetEnableBlur,
                            )
                        }
                        item {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.CallToAction,
                                title = "悬浮底栏",
                                summary = "使用 Apple 风格的悬浮底栏",
                                checked = uiState.enableFloatingBottomBar,
                                onCheckedChange = actions.onSetEnableFloatingBottomBar,
                            )
                        }
                        item(visible = uiState.enableFloatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.WaterDrop,
                                title = "液态玻璃",
                                summary = "启用悬浮底栏的液态玻璃效果",
                                checked = uiState.enableFloatingBottomBarBlur,
                                onCheckedChange = actions.onSetEnableFloatingBottomBarBlur,
                            )
                        }
                        item {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.Pin,
                                title = "导航栏角标",
                                summary = "在导航栏显示已授权应用和已启用模块数量",
                                checked = uiState.enableNavigationBadge,
                                onCheckedChange = actions.onSetEnableNavigationBadge,
                            )
                        }
                    }
                )

                // 第三组：其他
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = {
                        item(visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            SegmentedSwitchItem(
                                icon = Icons.AutoMirrored.Rounded.MenuOpen,
                                title = "预测性返回手势",
                                summary = "启用对预测性返回手势的支持",
                                checked = uiState.enablePredictiveBack,
                                onCheckedChange = actions.onSetEnablePredictiveBack,
                            )
                        }
                    }
                )

                // 界面缩放（TonalCard 等价 Surface + Slider）
                Surface(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    var sliderValue by remember(uiState.pageScale) { mutableFloatStateOf(uiState.pageScale) }
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.AspectRatio,
                                contentDescription = "界面缩放",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "界面缩放",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "调整全局显示比例",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${(sliderValue * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { actions.onSetPageScale(sliderValue) },
                            valueRange = 0.8f..1.1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp + navBars.calculateBottomPadding() + captionBar.calculateBottomPadding()))
        }
    }
}

/**
 * 主题预览卡 — 对齐 KernelSU Material 版 `ThemePreviewCard`（TonalCard 用 Surface 等价实现）。
 * keyColor 实时预览：seed 未指定（keyColor=0）时取系统动态色 primary。
 * 宽屏（[isExpandedWidth]）预览左侧导航 rail，窄屏预览底部导航条。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    isAmoled: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight
    val useRail = isExpandedWidth()

    val colorScheme = rememberRemoteConfigColorScheme(
        seedColor = if (keyColor == 0) Color.Unspecified else Color(keyColor),
        isDark = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio),
            color = colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, color = colorScheme.outlineVariant)
        ) {
            val content: @Composable ColumnScope.() -> Unit = {
                // 顶部标题栏
                Box(
                    modifier = Modifier
                        .height(if (useRail) 36.dp else 48.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, top = if (useRail) 8.dp else 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val showInfoCard = maxHeight >= 72.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { }
                        if (showInfoCard) {
                            Surface(
                                color = colorScheme.surfaceBright,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(8.dp),
                            ) { }
                        }
                    }
                }
            }

            if (useRail) {
                Row {
                    Surface(
                        color = colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Home, null, tint = colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) { content() }
                }
            } else {
                Column {
                    content()

                    // 底部导航条
                    Surface(
                        color = colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Home, null, tint = colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
