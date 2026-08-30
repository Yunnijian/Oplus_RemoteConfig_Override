# HyperOS 4 适配方案设计（调研/方案阶段）

日期：2026-08-30
状态：**方案已评审挂起**。2026-08-30 用户决策：HyperOS 4 适配暂停，待系统版本更新后再行兼容开发；现阶段实施严格限定 OS3，不承诺 HyperOS 4 兼容。本方案的 platform 检测/路由/双皮肤规范中与 OS3 无关的 4.x 特性部分随挂起一并冻结。
前置调研：[hyperos-platform-support-research](2026-08-30-hyperos-platform-support-research.md)、[rust-cli-and-booster-config-analysis](2026-08-30-hyperos-rust-cli-and-booster-config-analysis.md)、[cosa-cli-module-design](2026-08-30-cosa-cli-module-design.md)
实测基础：songyuan（HyperOS **3.0** / Android 16）；HyperOS 4 无实机（开放问题见 §8）。
文档管理：本文档按 2026-08-30 文档管理规范，未经书面指示不入库。

---

## 0. 目标与非目标

- 目标：同一 APK 内 ColorOS 与 HyperOS 4 双平台共存；HyperOS 侧以**结构化功能页**取代整文档 JSON 编辑模式；布尔开关集中管理；全部新 UI 严格遵循双皮肤（Miuix/Material）框架。
- 非目标：不改 ColorOS 现有交互；不做 CloudLab/出厂还原（远期）；本阶段不写实现代码。

---

## 1. 平台检测与动态界面分发（任务 a）

### 1.1 检测

```
Platform { ColorOS, HyperOS(hyperosMajor: Int) }
```

| 判据 | 属性 | 实测值 | 结论 |
|---|---|---|---|
| HyperOS 主判据 | `ro.mi.os.version.name` | `OS3.0`（songyuan） | 前缀 `OS` → HyperOS，主版本号解析出 hyperosMajor（3/4…） |
| HyperOS 辅判据 | `ro.miui.region` 非空 | `CN` | 兜底 |
| ColorOS 主判据 | `ro.build.version.oplusrom` 非空（或 `ro.oplus.version`） | OPD2515 实机可验 | — |

- 实现点：[OemHelper.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/ui/util/OemHelper.kt) 已有 `getSystemProperty`，注释中点名 KernelSU 的 `isMiui/isHyperOS/isColorOS` 即为同款。
- **决策（已实证）**：检测只看系统属性，**不做 DB 文件探测**——songyuan 上存在手工布置的 cosa DB 但无 com.oplus.cosa 包，文件探测会误判。
- 设置项：`SettingsRepository.platformMode: String`（`auto/coloros/hyperos`，默认 auto），走现有快照可观察仓库，无 settingsVersion hack。

### 1.2 分发机制

与现有 UI 模式分发**正交**，形成 2×2 矩阵：

```
MainActivity 根部 CompositionLocalProvider(LocalPlatform provides platform)
    │  （LocalUiMode 同级，单一事实源 = PlatformController/SettingsRepository）
    ▼
每个 *Screen.kt 分发器（与现有 HomeScreen/ConfigListScreen 同层）
    when (platform) {
        ColorOS -> 现有实现（零改动）
        HyperOS -> 新 HyperOS 实现
    } ；内层继续 when (uiMode) { Miuix -> ; Material -> }
```

- 新 HyperOS 屏幕只需实现 HyperOS×Miuix 与 HyperOS×Material 两个象限。
- 路由（[Routes.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/navigation/Routes.kt) sealed interface 扩展，保持类型安全）：
  - `Main / ColorPalette` 平台无关，共用
  - `ConfigEditor` 仅 ColorOS 入口（HyperOS 不再默认进入）
  - 新增 `HyperOsAppDetail(packageName: String)`、`HyperOsCommonConfig` 两条 NavKey
