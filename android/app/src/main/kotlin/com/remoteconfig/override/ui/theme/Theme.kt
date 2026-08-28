package com.remoteconfig.override.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.remoteconfig.override.settings.AppSettingsRepository
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.UiMode

/** 当前是否深色主题（读设置，SYSTEM 时跟随系统）。 */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (AppSettingsRepository.colorMode) {
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
        ColorMode.SYSTEM -> isSystemInDarkTheme()
    }
}

/** 双主题分发入口：按 uiMode 将主题分发到 MiuixTheme / MaterialTheme。 */
@Composable
fun RemoteConfigTheme(content: @Composable () -> Unit) {
    val darkTheme = isInDarkTheme()
    when (AppSettingsRepository.uiMode) {
        UiMode.Miuix ->
            RemoteConfigMiuixTheme(
                colorMode = AppSettingsRepository.colorMode,
                enableMonet = AppSettingsRepository.enableMonet,
                darkTheme = darkTheme,
                content = content,
            )
        UiMode.Material ->
            RemoteConfigMaterialTheme(darkTheme = darkTheme, content = content)
    }
}
