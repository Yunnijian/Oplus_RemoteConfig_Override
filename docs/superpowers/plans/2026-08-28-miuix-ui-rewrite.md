# Miuix 双模式 UI 重写 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Color云控修改 的 UI 重写为 Miuix(HyperOS) 风格 + Material 3 双模式可切换，底部导航使用 AndroidLiquidGlass 液态玻璃，数据层不动。

**Architecture:** 对齐 KernelSU Manager 的双 UI 模式架构——每个屏幕拆 `<Screen>Screen.kt`(分发) + `<Screen>Material.kt` + `<Screen>Miuix.kt`；主题按 UiMode 分发到 MiuixTheme/MaterialTheme；导航从 Navigation Compose 迁移到 Navigation3(NavDisplay + NavKey)；底部 3 tab 用 HorizontalPager + backdrop 液态玻璃浮动底栏（带能力检测和三级开关降级）。

**Tech Stack:** Kotlin 2.4.10 · AGP 9.3.2 · Gradle 9.7.1 · Compose BOM 2026.08.00 · miuix 0.9.3 (`top.yukonga.miuix.kmp:*-android`) · backdrop 2.0.1 (`io.github.kyant0:backdrop`) · androidx.navigation3 1.1.6 · libsu 6.0.0（不变）· Rust 工具链（不变）

**规格文档:** `docs/superpowers/specs/2026-08-28-miuix-ui-rewrite-design.md`（含第 9 节性能预算，实施时强制遵循）

**参考代码（本地已克隆）:**
- `KernelSU/manager/app/src/main/java/me/weishu/kernelsu/ui/` — 双模式主题、屏幕结构
- `/tmp/alg-check/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/` — backdrop 官方液态玻璃底栏示例（若 `/tmp/alg-check` 已被清理，从 https://github.com/Kyant0/AndroidLiquidGlass kmp 分支重新获取，路径 `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/`）

## Global Constraints

- 分支：`MIUIX-test`。每个任务结束：`./gradlew :app:compileDebugKotlin` 通过 + git commit。
- **数据层完全不动**：`MainViewModel.kt`、`DatabaseManager.kt`、`model/GameConfig.kt`、`App.kt` 的公开签名保持不变。UI 任务如需 MainViewModel 新增只读状态必须走 `derivedStateOf` 且不改现有函数签名。
- 版本：`versionCode = 2`、`versionName = "1.2.1"` 保持不变（发版号调整不在本计划范围）。
- `minSdk = 26` 不变；`compileSdk = 37`（BOM 2026.08.00 AAR 元数据要求，Task 2 已验证）、`targetSdk = 36`；Java 21。
- 性能规则（规格 9 节）：动画值只在 `graphicsLayer{}`/`drawBackdrop` lambda 中读取；Animatable 必须设 visibilityThreshold；`items(key, contentType)`；`collectAsStateWithLifecycle`；重计算 `Dispatchers.IO`；液态玻璃必须有能力检测 + 关闭降级分支。
- 所有新 UI 代码中文文案与现有 app 保持一致（"写入数据库"、"应用增强服务" 等术语）。
- 测试策略：纯逻辑（枚举/仓库映射）用 kotlin.test 单测；UI 用编译 + 手动验收清单（无设备自动化）。每个 UI 任务的验证步骤包含 `assembleDebug` 成功。

---

### Task 1: 构建环境与工具链升级

**Files:**
- Modify: `android/gradle/wrapper/gradle-wrapper.properties`
- Modify: `android/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/GameListScreen.kt`（rememberRipple 替换）

**Interfaces:**
- Produces: 可编译的工程基线；后续任务依赖的新依赖坐标。

- [ ] **Step 1: 安装 JDK 21（本机当前无 Java）**

```bash
brew install --cask temurin@21
# 验证
/usr/libexec/java_home -v 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

若 brew 不可用，从 https://adoptium.net 安装 Temurin 21。后续所有 gradle 命令都需要 `JAVA_HOME` 指向 JDK 21。

- [ ] **Step 2: 升级 Gradle wrapper**

`android/gradle/wrapper/gradle-wrapper.properties` 中：
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
```

- [ ] **Step 3: 根 build.gradle.kts 升级插件**

替换 `android/build.gradle.kts` 全部内容为：
```kotlin
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.10" apply false
}
```

- [ ] **Step 4: app/build.gradle.kts 升级**

顶部 plugins 块改为：
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.parcelize")
}
```

android 块：
- `compileSdk = 34` → 按 AGP 9 新 DSL（对齐 KernelSU `KernelSU/manager/app/build.gradle.kts:135-141` 的 `compileSdk { version = release(36) {} }` 写法；若该 DSL 编译报错则回退 `compileSdk = 36`）
- `targetSdk = 34` → `targetSdk = 36`
- `minSdk = 26` 不变
- `compileOptions` 两处 `VERSION_17` → `JavaVersion.VERSION_21`
- 删除整个 `kotlinOptions { jvmTarget = "17" }` 块，替换为：
```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
```
- 删除整个 `composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }` 块（Compose 编译器插件已由 `org.jetbrains.kotlin.plugin.compose` 提供）

dependencies 块替换为：
```kotlin
dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Miuix (HyperOS design) — Android 单平台构件
    val miuix = "0.9.3"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuix")

    // AndroidLiquidGlass (backdrop) — 液态玻璃
    implementation("io.github.kyant0:backdrop:2.0.1")

    // Material 取色（PaletteStyle/ColorSpec，供 Material 主题与取色屏使用）
    implementation("com.materialkolor:material-kolor:5.0.0")

    // 平板/大屏适配（Google 标准 WindowSizeClass）
    implementation("androidx.compose.material3:material3-window-size-class")

    // Navigation3
    implementation("androidx.navigation3:navigation3-runtime:1.1.6")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Kotlinx Serialization (JSON parsing)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // libsu (Root access)
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Unit tests
    testImplementation(kotlin("test"))

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

注意：`navigation-compose`、`navigation3-ui`（不存在该 artifact，NavDisplay 在 navigation3-runtime 中）不要添加。`packaging`、`ndk.abiFilters`、`buildTypes`、`buildFeatures { compose = true }` 保持不动。

- [ ] **Step 5: 修复 rememberRipple（Compose 1.12 已移除）**

`GameListScreen.kt:199` 将：
```kotlin
indication = androidx.compose.material.ripple.rememberRipple(),
```
替换为：
```kotlin
indication = androidx.compose.material3.ripple(),
```
并在文件顶部补 `import androidx.compose.material3.ripple`（或保持全限定名，去掉 `androidx.compose.material.ripple` 的显式引用）。

- [ ] **Step 6: 编译验证**

```bash
cd android && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。若出现 Kotlin 2.4 弃用错误（非警告），按错误信息逐个修复（现有代码预期只有 rememberRipple 一处硬错误）。

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "build: upgrade toolchain for Miuix/backdrop (Kotlin 2.4.10, AGP 9.3.2, Gradle 9.7.1, Compose BOM 2026.08.00)"
```

---

