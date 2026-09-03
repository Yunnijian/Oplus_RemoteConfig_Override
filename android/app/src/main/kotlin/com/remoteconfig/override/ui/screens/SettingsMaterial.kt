package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.SegmentedColumn
import com.remoteconfig.override.ui.component.material.SegmentedDropdownItem
import com.remoteconfig.override.ui.component.material.SegmentedListItem
import com.remoteconfig.override.ui.component.material.expressiveTopAppBarColors
import com.remoteconfig.override.BuildConfig
import com.remoteconfig.override.platform.appDisplayName
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.viewmodel.SettingsViewModel

/** 关于项版本文本（应用名随生效平台：ColorOS=Color云控修改 / HyperOS=Hyper 云控修改）。 */
private val aboutVersion: String
    @Composable get() = "${LocalPlatform.current.appDisplayName()} v${BuildConfig.VERSION_NAME}"

/**
 * 设置页 — Material 3 实现（精简为对齐 KernelSU 设置页外观组）。
 *
 * 与 [SettingsContentMiuix] 行为一致、读同一 [com.remoteconfig.override.settings.SettingsRepositoryImpl]；
 * 结构对齐 KernelSU `SettingsMaterial.kt` 的外观组：`SegmentedColumn` + `SegmentedDropdownItem`
 * （界面风格）+ `SegmentedListItem`（主题设置 → Route.ColorPalette），关于用 `SegmentedListItem` +
 * Material3 [AlertDialog]。全部文案为 KernelSU values-zh-rCN 中文。
 *
 * [onOpenTheme]：主题设置项点击回调。null（窄屏）= push Route.ColorPalette（现状）；
 * 非 null（宽屏双窗）= 由 SettingsContent 注入选中右侧 pane，不 push 路由。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContentMaterial(
    bottomInnerPadding: Dp = 0.dp,
    onOpenTheme: (() -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // 数据源：KernelSU 风格 SettingsViewModel（StateFlow 驱动，写入即时更新）
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 外观组
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = {
                    item {
                        SegmentedDropdownItem(
                            icon = Icons.Rounded.Dashboard,
                            title = "界面风格",
                            summary = "选择应用的界面风格",
                            items = listOf("Miuix", "Material"),
                            selectedIndex = if (UiMode.fromValue(settingsState.uiMode) == UiMode.Material) 1 else 0,
                            onItemSelected = { index ->
                                settingsViewModel.setUiMode(if (index == 1) UiMode.Material.value else UiMode.Miuix.value)
                            },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = {
                                if (onOpenTheme != null) onOpenTheme()
                                else navigator.push(Route.ColorPalette)
                            },
                            headlineContent = { Text("主题设置") },
                            supportingContent = { Text("自定义更多主题选项") },
                            leadingContent = { Icon(Icons.Rounded.Palette, "主题设置") },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                }
            )

            // 关于
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = {
                    item {
                        SegmentedListItem(
                            onClick = { showAbout = true },
                            headlineContent = { Text("关于") },
                            supportingContent = { Text(aboutVersion) },
                            leadingContent = { Icon(Icons.Rounded.ContactPage, "关于") },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = { Text(aboutVersion) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("确定") }
            },
        )
    }
}
