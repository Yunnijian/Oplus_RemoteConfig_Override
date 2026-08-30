// Ported from KernelSU me.weishu.kernelsu.ui.component.bottombar.NavigationRailMiuix.
// - 移除了 KernelSU 特有的 Natives/rootAvailable/fullFeatured 门控与 navigationBadge。
// - tab 数按平台分支：ColorOS 3 项（首页/配置/设置），HyperOS 4 项（首页/应用配置/通用配置/设置），
//   图标对齐 BottomBarMiuix 的平台分支。

package com.remoteconfig.override.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.remoteconfig.override.platform.Platform
import com.remoteconfig.override.ui.LocalMainPagerState
import com.remoteconfig.override.ui.theme.LocalPlatform
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuix(
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    val hyperOS = LocalPlatform.current == Platform.HyperOS

    val items = if (hyperOS) {
        listOf(
            "首页" to MiuixIcons.Home,
            "应用配置" to MiuixIcons.GridView,
            "通用配置" to MiuixIcons.Tune,
            "设置" to MiuixIcons.Settings,
        )
    } else {
        listOf(
            "首页" to MiuixIcons.Home,
            "配置" to MiuixIcons.ListView,
            "设置" to MiuixIcons.Settings,
        )
    }

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
