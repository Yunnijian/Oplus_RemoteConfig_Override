# HyperOS 支持补充调研 — Rust CLI 方案对比 & booster_config 结构解析

日期：2026-08-30
前置：[2026-08-30-hyperos-platform-support-research.md](2026-08-30-hyperos-platform-support-research.md)
数据来源：songyuan 实机样本（SmartP.db booster_config params 277,867B / teg_config.db common_config rule 13KB），分析脚本直接解析原始 JSON，非猜测。

---

## Part 1. Rust CLI 方案对比：扩展现有 cosa vs 全新独立工具

### 1.1 两方案定义

- **方案 A（扩展）**：在现有 `rust/src/main.rs`（cosa 二进制，~530 行，rusqlite + serde_json）内新增 `joyose-*` 子命令族，单二进制随 APK 分发（`libcosa.so` 语义上变成"多平台数据库工具"）。
- **方案 B（新建）**：独立 Cargo 工程/二进制（如 `libjoyose.so`），与 cosa 并列放入 `jniLibs/arm64-v8a/`，Kotlin 层按平台选择调用。

（附：**方案 C（Cargo workspace：共享 lib + 两个 bin）** 为 B 的整洁形态，一并纳入比较。）

### 1.2 多维度对比

| 维度 | A 扩展现有 CLI | B 独立新工具 | C workspace 双 bin |
|---|---|---|---|
| **开发成本** | 低。rusqlite/serde_json 均为现有依赖（[Cargo.toml](../../../rust/Cargo.toml) 已引入），无新脚手架、无新打包步骤；输出协议（`{"ok":..}`/eprintln）、`finish()`/`chown_sidecars`/`columns()`/`value_to_json` 等基建直接复用 | 中高。新建工程 + build-android.sh 扩展两份产物自检 + jniLibs 双文件 + DatabaseManager 增加二进制定位逻辑 | 中。基建下沉共享 lib 的一次性重构成本，之后两平台各自演进 |
| **兼容性风险** | 低。现有子命令零改动（新增 case 分支），回归面 = 现有 cosa 路径手动冒烟一次 | 低但分散。双二进制各自版本漂移（一个升一个忘升）；APK 里旧 libcosa.so 与新工具配错版本的风险 | 同 A 的共享 lib 后回归面小；但打包/产物链路复杂度最高 |
| **功能完整性** | 完全等价（同一进程内想做什么都行） | 完全等价 | 完全等价 |
| **维护难度** | 单文件增长到 ~1000 行后需要**模块拆分**（`src/cmd/cosa.rs`、`src/cmd/joyose.rs`、`src/db.rs`、`src/json.rs`），拆完可读性反而优于现状 | 两个独立工程，重复逻辑（SQLite 魔数校验、sidecar 处理、JSON 输出协议）各自复制或各自造 lib | 结构最清晰，但维护三个 crate 的版本/发布心智负担 |
| **性能优化空间** | 同 B（CLI 短生命周期，277KB JSON 单次解析 ~ms 级，性能不构成决策因子；未来若要长驻服务，A 可平滑演进为同一二进制内 subcommand daemon） | 同左 | 同左 |
| **团队技术栈匹配** | 与现有仓库单 Cargo 包形态一致；build-android.sh 自检脚本一处维护 | 需要 CI/脚本/文档全面知晓双产物 | Rust workspace 概念简单但本项目无先例 |
| **APK 体积** | +0 个文件（同 .so 增 ~20-40KB） | +1 个文件（各 ~1-2MB 起点，bundled sqlite 双份）| +1 个文件（sqlite 可链接共享 lib 省一份，但仍双 .so 条目） |
| **调用端（Kotlin）** | DatabaseManager 现有 `binary` 路径逻辑不变，仅按命令分发 | 需按平台选二进制；错误排查时先分清"哪个工具" | 同 B |

### 1.3 决策建议

**选方案 A（扩展现有 CLI），附带一次预防性模块拆分。** 理由压缩为三条：

1. **依赖与基建 100% 重叠**：joyose 需要的能力（bundled SQLite 就地读写、JSON 解析/序列化、文件属主保护、错误输出协议）恰好就是 cosa 已实现的全部；方案 B 的"隔离"换不来任何能力增益，只带来双产物漂移风险。
2. **打包与调用链单点**：`useLegacyPackaging` 的单可执行文件 + `nativeLibraryDir` 定位 + `build-android.sh` 产物自检，整条链路零改动。
3. **成本唯一注意点**是 main.rs 体量，用目录内模块拆分消化（不动 Cargo 结构、不动打包）：

