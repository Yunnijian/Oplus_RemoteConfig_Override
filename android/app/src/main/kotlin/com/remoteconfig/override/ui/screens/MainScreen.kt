package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.remoteconfig.override.ui.component.rememberContentReady
import com.remoteconfig.override.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })
    val contentReady = rememberContentReady()
    val scope = rememberCoroutineScope()
    // settledPage 本身就是 state，直接读取即可（只在值变化时重组，拖动中不逐帧）
    val settledPage = pagerState.settledPage

    Scaffold(
        bottomBar = {
            // 临时底栏；Task 6 替换为液态玻璃 GlassBottomBar
            NavigationBar {
                NavigationBarItem(
                    selected = settledPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = settledPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("配置") },
                )
                NavigationBarItem(
                    selected = settledPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            beyondViewportPageCount = if (contentReady) 1 else 0,
            overscrollEffect = null,
        ) { page ->
            val isCurrentPage = page == settledPage
            when (page) {
                0 -> if (isCurrentPage || contentReady) PlaceholderPage("首页 — Task 8")
                1 -> if (isCurrentPage || contentReady) PlaceholderPage("配置列表 — Task 9")
                2 -> if (isCurrentPage || contentReady) PlaceholderPage("设置 — Task 7")
            }
        }
    }
}

@Composable
private fun PlaceholderPage(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
