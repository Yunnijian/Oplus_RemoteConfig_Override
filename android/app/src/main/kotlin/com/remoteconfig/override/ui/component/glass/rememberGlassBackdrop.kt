package com.remoteconfig.override.ui.component.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.remoteconfig.override.ui.theme.isInDarkTheme

/**
 * 内容层 backdrop：先垫不透明 surface 色，再记录内容（性能规格 9.4）。
 * enabled=false 或系统不支持 RuntimeShader（API<33）时返回 null → 下游自动走降级路径。
 *
 * 垫底色说明：Miuix/Material 的 surface 取不到时用深浅灰兜底，Task 7 接入真实主题色后
 * 可改为主题 surface——本任务先用固定兜底色，避免主题依赖。
 */
@Composable
fun rememberGlassBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || Build.VERSION.SDK_INT < 33) return null
    val fallback = if (isInDarkTheme()) Color(0xFF171717) else Color(0xFFF7F7F7)
    return rememberLayerBackdrop {
        drawRect(fallback)
        drawContent()
    }
}