### Task 2: 设置模型与仓库（含单元测试）

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/settings/UiMode.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/settings/ColorMode.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/settings/AppSettingsRepository.kt`
- Test: `android/app/src/test/kotlin/com/remoteconfig/override/settings/ColorModeTest.kt`

**Interfaces:**
- Produces:
  - `enum class UiMode(val value: Int) { Miuix(0), Material(1) }` + `companion object { fun fromValue(v: Int): UiMode }`
  - `enum class ColorMode(val value: Int) { SYSTEM(0), LIGHT(1), DARK(2) }` + `fromValue`、`val isDark: Boolean`
  - `object AppSettingsRepository`：`fun init(context: Context)`；`var uiMode: UiMode`、`var colorMode: ColorMode`、`var enableMonet: Boolean`、`var enableGlass: Boolean`、`var enableGlassBlur: Boolean`、`var keyColor: Int`（0=默认）、`var paletteStyle: String`（默认 `"TonalSpot"`）、`var colorSpec: String`（默认 `"SPEC_2025"`）——均为 Compose `mutableStateOf` 支撑的可观察属性，读写同步 SharedPreferences

- [ ] **Step 1: 写失败的单测**

`android/app/src/test/kotlin/com/remoteconfig/override/settings/ColorModeTest.kt`：
```kotlin
package com.remoteconfig.override.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorModeTest {
    @Test
    fun fromValue_roundTrips() {
        ColorMode.entries.forEach { mode ->
            assertEquals(mode, ColorMode.fromValue(mode.value))
        }
    }

    @Test
    fun fromValue_fallsBackToSystem() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(999))
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(-1))
    }

    @Test
    fun isDark_matchesSpec() {
        assertTrue(ColorMode.DARK.isDark)
        assertFalse(ColorMode.LIGHT.isDark)
        assertFalse(ColorMode.SYSTEM.isDark)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.remoteconfig.override.settings.ColorModeTest"
```
Expected: FAIL（`ColorMode` 未定义，编译错误）

- [ ] **Step 3: 实现 UiMode / ColorMode / AppSettingsRepository**

`settings/UiMode.kt`：
```kotlin
package com.remoteconfig.override.settings

/** UI 设计风格。 */
enum class UiMode(val value: Int) {
    Miuix(0),
    Material(1);

    companion object {
        fun fromValue(value: Int): UiMode = entries.find { it.value == value } ?: Miuix
    }
}
```

`settings/ColorMode.kt`：
```kotlin
package com.remoteconfig.override.settings

/** 深浅色模式。 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    val isDark: Boolean get() = this == DARK

    companion object {
        fun fromValue(value: Int): ColorMode = entries.find { it.value == value } ?: SYSTEM
    }
}
```

`settings/AppSettingsRepository.kt`：
```kotlin
package com.remoteconfig.override.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用设置仓库 — SharedPreferences 持久化 + Compose 可观察状态。
 * 在 App.onCreate 中 init；UI 直接读取属性即可响应变更。
 */
object AppSettingsRepository {
    private const val PREFS = "ui_settings"

    private val _uiMode = mutableStateOf(UiMode.Miuix)
    private val _colorMode = mutableStateOf(ColorMode.SYSTEM)
    private val _enableMonet = mutableStateOf(false)
    private val _enableGlass = mutableStateOf(true)
    private val _enableGlassBlur = mutableStateOf(true)
    private val _keyColor = mutableStateOf(0)
    private val _paletteStyle = mutableStateOf("TonalSpot")
    private val _colorSpec = mutableStateOf("SPEC_2025")

    val uiMode: UiMode get() = _uiMode.value
    val colorMode: ColorMode get() = _colorMode.value
    val enableMonet: Boolean get() = _enableMonet.value
    val enableGlass: Boolean get() = _enableGlass.value
    val enableGlassBlur: Boolean get() = _enableGlassBlur.value
    val keyColor: Int get() = _keyColor.value
    val paletteStyle: String get() = _paletteStyle.value
    val colorSpec: String get() = _colorSpec.value

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _uiMode.value = UiMode.fromValue(p.getInt("ui_mode", UiMode.Miuix.value))
        _colorMode.value = ColorMode.fromValue(p.getInt("color_mode", ColorMode.SYSTEM.value))
        _enableMonet.value = p.getBoolean("enable_monet", false)
        _enableGlass.value = p.getBoolean("enable_glass", true)
        _enableGlassBlur.value = p.getBoolean("enable_glass_blur", true)
        _keyColor.value = p.getInt("key_color", 0)
        _paletteStyle.value = p.getString("palette_style", "TonalSpot") ?: "TonalSpot"
        _colorSpec.value = p.getString("color_spec", "SPEC_2025") ?: "SPEC_2025"
    }

    fun setUiMode(mode: UiMode) {
        _uiMode.value = mode
        prefs?.edit()?.putInt("ui_mode", mode.value)?.apply()
    }

    fun setColorMode(mode: ColorMode) {
        _colorMode.value = mode
        prefs?.edit()?.putInt("color_mode", mode.value)?.apply()
    }

    fun setEnableMonet(enabled: Boolean) {
        _enableMonet.value = enabled
        prefs?.edit()?.putBoolean("enable_monet", enabled)?.apply()
    }

    fun setEnableGlass(enabled: Boolean) {
        _enableGlass.value = enabled
        prefs?.edit()?.putBoolean("enable_glass", enabled)?.apply()
    }

    fun setEnableGlassBlur(enabled: Boolean) {
        _enableGlassBlur.value = enabled
        prefs?.edit()?.putBoolean("enable_glass_blur", enabled)?.apply()
    }

    fun setKeyColor(color: Int) {
        _keyColor.value = color
        prefs?.edit()?.putInt("key_color", color)?.apply()
    }

    fun setPaletteStyle(style: String) {
        _paletteStyle.value = style
        prefs?.edit()?.putString("palette_style", style)?.apply()
    }

    fun setColorSpec(spec: String) {
        _colorSpec.value = spec
        prefs?.edit()?.putString("color_spec", spec)?.apply()
    }
}
```

`App.kt` 的 `onCreate()` 开头加一行（只加这一行，其余不动）：
```kotlin
AppSettingsRepository.init(this)
```
含 `import com.remoteconfig.override.settings.AppSettingsRepository`。

- [ ] **Step 4: 运行测试确认通过**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.remoteconfig.override.settings.ColorModeTest"
```
Expected: PASS（3 个测试）

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(settings): UiMode/ColorMode models and observable AppSettingsRepository"
```

---

### Task 3: 双主题系统

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/theme/ThemeLocals.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/theme/RemoteConfigMaterialTheme.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/theme/RemoteConfigMiuixTheme.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/theme/Theme.kt`（重写为分发入口）

**Interfaces:**
- Consumes: Task 2 的 `UiMode`/`ColorMode`/`AppSettingsRepository`。
- Produces:
  - `val LocalUiMode: ProvidableCompositionLocal<UiMode>`
  - `val LocalEnableGlass: ProvidableCompositionLocal<Boolean>`、`val LocalEnableGlassBlur: ProvidableCompositionLocal<Boolean>`（默认 false——必须显式 provide）
  - `@Composable fun RemoteConfigTheme(content: @Composable () -> Unit)`（内部读 AppSettingsRepository）
  - `@Composable fun isInDarkTheme(): Boolean`
  - 主题取色（Task 2 仓库新增字段在此消费）：`keyColor: Int`（0=默认蓝）、`paletteStyle: String`（materialkolor `PaletteStyle` 名，默认 `"TonalSpot"`）、`colorSpec: String`（`"SPEC_2021"`/`"SPEC_2025"`）——Miuix 侧映射为 `ThemePaletteStyle`/`ThemeColorSpec`（参照 KernelSU `MiuixTheme.kt:33-43` 的 valueOf+try-catch 模式），Material 侧用 materialkolor `dynamicColorScheme(seedColor, paletteStyle, specVersion, ...)` 替换原 dynamicColorScheme 分支（对齐 KernelSU `MaterialTheme.kt`）。

- [ ] **Step 1: ThemeLocals.kt**

```kotlin
package com.remoteconfig.override.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.remoteconfig.override.settings.UiMode

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }

// 性能开关：默认必须为 false，由 MainActivity 显式 provide（对齐 KernelSU Theme.kt:135-139）
val LocalEnableGlass = staticCompositionLocalOf { false }
val LocalEnableGlassBlur = staticCompositionLocalOf { false }
```

- [ ] **Step 2: RemoteConfigMaterialTheme.kt（现有 Material 配色迁入）**

把现 `Theme.kt` 中的 `LightColors`/`DarkColors` 两个私有色表 + dynamicColorScheme 逻辑 + `MaterialTheme(...)` 包装整体搬到新文件（保留原代码，改函数名）：
```kotlin
package com.remoteconfig.override.ui.theme

// ... import 同原 Theme.kt ...

@Composable
fun RemoteConfigMaterialTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    // 原 RemoteConfigTheme 的 colorScheme 解析 + SideEffect 状态栏逻辑 + MaterialTheme(...) 原样迁入
    // 区别：darkTheme 改为参数传入；删除旧文件中的这两块
}
```
具体要求：`dynamicDarkColorScheme/dynamicLightColorScheme`（SDK≥S）+ `DarkColors`/`LightColors` 回退；`SideEffect` 中 `window.statusBarColor`/`navigationBarColor`/`isAppearanceLight*` 逻辑原样保留。

- [ ] **Step 3: RemoteConfigMiuixTheme.kt**

```kotlin
package com.remoteconfig.override.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.remoteconfig.override.settings.ColorMode

/** Miuix 默认蓝（对齐 miuix README 示例 keyColor）。 */
private val MiuixBlue = Color(0xFF3482FF)

@Composable
fun RemoteConfigMiuixTheme(
    colorMode: ColorMode,
    enableMonet: Boolean,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val mode = when {
        enableMonet && colorMode == ColorMode.SYSTEM -> ColorSchemeMode.MonetSystem
        enableMonet && colorMode == ColorMode.LIGHT -> ColorSchemeMode.MonetLight
        enableMonet && colorMode == ColorMode.DARK -> ColorSchemeMode.MonetDark
        colorMode == ColorMode.LIGHT -> ColorSchemeMode.Light
        colorMode == ColorMode.DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val controller = ThemeController(mode, keyColor = MiuixBlue)
    MiuixTheme(controller = controller) {
        val context = LocalContext.current
        LaunchedEffect(darkTheme) {
            val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        content()
    }
}
```
若 `ThemeController(mode, keyColor = ...)` 构造签名与 0.9.3 不符，参照 `KernelSU/manager/.../ui/theme/MiuixTheme.kt:54-67` 的具名参数写法修正（`ColorSchemeMode`/`keyColor`/`isDark` 均为具名参数）。

