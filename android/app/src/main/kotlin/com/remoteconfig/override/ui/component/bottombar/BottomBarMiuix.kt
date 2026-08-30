// Ported from KernelSU me.weishu.kernelsu.ui.component.bottombar.BottomBarMiuix.
// - 移除了 KernelSU 特有的 Natives/rootAvailable/fullFeatured 门控与 navigationBadge。
// - tab 按平台分支（P2.0）：ColorOS 3 项（首页/配置/设置），HyperOS 4 项
//   （首页/应用配置/通用配置/设置），图标用我们的 Miuix/Material 双源选图标（MiuixIcons）。

package com.remoteconfig.override.ui.component.bottombar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.platform.Platform
import com.remoteconfig.override.ui.LocalMainPagerState
import com.remoteconfig.override.ui.component.FloatingBottomBar
import com.remoteconfig.override.ui.component.FloatingBottomBarItem
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBar
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBarBlur
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomBarMiuix(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    modifier: Modifier,
) {
    val mainState = LocalMainPagerState.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    // 平台 tab 分支（P2.0）：在底栏宿主处读一次 LocalPlatform，与 MainScreen 分页一致
    val hyperOS = LocalPlatform.current == Platform.HyperOS
    val items = if (hyperOS) {
        listOf(
            NavigationItemModel("首页", MiuixIcons.Home),
            NavigationItemModel("应用配置", MiuixIcons.GridView),
            NavigationItemModel("通用配置", MiuixIcons.Tune),
            NavigationItemModel("设置", MiuixIcons.Settings),
        )
    } else {
        listOf(
            NavigationItemModel("首页", MiuixIcons.Home),
            NavigationItemModel("配置", MiuixIcons.ListView),
            NavigationItemModel("设置", MiuixIcons.Settings),
        )
    }
    if (!enableFloatingBottomBar) {
        BlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                content = {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            icon = item.icon,
                            label = item.label,
                            selected = mainState.selectedPage == index,
                            onClick = {
                                mainState.animateToPage(index)
                            },
                        )
                    }
                }
            )
        }
    } else {
        FloatingBottomBar(
            modifier = modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            selectedIndex = { mainState.selectedPage },
            onSelected = { mainState.animateToPage(it) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = enableFloatingBottomBarBlur,
        ) {
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    onClick = {
                        mainState.animateToPage(index)
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

private data class NavigationItemModel(
    val label: String,
    val icon: ImageVector,
)
