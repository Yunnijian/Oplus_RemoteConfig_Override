// Ported from KernelSU me.weishu.kernelsu.ui.component.bottombar.NavigationRailMiuix.
// - 移除了 KernelSU 特有的 Natives/rootAvailable/fullFeatured 门控与 navigationBadge。
// - 3 项 tab（首页/配置/设置），图标用 MiuixIcons。

package com.remoteconfig.override.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.remoteconfig.override.ui.LocalMainPagerState
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuix(
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current

    val items = listOf(
        "首页" to MiuixIcons.Home,
        "配置" to MiuixIcons.ListView,
        "设置" to MiuixIcons.Settings,
    )

    NavigationRail(
        modifier = modifier,
        state = rememberNavigationRailState(),
        color = MiuixTheme.colorScheme.surface,
        expandContentDescription = "展开导航",
        collapseContentDescription = "收起导航",
    ) {
        items.forEachIndexed { index, (label, icon) ->
            NavigationRailItem(
                selected = mainState.selectedPage == index,
                onClick = {
                    mainState.animateToPage(index)
                },
                icon = icon,
                label = label,
            )
        }
    }
}
