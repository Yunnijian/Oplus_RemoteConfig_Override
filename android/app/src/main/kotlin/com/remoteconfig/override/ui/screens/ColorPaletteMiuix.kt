package com.remoteconfig.override.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.remoteconfig.override.R
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.theme.isExpandedWidth
import com.remoteconfig.override.ui.theme.keyColorOptions
import com.remoteconfig.override.ui.util.BlurredBar
import com.remoteconfig.override.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

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
 * 主题取色屏 — Miuix 实现（完整对齐 KernelSU `ColorPaletteScreenMiuix.kt` 分组结构）。
 *
 * 主题模式 TabRow → 第一组 Card（启用 Monet 颜色 / 强调色 / 色彩风格 / 色彩标准）
 * → 第二组 Card（模糊 / 悬浮底栏 / 液态玻璃 / 导航栏角标）
 * → 第三组 Card（预测性返回手势 / 界面缩放）。
 * 全部文案为 KernelSU values-zh-rCN 中文，硬编码。
 *
 * [showTopBar]：全屏路由 true（带返回按钮）；宽屏双窗右侧 pane 传 false（无顶栏返回按钮，
 * 由左侧设置列表选择控制）。无顶栏时内容自带 32dp 顶部间距，不被状态栏挤压。
 */
@Composable
fun ColorPaletteContentMiuix(
    state: ColorPaletteUiState,
    actions: ColorPaletteScreenActions,
    showTopBar: Boolean = true,
) {
    // pane 模式（showTopBar=false）无 TopAppBar：不创建 scrollBehavior（创建也没顶栏去
    // 折叠），更不能把它的 nestedScrollConnection 挂到 LazyColumn —— ExitUntilCollapsed
    // 的 onPreScroll 在无顶栏时 heightOffsetLimit 保持默认 -Float.MAX_VALUE，
    // 会上拉滚动无限吞掉，导致 pane 无法滚动（横屏双窗右侧滑不动）。
    val scrollBehavior = if (showTopBar) MiuixScrollBehavior() else null
    val enableBlurState = LocalEnableBlur.current
    // pane 模式（showTopBar=false）不创建自己的模糊背景层：外层 MainScreen 已对 Pager
    // 内容铺 layerBackdrop，嵌套 RenderEffect 层会导致重复采样/渲染伪影（对齐配置双窗行为）。
    val backdrop = rememberBlurBackdrop(if (showTopBar) enableBlurState else false)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val uiState = state
    val currentColorMode = state.currentColorMode
    val isDark = currentColorMode.isDark || currentColorMode.isSystem && isSystemInDarkTheme()
    val showScaleDialog = rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showTopBar) {
                BlurredBar(backdrop) {
                    TopAppBar(
                        color = barColor,
                        title = "主题设置",
                        navigationIcon = {
                            IconButton(
                                onClick = actions.onBack
                            ) {
                                val layoutDirection = LocalLayoutDirection.current
                                Icon(
                                    modifier = Modifier.graphicsLayer {
                                        if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                    },
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
        // popupHost 用默认 MiuixPopupHost()（不传 {}）：OverlayDropdownPreference 的
        // OverlayDialog 依赖 popupHost 渲染 dialogStates；本页是独立 NavDisplay entry
        // （父级 root dialog states 为 null），传 {} 会导致强调色下拉无法弹出（Bug 2）。
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    // 仅全屏路由（有 TopAppBar 可折叠）挂载 appbar 的 nestedScrollConnection；
                    // pane（无顶栏）挂上会把上拉滚动全部吞掉导致无法滚动。
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                    )
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    ThemePreviewCardMiuix(
                        keyColor = uiState.keyColor,
                        isDark = isDark,
                        miuixMonet = uiState.miuixMonet,
                        enableFloatingBottomBar = uiState.enableFloatingBottomBar,
                        enableFloatingBottomBarBlur = uiState.enableFloatingBottomBarBlur,
                        paletteStyle = state.currentPaletteStyle,
                        colorSpec = state.currentColorSpec,
                    )
                    Spacer(modifier = Modifier.height(72.dp))

                    val themeItems = listOf("跟随系统", "浅色", "深色")
                    TabRow(
                        tabs = themeItems,
                        selectedTabIndex = (if (uiState.themeMode >= 3) uiState.themeMode - 3 else uiState.themeMode).coerceIn(0, 2),
                        onTabSelected = { index ->
                            actions.onSetThemeMode(index)
                        },
                    )

                    // 第一组：主题
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = "启用 Monet 颜色",
                            startAction = {
                                Icon(
                                    Icons.Rounded.Wallpaper,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "启用 Monet 颜色",
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.miuixMonet,
                            onCheckedChange = {
                                actions.onSetMiuixMonet(it)
                            }
                        )

                        // 强调色始终显示（不包 AnimatedVisibility）：
                        // AnimatedVisibility 内的 OverlayDropdownPreference 弹出锚点异常，
                        // 下拉无法展开（Bug 2）。移出动画容器后始终可展开。
                        // 色彩风格/色彩标准（keyColor!=0 时）同理保持可见。
                        Column {
                            val colorItems = KeyColorNames
                            val colorValues = listOf(0) + keyColorOptions
                            OverlayDropdownPreference(
                                title = "强调色",
                                summary = "自定义应用的强调色",
                                items = colorItems,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Colorize,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "强调色",
                                        tint = colorScheme.onBackground
                                    )
                                },
                                selectedIndex = colorValues.indexOf(uiState.keyColor).takeIf { it >= 0 } ?: 0,
                                onSelectedIndexChange = { index ->
                                    actions.onSetKeyColor(colorValues[index])
                                }
                            )

                            AnimatedVisibility(
                                visible = uiState.keyColor != 0
                                ) {
                                    Column {
                                        val styles = PaletteStyle.entries
                                        OverlayDropdownPreference(
                                            title = "色彩风格",
                                            summary = "选择色彩风格",
                                            startAction = {
                                                Icon(
                                                    Icons.Rounded.Style,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    contentDescription = "色彩风格",
                                                    tint = colorScheme.onBackground
                                                )
                                            },
                                            items = styles.map { it.name },
                                            selectedIndex = styles.indexOfFirst { it.name == uiState.colorStyle }.coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                actions.onSetColorStyle(styles[index].name)
                                            }
                                        )

                                        val specs = ColorSpec.SpecVersion.entries
                                        OverlayDropdownPreference(
                                            title = "色彩标准",
                                            summary = "选择色彩标准",
                                            startAction = {
                                                Icon(
                                                    Icons.Rounded.DesignServices,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    contentDescription = "色彩标准",
                                                    tint = colorScheme.onBackground
                                                )
                                            },
                                            items = specs.map { it.name },
                                            selectedIndex = specs.indexOfFirst { it.name == uiState.colorSpec }.coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                actions.onSetColorSpec(specs[index].name)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                    // 第二组：效果
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SwitchPreference(
                                title = "模糊",
                                summary = "启用顶栏和底栏的模糊效果",
                                startAction = {
                                    Icon(
                                        Icons.Rounded.BlurOn,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "模糊",
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.enableBlur,
                                onCheckedChange = {
                                    actions.onSetEnableBlur(it)
                                }
                            )
                        }
                        SwitchPreference(
                            title = "悬浮底栏",
                            summary = "使用 Apple 风格的悬浮底栏",
                            startAction = {
                                Icon(
                                    Icons.Rounded.CallToAction,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "悬浮底栏",
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.enableFloatingBottomBar,
                            onCheckedChange = {
                                actions.onSetEnableFloatingBottomBar(it)
                            }
                        )
                        AnimatedVisibility(visible = uiState.enableFloatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SwitchPreference(
                                title = "液态玻璃",
                                summary = "启用悬浮底栏的液态玻璃效果",
                                startAction = {
                                    Icon(
                                        Icons.Rounded.WaterDrop,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "液态玻璃",
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.enableFloatingBottomBarBlur,
                                onCheckedChange = {
                                    actions.onSetEnableFloatingBottomBarBlur(it)
                                }
                            )
                        }
                    }

                    // 第三组：其他
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            SwitchPreference(
                                title = "预测性返回手势",
                                summary = "启用对预测性返回手势的支持",
                                startAction = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.MenuOpen,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "预测性返回手势",
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.enablePredictiveBack,
                                onCheckedChange = {
                                    actions.onSetEnablePredictiveBack(it)
                                }
                            )
                        }

                        var sliderValue by remember(uiState.pageScale) { mutableFloatStateOf(uiState.pageScale) }
                        ArrowPreference(
                            title = "界面缩放",
                            summary = "调整全局显示比例",
                            startAction = {
                                Icon(
                                    Icons.Rounded.AspectRatio,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "界面缩放",
                                    tint = colorScheme.onBackground
                                )
                            },
                            endActions = {
                                Text(
                                    text = "${(sliderValue * 100).toInt()}%",
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { showScaleDialog.value = !showScaleDialog.value },
                            holdDownState = showScaleDialog.value,
                            bottomAction = {
                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        sliderValue = it
                                    },
                                    onValueChangeFinished = {
                                        actions.onSetPageScale(sliderValue)
                                    },
                                    valueRange = 0.8f..1.1f,
                                    showKeyPoints = true,
                                    keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                                    magnetThreshold = 0.01f,
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                        )
                        ScaleDialog(
                            show = showScaleDialog.value,
                            onDismissRequest = { showScaleDialog.value = false },
                            volumeState = { uiState.pageScale },
                            onVolumeChange = {
                                actions.onSetPageScale(it)
                            }
                        )
                    }
                }
                item {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                                    12.dp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 界面缩放对话框 — 对齐 KernelSU `ScaleDialog.kt`（用本项目已验证的 [WindowDialog]）。
 * 点击「界面缩放」行展开，支持键盘输入 80% - 110% 精确数值。
 */
@Composable
private fun ScaleDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    volumeState: () -> Float,
    onVolumeChange: (Float) -> Unit,
) {
    WindowDialog(
        show = show,
        title = "界面缩放",
        summary = "80% - 110%",
        onDismissRequest = onDismissRequest,
        content = {
            var text by remember(show) {
                mutableStateOf((volumeState() * 100).toInt().toString())
            }
            top.yukonga.miuix.kmp.basic.TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = {
                    Text(
                        text = "%",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colorScheme.onSurfaceVariantActions,
                    )
                },
                onValueChange = { newValue ->
                    if (newValue.isEmpty()) {
                        text = ""
                    } else {
                        val valid = newValue.all { it.isDigit() }
                        if (valid) {
                            text = newValue
                        }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val parsed = text.toIntOrNull()
                        val clamped = parsed?.coerceIn(80, 110) ?: (volumeState() * 100).toInt()
                        onVolumeChange(clamped / 100f)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )
}

/**
 * 主题预览卡 — 对齐 KernelSU `ThemePreviewCardMiuix`。
 * keyColor 实时预览：Monet 开启时用 materialkolor 动态色；否则回落到当前 Miuix 主题色。
 * 宽屏（[isExpandedWidth]）预览左侧导航 rail，窄屏预览底部导航条（悬浮底栏开启时预览悬浮样式）。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCardMiuix(
    keyColor: Int,
    isDark: Boolean,
    miuixMonet: Boolean,
    enableFloatingBottomBar: Boolean = false,
    enableFloatingBottomBarBlur: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight
    val useRail = isExpandedWidth()

    val seedColor = if (keyColor == 0) colorScheme.primary else Color(keyColor)
    val effectiveStyle = if (keyColor == 0) PaletteStyle.TonalSpot else paletteStyle
    val effectiveSpec = if (keyColor == 0) ColorSpec.SpecVersion.Default else colorSpec
    val dynamicCs = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = effectiveStyle,
        specVersion = effectiveSpec,
    )

    val bgColor = if (miuixMonet) dynamicCs.background else colorScheme.surface
    val textColor = if (miuixMonet) dynamicCs.onSurface else colorScheme.onBackground
    val accentCardColor = when {
        miuixMonet -> dynamicCs.secondaryContainer
        isDark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val cardColor = if (miuixMonet) dynamicCs.surfaceContainerHighest else colorScheme.surfaceVariant
    val navBarColor = if (miuixMonet) dynamicCs.surfaceContainer else colorScheme.surface
    val iconColor = if (miuixMonet) dynamicCs.primary else colorScheme.primary
    val navSelectedColor = colorScheme.onSurfaceContainer
    val navUnselectedColor = colorScheme.onSurfaceContainer.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            val content = @Composable {
                Column {
                    Row(
                        modifier = Modifier
                            .height(if (useRail) 36.dp else 48.dp)
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = if (useRail) 12.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontSize = 12.sp,
                            color = textColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentCardColor)
                    )

                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val smallCardHeight = 12.dp
                        val smallCardCount = when {
                            maxHeight >= 96.dp -> 2
                            maxHeight >= 72.dp -> 1
                            else -> 0
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cardColor)
                            )
                            repeat(smallCardCount) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(smallCardHeight)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cardColor)
                                )
                            }
                        }
                    }
                }
            }

            if (useRail) {
                Row {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(30.dp)
                            .background(navBarColor),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Box(modifier = Modifier.weight(1f)) { content() }
                }
            } else {
                content()
            }

            if (!useRail && enableFloatingBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enableFloatingBottomBarBlur) navBarColor.copy(alpha = 0.5f)
                                else navBarColor
                            )
                            .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (it == 0) iconColor else textColor)
                            )
                        }
                    }
                }
            } else if (!useRail) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navBarColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
