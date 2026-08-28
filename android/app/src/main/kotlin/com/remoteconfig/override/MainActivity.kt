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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Navigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.navigation.rememberNavigator
import com.remoteconfig.override.settings.AppSettingsRepository
import com.remoteconfig.override.ui.screens.ColorPaletteScreen
import com.remoteconfig.override.ui.screens.ConfigEditorScreen
import com.remoteconfig.override.ui.screens.MainScreen
import com.remoteconfig.override.ui.theme.LocalEnableGlass
import com.remoteconfig.override.ui.theme.LocalEnableGlassBlur
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.LocalWindowWidthClass
import com.remoteconfig.override.ui.theme.RemoteConfigTheme
import com.remoteconfig.override.ui.theme.isInDarkTheme
import com.remoteconfig.override.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()
            // Bug 3：系统栏图标明暗必须跟随应用主题（colorMode），而非系统深色。
            // 若强制 ColorMode 与系统相反，isSystemInDarkTheme() 会导致
            // 白图标落在浅色背景（或反之）不可见。isInDarkTheme 在组合期
            // 读取设置（@Composable @ReadOnlyComposable），可直接赋值给变量。
            val darkTheme = isInDarkTheme()

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

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalUiMode provides AppSettingsRepository.uiMode,
                LocalEnableGlass provides AppSettingsRepository.enableGlass,
                LocalEnableGlassBlur provides AppSettingsRepository.enableGlassBlur,
                LocalWindowWidthClass provides windowSizeClass.widthSizeClass,
            ) {
                RemoteConfigTheme {
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
