package com.remoteconfig.override.ui.component.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 内容层 backdrop：先垫不透明 surface 色，再记录内容（性能规格 9.4）。
 * enabled=false 或系统不支持 RuntimeShader（API<33）时返回 null → 下游自动走降级路径。
 *
 * 垫底色：读主题 surface（Miuix/Material 双源，见 GlassBottomBar.kt 顶部的 helper）。
 */
@Composable
fun rememberGlassBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || Build.VERSION.SDK_INT < 33) return null
    val surface = glassSurfaceColor()
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}
