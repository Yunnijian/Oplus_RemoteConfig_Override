# 功能页重做修改方案（2026-09-02）

范围：仅方案，不落代码。前置审查基于 2026-08-31-feature-page-param-redesign.md（spec v1.2.1）与当前代码实读。

## 〇、用户裁定（8 项 + 3 条澄清）

1. rust cli 拉取的每游戏 json 不再包含名单类段（`support_dynamic_refresh_rate_games`、`support_highfps_app` 等）——此类属通用配置，后续另设列表入口（本次只移除）。
2. `/game_booster/fisr_config/enhance_config` 不再出现在游戏单独 json。
3. 插帧超分页不展示：运行态、覆盖范围、策略 1/2、取值参考（无意义 / 不实际作用于 Novatek 插帧）。
4. 插帧超分页仅保留生效方法（Novatek 独显）：**FI 插帧 / SR 超分 / FISR 插帧超分** 三个功能卡；为 FI 与 FISR 降低修改难度：**预制插帧方案下拉（30-60 / 55-165 / 83-165 / 60-120 / 93-185）直接修改**；修复温度降档识别（连续温度组，兼容 2~N），预制温度偏移档（+10/+20/+30/+40℃）。
   - **澄清 1：只改每级前两组值（dynamicFps、targetFps），MEMC/MEMCMode 等 params 不动。**
   - **澄清 2：插帧超分及其温度组不再展示具体数据页（删除等级逐字段编辑弹窗），只保留功能卡。**
5. 温控限帧页：删除「低帧率统计阈值」（badfps_thresh1/2，已验证无修改意义）；温控开关关闭后**不隐藏**曲线入口，置灰不可点。
6. 通用配置页删除不可用的返回按钮。
7. 界面风格（Miuix/Material）切换后误跳通用配置页（HyperOS 遗留），核实并修。
8. 所有修改遵守当前写入纪律：修改即生效、选中即生效、无确认保存。

## 一、通用写回原则（既有，本次沿用）

- rust：`joyose-scoped-write`（force-stop + 双库镜像 + 回读校验）；Kotlin：`HyperOsViewModel.updateFragment*` 即时写回草稿，保存统一 scoped-write。
- 下拉/偏移/开关应用后**立即写回**，无“确定/保存”按钮。

## 二、分项方案

### 1. 名单类段移出游戏单独 json（rust）