- [ ] **Step 4: Theme.kt 重写为分发入口**

```kotlin
package com.remoteconfig.override.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.remoteconfig.override.settings.AppSettingsRepository
import com.remoteconfig.override.settings.ColorMode

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

@Composable
fun RemoteConfigTheme(content: @Composable () -> Unit) {
    val darkTheme = isInDarkTheme()
    when (AppSettingsRepository.uiMode) {
        com.remoteconfig.override.settings.UiMode.Miuix ->
            RemoteConfigMiuixTheme(
                colorMode = AppSettingsRepository.colorMode,
                enableMonet = AppSettingsRepository.enableMonet,
                darkTheme = darkTheme,
                content = content,
            )
        com.remoteconfig.override.settings.UiMode.Material ->
            RemoteConfigMaterialTheme(darkTheme = darkTheme, content = content)
    }
}
```
注意：`Color.kt` 中的 `SuccessGreen`/`WarningOrange` 保留不动。

- [ ] **Step 5: 编译验证 + Commit**

```bash
cd android && ./gradlew :app:compileDebugKotlin
git add -A
git commit -m "feat(theme): dual-mode theme system (Miuix blue default + Material) with CompositionLocals"
```

---

### Task 4: Navigation3 路由 + MainActivity 重写

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/navigation/Routes.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/navigation/Navigator.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/MainActivity.kt`（重写）
- Delete: `android/app/src/main/kotlin/com/remoteconfig/override/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: Task 3 的 `RemoteConfigTheme`/`LocalUiMode`/`LocalEnableGlass`/`LocalEnableGlassBlur`。
- Produces:
  - `sealed interface Route : NavKey`：`data object Main : Route`、`data object ColorPalette : Route`、`data class ConfigEditor(val packageName: String) : Route`（@Parcelize）
  - `class Navigator(initialKey: NavKey)`：`val backStack: SnapshotStateList<NavKey>`、`fun push(key: NavKey)`、`fun pop()`、`fun current(): NavKey?`、`companion object { val Saver }`
  - `@Composable fun rememberNavigator(startRoute: NavKey): Navigator`
  - `val LocalNavigator: ProvidableCompositionLocal<Navigator>`

- [ ] **Step 1: Routes.kt**

```kotlin
package com.remoteconfig.override.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize

/** Navigation3 类型安全路由键。 */
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    data object Main : Route

    @Parcelize
    data object ColorPalette : Route

    @Parcelize
    data class ConfigEditor(val packageName: String) : Route
}
```

同时在 Task 4 的 MainActivity `entryProvider` 中加入（Step 3 代码相应扩展）：
```kotlin
entry<Route.ColorPalette> { ColorPaletteScreenPlaceholder() }
```
并创建临时占位 `ui/screens/ColorPaletteScreen.kt`（Task 11 替换为完整实现）：
```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ColorPaletteScreenPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("主题取色 — Task 11")
    }
}
```

- [ ] **Step 2: Navigator.kt（精简自 KernelSU Navigator.kt）**

移植 `KernelSU/manager/.../ui/navigation3/Navigator.kt`，保留：`backStack`、`push`、`pop`、`current`、`Saver`（restore 时的 fallback initialKey 改为 `Route.Main`）、`rememberNavigator`、`LocalNavigator`。删除：`replace`/`replaceAll`/`popUntil`/`navigateForResult`/`setResult`/`observeResult`/`clearResult`/`backStackSize`/resultBus（YAGNI——本 app 只有 push/pop）。

```kotlin
package com.remoteconfig.override.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

/** 简单导航器 — 持有返回栈。 */
class Navigator(initialKey: NavKey) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    fun current(): NavKey? = backStack.lastOrNull()

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { navigator -> navigator.backStack.toList() },
            restore = { savedList ->
                val initialKey = savedList.firstOrNull() ?: Route.Main
                val navigator = Navigator(initialKey)
                navigator.backStack.clear()
                navigator.backStack.addAll(savedList)
                navigator
            },
        )
    }
}

@Composable
fun rememberNavigator(startRoute: NavKey): Navigator {
    return rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
```

- [ ] **Step 3: MainActivity 重写**

对齐 `KernelSU/manager/.../ui/MainActivity.kt:108-212` 的结构（enableEdgeToEdge + CompositionLocals + Theme + NavDisplay），删除旧 NavGraph/导航 Compose 用法：

```kotlin
package com.remoteconfig.override

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Navigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.navigation.rememberNavigator
import com.remoteconfig.override.settings.AppSettingsRepository
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
                window.isNavigationBarContrastEnforced = false
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
```

说明：
- 旧代码里的 `Surface` 包装、`rememberNavController`、`NavGraph` 全部删除；`androidx.navigation.compose.*` import 清除。
- `MainScreen` / `viewModel.clearEditingConfig()` 在本任务先保编译：`MainScreen` 在 Task 5 创建；本步骤先创建一个最小占位（见 Step 4）。
- `collectAsStateWithLifecycle` import 若未用到则删除（本文件最终版可不带）。
- ConfigEditorScreen 的旧签名 `ConfigEditorScreen(viewModel, onBack)` 保持不变（Task 10 才改内部）。

- [ ] **Step 4: MainScreen 临时占位（Task 5 完善）**

`ui/screens/MainScreen.kt`：
```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.remoteconfig.override.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("MainScreen — Task 5")
    }
}
```

- [ ] **Step 5: 删除 NavGraph.kt，编译验证**

```bash
git rm android/app/src/main/kotlin/com/remoteconfig/override/navigation/NavGraph.kt
cd android && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL（ConfigListContent/HomeContent 旧文件暂未接线会有 unused 警告，可忽略）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(nav): migrate to Navigation3 with type-safe routes; rewrite MainActivity"
```

---

### Task 5: MainScreen — 3 tab Pager + 延迟组装

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/component/rememberContentReady.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/MainScreen.kt`（实现完整版）

**Interfaces:**
- Consumes: Task 4 的 `MainScreen(viewModel)` 占位签名（保持不变）。
- Produces:
  - `@Composable fun rememberContentReady(): Boolean`（转场结束后才 true，粘性）
  - `MainScreen(viewModel: MainViewModel)`：HorizontalPager 3 页 + 底部栏（本任务用临时 M3 NavigationBar，Task 6 换玻璃）

- [ ] **Step 1: rememberContentReady.kt**

移植 `KernelSU/manager/.../ui/util/DeferredContent.kt` 的 `rememberContentReady()`，import 路径改 `androidx.navigation3.ui.LocalNavAnimatedContentScope`（同源 API）。完整注释一并保留：
```kotlin
package com.remoteconfig.override.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * 仅在导航转场动画结束后 + 1 帧才返回 true。
 * 转场期间返回 false → 页面显示轻量占位（动画流畅）；
 * 动画结束 + 1 帧 → 重内容开始组装（此时页面已静止，卡顿不可见）。
 * 值是粘性的 —— 一旦 true 永不回退，退出转场时内容不闪。
 */
@Composable
fun rememberContentReady(): Boolean {
    val scope = LocalNavAnimatedContentScope.current
    val transitionRunning = scope.transition.isRunning
    val ready = remember { mutableStateOf(false) }

    LaunchedEffect(transitionRunning) {
        if (!transitionRunning && !ready.value) {
            withFrameNanos { }
            ready.value = true
        }
    }

    return ready.value
}
```

- [ ] **Step 2: MainScreen 完整实现**

对齐 KernelSU `MainActivity.kt:224-388` 的 MainScreen 模式（简化：无 badge、无 rail、无 pageScale）：

```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.remoteconfig.override.ui.component.rememberContentReady
import com.remoteconfig.override.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val PAGE_COUNT = 3

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })
    val contentReady = rememberContentReady()
    val scope = rememberCoroutineScope()
    // settledPage：停稳才触发重组，拖动中不逐帧
    val settledPage by remember { derivedStateOf { pagerState.settledPage } }

    Scaffold(
        bottomBar = {
            // 临时底栏；Task 6 替换为液态玻璃 GlassBottomBar
            NavigationBar {
                NavigationBarItem(selected = settledPage == 0, onClick = {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("首页") })
                NavigationBarItem(selected = settledPage == 1, onClick = {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }, icon = { Icon(Icons.Filled.List, null) }, label = { Text("配置") })
                NavigationBarItem(selected = settledPage == 2, onClick = {
                    scope.launch { pagerState.animateScrollToPage(2) }
                }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
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
                0 -> if (isCurrentPage || contentReady) HomePage(viewModel, isCurrentPage)
                1 -> if (isCurrentPage || contentReady) ConfigListPage(viewModel, isCurrentPage, onGameClick = { pkg ->
                    viewModel.loadConfig(pkg)
                }, onNewConfig = { pkg -> viewModel.createNewConfig(pkg) })
                2 -> if (isCurrentPage || contentReady) SettingsPlaceholder()
            }
        }
    }
}

