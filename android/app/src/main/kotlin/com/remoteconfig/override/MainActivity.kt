package com.remoteconfig.override

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.remoteconfig.override.ui.screens.ColorPaletteScreenPlaceholder
import com.remoteconfig.override.ui.screens.ConfigEditorScreen
import com.remoteconfig.override.ui.screens.MainScreen
import com.remoteconfig.override.ui.theme.LocalEnableGlass
import com.remoteconfig.override.ui.theme.LocalEnableGlassBlur
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.RemoteConfigTheme
import com.remoteconfig.override.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val darkTheme = isSystemInDarkTheme()

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

            val navigator = rememberNavigator(Route.Main)

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalUiMode provides AppSettingsRepository.uiMode,
                LocalEnableGlass provides AppSettingsRepository.enableGlass,
                LocalEnableGlassBlur provides AppSettingsRepository.enableGlassBlur,
            ) {
                RemoteConfigTheme {
                    NavDisplay(
                        backStack = navigator.backStack,
                        onBack = { navigator.pop() },
                        entryProvider = entryProvider {
                            entry<Route.Main> {
                                MainScreen(viewModel = viewModel)
                            }
                            entry<Route.ColorPalette> { ColorPaletteScreenPlaceholder() }
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