- 底栏：ColorOS 维持 3 tab（首页/配置/设置）；HyperOS 显示 4 tab（首页/**应用配置**/**通用配置**/设置）——MainPager 页数按平台分支，Miuix/Material 底栏组件 items 参数化即可（[BottomBarMiuix](../../../android/app/src/main/kotlin/com/remoteconfig/override/ui/component/bottombar/BottomBarMiuix.kt) / [BottomBarMaterial](../../../android/app/src/main/kotlin/com/remoteconfig/override/ui/component/bottombar/BottomBarMaterial.kt)）。
- 首页状态卡分支：ColorOS = cosa 版本/数据库状态；HyperOS = Joyose 双库状态 + teg 冻结状态 + 云控版本。

---

## 2. HyperOS 配置展示架构重构（任务 b）

### 2.1 技术依据（为什么 HyperOS 不适用 JSON 直接编辑写回）

1. **双库镜像**：同一份云控在 SmartP.db `cloud_config` 与 teg_config.db `rules` 各一份，散装 JSON 编辑无法保证双写一致（P1 的 `joyose-write` 单流程双写即为此设计）。
2. **版本联动**：`cloud_config.version` 列与 `params.header.version` 字符串需同步；自由编辑极易破坏版本语义导致云端覆盖行为不可预测。
3. **异构 per-app 形态**：§3 的 22 类功能结构分属 6 种数据形态（对象数组/包名数组/`_ : @ ;` 分隔 token/别名+cmdlines/包名键对象/白名单兜底），自由 JSON 编辑对用户不可读、对校验不可行。
4. 无损约束：写回必须保序、保未知字段（serde_json preserve_order 已备），任何"用户手编→重序列化"流程都放大破坏面。

**结论**：HyperOS 侧默认进入**结构化功能页**（定点读写），JSON 编辑降级为每条目的"高级编辑"入口（溢出菜单/长按），保留兜底能力。

### 2.2 页面结构

- **应用列表**（复用现有列表骨架：图标、搜索、双窗格）：数据源 = P1 `joyose-list`（cloud_config ∪ rules 模块）+ **P1.5 新增 `joyose-apps`** 批量子命令——一次遍历输出 `[{package, group, featureCount}]`（复用已实现的 resolve/collect 逻辑），避免在 Kotlin 重复实现六形态 join。
  - 列表项语义：HyperOS 下 = "有 per-app 配置命中的应用"（featureCount>0），另附"通用配置"常驻入口（不依赖具体 App）。
- **应用功能页 `HyperOsAppDetail(packageName)`**：
  - 数据 = `joyose-app <pkg>` 的 AppView（已实现并实机验证）
  - 布局：按 feature.category 分组的卡片 Section——头部 = 中文 label + source 徽标（direct/group_alias/fallback）+ gate 状态 pill（如 `cgame_enable=false` 时 cgame_df 卡片灰显标注"主开关已关闭"）；主体 = params 列表
  - params 渲染按类型：bool → 开关（可写）；token 串（novatek/migt/cgame_df）→ mono 只读 + 数值摘要（min_fps/target_fps 已解析）；对象（gpu_tuner 分档/xrender_config）→ 折叠展开；`overrides` 链 → 卡片内提示行；`conflicts` → 警告条
  - 顶部：App 图标/名称/组名/game_list·support_app 成员关系 + 「高级编辑」入口
- **宽屏**：沿用 material3-window-size-class 的 list-detail 基建（列表-功能页双窗格）。

---

## 3. Joyose 功能分类（任务 c）

以 AppView 的 22 类目 + 全局结构盘点（实测自 songyuan booster_config 277KB）：

### 3.1 应用专属（per-app，AppView features 承载）

| 组 | 类目 |
|---|---|
| 帧率/温控 | booster_override（锁帧/温度/场景/perflock）、cgame_df、highfps、dynamic_rr、low_rr_scenes、low_rr_invalid |
| 独显/过渲染 | novatek_main、novatek_non_playing、novatek_gex_limit、novatek_blacklist、mivk、migl、fisr、resolution_enhance |
| 调度/性能 | soc、gpu_tuner、migt、vrs、scenario_control |
| 其他 | mqs_enhance、scale、force_scale、game_light、predownload、motor |

