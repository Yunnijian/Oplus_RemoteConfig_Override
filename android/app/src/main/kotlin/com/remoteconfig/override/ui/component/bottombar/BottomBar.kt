// Ported from KernelSU me.weishu.kernelsu.ui.component.bottombar.BottomBar.
// - 保留了 MainPagerState（tab/pager 协调核心）与 springAnimateToPage（点击/滑动协调关键）。
// - 移除了 KernelSU 特有的 NavigationBadgeState / badgeFor / useNavigationRail（R5 不需要角标），
//   改用我们的 isExpandedWidth()（Task 12 双窗判断）。

package com.remoteconfig.override.ui.component.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.component.PagerNavigationSpringSpec
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isExpandedWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlin.math.abs

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        // 只在"滚动中（跟手）"或"已停稳（settledPage 到位）"时同步 selectedPage。
        // 守卫：IME 弹出/窗口尺寸变化会让 Pager 短暂重排、currentPage 抖动（非滚动、非停稳），
        // 此时不同步，避免 selectedPage 误跳到错误页（C1：横屏编辑器弹输入法跳设置页）。
        // isNavigating=true（点击 tab 的程序滚动）时也不同步，避免被拉回。
        if (isNavigating) return
        if (pagerState.isScrollInProgress || pagerState.settledPage == pagerState.currentPage) {
            if (selectedPage != pagerState.currentPage) {
                selectedPage = pagerState.currentPage
            }
        }
    }
}

private suspend fun PagerState.springAnimateToPage(target: Int) {
    if (target !in 0 until pageCount) return
    var shouldSnapToTarget = false
    scroll(MutatePriority.UserInput) {
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distance = target - currentPage - currentPageOffsetFraction
        val scrollPixels = distance * pageSize
        if (abs(scrollPixels) <= 0.5f) return@scroll

        var consumedScroll = 0f
        var skipScroll = false
        Animatable(0f).animateTo(
            targetValue = scrollPixels,
            animationSpec = PagerNavigationSpringSpec,
        ) {
            if (skipScroll) return@animateTo

            val delta = value - consumedScroll
            if (abs(delta) > 0.5f) {
                val consumed = scrollBy(delta)
                consumedScroll += consumed
                if (abs(delta - consumed) > 0.1f) {
                    shouldSnapToTarget = true
                    skipScroll = true
                }
            } else {
                consumedScroll = value
            }

            if (abs(velocity) < 0.1f && abs(scrollPixels - consumedScroll) < 1.0f) {
                skipScroll = true
            }
        }

        val remaining = scrollPixels - consumedScroll
        if (abs(remaining) > 0.5f) {
            scrollBy(remaining)
        }
    }

    if (shouldSnapToTarget || currentPage != target) {
        scrollToPage(target)
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MainPagerState {
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}

/**
 * 宽屏是否用左侧导航 rail。
 *
 * 遵循 Google 官方自适应布局规范（WindowSizeClass）：宽度 Expanded（≥840dp）用
 * navigation rail，Compact/Medium 用底部栏。判定只看窗口尺寸类，**不受**"悬浮底栏"
 * 外观开关影响——KernelSU 在 Miuix + 悬浮底栏时豁免 rail 是其产品选择，
 * 与 Google 规范冲突（横屏平板应切 rail），故此处不移植该豁免。
 */
@Composable
fun useNavigationRail(): Boolean = isExpandedWidth()

@Composable
fun BottomBar(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> BottomBarMiuix(blurBackdrop, backdrop, modifier)
        UiMode.Material -> BottomBarMaterial(modifier)
    }
}

@Composable
fun SideRail(
    modifier: Modifier = Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> NavigationRailMiuix(modifier)
        UiMode.Material -> NavigationRailMaterial(modifier)
    }
}

/** 底部栏/侧栏统一按此顺序枚举 tab（3 项：首页/配置/设置）。 */
enum class BottomBarDestination(val index: Int) {
    Home(0),
    Config(1),
    Setting(2),
}
