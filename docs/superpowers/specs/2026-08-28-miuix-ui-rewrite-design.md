# Miuix 双模式 UI 重写 — 设计文档

日期：2026-08-28
分支：`MIUIX-test`
作者：Yunnijian

## 背景与目标

将 Oplus RemoteConfig Override（Color云控修改 v1.2.1）的 UI 从纯 Material 3 重写为 **Miuix（HyperOS/MIUI 设计语言）**，参考 **KernelSU Manager** 的成熟实现。

已确认的决策：
- 使用 **Miuix** 组件库（`top.yukonga.miuix.kmp`）
- **双模式可切换**：Material 3 / Miuix 两套 UI，设置中切换（对齐 KernelSU Manager 架构）
- 保留**底部导航**，底部 tab 与液态玻璃底栏使用 **AndroidLiquidGlass（backdrop）** 开源项目
- 配色：**Miuix 默认蓝白**
- 工具链：**对齐 KernelSU Manager 参考栈**（方案 A）
- 实施位置：工作目录即仓库根，在 `MIUIX-test` 分支开发
- 全部界面重写（首页 / 配置列表 / JSON 编辑器）+ 新增设置页

## 1. 工具链升级

当前 → 目标（对齐 KernelSU Manager）：

| 项 | 当前 | 目标 |
|---|---|---|
| Kotlin | 1.9.22 | 2.4.10 |
| AGP | 8.2.2 | 9.3.2 |
| Gradle | 8.5 | 9.7.1 |
| Compose BOM | 2024.02.00 | 2026.08.00（androidx Compose ~1.12.x）|
| Java（source/target + 构建）| 17（本机未装）| 21 |
| compileSdk / targetSdk | 34 | 36 |
| minSdk | 26 | 26（不变）|

迁移动作：
1. 移除 `app/build.gradle.kts` 中 `composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }`
2. 应用 `org.jetbrains.kotlin.plugin.compose`（版本 = Kotlin 版本 2.4.10）
3. 根 `build.gradle.kts`：`kotlin` 插件升到 2.4.10，`agp` 升到 9.3.2
4. `gradle-wrapper.properties`：Gradle 8.5 → 9.7.1
5. compileSdk/targetSdk 34 → 36
6. 新增依赖：
   - `top.yukonga.miuix.kmp:miuix-ui-android:0.9.3`
   - `top.yukonga.miuix.kmp:miuix-preference-android:0.9.3`
   - `top.yukonga.miuix.kmp:miuix-icons-android:0.9.3`
   - `top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3`
   - `io.github.kyant0:backdrop:2.0.1`（AndroidLiquidGlass）
   - `androidx.navigation3:navigation3-runtime:1.1.x`
   - 保留 material3（双模式需要）、material-icons-extended（Material 模式用）
7. **不引入** `org.jetbrains.compose` 插件，**不转 KMP**（Android-only 用 `-android` 构件即可）
8. Rust / NDK / abiFilters / useLegacyPackaging 配置**不受影响**

> ⚠️ 本机当前无 JDK，构建前需安装 JDK 21。

## 2. 主题系统（双模式）

完全参考 KernelSU Manager `ui/theme/` 架构：

- `UiMode`（`Miuix` / `Material`）+ `LocalUiMode` CompositionLocal
- `ColorMode` enum：SYSTEM / LIGHT / DARK / MONET_SYSTEM / MONET_LIGHT / MONET_DARK（含 AMOLED 可选）
- `AppSettings(colorMode, keyColor, paletteStyle, colorSpec)` + `ThemeController.getAppSettings()`
- `RemoteConfigTheme`（app 入口）按 `UiMode` 分发：
  - Miuix → `MiuixTheme(controller = ThemeController(mode, keyColor, ...))`
  - Material → `MaterialTheme(colorScheme = dynamicColor/fallback)`
- 默认配色：Miuix 蓝白（keyColor 蓝色种子色）
- 状态栏/导航栏图标深浅随 `darkTheme` 切换（参考 KernelSU `LaunchedEffect(darkTheme)` 写法）

新增设置项：**设计风格**（Miuix / Material）、深色模式（跟随系统/浅/深）、Monet 动态取色、强调色 keyColor。

## 3. 导航结构