@Composable
private fun SettingsPlaceholder() {
    Box contentAlignment = Alignment.Center { Text("设置 — Task 7") }
}
```

要点（性能，规格 9.2）：
- `beyondViewportPageCount = if (contentReady) 1 else 0`
- `overscrollEffect = null`
- 每页接收 `isCurrentPage`，页面用它做粘性首刷（Task 8/9 内实现 `hasActivated` 模式）
- 页面过渡期间旧内容保持（Pager 行为天然如此）

注意 `derivedStateOf { pagerState.settledPage }`：`settledPage` 本身就是 state，直接 `pagerState.settledPage` 读取即可（只在值变化时重组）——若 derivedStateOf 多余就去掉，直接读。

- [ ] **Step 3: 编译验证 + Commit**

```bash
cd android && ./gradlew :app:compileDebugKotlin
git add -A
git commit -m "feat(main): 3-tab pager with deferred content composition (rememberContentReady)"
```

---

### Task 6: 液态玻璃底栏（backdrop）

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/component/glass/DragGestureInspector.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/component/glass/DampedDragAnimation.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/component/glass/InteractiveHighlight.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/component/glass/GlassBottomBar.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/component/glass/rememberGlassBackdrop.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/MainScreen.kt`（换底栏）

**Interfaces:**
- Consumes: Task 3 的 `LocalEnableGlass`/`LocalEnableGlassBlur`、`isInDarkTheme()`。
- Produces:
  - `@Composable fun rememberGlassBackdrop(enabled: Boolean): LayerBackdrop?`（enabled=false 或 SDK<33 时返回 null）
  - `@Composable fun GlassBottomBar(selectedIndex: () -> Int, onSelected: (Int) -> Unit, backdrop: LayerBackdrop?, modifier: Modifier = Modifier, tabsCount: Int = 3, content: @Composable RowScope.() -> Unit)`（内部按 blurEnabled 分支：玻璃路径 / 纯色降级路径）
  - 性能模式：所有动画值只在 graphicsLayer/drawBackdrop lambda 中读；Animatable 全带 visibilityThreshold

- [ ] **Step 1: 移植三个手势/动画工具类**

从 `/tmp/alg-check/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/` 原样复制三个文件到 `ui/component/glass/`，仅改包名为 `com.remoteconfig.override.ui.component.glass`：
1. `DragGestureInspector.kt`（inspectDragGestures + 私有 drag/awaitDragOrUp）
2. `DampedDragAnimation.kt`（补上原文件缺失的 `import kotlinx.coroutines.android.awaitFrame`——catalog 源文件里 import 不全，移植时必须加上）
3. `InteractiveHighlight.kt`（imports: `com.kyant.backdrop.RuntimeShader` / `asComposeShader` / `isRuntimeShaderSupported` 均为 backdrop 2.0.1 公开 API，无需改动）

- [ ] **Step 2: rememberGlassBackdrop.kt**

```kotlin
package com.remoteconfig.override.ui.component.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.remoteconfig.override.ui.theme.isInDarkTheme

/**
 * 内容层 backdrop：先垫不透明 surface 色，再记录内容（规格 9.4）。
 * enabled=false 或系统不支持 RuntimeShader(API<33) 时返回 null → 下游自动走降级路径。
 */
@Composable
fun rememberGlassBackdrop(enabled: Boolean): com.kyant.backdrop.backdrops.LayerBackdrop? {
    if (!enabled || Build.VERSION.SDK_INT < 33) return null
    // 垫底色：Miuix/Material 的 surface 取不到时用深浅灰兜底
    val fallback = if (isInDarkTheme()) Color(0xFF171717) else Color(0xFFF7F7F7)
    return rememberLayerBackdrop {
        drawRect(fallback)
        drawContent()
    }
}
```
说明：`LayerBackdrop` 的确切包路径以 `com.kyant.backdrop.backdrops.LayerBackdrop`（见 backdrop 源码树 `backdrops/LayerBackdrop.kt`）为准；`rememberLayerBackdrop { drawRect(...); drawContent() }` 的 lambda receiver 为 DrawScope，同 catalog 用法。垫底色在 Task 7 接入真实主题色后可改为 `MiuixTheme.colorScheme.surface`——本任务先用固定兜底色，避免主题依赖。

- [ ] **Step 3: GlassBottomBar.kt**

以 `/tmp/alg-check/.../components/LiquidBottomTabs.kt`（完整代码见该文件，288 行）为基底移植，融合 KernelSU `FloatingBottomBar.kt:320-356` 的降级分支：

结构（保持 catalog 的三段式布局——玻璃面板 Row + alpha(0f) 离屏着色 Row + 指示器 Box）：
```kotlin
package com.remoteconfig.override.ui.component.glass

// imports: LiquidBottomTabs.kt 的 import 清单 + LocalEnableGlassBlur + MiuixTheme(若可用)

@Composable
fun GlassBottomBar(
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    tabsCount: Int = 3,
    content: @Composable RowScope.() -> Unit,
) {
    val glassEnabled = backdrop != null && LocalEnableGlass.current
    val blurEnabled = glassEnabled && LocalEnableGlassBlur.current

    if (glassEnabled) {
        // —— 玻璃路径：LiquidBottomTabs 的完整移植 ——
        // 参数对照：selectedTabIndex=selectedIndex, onTabSelected=onSelected,
        //           backdrop=backdrop, tabsCount=tabsCount
        // 颜色：accentColor = Color(0xFF0088FF)/深色 0xFF0091FF（catalog 原值，
        //       与 Miuix 蓝 0xFF3482FF 二选一后统一，建议用 MiuixBlue）
        //       containerColor = 浅色 0xFFFAFAFA.copy(0.4f) / 深色 0xFF121212.copy(0.4f)
        // effect 参数保持 catalog：blur(8f.dp.toPx()) + lens(24dp, 24dp)
        // 指示器：lens(10dp*progress, 14dp*progress, chromaticAberration = true)
        //         + Highlight.Default.copy(alpha=progress) + Shadow(alpha=progress)
        //         + InnerShadow(8dp*progress, alpha=progress)
    } else {
        // —— 降级路径：无 shader 的简单底栏（性能规格 9.1 零成本分支）——
        // Row(background(纯色, Capsule)) + indicator Box(clip(Capsule).background(accent.copy(0.15f))
        //     + graphicsLayer { translationX = 选中索引 * tabWidth })，
        // 指示器位移动画用 animateDpAsState(spring())
        // content() 照常渲染（tab 的 LiquidBottomTab 点击逻辑保留）
    }
}
```
实现要求：
- catalog 代码逐段移植，保留 `derivedStateOf` panelOffset、`snapshotFlow + collectLatest`、`DampedDragAnimation`（全部动画值只在 lambda 中读）
- 降级路径里也保留 `LiquidBottomTab` 的点击 + scale 动画（`LocalLiquidBottomTabScale` 同步移植，internal 改 public 或同包）
- `Capsule()` 来自 `com.kyant.shapes.Capsule`（backdrop 传递依赖 `io.github.kyant0:shapes`，无需额外添加）
- 若 `chromaticAberration = true` 参数在 2.0.1 签名不同（catalog 用 boolean，KernelSU 用 Float 0.5f），按编译器提示取 2.0.1 的实际签名

