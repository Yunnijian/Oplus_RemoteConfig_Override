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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.compose.foundation.isSystemInDarkTheme
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Navigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.navigation.rememberNavigator
import com.remoteconfig.override.platform.detectPlatform
import com.remoteconfig.override.platform.resolvePlatform
import com.remoteconfig.override.ui.screens.ColorPaletteScreen
import com.remoteconfig.override.ui.screens.ConfigEditorScreen
import com.remoteconfig.override.ui.screens.HyperOsAppDetailScreen
import com.remoteconfig.override.ui.screens.HyperOsCommonConfigScreen
import com.remoteconfig.override.ui.screens.HyperOsDynResScreen
import com.remoteconfig.override.ui.screens.HyperOsFisrScreen
import com.remoteconfig.override.ui.screens.HyperOsGpuTunerScreen
import com.remoteconfig.override.ui.screens.HyperOsJsonEditorScreen
import com.remoteconfig.override.ui.screens.HyperOsMigtScreen
import com.remoteconfig.override.ui.screens.HyperOsPerfScheduleScreen
import com.remoteconfig.override.ui.screens.HyperOsScopedEditorScreen
import com.remoteconfig.override.ui.screens.HyperOsThermalFpsScreen
import com.remoteconfig.override.ui.screens.HyperOsBandEditorScreen
import com.remoteconfig.override.ui.screens.MainScreen
import com.remoteconfig.override.ui.theme.LocalColorMode
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBar
import com.remoteconfig.override.ui.theme.LocalEnableFloatingBottomBarBlur
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.LocalWindowWidthClass
import com.remoteconfig.override.ui.theme.RemoteConfigTheme
import com.remoteconfig.override.viewmodel.MainActivityViewModel
import com.remoteconfig.override.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()
            // 对齐 KernelSU MainActivity：设置经 MainActivityViewModel 的
            // OnSharedPreferenceChangeListener → StateFlow 驱动（无快照观察），
            // collectAsStateWithLifecycle 订阅，根主题/CompositionLocal 即时响应。
            val mainActivityViewModel: MainActivityViewModel = viewModel()
            val uiState by mainActivityViewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by mainActivityViewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val uiMode = uiState.uiMode

            // 平台检测（P2.0a）：反射读系统属性开销大，remember 缓存避免每次重组重复检测；
            // platformMode 为用户设置（auto/coloros/hyperos），auto 及未知值时跟随检测结果。
            val detectedPlatform = remember { detectPlatform() }
            val platform = resolvePlatform(appSettings.platformMode, detectedPlatform)

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
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalUiMode provides uiMode,
                LocalColorMode provides appSettings.colorMode.value,
                LocalPlatform provides platform,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalWindowWidthClass provides windowSizeClass.widthSizeClass,
            ) {
                RemoteConfigTheme(
                    appSettings = appSettings,
                    uiMode = uiMode,
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
                                MainScreen(
                                    viewModel = viewModel,
                                    initialPage = selectedMainPage,
                                    onPageChanged = mainActivityViewModel::setSelectedMainPage,
                                )
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
                            // HyperOS 路由（P2.0b）：应用配置详情 / 通用配置。
                            // 两个 Screen 组合函数由并行任务提供（ui/screens/HyperOsAppDetailScreen.kt /
                            // HyperOsCommonConfigScreen.kt），数据加载由各自内部完成，这里只做路由挂载。
                            entry<Route.HyperOsAppDetail> { key ->
                                HyperOsAppDetailScreen(key.packageName)
                            }
                            entry<Route.HyperOsCommonConfig> {
                                HyperOsCommonConfigScreen()
                            }
                            entry<Route.HyperOsJsonEditor> { key ->
                                HyperOsJsonEditorScreen(key.configName)
                            }
                            entry<Route.HyperOsScopedEditor> { key ->
                                HyperOsScopedEditorScreen(key.packageName)
                            }
                            // 功能页 v2：按功能入口组织的子屏（与作用域编辑器共享草稿）
                            entry<Route.HyperOsPerfSchedule> { key ->
                                HyperOsPerfScheduleScreen(key.packageName)
                            }
                            entry<Route.HyperOsThermalFps> { key ->
                                HyperOsThermalFpsScreen(key.packageName)
                            }
                            entry<Route.HyperOsBandEditor> { key ->
                                HyperOsBandEditorScreen(
                                    key.packageName, key.curveKey, key.curveLabel, key.bandKey,
                                )
                            }
                            entry<Route.HyperOsFisr> { key ->
                                HyperOsFisrScreen(key.packageName)
                            }
                            entry<Route.HyperOsDynRes> { key ->
                                HyperOsDynResScreen(key.packageName)
                            }
                            entry<Route.HyperOsGpuTuner> { key ->
                                HyperOsGpuTunerScreen(key.packageName)
                            }
                            entry<Route.HyperOsMigt> { key ->
                                HyperOsMigtScreen(key.packageName)
                            }
                        },
                    )
                }
            }
        }
    }
}
