package com.remoteconfig.override

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Navigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.navigation.rememberNavigator
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.screens.ColorPaletteScreen
import com.remoteconfig.override.ui.screens.ConfigEditorScreen
import com.remoteconfig.override.ui.screens.MainScreen
import com.remoteconfig.override.ui.theme.LocalColorMode
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBar
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBarBlur
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.LocalWindowWidthClass
import com.remoteconfig.override.ui.theme.RemoteConfigTheme
import com.remoteconfig.override.ui.theme.ThemeController
import com.remoteconfig.override.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()
            // 对齐 KernelSU：设置由 SharedPreferences 仓库驱动，MainActivity 注册
            // OnSharedPreferenceChangeListener，任一主题字段变更 → settingsVersion++ →
            // 重组 → getAppSettings 重新读取（主题/底栏即时响应）。
            val settingsRepository = remember { SettingsRepositoryImpl() }
            var settingsVersion by remember { mutableIntStateOf(0) }
            DisposableEffect(settingsRepository) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    settingsVersion++
                }
                settingsRepository.prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    settingsRepository.prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            val appSettings = remember(settingsVersion) { ThemeController.getAppSettings(settingsRepository) }
            val uiMode = UiMode.fromValue(settingsRepository.uiMode)

            // Bug 3：系统栏图标明暗必须跟随应用主题（colorMode），而非系统深色。
            // 若强制 ColorMode 与系统相反，isSystemInDarkTheme() 会导致
            // 白图标落在浅色背景（或反之）不可见。直接读 appSettings.colorMode
            // （对齐 KernelSU MainActivity.kt:120），不受 LocalColorMode provider 位置影响。
            val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose { }
            }

            // 平板/大屏适配（Google 标准 WindowSizeClass）：在 setContent 内计算，
            // 每次重组读取当前窗口尺寸 → 分屏/平行视窗/自由窗口重布局即时响应。
            val windowSizeClass = calculateWindowSizeClass(this)

            val navigator = rememberNavigator(Route.Main)

            // 对齐 KernelSU MainActivity.kt:138-141：pageScale 全局缩放 —— 用 Density 缩放
            // LocalDensity.density，实现整体 UI 缩放（fontScale 保持不变）。
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, settingsRepository.pageScale) {
                Density(systemDensity.density * settingsRepository.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalUiMode provides uiMode,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides settingsRepository.enableBlur,
                LocalEnableFloatingBottomBar provides settingsRepository.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides settingsRepository.enableFloatingBottomBarBlur,
                LocalWindowWidthClass provides windowSizeClass.widthSizeClass,
            ) {
                RemoteConfigTheme(
                    appSettings = appSettings,
                    uiMode = uiMode,
                    miuixMonet = settingsRepository.miuixMonet,
                ) {
                    NavDisplay(
                        backStack = navigator.backStack,
                        onBack = {
                            // Bug 3：系统返回键退出编辑器时先清理编辑态，避免残留跨模式泄漏
                            // （顶栏返回箭头已在 ConfigEditorScreen 的 onBack 内 clearEditingConfig）
                            if (navigator.backStack.lastOrNull() is Route.ConfigEditor) {
                                viewModel.clearEditingConfig()
                            }
                            navigator.pop()
                        },
                        entryProvider = entryProvider {
                            entry<Route.Main> {
                                MainScreen(viewModel = viewModel)
                            }
                            entry<Route.ColorPalette> { ColorPaletteScreen() }
                            entry<Route.ConfigEditor> { key ->
                                // Bug 5：进程重建恢复 backStack（含 ConfigEditor(pkg)）时新 VM 无编辑态，
                                // 按 route 参数幂等加载。判重：仅当 editingPackageName 不是该包时才 load，
                                // 避免与列表页 onGameClick 已先调用的 loadConfig 重复（重复无害但会闪 loading）。
                                LaunchedEffect(key.packageName) {
                                    if (viewModel.editingPackageName.value != key.packageName) {
                                        viewModel.loadConfig(key.packageName)
                                    }
                                }
                                ConfigEditorScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        viewModel.clearEditingConfig()
                                        navigator.pop()
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