MainScreen 中的接线（替换 Step 2 的临时 NavigationBar）：
```kotlin
val glassBackdrop = rememberGlassBackdrop(enabled = LocalEnableGlass.current)
Scaffold(
    bottomBar = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            GlassBottomBar(
                selectedIndex = { settledPage },
                onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                backdrop = glassBackdrop,
            ) {
                GlassTab(icon = Icons.Filled.Home, label = "首页")  // 见下
                GlassTab(icon = Icons.Filled.List, label = "配置")
                GlassTab(icon = Icons.Filled.Settings, label = "设置")
            }
        }
    },
) { ... }
```
`GlassTab`（在同文件实现，对应 catalog LiquidBottomTab + KernelSU FloatingBottomBarItem）：
```kotlin
@Composable
fun RowScope.GlassTab(icon: ImageVector, label: String) {
    val scale = LocalGlassTabScale.current
    Column(
        Modifier
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClickHandledByParent)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s; scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
```
（点击处理沿用 catalog LiquidBottomTab 模式：tab 本身只做视觉，onSelected 由 GlassBottomBar 的 snapshotFlow 驱动——移植时保持 catalog 的 currentIndex 逻辑即可。）

- [ ] **Step 4: 编译验证 + 手动冒烟 + Commit**

```bash
cd android && ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。设备验证（如已连接）：底栏玻璃质感、点击切换带阻尼动画、设置关闭玻璃后为纯色底栏。
```bash
git add -A
git commit -m "feat(glass): liquid-glass bottom bar via AndroidLiquidGlass with graceful degradation"
```

---

### Task 7: 设置页（双实现）

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/SettingsScreen.kt`（分发器）
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/SettingsMiuix.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/SettingsMaterial.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/MainScreen.kt`（SettingsPlaceholder → SettingsScreen）

**Interfaces:**
- Consumes: `AppSettingsRepository` 全部 setter/getter；Task 3 locals。
- Produces: `@Composable fun SettingsContent(onNavigateAbout: () -> Unit = {})`（Miuix 版）+ `SettingsContentMaterial(...)`（Material 版）+ 分发器 `SettingsContent()` 按 LocalUiMode 选择。

- [ ] **Step 1: 分发器 SettingsScreen.kt**

```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode

@Composable
fun SettingsContent() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsContentMiuix()
        UiMode.Material -> SettingsContentMaterial()
    }
}
```

- [ ] **Step 2: SettingsMiuix.kt**

参照 `KernelSU/manager/.../screen/settings/SettingsMiuix.kt` 的组件用法（`Scaffold + TopAppBar(MiuixScrollBehavior) + Card + SwitchPreference/ArrowPreference`）：

```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.remoteconfig.override.settings.AppSettingsRepository
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.ui.theme.LocalEnableGlass
import com.remoteconfig.override.ui.theme.LocalEnableGlassBlur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsContentMiuix() {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                // 外观
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    SwitchPreference(
                        title = "MIUI 风格 (Miuix)",
                        summary = "切换 Miuix / Material 设计风格",
                        checked = AppSettingsRepository.uiMode == UiMode.Miuix,
                        onCheckedChange = { miuix ->
                            AppSettingsRepository.setUiMode(if (miuix) UiMode.Miuix else UiMode.Material)
                        },
                    )
                    SuperDropdown(
                        title = "深色模式",
                        items = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = AppSettingsRepository.colorMode.value,
                        onSelectedIndexChange = { idx ->
                            AppSettingsRepository.setColorMode(ColorMode.fromValue(idx))
                        },
                    )
                    SwitchPreference(
                        title = "动态取色 (Monet)",
                        summary = "跟随系统壁纸取色",
                        checked = AppSettingsRepository.enableMonet,
                        onCheckedChange = { AppSettingsRepository.setEnableMonet(it) },
                    )
                    ArrowPreference(
                        title = "主题取色",
                        summary = "自定义强调色与调色板风格",
                        onClick = { LocalNavigator.current.push(Route.ColorPalette) },
                    )
                }
                // 液态玻璃
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    SwitchPreference(
                        title = "液态玻璃底栏",
                        summary = "关闭后使用普通底栏（更省电）",
                        checked = AppSettingsRepository.enableGlass,
                        onCheckedChange = { AppSettingsRepository.setEnableGlass(it) },
                    )
                    if (AppSettingsRepository.enableGlass) {
                        SwitchPreference(
                            title = "底栏实时模糊",
                            summary = "关闭后保留玻璃质感但更省电",
                            checked = AppSettingsRepository.enableGlassBlur,
                            onCheckedChange = { AppSettingsRepository.setEnableGlassBlur(it) },
                        )
                    }
                }
                // 关于
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ArrowPreference(
                        title = "关于",
                        summary = "Color云控修改 v1.2.1",
                        onClick = { /* TODO: About 可复用首页作者卡，首版不跳转 */ },
                    )
                }
            }
        }
    }
}
```
注意：`SuperDropdown` 若在 0.9.3 中名为 `SuperDropDown` 或参数不同，参照 KernelSU `SettingsMiuix.kt` 使用的 `OverlayDropdownPreference` 或 docs（https://compose-miuix-ui.github.io/miuix/）调整；`LazyColumn` 的 `items` 都带 key（本页 item 少，单 item 无所谓）。`import androidx.compose.ui.unit.dp`、`UiMode` import 记得补。ArrowPreference 的 onClick 里 TODO 不允许——实现为跳转首页作者/仓库卡等同信息弹窗 `WindowDialog`（复用 Task 6 移植时如未移植 WindowDialog，则用 `SuperDialog`；最简：弹出 `androidx.compose.material3.AlertDialog` 版本信息，两种 UI 各自用自家对话框）。

- [ ] **Step 3: SettingsMaterial.kt**

将 Step 2 的选项原样用 Material 3 重写（`Scaffold + LargeTopAppBar + ElevatedCard + Switch/SegmentedButton`），行为一致、读同一 `AppSettingsRepository`。组件全部来自 `androidx.compose.material3.*`。结构：
- 「外观」卡：`Switch`（MIUI 风格）+ 三选项 `SegmentedButton`（跟随系统/浅色/深色）+ `Switch`（Monet）
- 「液态玻璃」卡：两个 `Switch`
- 「关于」项：`ListItem + ChevronRight`

- [ ] **Step 4: MainScreen 接线**

`MainScreen.kt` 中 `SettingsPlaceholder()` 替换为 `SettingsContent()`（含 `isCurrentPage` 粘性参数如 Task 8/9 模式，本页无数据加载可不需要）。删除占位函数。

- [ ] **Step 5: 编译 + Commit**

```bash
cd android && ./gradlew :app:assembleDebug
git add -A
git commit -m "feat(settings): dual-mode settings screen (ui mode / color mode / monet / glass switches)"
```

---

### Task 8: 首页双实现

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HomeScreen.kt`（重写为分发器 + HomePage 包装）
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HomeMaterial.kt`（现 HomeContent 迁入）
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HomeMiuix.kt`
- Delete: 旧 `HomeScreen.kt` 内容（被上述三文件替代）

**Interfaces:**
- Produces: `@Composable fun HomePage(viewModel: MainViewModel, isCurrentPage: Boolean)`（MainScreen 调用，签名与 Task 5 一致）；内部分发 `HomeContentMiuix(viewModel)` / `HomeContentMaterial(viewModel)`。

- [ ] **Step 1: 现有 HomeContent 移入 HomeMaterial.kt**

- 现 `HomeScreen.kt` 的 `HomeContent(viewModel, isActive, onNavigateConfig, modifier)` 改名 `HomeContentMaterial(viewModel: MainViewModel)`（删掉未用的 `isActive`/`onNavigateConfig`/`modifier` 参数——MainScreen 从不传它们；若体内用到则保留默认值）
- 包名不变，函数可见性 internal 或 private 均可
- 内部所有 Material3 组件用法保持不变

- [ ] **Step 2: HomeScreen.kt 改为分发器 + 粘性首刷**

```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.MainViewModel

@Composable
fun HomePage(viewModel: MainViewModel, isCurrentPage: Boolean) {
    // 粘性激活：首次成为当前页才触发刷新（性能规格 9.2，对齐 KernelSU HomeScreen.kt:50-56）
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshAll()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomeContentMiuix(viewModel)
        UiMode.Material -> HomeContentMaterial(viewModel)
    }
}
```

- [ ] **Step 3: HomeMiuix.kt**

内容分区与 Material 版一致（Root 状态卡 / 设备信息 / 作者·捐赠 / 源码入口），组件对齐 KernelSU `HomeMiuix.kt` 用法（`Scaffold + TopAppBar + MiuixScrollBehavior + Card + BasicComponent + TextButton`）：