- 从 Navigation Compose 迁移到 **Navigation3** + `miuix-navigation3-ui`
- 底部导航 **3 个 tab**：首页 / 配置 / 设置
- 底部栏 = **backdrop 液态玻璃浮动底栏**（见第 5 节）
- 路由：`main`（tab 容器）+ `config_editor/{packageName}`
- 页面过渡动画使用 miuix-navigation3-ui 提供的 Miuix 风格

## 4. 屏幕设计（每屏 Material + Miuix 双实现）

每个屏幕拆成 `<Screen>Material.kt` + `<Screen>Miuix.kt` + `<Screen>Screen.kt`（按 UiMode 分发），对齐 KernelSU Manager 惯例。

### 4.1 首页（Home）
- Root 状态卡（权限正常 / 数据库连接失败 / 未授权三态）
- 设备信息（型号 / Android 版本 / 内核 / 应用增强服务版本）
- 作者卡片（头像、酷安、版本号、微信/支付宝捐赠）
- 源码入口卡片
- 用 Miuix 卡片/分组 + Material 对应实现

### 4.2 配置列表（ConfigList）
- 顶部栏：标题 + 搜索 + 更多操作（刷新、清除数据）
- 列表：Miuix `SuperPreference` 风格分组项（应用图标 + 名称 + 包名 + 箭头）
- 底部 + 新建、长按删除、清除数据确认、结果弹窗
- Miuix Dialog / Material Dialog 双实现

### 4.3 配置编辑器（ConfigEditor）
- **保留自研 JSON 编辑器核心**（语法高亮、行号、捏合缩放、IME 光标跟随、后台高亮）
- 仅替换外观：Toolbar/标题栏、溢出菜单、对话框 → Miuix 风格
- 顶栏操作：缩小/放大、写入数据库、导入、导出

### 4.4 设置页（Settings，新增）
- 设计风格（Miuix / Material）
- 深色模式（跟随系统 / 浅色 / 深色）
- Monet 动态取色开关
- 强调色 keyColor
- 关于（版本信息）
- 用 Miuix `SuperPreference`/`PreferenceGroup` + Material 对应实现

## 5. 液态玻璃底部栏（backdrop）

- 用 backdrop 2.0.1 的 `drawBackdrop` / `rememberLayerBackdrop` / `Modifier.lens` 等实现**浮动液态玻璃底部导航条**
- 视觉参考：KernelSU Manager `FloatingBottomBar.kt`（高光描边 BloomStroke、透镜、设备倾斜视差）——但底层换成 backdrop 而非 miuix-blur
- backdrop 无高层组件，需基于其示例组件（LiquidBottomTabs 等）自建底部栏组件
- 底部栏含 3 个 tab：首页 / 配置 / 设置
- 首版液态玻璃仅用于底部栏；首页卡片玻璃化为可选扩展

## 6. 数据与逻辑层（不变）

- `MainViewModel`、`DatabaseManager`、Rust 工具（`libcosa.so`）、libsu 交互**完全不动**
- 纯 UI 层重写，ViewModel 接口保持兼容

## 7. 验证与环境

- 构建验证：`./gradlew assembleDebug`
- 前置：安装 JDK 21
- 验证项：三屏切换、双模式切换、底部 tab 切换、编辑器读写、设置持久化
- 设置持久化：SharedPreferences（对齐现有 `SettingsRepository` 思路，KernelSU 风格）

## 8. 里程碑

- **M1 工程升级**：工具链/依赖/编译器插件升级，能编译通过（双模式框架 + 简单页面验证）
- **M2 主题框架**：UiMode/ColorMode/ThemeController + 设置页雏形 + 底部液态玻璃导航
- **M3 屏幕重写**：首页 / 配置列表 / 配置编辑器双实现
- **M4 打磨**：对话框/菜单/动画对齐、深色与 Monet 验证、完整构建验证

## 参考实现

- KernelSU Manager：`KernelSU/manager/`（本地已克隆，浅克隆 `--depth 1`）—— 双模式主题、Miuix 组件用法、FloatingBottomBar 视觉
- AndroidLiquidGlass：`Kyant0/AndroidLiquidGlass`（backdrop 2.0.1）—— 液态玻璃 API
- Miuix：`compose-miuix-ui/miuix` v0.9.3 —— 组件库文档/示例
