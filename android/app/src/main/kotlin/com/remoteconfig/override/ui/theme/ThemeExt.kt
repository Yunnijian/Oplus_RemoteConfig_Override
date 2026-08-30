package com.remoteconfig.override.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 预设强调色列表（ARGB Int）— 完整对齐 KernelSU `Colors.kt` 的 keyColorOptions。
 * 首项「跟随默认」用 keyColor=0 表示，不入此表。
 */
val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(), // 红色
    Color(0xFFE91E63).toArgb(), // 粉色
    Color(0xFF9C27B0).toArgb(), // 紫色
    Color(0xFF673AB7).toArgb(), // 深紫色
    Color(0xFF3F51B5).toArgb(), // 靛蓝色
    Color(0xFF2196F3).toArgb(), // 蓝色
    Color(0xFF00BCD4).toArgb(), // 青色
    Color(0xFF009688).toArgb(), // 蓝绿色
    Color(0xFF4FAF50).toArgb(), // 绿色
    Color(0xFFFFEB3B).toArgb(), // 黄色
    Color(0xFFFFC107).toArgb(), // 琥珀色
    Color(0xFFFF9800).toArgb(), // 橙色
    Color(0xFF795548).toArgb(), // 棕色
    Color(0xFF607D8F).toArgb(), // 蓝灰色
    Color(0xFFFF9CA8).toArgb(), // 樱花色
)

/** AMOLED 模式：把表面色系全部压成纯黑（对齐 KernelSU ThemeExt.amoledBackground）。 */
fun ColorScheme.amoledBackground(amoled: Boolean): ColorScheme =
    if (!amoled) this
    else copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    )

/**
 * 取色核心（非组合、可在任意线程调用）— 与组合版 [rememberRemoteConfigColorScheme]
 * 同引擎同后处理（effectiveFor 降级 + AMOLED 压黑）。供后台线程使用：
 * 单次调用即一整套色调方案生成，在组合期同步执行会阻塞主线程。
 */
fun remoteConfigColorSchemeFromSeed(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
): ColorScheme = dynamicColorScheme(
    seedColor = seedColor,
    isDark = isDark,
    isAmoled = isAmoled,
    style = paletteStyle,
    specVersion = colorSpec.effectiveFor(paletteStyle),
).amoledBackground(isAmoled)

/**
 * 取色方案进程级缓存 — 根因修复的预热层（验收标准：色块随页面内容一并呈现）。
 *
 * 单次 [remoteConfigColorSchemeFromSeed] ≈ 56ms（Spec2025 HCT 求解），取色屏共需 17 套
 * （默认种子 + 16 预设色）。若进页后才开始算，色块行必然先空白占位再出现。
 * [PrewarmRemoteConfigSchemes] 在应用根主题组合时按当前 (深色/AMOLED/风格/标准)
 * 把 17 套全部并行预取（后台线程池 ≈120ms），用户到达取色屏时 [peek] 直接命中 →
 * 组合期零取色计算、色块首帧即现。用户中途改风格/标准/深色导致未命中时，
 * 调用方用 [prefetch] 并行补齐（与预取去重，不重复计算）。
 */