```kotlin
package com.remoteconfig.override.ui.screens

@Composable
fun HomeContentMiuix(viewModel: MainViewModel) {
    val systemStatus by viewModel.systemStatus.collectAsState()
    val cosaVersion by viewModel.cosaVersion.collectAsState()
    val context = LocalContext.current
    val kernelVersion = remember { try { Os.uname().release } catch (_: Exception) { "未知" } }
    var showDonateDialog by remember { mutableStateOf(false) }
    var donateImageId by remember { mutableIntStateOf(0) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color云控修改", fontSize = 22.sp, fontWeight = FontWeight.Medium) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Root 状态卡：Card(containerColor 按 status 着色) + Row(Icon + 标题/副标题)
            //    三态文案与 Material 版完全一致（Root 权限正常/数据库连接失败/未授予 Root 权限）
            // ── 设备信息卡：Card { 4 × BasicComponent(title=标签, summary=值) 或 Row(Icon+两行文本) }
            //    项：设备型号 / 安卓版本 / 内核版本 / 应用增强服务 v$cosaVersion
            // ── 作者卡：BasicComponent(
            //        title = "Smartisan_Apple",
            //        summary = "酷安 @Smartisan_Apple",
            //        leftAction = 头像 Image(clip CircleShape),
            //        rightActions = 箭头 Icon,
            //        onClick = 酷安链接)
            //    + 捐赠按钮行：两个 TextButton(微信/支付宝, Icon + 文本)
            // ── 源码卡：BasicComponent(title="查看源代码", onClick = GitHub 链接)
        }
    }

    // 捐赠弹窗：WindowDialog(show=..., onDismissRequest=...) { Image(donateImageId) }
    // （WindowDialog 来自 top.yukonga.miuix.kmp.window，用法同 KernelSU DialogMiuix.kt:40-46）
}
```
实现要求：
- `collectAsState`（MainViewModel 的 StateFlow 不变）
- 状态三态的颜色映射用 `MiuixTheme.colorScheme`（primaryContainer/tertiaryContainer/errorContainer 对应正常/警告/错误）
- 所有 `Card` 用 `top.yukonga.miuix.kmp.basic.Card`（不是 material3 的）
- 圆角/间距对齐 Miuix 默认（Card 自带）；不设自定义 shape

- [ ] **Step 4: 编译 + Commit**

```bash
cd android && ./gradlew :app:assembleDebug
git add -A
git commit -m "feat(home): dual-mode home screen with sticky first-refresh"
```

---

### Task 9: 配置列表双实现

**Files:**
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigListScreen.kt`（分发器 + ConfigListPage）
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigListMaterial.kt`（现 ConfigListContent 迁入）
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigListMiuix.kt`
- Delete: 旧 `GameListScreen.kt`（内容已迁走）

**Interfaces:**
- Produces: `@Composable fun ConfigListPage(viewModel: MainViewModel, isCurrentPage: Boolean, onGameClick: (String) -> Unit, onNewConfig: (String) -> Unit)`（MainScreen 调用，签名与 Task 5 一致）

- [ ] **Step 1: 现有 ConfigListContent 迁入 ConfigListMaterial.kt**

- `GameListScreen.kt` 的 `ConfigListContent(viewModel, onGameClick, onNewConfig, onBack, isActive, modifier)` 改名 `ConfigListContentMaterial(viewModel, onGameClick, onNewConfig)`（去掉 MainScreen 不再使用的 `onBack`/`isActive`/`modifier`）
- `onGameClick` 不再内部导航（MainScreen 的回调里做 `viewModel.loadConfig + navigator.push(Route.ConfigEditor(pkg))`，见 Step 4）——确认现文件里 ConfigListContent 本身没有导航代码（它只调回调，无需改动）
- Material3 组件用法保持

- [ ] **Step 2: ConfigListScreen.kt 分发器**

```kotlin
package com.remoteconfig.override.ui.screens

@Composable
fun ConfigListPage(
    viewModel: MainViewModel,
    isCurrentPage: Boolean,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
) {
    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    LaunchedEffect(hasActivated) {
        if (hasActivated) viewModel.refreshAll()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigListContentMiuix(viewModel, onGameClick, onNewConfig)
        UiMode.Material -> ConfigListContentMaterial(viewModel, onGameClick, onNewConfig)
    }
}
```

- [ ] **Step 3: ConfigListMiuix.kt**

对齐 KernelSU `SuperUserMiuix.kt` 的列表模式（`LazyColumn + Card 分组 + BasicComponent 行 + key/contentType`）：

```kotlin
package com.remoteconfig.override.ui.screens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigListContentMiuix(
    viewModel: MainViewModel,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
) {
    val gameList by viewModel.gameList.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasDbData by viewModel.hasDbData.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var resultMsg by remember { mutableStateOf("") }
    var showResultDialog by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()
    val qt = searchQuery.trim().lowercase()
    val filteredGames by remember(gameList, qt) {
        derivedStateOf {
            if (qt.isEmpty()) gameList
            else gameList.filter { it.appName.lowercase().contains(qt) || it.packageName.lowercase().contains(qt) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("云控配置") },
                scrollBehavior = scrollBehavior,
                actions = {
                    // 搜索 IconButton → isSearching = true（搜索框用 top.yukonga.miuix.kmp.basic.TextField 置于 topBar 标题位，同 Material 版布局）
                    // 更多 IconButton → ListPopup/DropdownMenu：刷新配置 / 清除应用增强服务数据
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()  // miuix basic 有同名的用 miuix 的，否则 material3 的
                }
                filteredGames.isEmpty() -> /* 空态文案与 Material 版一致 */
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Text("共 ${filteredGames.size} 个应用", fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    items(filteredGames, key = { it.packageName }, contentType = { "app" }) { summary ->
                        // 应用图标（viewModel.getCachedIcon，BitmapPainter，同 Material 版逻辑）
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = if (summary.isInstalled) summary.appName else summary.packageName,
                                summary = summary.packageName,
                                leftAction = { AppIcon(summary.packageName, viewModel) },
                                rightActions = { Icon(MiuixIcons.ArrowRight?或 material KeyboardArrowRight, null) },
                                onClick = { onGameClick(summary.packageName) },
                                onLongClick = { showDeleteConfirm = summary.packageName },
                            )
                        }
                    }
                }
            }
        }
    }

    // 弹窗组：新建 / 结果 / 清除确认 / 删除确认
    // 全部用 WindowDialog + TextButton 双按钮（ConfirmDialogMiuix 模式，见 KernelSU DialogMiuix.kt:72-139）
    // 确认/取消文案与 Material 版逐字一致
}
```
要点：
- `items(key = { it.packageName }, contentType = { "app" })`（性能规格 9.5，强制）
- `BasicComponent` 若无 `onLongClick` 参数，包一层 `Modifier.combinedClickable`（indication 用 miuix 默认或 null）
- `MiuixIcons` 图标名不确定时用 material-icons-extended 的现有图标（`Icons.Default.KeyboardArrowRight` 等），Miuix 组件接受任意 ImageVector
- 长按删除、下拉搜索、新建对话框行为与 Material 版逐字一致

- [ ] **Step 4: MainScreen 回调接线（编辑器导航）**

`MainScreen.kt` 的 ConfigListPage 调用处改为：
```kotlin
val navigator = LocalNavigator.current
1 -> if (isCurrentPage || contentReady) ConfigListPage(
    viewModel, isCurrentPage,
    onGameClick = { pkg ->
        viewModel.loadConfig(pkg)
        navigator.push(Route.ConfigEditor(pkg))
    },
    onNewConfig = { pkg ->
        viewModel.createNewConfig(pkg)
        navigator.push(Route.ConfigEditor(pkg))
    },
)
```

- [ ] **Step 5: 编译 + Commit**

```bash
git rm android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/GameListScreen.kt
cd android && ./gradlew :app:assembleDebug
git add -A
git commit -m "feat(config-list): dual-mode config list with grouped Miuix cards"
```

---

### Task 10: 配置编辑器双模式外观

**Files:**
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigEditorScreen.kt`

**Interfaces:**
- Consumes: 现有编辑器核心（fieldValue/highlighted/cursorRect/gestureScale/行号等全部保留）；Task 3 locals。
- Produces: `ConfigEditorScreen(viewModel, onBack)`（签名不变——Task 4 已按此调用）。

- [ ] **Step 1: 保留编辑器核心，抽出 chrome 分支**

现有文件中**不动**的（自研编辑器核心，性能关键已优化）：
- `fieldValue`/`TextFieldValue` 状态同步、`highlighted` 后台防抖高亮、`cursorRect` 光标跟随、`transformable` 捏合缩放、行号 `lineNumbers` 缓存、`animateScrollTo` IME 跟随、`LaunchedEffect` 校验