```
rust/src/
├── main.rs          # 仅参数分发
├── common/          # db 打开/列信息/值转换/chown_sidecar/finish/json 输出协议
├── cmd/cosa.rs      # list|read|write|delete|protect（原逻辑平移）
└── cmd/joyose.rs    # stat|list|read|write|freeze|unfreeze|backup|revert
```

若未来出现"两平台发布节奏/依赖冲突真的撕裂"的信号，再迁移到方案 C（共享 lib 已在 A 的拆分中就位，迁移成本近似为零）。**不建议裸方案 B。**

---

## Part 2. booster_config JSON 结构完整解析（实测）

### 2.1 总览：云控数据的三层来源

| 层 | 位置 | 内容 |
|---|---|---|
| 主配置 | SmartP.db `cloud_config.params`（config_name=booster_config） | `{header, game_booster}`，game_booster 60 键 / 277KB |
| 白名单 | teg_config.db `rules.rule_content`（rule_module=common_config） | 外层是"伪 cloud_config 行"包装（config_name/enable/group_name/version/with_model/**params**），params 内含 `game_list`[168]、`support_app`[364]、`debug_log_collect_config` |
| 出厂 | `/odm/etc/default_cloud.json`（AES-256/ECB） | 三模块出厂值，仅在"恢复出厂"时使用 |

### 2.2 booster_config 层级与字段（按业务类别归组）

```
params
├── header
│   ├── version: str "2026072751"        # 云控版本（与 DB version 列同步，2099 锁的作用点）
│   ├── network_improve: bool true
│   ├── index_enable: bool true
│   ├── mqs_enable: bool true
│   └── lazy_load_booster_config: bool false
└── game_booster (60 keys)
```

**① 全局开关（bool，20 个）**：`booster_enable`、`cpuset_enable`、`tuner_enable`、`qsync_enable`、`action_key_optimized`、`fisr_mqs_v2`、`key_mivk_gputuner_select_enable`、`support_new_gpu_tunermode`、`game_group_mapping_enable`、`cgame_enable`（云控游戏接管/锁帧总开关）、`SOC_enable`、`self_gpu_tuner_enable`、`affinity_enable`、`support_ysre`、`scene_id_reuse`、`GameSight`、`scene_id_sender_enable`、`predownload_enable`、`multi_windows_v2`、`background_freeze_enable`、`scale_app_enable`、`qfps_enable`、`force_set_drr_path`。

**② 设备/系统级参数**：`monitor{monitor_enable,analytics_enable,default_interval…}`、`support_display_refresh_rates[6]`(60…185)、`clusterList[2]`(0,6)、`vrs_soc:"8850"`、`recommend_tgame_thresh:"44#90"`（温度#帧率 token 串）、`booster_debug_log_collect_config{5}`、`dynamic_fps_global{dynamic_fps:"10:0,43:120,45:90,47:60,48:45", dynamic_fps_M:…}`（温度:帧率降档曲线，去锁帧目标之一）、`global_config{start[],end[]}`（进入/退出游戏的通用 perflock cmd）。

**③ 场景级（非 per-app）**：`booster_config{default_config[], scene_config[2]{scene_id,scene_name,booster[{permission:"root",cmd:"perflock#…|glk#…"}]}}`、`game_scenario_control_config{package_list,game_stop_notify}`。

**④ 按应用维度（核心，形态多样）**：

| 字段 | 类型/形态 | App 键 | 实测 | 业务含义 |
|---|---|---|---|---|
| `booster_config.ovrride_config` | array[40] 对象 | `game_name`（**组别名或包名**，见 2.3） | SGAME/LOLM/PUBG/COD/YUANSHEN/NAP/BH3/HKRPG/… | **单游戏主配置**：start/end_scene、badfps_thresh1/2（"54,81,108"）、dynamic_fps/_M（"10:0,46:121,48:60" 温度:帧率）、dynamicfps_by_battery_T/M、dynamic_fps_hysteresis、PID_T/PID_M、`scene_ovrride[]{scene_id,scene_name,booster[].cmd}`（每场景 perflock/glk 指令） |
| `game_group_mapping_config` | array[20] | `game_group_name`→`package_list[]` | SGAME→[sgame,sgamece,…]、YUANSHEN→[Yuanshen,GenshinImpact,ys.bilibili,cb]、PUBG→[pubgmhd,ig,gnyx] | **组名→包名映射**，ovrride/scene 键解析的必经 join 表 |
| `novatek_game_params` | array[115] 字符串 | 前缀（真实包名） | `pkg_minFps#target#…(4 段下划线，段内 # 分隔，含温度组)` | 红米独显帧率/温度档 |
| `novatek_extend_config.novatek_non_playing_config` | string[] | 前缀 | 同上格式 | 非游戏/非游玩中档位 |
| `novatek_extend_config.novatek_gex_fps_limit` | `"pkg:fps"`[] | 包名 | sgame:60 等 | GEX 帧率上限 |
| `novatek_black_app` | string[] | 包名 | 13 项 | 独显黑名单 |
| `mivk_settings{enable, app_params[]}` | app_params[].`app`(别名 hkrpg)+`app_cmdlines[]`(包名)+`xrender_config{checkMainInfo,support_module["misr:0",…],…}`+`drr_static` | app_cmdlines | 原神/星铁 | Vulkan 通路过渲染配置 |
| `migl_settings{enable, game_params[]}` | 同上（game 别名 pubg/jkchess） | game_cmdlines | 3 项 | GL 通路，含 `drr{drr_by_temp_T/M}` 温度-分辨率档 |
| `fisr_config.enhance_config[]` | `{game_list[], enhance_policy_config[{feature:PQ_FIRST/FPS_FIRST, strategy:"NT#FISR/FI"}]}` | game_list | 原神/星铁/pubgm/OTHER | FISR 插帧超分策略 |
| `self_gpu_tuner_config` | object，键=包名 | 直接包名 | 4 包 × {CUSTOMIZE,POWERSAVE,BALANCE,HIGH_QUALITY,STANDARD}×{系统属性} | 高通 GPU tuner 每档属性 |
| `migt` | array[94] 字符串 `pkg;freq表;…;温度权重;…` | 分号首段包名 | pubgmhd/ig/… | Migt 温控调频脚本 |
| `cgame_df` | `"pkg@fps#10:0,42:90"`[] | @ 前包名 | sgame@120、Yuanshen@60 | 云控游戏接管动态帧率 |
| `support_highfps_app` | `"pkg:fps"`[] | 冒号前包名 | 139 项 | 高帧率白名单 |
| `mqs_enhance_list` | `"pkg:60#default"`[] | 冒号前 | 21 项 | MQS 增强名单 |
| `support_scale_app_list` / `force_scale_app_list` | `"pkg"` / `"pkg:480#1.6:activity"`[] | 冒号前 | 50/8 项 | 分辨率缩放/强制缩放 |
| `SOC_GameList`、`support_vrs_app`、`support_dynamic_refresh_rate_games`、`invalid_low_display_scenes_games`、`game_light_support_list`、`support_predownload_app_list`、`support_motor_app` | string[] | 直接包名 | 9/8/26/2/3/4/0 项 | 各特性白名单 |
| `low_display_refresh_rate_scenes_by_single_game` | object 键=包名 → 场景 id 数组 | 直接包名 | sgame | 单游戏低刷场景 |
| `support_resolution_enhance_config[]` | `{pkg, isSupportHotSwap}` | pkg 字段 | 5 项 | 分辨率增强 |
| `background_freeze_whitelist` | string[] | 包名 | joyose/powerkeeper/… | 后台冻结白名单 |

**⑤ common_config（teg）**：`params.game_list`（正式优化名单，168）、`params.support_app`（更宽支持名单，364）、`debug_log_collect_config`。Joyose 判定一个应用是否纳入策略 = 白名单 ∪ 各 per-app 结构命中。

### 2.3 关键解析规则：game_name ≠ 包名

实测 `ovrride_config.game_name` 的 40 个值中大量是**组别名**（SGAME、PUBG、COD、YUANSHEN、NiShuiHan、NAP、BH3、HKRPG、DWRG…），仅部分是直接包名（com.tencent.lolm、com.hypergryph.arknights…）。**按包名定位一个 App 的完整配置必须做多表 join**：

```
resolve(pkg):
  1. game_group_mapping_config: 找到 package_list ∋ pkg 的组 → 得到别名集合（组名）
  2. ovrride_config:            game_name == pkg 或 game_name ∈ 别名集合
  3. novatek/migt/cgame_df/highfps/scale/…: 前缀或分隔符首段 == pkg
  4. mivk/migl:                 app_cmdlines ∋ pkg → 对应 app 参数条目
  5. fisr_config.enhance_config[].game_list ∋ pkg（或 == "OTHER" 兜底）
  6. common_config:             game_list/support_app 成员关系
```

### 2.4 Rust 层按 App 维度解析实现方案

**核心矛盾**：写回必须无损（未知字段、键顺序、数值精度都不能动——Joyose 服务端/端上会整段消费这份 JSON），而查询/校验又想要类型化。解法 = **双表示**。

#### (1) 无损文档层（写回路径）

- serde_json 启用 `preserve_order` feature（内部 IndexMap）：**键顺序保持原样**，写回 diff 最小化；未知键天然保留。
- 所有"一键变换"（去锁帧/温度解锁等）以 `Value` 树上定点变换实现，只触碰目标子树。
- 数值谨慎：`version` 实际是字符串（"2026072751"）而 DB `version` 列是 INTEGER——CLI 写库时二者分别处理（对齐模块行为：`UPDATE cloud_config SET params=:p, version=:v`，v 来自 JSON 顶层 version 字段解析为 i64，同时刷新 `params.header.version` 字符串）。

#### (2) 类型化投影层（查询/校验路径）

为 per-app 相关子结构定义 serde 结构体（宽松模式：`#[serde(default)]` + 不 deny_unknown_fields），只为**读取**服务，永不参与序列化写回：

```rust
#[derive(Deserialize)]
struct OvrrideEntry {
    game_name: String,
    #[serde(default)] start_scene: Option<String>,
    #[serde(default)] end_scene: Option<String>,
    #[serde(default)] dynamic_fps: Option<String>,
    #[serde(default)] scene_ovrride: Vec<SceneOvrride>,
    // … 其余字段留在 Value 层
}
```

#### (3) CLI 查询命令设计（新增 `joyose-app`）

```
cosa joyose-app <package> [--json]
```

- 单次解析 params（277KB ≈ 毫秒级），按 §2.3 resolve() 规则构建 `HashMap<String, AppView>`，输出该包在全部子结构中的聚合视图（JSON）：
  ```json
  {"package":"com.miHoYo.Yuanshen","group":"YUANSHEN","in_game_list":true,
   "ovrride":{"dynamic_fps":"10:0,46:121,48:60","scenes":[…]},
   "novatek":"…","mivk":{…},"migt":"…","highfps":60,"features":["SOC","VRS","FISR"]}
  ```
- 供 Kotlin/UI 层做"单游戏卡片"展示，也是后续一键变换的影响面预演（dry-run）。
- 无缓存必要：CLI 短生命周期，O(n) 一次遍历即可；App 级查询 O(1) 取自索引。

#### (4) 数据验证与错误处理（对齐现有 cosa 协议）

| 校验点 | 规则 | 失败行为 |
|---|---|---|
| 顶层形状 | params 为 object、header.version 存在、game_booster 为 object | fatal，`{"ok":false,"error":…}` exit 1（与 cosa `write` 一致） |
| 未知 config_name | 白名单 + 已存在 rule_module 集合 | fatal（"没有匹配到任何列"语义的等价物） |
| 字段级 | 投影层反序列化的逐字段类型错误 | **可跳过**：收集为 warnings（JSON Pointer 定位，如 `/game_booster/ovrride_config/3/dynamic_fps`），eprintln 输出 `已忽略未知字段: …` 风格的多行告警 |
| 深度/大小 | 递归深度上限（防恶意/损坏 JSON 栈溢出，serde_json 已有 128 默认深度限制，显式复核）+ params 长度上限护栏 | fatal |
| 双库一致性 | write 后 read-back 校验（cosc 的"写入后未找到包名"等价物：校验 cloud_config 行存在且 rules 行 rule_version 已更新） | fatal，错误信息标明失败侧 |

#### (5) 测试策略

- `/tmp/joyose-research/` 的 songyuan 实测样本（SmartP.db / teg_config.db）转成仓库内 `rust/tests/fixtures/` 小型化样本（脱敏不必要——全是游戏调优数据，但体积需裁剪：ovrride_config 保留 3-5 条代表性条目）。
- golden test：`read → Value 往返 → 序列化` 输出与输入逐字节一致（preserve_order 生效性验证）；`joyose-app` 对 YUANSHEN（组名展开）与 com.tencent.lolm（直接包名）各断言一份期望聚合。

---

## 8. 给主报告的修正点

1. 主报告 §4.2 的 `joyose-list/read/write` 建议补充 `joyose-app` 查询子命令（本文件 §2.4(3)）。
2. 主报告 §4.3 "双库不一致" 风险补充：SmartP 侧 `version` 列与 `params.header.version` 字符串需同步刷新（实测二者相等）。
3. 主报告 §4.1 对比表 HyperOS 列的"配置粒度"补充：per-app 数据大量使用**组别名 + 多形态分隔符字符串**，按包名定位是 join 问题而非单表查询（本文件 §2.3）。