展示机制：全部经 AppView 的 `features[]{category,label,source,path,params,gate,overrides}` 驱动 **HyperOsAppDetail** 卡片分组（§2.2），无需独立数据通道。

### 3.2 通用配置（全局，集中管理）

| 组 | 内容 | 管理方式 |
|---|---|---|
| **总开关** | `game_booster` 顶层 22 个 bool + `mivk/migl_settings.enable`（= AppView.global_switches） | **布尔开关模块（任务 d，可读写）** |
| 全局帧率 | dynamic_fps_global、recommend_tgame_thresh、support_display_refresh_rates、clusterList | 只读展示 → 远期结构化编辑 |
| 白名单 | common_config 的 game_list[168]/support_app[364] | 只读+搜索 → 远期编辑（P3） |
| 设备级 | vrs_soc、monitor、booster_debug_log_collect_config、scene_config/default_config、global_config | 只读展示 |
| 云控状态 | header.version、双库 version、teg 冻结状态（pref_local_max_version）、备份/回滚入口 | 状态卡 + 操作（对齐参考模块 LockView 语义：freeze/unfreeze、backup/revert） |

集中管理载体 = **`HyperOsCommonConfig` 页**（新增路由 + 三件套）。

---

## 4. 布尔开关通用配置模块（任务 d）

### 4.1 数据与读取

- 数据源：`AppView.global_switches`（已实现，文档序，含嵌套 enable）。
- 读取时机：页面进入 / onResume / 写操作完成后 → P1 `joyose-read booster_config`（DB 直读）→ 解析刷新 StateFlow。**不做** root inotify 常驻监听（inotifyd 收益差），以生命周期刷新 + 写后回读保证"实时同步"。
- 中文名映射：Kotlin 常量 `Map<String, String>`（22+2 项），业务含义来自调研报告 §2.2 表；分组：基础加速 / 游戏接管 / GPU 与渲染 / 显示与刷新率 / 系统行为。未知键（未来云控新增）落入"其他"组原样展示，不丢失。

### 4.2 切换写路径（事务语义）

```
用户点开关
→ 乐观更新 UI（快照可观察状态）
→ 后台事务：
   1. am force-stop com.xiaomi.joyose        （Kotlin 层，libsu）
   2. P1 joyose-write booster_config          （rusqlite 就地翻转该 bool，
                                               双库镜像写，busy_timeout）
   3. joyose-read 回读校验该键 == 目标值
   4. freezeOnWrite 设置（默认开）→ teg 冻结状态确认/补写
→ 成功：保持新状态 + 回读值刷新；失败：回滚 UI + Snackbar（含 stderr 详情）
```

- 并发守护：复用 [MainViewModel](../../../android/app/src/main/kotlin/com/remoteconfig/override/viewmodel/MainViewModel.kt) `_isWriting` 模式（root shell 最长 ~10s，防重复触发）。
- 版本策略：bool 翻转**不 bump** `version`/`header.version`（本地改动由 force-stop 生效 + 冻结防覆盖；bump 反而干扰版本语义）——与参考模块"编辑会话才刷版本"区分。
- 性能：单次写 = 277KB params 就地 UPDATE，毫秒级；与"去锁帧/温度解锁"等批量变换共用 `joyose-write` 唯一写入口。
- 冻结副作用 UI 明示（冻结所有 teg 云控模块），解冻入口常驻状态卡。

### 4.3 UI 形态

- `HyperOsCommonConfigScreen.kt`（分发器）+ Miuix/Material 双实现
- 开关控件：Miuix `SuperSwitch` / Material `ExpressiveSwitch`（两者均已在组件库）
- 每组一个 Section 卡片；开关行 = 中文名 + 属性名（mono 小字）+ Switch；gate 关联提示（如关闭 `cgame_enable` 时提示"将影响 App 功能页中的 cgame_df 项"——利用 §3 gate 映射）

---

## 5. 双皮肤框架遵循（任务 e）

