// Ported from KernelSU me.weishu.kernelsu.ui.component.bottombar.BottomBarMaterial.
// - 移除了 KernelSU 特有的 Natives/rootAvailable/fullFeatured 门控与 navigationBadge。
// - 3 项 tab（首页/配置/设置），M3 ShortNavigationBar + filled/outlined 双态图标。

package com.remoteconfig.override.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import com.remoteconfig.override.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial(modifier: Modifier = Modifier) {
    val mainPagerState = LocalMainPagerState.current

    val items = listOf(
        Triple("首页", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("配置", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
        Triple("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    ShortNavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            ShortNavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) selectedIcon else unselectedIcon,
                        contentDescription = label,
                    )
                },
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
