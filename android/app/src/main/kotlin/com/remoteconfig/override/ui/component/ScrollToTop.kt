package com.remoteconfig.override.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private const val LIST_QUIET_MILLIS = 250L
private const val LIST_SETTLE_TIMEOUT_MS = 3000L

private class KeysRef {
    var value: List<Any?>? = null
}

/**
 * 排序/筛选/刷新时把列表安静地滚回顶部 —— 逐字移植自 KernelSU `ui/component/ScrollToTop.kt`。
 *
 * 首次组合与"从详情页返回"不滚动（NavDisplay 会重建页面并重置 remember），避免回到列表
 * 时被强行拽回首行。下拉刷新这类不改变排序的动作，用只自增的 refreshTick 作为 key。
 *
 * [isBusy] 传 `{ isRefreshing }`，让置顶持续到慢刷新结束；快照读取需经 rememberUpdatedState。
 */
@Composable
fun ScrollToTopOnChange(
    listState: LazyListState,
    vararg keys: Any?,
    onScrolledToTop: () -> Unit = {},
    isBusy: () -> Boolean = { false },
    observedList: () -> Any?,
) {
    val keysList = keys.asList()
    val sideRef = remember { KeysRef() }
    SideEffect {
        val prev = sideRef.value
        if (prev != null && prev != keysList) {
            listState.requestScrollToItem(0)
        }
        sideRef.value = keysList
    }
    val effectRef = remember { KeysRef() }
    LaunchedEffect(*keys) {
        val prev = effectRef.value
        effectRef.value = keysList
        if (prev != null && prev != keysList) {
            scrollToTopAfterListSettles(listState, isBusy = isBusy, observedList = observedList)
            onScrolledToTop()
        }
    }
}

/**
 * 每帧钉住顶部，直到 [observedList] 静默 [LIST_QUIET_MILLIS] 且 [isBusy] 为假
 * （上限 [LIST_SETTLE_TIMEOUT_MS]），压过 LazyColumn 基于 key 的位置恢复。
 */
suspend fun scrollToTopAfterListSettles(
    listState: LazyListState,
    isBusy: () -> Boolean = { false },
    observedList: () -> Any?,
): Unit = coroutineScope {
    var settled = false
    val watcher = launch {
        while (true) {
            val changed = withTimeoutOrNull(LIST_QUIET_MILLIS.milliseconds) {
                snapshotFlow(observedList).drop(1).first()
            }
            if (changed == null && !isBusy()) break
        }
        settled = true
    }
    withTimeoutOrNull(LIST_SETTLE_TIMEOUT_MS.milliseconds) {
        while (!settled) {
            listState.requestScrollToItem(0)
            withFrameNanos { }
        }
    }
    watcher.cancel()
    listState.requestScrollToItem(0)
}