需要按 `LocalUiMode.current` 分支的部分：
1. `TopAppBar`：Miuix 用 `top.yukonga.miuix.kmp.basic.TopAppBar`（title "JSON 编辑"，actions 同现有：缩放±/更多）；Material 用现有 M3 TopAppBar。两个分支共享同一套 action lambda（提取 `val editorActions: @Composable RowScope.() -> Unit`）
2. 对话框（结果弹窗）：Miuix 用 `WindowDialog`，Material 用现有 `AlertDialog`
3. `Scaffold`：Miuix 分支用 `top.yukonga.miuix.kmp.basic.Scaffold`
4. 颜色常量 `DARK_BG/LIGHT_BG/...` 保留（编辑器代码区配色两种模式通用——代码编辑器配色独立于主题是合理设计）

结构：
```kotlin
@Composable
fun ConfigEditorScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigEditorContent(viewModel, onBack, isMiuix = true)
        UiMode.Material -> ConfigEditorContent(viewModel, onBack, isMiuix = false)
    }
}

@Composable
private fun ConfigEditorContent(viewModel: MainViewModel, onBack: () -> Unit, isMiuix: Boolean) {
    // ……现有全部编辑器逻辑原样……
    // 仅 Scaffold/TopAppBar/对话框 三处按 isMiuix 分支
}
```

- [ ] **Step 2: 编译 + Commit**

```bash
cd android && ./gradlew :app:assembleDebug
git add -A
git commit -m "feat(editor): dual-mode editor chrome, self-contained highlighting core untouched"
```

---

### Task 11: ColorPalette 主题取色屏（双实现）

**Files:**
- Replace: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ColorPaletteScreen.kt`（占位 → 分发器）
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ColorPaletteMiuix.kt`
- Create: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ColorPaletteMaterial.kt`

**Interfaces:**
- Consumes: Task 2 仓库的 `keyColor`/`paletteStyle`/`colorSpec` 及其 setter；Task 4 `Route.ColorPalette`/`LocalNavigator`；materialkolor 5.0.0 的 `PaletteStyle`/`ColorSpec.SpecVersion`。
- Produces: `@Composable fun ColorPaletteScreen()`（完整实现，替换占位）。
- 参考实现：`KernelSU/manager/.../screen/colorpalette/`（ColorPaletteScreen.kt 分发器 + UiState + 双实现，本地已克隆）。

- [ ] **Step 1: 分发器 + 顶栏返回**

```kotlin
package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode

@Composable
fun ColorPaletteScreen() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorPaletteContentMiuix(onBack = { LocalNavigator.current.pop() })
        UiMode.Material -> ColorPaletteContentMaterial(onBack = { LocalNavigator.current.pop() })
    }
}
```

- [ ] **Step 2: 数据模型（对齐 KernelSU ColorPaletteUiState.kt）**

```kotlin
// 预设强调色板（与 KernelSU 预设色卡对齐，含恢复默认）
val PresetKeyColors = listOf(
    0xFF3482FF, // Miuix 默认蓝
    0xFFBA1A1A, 0xFFE8590C, 0xFFF08C00, 0xFF2B8A3E,
    0xFF0B7285, 0xFF1971C2, 0xFF6741D9, 0xFFC2255C,
)
val PaletteStyleNames = listOf(
    "TonalSpot", "Neutral", "Vibrant", "Expressive", "Rainbow", "FruitSalad", "Monochromatic",
)
```
说明：materialkolor 的 `PaletteStyle.valueOf(name)` 直接解析以上名称；`Monochromatic` 若编译报错改为 `Monochrome`（以 5.0.0 实际枚举为准，参照 KernelSU 用法）。

- [ ] **Step 3: ColorPaletteMiuix.kt**

对齐 KernelSU `ColorPaletteScreenMiuix.kt` 的结构（`Scaffold + TopAppBar(返回键) + Card 分组`）：

```kotlin
@Composable
fun ColorPaletteContentMiuix(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题取色") },
                navigationIcon = {
                    IconButton(onClick = onBack) { /* 返回箭头 Icon */ }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            // ── 强调色卡组：Card { 预设色圆形色卡 FlowRow/Grid，点击 → AppSettingsRepository.setKeyColor(color) }
            //    首项「跟随默认」(keyColor=0)；当前选中项描边高亮
            // ── 调色板风格组：Card { SuperDropdown(PaletteStyleNames) → setPaletteStyle(name) }
            // ── 颜色规范组：Card { SuperDropdown(listOf("SPEC_2021", "SPEC_2025")) → setColorSpec(name) }
            // ── 恢复默认：Card { TextButton("恢复默认取色") → setKeyColor(0); setPaletteStyle("TonalSpot"); setColorSpec("SPEC_2025") }
        }
    }
}
```
要点：色卡用 `Box(Modifier.size(40.dp).clip(CircleShape).background(Color(key)).border(...选中描边...))`，`Modifier.selectable` 包裹；所有写入直接调 `AppSettingsRepository`（主题即时响应，无需确认）。

- [ ] **Step 4: ColorPaletteMaterial.kt**

同样的四个分组用 M3 重写：`Scaffold + TopAppBar(返回) + ElevatedCard`；色卡 `FlowRow`；下拉用 `ExposedDropdownMenuBox` 或 `SegmentedButton`；行为与 Miuix 版逐字一致。

- [ ] **Step 5: 编译 + Commit**

```bash
cd android && ./gradlew :app:assembleDebug
git add -A
git commit -m "feat(theme): ColorPalette screen — preset key colors, palette style, color spec (dual-mode)"
```

---

### Task 12: 平板适配 — WindowSizeClass + NavigationRail + 双窗视图

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/MainScreen.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigListScreen.kt`
- Modify: `android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigEditorScreen.kt`

**Interfaces:**
- Produces:
  - `val LocalWindowWidthClass: ProvidableCompositionLocal<WindowWidthSizeClass>`（MainActivity 计算并 provide）
  - `@Composable fun isExpandedWidth(): Boolean`（≥840dp）
  - ConfigListPage 扩展可选参数：`dualPaneSelected: String? = null, onDualPaneSelect: (String) -> Unit = {}`（宽屏双窗模式，null = 窄屏路由模式）
  - ConfigEditorContent 扩展可选参数：`showBack: Boolean = true`（双窗模式右侧窗格隐藏返回键）

**背景（OPPO ColorOS 平板双窗视图调研结论）**：
- ColorOS「平行视窗/双窗视图」有两种系统侧方案：OPPO 自研 `EasyGoClient` + `assets/easygo.json`、以及 Google 原生 Activity Embedding（`res/xml` SplitPairRule）。两者均以 **Activity 跳转**为分屏单位。
- 本 app 是**单 Activity + Compose Navigation3**，列表→编辑器是同 Activity 内路由切换，系统无法感知两级页面 → 两种系统方案均不适用。
- 因此按 Google 标准方法**应用内实现双窗格（list-detail）**：`material3-window-size-class` 判宽，Expanded(≥840dp) 时配置页分栏（左列表 + 右编辑器）。
- 兼容基础（OPPO 指导书要求）：显式 `android:resizeableActivity="true"` + 不锁方向 + Compose 响应式布局，保证在系统分屏/平行视窗/自由窗口中正确重布局。

- [ ] **Step 1: AndroidManifest.xml 兼容声明**

`<application>` 标签加（若无）：
```xml
android:resizeableActivity="true"
```
确认 `<activity android:name=".MainActivity">` **没有** `android:screenOrientation`（不锁方向）。当前 Manifest 已满足不锁方向，只需补 resizeableActivity。

- [ ] **Step 2: MainActivity 计算 WindowSizeClass**

```kotlin
// onCreate 中：
val windowSize = calculateWindowSizeClass(this)
setContent {
    CompositionLocalProvider(
        LocalWindowWidthClass provides windowSize.widthSizeClass,
        // ...其余 provider 不变...
    ) { ... }
}
```
import：`androidx.compose.material3.windowsizeclass.calculateWindowSizeClass`、`WindowWidthSizeClass`。注意 `calculateWindowSizeClass` 是 `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`。

`ui/theme/ThemeLocals.kt` 追加：
```kotlin
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

@Composable
fun isExpandedWidth(): Boolean = LocalWindowWidthClass.current == WindowWidthSizeClass.Expanded
```

- [ ] **Step 3: MainScreen 宽屏 NavigationRail**

`MainScreen` 顶部读 `isExpandedWidth()`：
- **Expanded**：布局改为
```kotlin
Row {
    // 宽屏左侧导航 rail（替代底栏），双模式：
    if (isMiuix) MiuixNavRail(selectedPage = settledPage, onSelect = { scope.launch { pagerState.animateScrollToPage(it) } })
    else NavigationRail { /* 三个 NavigationRailItem，同底部栏 */ }
    HorizontalPager(Modifier.weight(1f), ...) { ... }
}
```
`MiuixNavRail`：用 miuix `NavigationRail` 组件（top.yukonga.miuix.kmp.basic.NavigationRail，参照 KernelSU `NavigationRailMiuix.kt`；若组件名不同以 KernelSU import 为准）。
- **Medium/Compact**：保持现有底栏（玻璃/降级）不变。
- Scaffold 的 `bottomBar` 仅在非 Expanded 时提供。