- **三件套约定**：每个新页面 = `XxxScreen.kt`（分发器：platform→uiMode 两级 when）+ `XxxMiuix.kt` + `XxxMaterial.kt`；命名/目录对齐现有 `ui/screens/`。
- **控件映射**（对齐 KernelSU 移植组件库）：卡片 → Miuix SuperCard / Material TonalCard；开关 → SuperSwitch / ExpressiveSwitch；分段列表 → SegmentedList；状态徽标 → StatusTag；顶栏 → 各主题 TopBar；空态/加载 → 各主题既有占位组件。
- **主题令牌**：颜色一律经 [ThemeLocals](../../../android/app/src/main/kotlin/com/remoteconfig/override/ui/theme/ThemeLocals.kt) / RemoteConfigTheme 读取（含取色 keyColor/palette style 联动），禁止硬编码色值；排版走各自 theme typography。
- **工程约定继承**：重内容组合 `rememberContentReady` 门控（功能页 277KB 解析结果大，读路径放 IO 线程，组合期只读结果）；`DampedDragAnimation`/`InteractiveHighlight` 不做时序改动（承重墙）。
- 宽屏（WindowSizeClass）与底栏玻璃效果在 HyperOS 页面同样生效，无平台特殊化。

---

## 6. 数据流总图

```
属性检测 ─► Platform ─► LocalPlatform ─► 屏幕分发（platform × uiMode）
                                    │
root shell (libsu) ─► libcosa.so    │
   ├─ joyose-list / joyose-apps ──► 应用列表（图标缓存复用 MainViewModel 模式）
   ├─ joyose-app <pkg> ──────────► HyperOsAppDetail（AppView → 卡片分组）
   ├─ joyose-read booster_config ─► HyperOsCommonConfig（global_switches → 开关）
   ├─ joyose-write booster_config ◄─ 开关切换 / 批量变换（唯一写入口，双库镜像）
   ├─ joyose-freeze/unfreeze ─────► 云控状态卡
   └─ joyose-backup/revert ───────► 备份回滚入口
生效：每次写后 force-stop Joyose；（默认）冻结防云控覆盖
```

## 7. 分阶段落地计划（每步可构建可验证）

| 阶段 | 内容 | 依赖 |
|---|---|---|
| P1 | Rust：joyose-stat/list/read/write/freeze/unfreeze/backup/revert（DB 源） | 已设计（cosa-cli-module-design §7） |
| P1.5 | Rust：`joyose-apps` 批量列表聚合 | P1 |
| P2.0 | Platform 检测 + LocalPlatform + 设置项 + 底栏/路由分支（HyperOS 界面壳，仅占位） | 无（可先行） |
| P2.1 | HyperOsAppDetail 读路径（AppView → 双皮肤卡片） | P1、P1.5 |
| P2.2 | HyperOsCommonConfig 布尔模块（写事务 + 冻结联动） | P1 |
| P2.3 | 双窗格、图标、空态/错误态、首页状态卡打磨 | P2.1/2.2 |
| P3 | 白名单编辑、全局帧率结构化编辑、CloudLab | 远期 |

## 8. 风险与开放问题

1. **HyperOS 4 无实机**：4.x 的 Joyose schema（表/列/字段）可能演进 → P1 的 joyose-* 命令按"列名白名单 + PRAGMA 校验，缺失列报错不崩溃"设计自适配面；拿到 OS4 设备后先跑只读探测（对齐本次 §3.1 调研流程）。
2. OS3 实测样本（songyuan）作为 golden 基线；OS4 上差异部分需重采集。
3. MCC 端点/appVersion 在 OS4 是否变化 → CloudLab（P3）阶段核实。
4. 布尔写不 bump version 的策略依赖"冻结防覆盖"，若用户拒绝冻结则需在 UI 提示覆盖风险（对齐参考模块 LockView 文案）。
5. HyperOS 深度定制（澎湃 OS 4 游戏加速架构调整）可能引入新 config_name / rule_module → joyose-list 按"白名单 + 已存在项"双轨呈现，未知模块只读展示。