现状：[appview.rs:598-639 ⑦](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/rust/src/joyose/appview.rs#L598-L639) 将 `support_highfps_app` 等 token 名单注入 per-app feature；[appview.rs:641-684 ⑧](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/rust/src/joyose/appview.rs#L641-L684) 将 `support_dynamic_refresh_rate_games` 等纯包名单注入 per-app feature；[appview.rs:215-259](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/rust/src/joyose/appview.rs#L215-L259) 索引计数同样纳入名单段。

改：
- ⑦ 移除 `("support_highfps_app", ':', "highfps", …)`（其余 migt/cgame_df/mqs/force_scale 保留）。
- ⑧ 移除归属于“通用名单”的字段：`support_scale_app_list`、`SOC_GameList`、`support_vrs_app`、`support_dynamic_refresh_rate_games`、`invalid_low_display_scenes_games`、`game_light_support_list`、`support_predownload_app_list`、`support_motor_app`（保留 `background_freeze_whitelist`？否——同属全局名单，一并移除；如功能页依赖再议）。
- `count_packages` ②/③ 同步去掉名单字段，避免“特性数”虚高。

后续：全局“支持列表”编辑（自定义包名 + fps 档位）另开新功能页（本次不做）。

### 2. fisr_config/enhance_config 移出游戏单独 json（rust）

现状：[appview.rs:540-570](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/rust/src/joyose/appview.rs#L540-L570) 将 `fisr_config/enhance_config{game_list…}` 注入 per-app（category "fisr"）。

改：移除该注入；Novatek（`novatek_game_params` 等）注入保留（真正生效路径）。由此插帧超分页 fragment（fisr_config/enhance_config）将不再出现 → 页面上「覆盖范围」「策略」等块自然消失，页面聚焦 Novatek（与第 3/4 点一致）。

### 3+4. 插帧超分页重做（HyperOsFisrScreen）

现状（[HyperOsFeatureScreens.kt:795-1099](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HyperOsFeatureScreens.kt#L795-L1099)）：
- 864-883「运行态（只读）」→ 删
- 885-893「覆盖范围（game_list）」→ 删（fisr_config 已移出）
- 895-968「策略 N + 字段表单」/ 970-981「+ 添加策略」→ 删
- 983-997「取值参考」→ 删
- 999-1052 Novatek FI/SR/FISR 三段卡（等级行 → NovatekLevelDialog 逐字段弹窗）→ 重构为**功能卡**
- 1055-1108 GEX / 非游玩降级 / 黑名单 → 保留（非方法卡内容，按需展示）

新 UI（仅功能卡，无数据页）：

```
插帧超分
├─ FI 插帧（卡）
│   ├─ 摘要行：当前 dynamicFps → targetFps（如 93 → 185fps）
│   ├─ 「应用插帧方案」下拉：30-60 / 55-165 / 83-165 / 60-120 / 93-185（选中即改写各级前两组）
│   ├─ 「温度档位偏移」下拉：+10/+20/+30/+40℃（整段所有等级温度组偏移）
│   └─ 温度摘要：TG 45/43 · MG 43/41（只读展示）
├─ SR 超分（卡）          （无插帧方案下拉；温度偏移适用）
├─ FISR 插帧超分（卡）    （同 FI：方案 + 温度偏移）
├─ GEX 帧率上限（保持）
└─ 非游玩降级档 / 独显黑名单（保持，仅展示/跳 JSON）
```

数据规则：
- **插帧方案只改前两组 fps（共 4 个数）**：
  1. 第一组 `dynamicFps#targetFps`（等级前 2 字段）
  2. 第二组 params 串开头 2 个 token `InputFPS,TargetFPS`
  其余 params（MEMC、MEMCMode、超分等）**原样保留**。
  段内所有等级统一应用方案 4 数；SR 段（`0#0#`）不提供方案下拉。
- 预制表（**全部取自 songyuan 配置内现成样例**，非推算）：

| 方案 | 写入值（dynamicFps#targetFps#InputFPS,TargetFPS） | 现成出处 |
|---|---|---|
| 30-60 | `31#60#30,60` | bilibili.fatego |
| 55-165（3x） | `55#165#55,165` | 大量游戏（mf.uam/AndroidAnimal/nfsonline…）|
| 83-165（2x） | `83#165#83,165` | tencent.ig / tmgp.gnyx |
| 60-120 | `61#120#60,120` | miHoYo.Yuanshen |
| 93-185 | `93#185#93,185` | pubgmhd / jkchess / cf / lolm |
| 73-144 | `73#144#72,144` | tmgp.sgame / sgamece |

注：样例间存在两种派生风——165/185 组 `dynamicFps==InputFPS`，120/144/60 组 `dynamicFps==InputFPS+1`；**套用方案时原样采用上述现成 4 数**，不做 +1 推断。

- **温度偏移**：解析温度组（tgTh/tgRec/mgTh/mgRec，容忍小数如 46.7 与数量 2~N），整体加偏移（保留组内差与 TG/MG 分组），选中即写回段内全部等级。
- **温度组识别修复**：[NovatekCodec.parseSegment](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/data/NovatekCodec.kt#L62-L71) 由“固定 7 字段，否则整段 null”放宽为：必含前 3 字段（dynamicFps#targetFps#params），温度字段 0~N 容错（缺省补空/0）；解析失败仍走原文只读兜底。段卡行展示温度组摘要（此前只显示 `dynamicFps → targetFps`，温度藏于弹窗，观感“只识别 45/43”）。
- **删除**：`NovatekLevelDialog` / `NovatekLevelBody`（[HyperOsFeatureUi.kt:1205-1307](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HyperOsFeatureUi.kt#L1205-L1307)）等级逐字段编辑入口与相关 state；页面不再进入具体数据页。

### 5. 温控限帧页

- 删除 [THERMAL_COMMON](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HyperOsFeatureScreens.kt#L51-L56) 中 `badfps_thresh1/2`。
- 开关不隐藏：[HyperOsViewModel.setThermalEnabled](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/viewmodel/HyperOsViewModel.kt#L466-L497) 关闭时改为仅置 `enabled=false`（持久化 `enabled_<pkg>`），**不再 updateFragmentRemoveKeys 删曲线**；删除 backup/恢复逻辑。UI 已有灰置（[editable/click=null](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HyperOsFeatureScreens.kt#L152-L154)）→ 曲线行保留、置灰不可点。

### 6. 通用配置页返回按钮

根因：HyperOS 下 `HyperOsCommonConfig` 以 **Pager page2 内嵌**（[MainScreen.kt:270-273](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/MainScreen.kt#L270-L277)），无 push 栈，`navigator.pop()` 无效。

改：`HyperOsCommonConfigScreen(embeddedInPager: Boolean = false)` 透传：
- 内嵌（MainScreen 调用）→ Miuix/Material 两套 [TopAppBar](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HyperOsCommonConfigMiuix.kt#L116-L128) / [LargeFlexibleTopAppBar](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/HyperOsCommonConfigMaterial.kt#L100-L104) 不渲染 navigationIcon；
- push 路由进入（MainActivity entry 挂载）→ 保留返回。

### 7. 切风格跳通用配置页修复

根因（实锤）：[MainPagerConfig](file:///Users/tubi/Desktop/Oplus_RemoteConfig_Override-1.2.1/Oplus_RemoteConfig_Override-1.2.1/android/app/src/main/kotlin/com/remoteconfig/override/viewmodel/MainActivityViewModel.kt#L116-L121) 固定 `PAGE_COUNT=3 / LAST_PAGE_INDEX=2`（ColorOS 遗留）。HyperOS 4 页（Settings=index3）：`setSelectedMainPage(3) → coercePage→2`，切风格重建后落回 **2=通用配置**；与用户观察一致（ColorOS 下第 3 页=Settings 不受影响）。

改：页面选择持久化不再按 ColorOS 页数 clamp——
- `MainPageState.updateSelectedPage` 保存原值（去掉 `coercePage`，或改为按上限 3 clamp）；
- `MainScreen` 以 `pageCount = { if (hyperOS) 4 else 3 }` 兜底越界（恢复时越界 index 由 Pager 截断或按平台再 clamp）。
- ColorOS 行为不变（3 页，Settings=2 正常）。

### 8. 即时生效

所有下拉/偏移/开关选择后立即 `updateFragment*` 写回草稿（同现有 Curve/Band 编辑语义），无确认步骤。

## 三、涉及文件清单

| 文件 | 改动 |
|---|---|
| rust/src/joyose/appview.rs | ⑦⑧ 名单段与 fisr/enhance_config 注入移除；count_packages 同步 |
| android/.../data/NovatekCodec.kt | parseSegment 温度组容错（2~N / 小数）；段温度组摘要提取 |
| android/.../ui/screens/HyperOsFeatureScreens.kt | FisrScreen 重构为功能卡；热控删 badfps；入口灰置保持 |
| android/.../ui/screens/HyperOsFeatureUi.kt | 删 NovatekLevelDialog/Body 及参数逐字段表单；新增方案/偏移下拉组件 |
| android/.../viewmodel/HyperOsViewModel.kt | setThermalEnabled 不删曲线；新增插帧方案/温度偏移写回接口 |
| android/.../ui/screens/HyperOsCommonConfigScreen.kt + Miuix/Material | embeddedInPager 控制返回按钮显隐 |
| android/.../viewmodel/MainActivityViewModel.kt | coercePage 按平台页数修正 |

## 四、验证清单

1. rust：`joyose-appview` 输出中游戏 json 不再含名单类段与 enhance_config；索引“特性数”回落。
2. 插帧超分页：无运行态/覆盖范围/策略/取值参考；三方法卡 + 方案下拉 + 温度偏移可用；选中即生效（logcat 可见 FrameMaster cmd 的 InputFPS/TargetFPS 变更，params 不变）。
3. 温度组：jkchess `45#43#43#41` 四值完整展示；原神 `46.7#45#43#41` 兼容；偏移 +N 后写回正确。
4. 温控限帧：开关关闭后曲线置灰不隐藏；badfps 行消失。
5. 通用配置：Pager 内嵌无返回按钮；push 进入保留。
6. 设置切 Miuix/Material 后停留在设置页（不再跳通用配置）；ColorOS 分页行为不变。