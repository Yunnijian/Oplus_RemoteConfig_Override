package com.remoteconfig.override.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.remoteconfig.override.R
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.ExpressiveToggleButton
import com.remoteconfig.override.ui.component.material.SegmentedColumn
import com.remoteconfig.override.ui.component.material.SegmentedDropdownItem
import com.remoteconfig.override.ui.component.material.SegmentedSwitchItem
import com.remoteconfig.override.ui.component.material.expressiveTopAppBarColors
import com.remoteconfig.override.ui.theme.RemoteConfigSchemeCache
import com.remoteconfig.override.ui.theme.isExpandedWidth
import com.remoteconfig.override.ui.theme.keyColorOptions
import com.remoteconfig.override.ui.theme.rememberRemoteConfigColorScheme
import kotlinx.coroutines.awaitAll

/**
 * 主题取色屏 — Material 3 实现（组件对齐 KernelSU
 * `ColorPaletteScreenMaterial.kt`：SegmentedColumn / SegmentedDropdownItem /
 * SegmentedSwitchItem / ExpressiveSwitch）。
 *
 * 主题模式分段按钮 → 第一组（色彩风格 / 色彩标准）
 * → 第二组（预测性返回手势）→ 界面缩放。
 * （对齐 KernelSU Material 取色屏：不含 Monet/模糊/悬浮底栏/液态玻璃等
 * Material 模式不生效的开关。）
 *
 * [showTopBar]：全屏路由 true（带返回按钮）；宽屏双窗右侧 pane 传 false（无顶栏返回按钮，
 * 由左侧设置列表选择控制）。无顶栏时 contentWindowInsets 去掉 Top（左侧列表顶栏已消费状态栏），
 * 顶部补 16dp 间距对齐左列内容起点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteContentMaterial(
    state: ColorPaletteUiState,
    actions: ColorPaletteScreenActions,
    showTopBar: Boolean = true,
) {
    // 仅全屏路由（有顶栏）才需要折叠滚动行为：宽屏双窗右侧 pane 无顶栏时，
    // ExitUntilCollapsed 的 heightOffsetLimit 保持 -Float.MAX_VALUE，其 onPreScroll 会吞掉
    // 全部上滑滚动，导致主题窗格无法滚动（对齐 Miuix 版的条件挂载修复）。
    val scrollBehavior = if (showTopBar) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    } else {
        null
    }
    val uiState = state
    val currentColorMode = state.currentColorMode
    val currentKeyColor = uiState.keyColor
    val colorStyle = state.currentPaletteStyle
    val colorSpec = state.currentColorSpec
    val haptic = LocalHapticFeedback.current

    ExpressiveScaffold(
        topBar = {
            if (showTopBar) {
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
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            if (showTopBar) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            else WindowInsetsSides.Horizontal
        ),
    ) { paddingValues ->
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!showTopBar) {
                Spacer(modifier = Modifier.height(16.dp))
            }
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

            // 强调色色块选择（对齐 KernelSU v3.3.0 ColorPaletteScreenMaterial：LazyRow
            // ColorButtonMaterial，首项「默认」= keyColor 0 = 动态取色）。
            //
            // 根因修复（主线程堆栈实证）：Spec2025 单次取色 ≈ 56ms（HctSolver 二分求解），
            // 16 个按钮逐个在组合期同步执行 ≈ 900ms 主线程冻结（转场因此过期消失）。
            // 方案：17 套方案由根主题 PrewarmRemoteConfigSchemes 在进页前后台并行预取
            // 进进程缓存（RemoteConfigSchemeCache），组合期仅读缓存 → 首帧零取色、
            // 色块随页面内容一并呈现；未命中的参数组合才在页内并行补齐。
            val context = LocalContext.current
            val defaultSeed = remember(isDark) {
                // 「默认」= 系统动态取色的 primary 作为种子（与 rememberRemoteConfigColorScheme 相同解析）
                (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
            }
            val seeds = remember(defaultSeed) { listOf(defaultSeed) + keyColorOptions.map { Color(it) } }
            // 首值直接读预热缓存（根主题 PrewarmRemoteConfigSchemes 已按当前
            // 深色/AMOLED/风格/标准 预取整套）→ 命中时色块随页面首帧呈现，无空白占位；
            // 个别未命中（中途切风格/标准/深色）才走后台并行补齐，仍与预取去重。
            val buttonSchemes by produceState<List<ColorScheme?>>(
                initialValue = seeds.map {
                    RemoteConfigSchemeCache.peek(it, isDark, isAmoled, colorStyle, colorSpec)
                },
                seeds, isDark, isAmoled, colorStyle, colorSpec,
            ) {
                if (value.any { it == null }) {
                    value = seeds.map {
                        RemoteConfigSchemeCache.prefetch(it, isDark, isAmoled, colorStyle, colorSpec)
                    }.awaitAll()
                }
            }
            val schemesReady = buttonSchemes.size == seeds.size && buttonSchemes.none { it == null }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                if (schemesReady) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            val scheme = buttonSchemes[0] ?: return@item
                            ColorButtonMaterial(
                                colorScheme = scheme,
                                isSelected = currentKeyColor == 0,
                                onClick = { actions.onSetKeyColor(0) },
                            )
                        }

                        itemsIndexed(keyColorOptions) { index, colorArgb ->
                            val scheme = buttonSchemes[index + 1] ?: return@itemsIndexed
                            ColorButtonMaterial(
                                colorScheme = scheme,
                                isSelected = currentKeyColor == colorArgb,
                                onClick = { actions.onSetKeyColor(colorArgb) },
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 主题模式（跟随系统/浅色/深色/AMOLED 深色）— 对齐 KernelSU v3.3.0
                // ExpressiveToggleButton ButtonGroup 四态（原 SegmentedButton 三态缺 AMOLED 入口）
                val themeModeOptions = listOf(
                    listOf(ColorMode.SYSTEM) to Icons.Filled.Brightness4,
                    listOf(ColorMode.LIGHT) to Icons.Filled.Brightness7,
                    listOf(ColorMode.DARK) to Icons.Filled.Brightness3,
                    listOf(ColorMode.DARK_AMOLED) to Icons.Filled.Brightness1,
                )
                themeModeOptions.chunked(4).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        rowOptions.forEachIndexed { index, (modes, icon) ->
                            ExpressiveToggleButton(
                                checked = currentColorMode in modes,
                                onCheckedChange = {
                                    if (it) {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        actions.onSetThemeMode(modes.first().value)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    rowOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = when (modes.first()) {
                                        ColorMode.SYSTEM -> "跟随系统"
                                        ColorMode.LIGHT -> "浅色"
                                        ColorMode.DARK -> "深色"
                                        ColorMode.DARK_AMOLED -> "深色 AMOLED"
                                        else -> "跟随系统"
                                    }
                                )
                            }
                        }
                    }
                }

                // 第一组：色彩风格/标准（对齐 KernelSU Material 取色屏，无 Monet/效果开关）。
                // 仅在有强调色时展示（keyColor==0 且 Monet 开启时走系统动态色，两者不参与取色）。
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = {
                        item(visible = uiState.keyColor != 0) {
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
                        item(visible = uiState.keyColor != 0) {
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

                // 第二组：其他
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = {
                        item(visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            SegmentedSwitchItem(
                                icon = Icons.AutoMirrored.Rounded.MenuOpen,
                                title = "预测性返回手势",
                                // 反射写入 ApplicationInfo 只在进程启动时被框架读取：重启应用后生效。
                                summary = "启用对预测性返回手势的支持（重启应用后生效）",
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

/**
 * 强调色色块按钮 — 对齐 KernelSU v3.3.0 `ColorPaletteScreenMaterial.ColorButtonMaterial`：
 * 72dp 圆角方块内以该强调色生成的主题绘制上半/下半双弧预览，选中态放大 + 描边圆环。
 * 取色方案由调用方批量预计算传入（[buttonSchemes]），本组件组合期零取色计算。
 */
@Composable
private fun ColorButtonMaterial(
    colorScheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = colorScheme.primaryContainer,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true
                )
                drawArc(
                    color = colorScheme.tertiaryContainer,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true
                )
            }

            val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f)
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(16.dp)
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}