- [ ] **Step 4: 配置页双窗视图（list-detail）**

MainScreen 中 ConfigList 分支扩展（宽屏双窗，窄屏路由不变）：
```kotlin
var dualPaneSelected by rememberSaveable { mutableStateOf<String?>(null) }
val expanded = isExpandedWidth()

1 -> if (isCurrentPage || contentReady) ConfigListPage(
    viewModel, isCurrentPage,
    onGameClick = { pkg ->
        if (expanded) dualPaneSelected = pkg   // 宽屏：选中，右侧窗格显示
        else {
            viewModel.loadConfig(pkg)
            navigator.push(Route.ConfigEditor(pkg))
        }
    },
    onNewConfig = { pkg ->
        viewModel.createNewConfig(pkg)
        if (expanded) dualPaneSelected = pkg else navigator.push(Route.ConfigEditor(pkg))
    },
    dualPaneSelected = if (expanded) dualPaneSelected else null,
    onDualPaneSelect = { dualPaneSelected = it },
)
```

`ConfigListPage`/两个实现的可选参数处理：
```kotlin
@Composable
fun ConfigListPage(
    viewModel: MainViewModel,
    isCurrentPage: Boolean,
    onGameClick: (String) -> Unit,
    onNewConfig: (String) -> Unit,
    dualPaneSelected: String? = null,
    onDualPaneSelect: (String) -> Unit = {},
) {
    // 窄屏：照旧整页渲染
    // 宽屏（dualPaneSelected != null 判定不可靠——改用 isExpandedWidth()）：
    //   Row(Modifier.fillMaxSize()) {
    //       Box(Modifier.fillMaxWidth(0.42f)) { ConfigListContentXxx(viewModel, onGameClick, onNewConfig) }
    //       VerticalDivider()  // miuix HorizontalDivider 或 M3 VerticalDivider
    //       Box(Modifier.fillMaxSize()) {
    //           if (dualPaneSelected == null) 空态提示("选择左侧应用查看配置")
    //           else ConfigEditorPane(viewModel, dualPaneSelected, onClosed = { onDualPaneSelect("") })
    //       }
    //   }
}
```
注意：宽屏点列表项时由 MainScreen 回调决定选中；列表实现内部**不感知**双窗（`onGameClick` 语义由 MainScreen 定义）。选中项视觉高亮：把 `dualPaneSelected` 传入列表实现，行内 `if (summary.packageName == dualPaneSelected) 容器色高亮`（可选参数，默认 null）。

- [ ] **Step 5: ConfigEditorScreen 抽出可复用窗格**

Task 10 已有 `ConfigEditorContent(viewModel, onBack, isMiuix)`。扩展：
```kotlin
@Composable
fun ConfigEditorPane(viewModel: MainViewModel, packageName: String, onClosed: () -> Unit) {
    // 双窗右侧窗格：进入时加载配置（仿 ConfigEditorScreen 打开路径：
    // LaunchedEffect(packageName) { viewModel.loadConfig(packageName) } —
    // 注意 loadConfig 是异步挂起函数，需在协程中调用）
    // 顶栏 showBack=false（无返回箭头，有关闭 X → onClosed）
    // 内部复用 ConfigEditorContent 的全部编辑器逻辑
}
```
实现要点：`ConfigEditorContent` 增加 `packageName: String? = null` 可选参数（非空 = 双窗模式，由调用方已 loadConfig；空 = 路由模式，签名/行为与 Task 10 完全一致），保证窄屏路径零回归。

- [ ] **Step 6: 编译 + 平板验证 + Commit**

```bash
cd android && ./gradlew :app:assembleDebug
```
设备验证（平板/自由窗口，或手机横屏 + `adb shell wm size 1200x2400` 模拟）：
- 宽屏：左侧 rail 导航、配置页左列表右编辑器双窗、点列表右侧即时切换
- 窄屏：行为与之前完全一致
- 系统分屏/平行视窗/自由窗口模式下重布局正常（不崩溃不拉伸）

```bash
git add -A
git commit -m "feat(tablet): WindowSizeClass adaptive layout — navigation rail + config list-detail dual pane (Google standard, OPPO 双窗兼容)"
```

---

### Task 13: 终验 + 文档

**Files:**
- Modify: `README.md`（截图说明、技术栈段、双模式描述）
- Modify: `docs/superpowers/specs/2026-08-28-miuix-ui-rewrite-design.md`（如实现中有偏差需回写）

- [ ] **Step 1: 全量构建**

```bash
cd android && ./gradlew clean assembleDebug assembleRelease
```
Expected: 两个 variant 均 BUILD SUCCESSFUL；release 产物 `app/build/outputs/apk/release/app-release-unsigned.apk`。

- [ ] **Step 2: 手动验收清单（连接真机逐项确认，Root 设备）**

- [ ] 冷启动无白屏闪烁；状态栏/导航栏透明且图标颜色随深浅色
- [ ] 底部玻璃底栏：三 tab 切换阻尼动画流畅（开"开发者选项→GPU 渲染条"目测无持续掉帧）
- [ ] 设置→设计风格切到 Material：全局即时切换且无崩溃，切回 Miuix 正常
- [ ] 设置→深色模式三种状态正确；Monet 开关生效（Android 12+）
- [ ] 设置→关闭"液态玻璃底栏"：底栏变纯色、无模糊；开启恢复
- [ ] 首页：Root 状态卡三态、设备信息、捐赠弹窗、GitHub 链接
- [ ] 配置列表：搜索过滤、图标显示、点进编辑器、长按删除、新建配置、清除数据确认
- [ ] 编辑器：语法高亮、捏合缩放、光标跟随滚动、写入数据库成功（读回验证）
- [ ] 转场：进编辑器动画期间列表页占位不卡顿（contentReady 生效）
- [ ] 杀进程重开：tab 记忆、设置持久化

- [ ] **Step 3: 更新 README**

技术栈段加：Miuix 0.9.3、AndroidLiquidGlass 2.0.1、Navigation3、双 UI 模式说明（设置内切换）。构建要求 JDK 21。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: update README for Miuix dual-mode UI; final verification pass"
```

---

## 自审记录

- **规格覆盖**：工具链(§1→Task1)、主题(§2→Task2/3)、导航(§3→Task4/5)、屏幕(§4→Task7-10)、玻璃(§5→Task6)、逻辑不动(§6→全计划约束)、性能(§9→Task5/6 内嵌+全局约束)、验证(§7→Task1 Step1/Task13)——全覆盖。§4.4 设置页"关于"从简（复用版本文本，无独立 About 页）——首版 YAGNI。
- **增补（2026-08-28 用户指令）**：
  - 主题风格切换 + 取色（对齐 KernelSU ColorPalette）：Task 1 依赖加 material-kolor 5.0.0；Task 2 仓库加 keyColor/paletteStyle/colorSpec；Task 3 双主题消费取色参数；Task 4 路由加 ColorPalette；Task 7 设置页加入口；Task 11 取色屏双实现。
  - 平板适配（Google 标准方法）：Task 1 依赖加 material3-window-size-class；Task 12 = WindowSizeClass + 宽屏 NavigationRail + 配置页 list-detail 双窗格 + OPPO 双窗（平行视窗）兼容声明。调研结论：ColorOS 平行视窗（easygo.json / Activity Embedding）均以 Activity 跳变为分屏单位，单 Activity Compose 应用不适用，故采用应用内 list-detail + resizeableActivity 兼容路线。
- **占位符**：无 TBD/TODO；Task 6/8/9/11 的"参照移植"均给出确切源文件路径，代码骨架完整。
- **类型一致性**：`MainScreen(viewModel)`(T4→T5)、`HomePage(viewModel, isCurrentPage)`(T5→T8)、`ConfigListPage(viewModel, isCurrentPage, onGameClick, onNewConfig, dualPaneSelected?, onDualPaneSelect?)`(T5→T9→T12)、`ConfigEditorScreen(viewModel, onBack)`(T4→T10)、`ConfigEditorPane(viewModel, packageName, onClosed)`(T12)、`ColorPaletteScreen()`(T4 占位→T11)、`rememberGlassBackdrop(enabled): LayerBackdrop?`(T6)、`AppSettingsRepository.*`(T2→T3/7/11) 签名已对齐。
