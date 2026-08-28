// Liquid-glass bottom bar, ported from the AndroidLiquidGlass catalog sample
// (com.kyant.backdrop.catalog.components.LiquidBottomTabs / LiquidBottomTab,
// https://github.com/Kyant0/AndroidLiquidGlass — Apache 2.0), with a shader-free fallback
// branch modeled after KernelSU's FloatingBottomBar (isBlurEnabled == false path).
//
// Performance spec (规格 9):
//  - every animation value is read ONLY inside graphicsLayer{} / drawBackdrop's
//    effects/layerBlock/onDrawSurface/highlight/shadow/innerShadow lambdas (draw phase);
//  - all Animatable instances carry visibilityThreshold;
//  - selection propagation uses snapshotFlow + collectLatest;
//  - panelOffset is cached in derivedStateOf;
//  - the fallback branch is 100% shader-free (background + clip only).

package com.remoteconfig.override.ui.component.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.remoteconfig.override.ui.theme.LocalEnableGlass
import com.remoteconfig.override.ui.theme.LocalEnableGlassBlur
import com.remoteconfig.override.ui.theme.isInDarkTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Tab 按压缩放比例（值只在 GlassTab 的 graphicsLayer lambda 中读取，draw-phase 观测）。 */
val LocalGlassTabScale = staticCompositionLocalOf { { 1f } }

/** Tab 点击回调：由 GlassBottomBar 提供（tab 只做视觉，点击统一汇入 currentIndex → snapshotFlow）。 */
val LocalGlassTabSelect = staticCompositionLocalOf<(Int) -> Unit> { {} }

// 强调色：Miuix 默认蓝（与 Miuix 主题主色一致；深色按 catalog 的方式提亮一档）。
// Task 7 接入真实主题后再改为读 MiuixTheme/MaterialTheme primary。
private val AccentLight = Color(0xFF3482FF)
private val AccentDark = Color(0xFF348BFF)

/**
 * 液态玻璃底栏。
 *
 * @param backdrop 内容层 backdrop（[rememberGlassBackdrop] 的结果）；为 null（玻璃总开关关闭
 * 或 API < 33）时自动走零 shader 降级路径。
 * @param blurEnabled 来自三级开关的次级开关；玻璃路径下 false 时只去掉 blur() 效果
 * （lens/vibrancy 保留），降级路径不受影响。
 */
@Composable
fun GlassBottomBar(
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    tabsCount: Int = 3,
    content: @Composable RowScope.() -> Unit,
) {
    val enableGlass = LocalEnableGlass.current
    val enableBlur = LocalEnableGlassBlur.current

    if (backdrop != null && enableGlass) {
        GlassLiquidBottomTabs(
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelected,
            backdrop = backdrop,
            blurEnabled = enableBlur,
            modifier = modifier,
            tabsCount = tabsCount,
            content = content,
        )
    } else {
        GlassFallbackBottomBar(
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            modifier = modifier,
            tabsCount = tabsCount,
            content = content,
        )
    }
}

/** 玻璃路径：catalog LiquidBottomTabs 的完整移植。 */
@Composable
private fun GlassLiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    tabsCount: Int,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isInDarkTheme()
    val accentColor = if (isLightTheme) AccentLight else AccentDark
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f, 0.5f) }
        // derivedStateOf 缓存橡皮筋偏移；key 上补 constraints.maxWidth，尺寸变化时重建闭包
        // （catalog 原码只 remember(density)，旋转后会读旧宽度——对齐 KernelSU 的修正思路）
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // tab 点击统一汇入 currentIndex（onSelected 由 snapshotFlow 驱动，见上方 LaunchedEffect）
        val tabSelect: (Int) -> Unit = { index -> currentIndex = index }

        // —— 第一段：可见玻璃面板 ——
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        if (blurEnabled) blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // —— 第二段：离屏着色 Row（alpha 0，记录进 tabsBackdrop 供指示器折射出选中色）——
        CompositionLocalProvider(
            LocalGlassTabSelect provides tabSelect,
            LocalGlassTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            if (blurEnabled) blur(8.dp.toPx())
                            lens(
                                24.dp.toPx() * progress,
                                24.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // —— 第三段：指示器（胶囊透镜，压在内容上方，自带拖拽手势）——
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/** 降级路径：零 shader 的纯色底栏（性能规格 9.1 的零成本分支）。 */
@Composable
private fun GlassFallbackBottomBar(
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabsCount: Int,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isInDarkTheme()
    val accentColor = if (isLightTheme) AccentLight else AccentDark
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA) else Color(0xFF121212)

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    var currentIndex by remember(selectedIndex) {
        mutableIntStateOf(selectedIndex())
    }

    // 复用 DampedDragAnimation（不挂拖拽 modifier，纯动画驱动）：点击 tab 时保留
    // press → release 的缩放脉冲；无任何 shader 参与。
    val dampedDragAnimation = remember(animationScope, tabsCount) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }
            .collectLatest { index ->
                currentIndex = index
            }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                onSelected(index)
            }
    }

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount
        }
        // 指示器位移用 animateDpAsState(spring)；其值只在下方 graphicsLayer 里读（draw-phase）
        val indicatorOffset by animateDpAsState(
            targetValue = with(density) { (currentIndex * tabWidth).toDp() },
            animationSpec = spring(1f, 300f, Dp.VisibilityThreshold),
            label = "glassFallbackIndicatorOffset"
        )

        val tabSelect: (Int) -> Unit = { index -> currentIndex = index }
        CompositionLocalProvider(
            LocalGlassTabSelect provides tabSelect,
            LocalGlassTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .background(containerColor, Capsule())
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val offset = indicatorOffset.toPx()
                    translationX = if (isLtr) offset else -offset
                }
                .clip(Capsule())
                .background(accentColor.copy(alpha = 0.15f))
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