object RemoteConfigSchemeCache {
    private data class Key(
        val seed: Int,
        val isDark: Boolean,
        val isAmoled: Boolean,
        val style: PaletteStyle,
        val spec: ColorSpec.SpecVersion,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap<Key, Deferred<ColorScheme>>()
    private val done = ConcurrentHashMap<Key, ColorScheme>()

    /** 取（或启动）一套方案的后台生成；相同参数去重。 */
    fun prefetch(
        seed: Color,
        isDark: Boolean,
        isAmoled: Boolean,
        style: PaletteStyle,
        spec: ColorSpec.SpecVersion,
    ): Deferred<ColorScheme> {
        val key = Key(seed.toArgb(), isDark, isAmoled, style, spec)
        return running.getOrPut(key) {
            scope.async {
                remoteConfigColorSchemeFromSeed(seed, isDark, isAmoled, style, spec).also { done[key] = it }
            }
        }
    }

    /** 非阻塞读取已完成的方案；未预热/生成中返回 null，调用方自行回退。 */
    fun peek(
        seed: Color,
        isDark: Boolean,
        isAmoled: Boolean,
        style: PaletteStyle,
        spec: ColorSpec.SpecVersion,
    ): ColorScheme? = done[Key(seed.toArgb(), isDark, isAmoled, style, spec)]

    /** 预取整套强调色方案（默认种子 + [keyColorOptions] 全部 16 色）。 */
    fun prewarm(
        defaultSeed: Color,
        isDark: Boolean,
        isAmoled: Boolean,
        style: PaletteStyle,
        spec: ColorSpec.SpecVersion,
    ) {
        (listOf(defaultSeed) + keyColorOptions.map { Color(it) }).forEach {
            prefetch(it, isDark, isAmoled, style, spec)
        }
    }
}

/**
 * 预热整套强调色取色方案 — 由应用根主题（RemoteConfigMaterialTheme）组合时调用。
 * 用户进「主题设置」前必经此处，预取在用户浏览设置列表期间完成；
 * 参数变更（切深色/风格/标准）自动触发新一批。详见 [RemoteConfigSchemeCache]。
 */
@Composable
fun PrewarmRemoteConfigSchemes(
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
) {
    val context = LocalContext.current
    val defaultSeed = remember(isDark) {
        (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    }
    LaunchedEffect(defaultSeed, isDark, isAmoled, paletteStyle, colorSpec) {
        RemoteConfigSchemeCache.prewarm(defaultSeed, isDark, isAmoled, paletteStyle, colorSpec)
    }
}

/**
 * Material 取色方案 — 对齐 KernelSU ThemeExt.rememberKernelSUColorScheme。
 * seed 未指定（keyColor==0）时取系统动态色 primary；调色板/规范经 effectiveFor 降级。
 *
 * 根因性能设计：单次取色 = 一整套色调方案生成（全量 HCT 计算）。首帧优先读
 * [RemoteConfigSchemeCache] 预热结果（根主题已预取则直接命中，主线程零取色）；
 * 仅冷启动未命中时才同步生成基线兜底。此后 keyColor/深色/AMOLED/风格/标准 的每次
 * 变更都走缓存去重的后台生成 —— 切色、切主题时主线程零取色计算。
 *
 * 命中路径为同步返回（对齐 KernelSU rememberDynamicColorScheme 的同 pass 语义）：
 * 预热命中时 scheme 随参数变更在**同一次重组**内生效，不再经 LaunchedEffect 异步
 * 往返多付一帧全树重组波（实测该波 70-95ms × 11 个独立 composition）。
 * 仅冷未命中时保留旧值并后台补齐。
 */
@Composable
fun rememberRemoteConfigColorScheme(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
): ColorScheme {
    val context = LocalContext.current
    // 种子解析在主线程（框架动态色调用很轻）；Unspecified（keyColor=0）→ 系统动态色 primary
    val resolvedSeed = if (seedColor == Color.Unspecified) {
        (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else {
        seedColor
    }
    // 每次重组先试预热缓存：命中 → 同步返回（单波更新）
    val warm = RemoteConfigSchemeCache.peek(resolvedSeed, isDark, isAmoled, paletteStyle, colorSpec)
    // 冷启动/新参数组合兜底：首帧同步基线 + 后台补齐（未就绪前沿用旧值）
    var fallback by remember {
        mutableStateOf(remoteConfigColorSchemeFromSeed(resolvedSeed, isDark, isAmoled, paletteStyle, colorSpec))
    }
    LaunchedEffect(resolvedSeed, isDark, isAmoled, paletteStyle, colorSpec) {
        fallback = RemoteConfigSchemeCache
            .prefetch(resolvedSeed, isDark, isAmoled, paletteStyle, colorSpec)
            .await()
    }
    return warm ?: fallback
}
