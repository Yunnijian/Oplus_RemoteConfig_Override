# HyperOS 云控配置支持 — 技术评估报告

日期：2026-08-30
范围：将本项目（当前仅支持 ColorOS `com.oplus.cosa`）拓展至小米 HyperOS 设备的 Joyose 云控配置修改。
参考：`joyose-edit-v1.1.0-cloudlab.zip`（KernelSU 模块，完整逆向分析见第 2 节）。
实测设备：Redmi songyuan（K90 Pro Max 系列）/ HyperOS 3.0 / Android 16 / CN 区，root 可用。

---

## 1. 结论速览

1. **技术上完全可行**，且本项目的 Rust 原生工具路线**优于**参考模块的 shell+base64+浏览器 sql.js 路线：Joyose 数据库的读写可直接扩展 `rust/src/main.rs`（rusqlite 就地编辑，天然保留 uid/gid，无需模块那套 chown/restorecon/分块传输补丁）。
2. 两平台数据模型差异是核心设计点：ColorOS = **按包名一行一 JSON**；HyperOS = **按配置模块一表项**，游戏级数据嵌在 `booster_config.params.game_booster` 内部，且同一份云控**双库镜像**（SmartP.db + teg_config.db）。
3. 防覆盖机制完全不同：ColorOS 用 SQL 触发器 + `from_server=0`；HyperOS 必须冻结 teg SDK（`pref_local_max_version` → Long.MAX_VALUE，主）+ 可选 DB 版本锁 2099（辅）。
4. 整合无需新建分支/大改架构：新增 `platform` 抽象层（检测 + Backend 接口）+ 扩展 Rust CLI + HyperOS 专用屏幕。ColorOS 现有功能路径零改动。
5. 主要风险：双库不一致、teg 冻结的全局副作用（冻结所有 MIUI 云控模块）、Joyose 进程持有 DB 导致的写入冲突（写入前 force-stop 解决）。

---

## 2. 参考模块 JoyoseEdit 逆向分析

### 2.1 模块形态与技术架构

```
joyose-edit-v1.1.0-cloudlab.zip
├── module.prop            # KernelSU 模块元数据（id=joyose-edit）
├── customize.sh           # 安装脚本：仅建 /data/adb/joyose-edit/{backup,history} 并 chmod 700
├── post-fs-data.sh        # 首次开机对 SmartP.db/teg_config.db 做基线快照（mtime#size 指纹去重）
├── uninstall.sh           # 卸载清理 + 若冻结哨兵值是自己写的则还原 pref_local_max_version=0
├── bin/joyose-edit.sh     # 核心：root 权限辅助脚本（白名单子命令，17KB）
└── webroot/               # KernelSU WebUI（Vue3 + CodeMirror + sql.js/SQLite WASM）
    ├── index.html         # 主界面（12 个功能视图，懒加载 chunk）
    └── cloud.html         # CloudLab：MCC 云端规则拉取/注入 + 出厂还原（未压缩，逻辑清晰）
```

架构模式：**前端富客户端**。WebUI 经 `ksu.exec` 调 shell 后端做白名单操作（pull/push 数据库字节），所有 SQLite 解析、JSON 编辑、diff、历史管理都在浏览器内用 sql.js 完成。数据通道 = argv 传 base64（ARG_MAX ≥ 2MB），超限走 `stage-*` 分块（60KB/片）。

### 2.2 后端子命令清单（joyose-edit.sh）

| 子命令 | 功能 | 要点 |
|---|---|---|
| `stat` | schema/版本指纹 JSON | 双库存在性、mtime/size/uid/gid |
| `pull smartp\|teg` | base64 输出整库 | — |
| `push smartp\|teg <b64>` | 原子写回 | 临时文件 → `SQLite format 3` 魔数校验 → chown 原 uid:gid → chmod 660 → mv → restorecon |
| `backup [label]` / `revert` / `revert-latest` / `backup-list` | 快照/回滚 | /data/adb/joyose-edit/backup/`<epoch>-<label>` |
| `restart` | `am force-stop com.xiaomi.joyose` | 写入与冻结前必做（SP 有进程内缓存） |
| `history-*` | 编辑历史（NDJSON，每会话一份） | 会话级 diff，可回滚 |
| `stage-clear/append`、`push-from-stage`、`history-save-from-stage` | 大 payload 分块 | 绕过 argv 上限 |
| `teg-status / teg-freeze / teg-unfreeze` | 冻结 teg SDK | 改 `teg_config_pref.xml` 的 `pref_local_max_version`（sed/awk 原位改写，先 force-stop） |
| `vision-status` | 查 `ro.vendor.gpp.frc.support` / `ro.vendor.xiaomi.sr.support` | 只读展示（安全中心游戏助手 UI 开关前置条件），不代写 |
| `dev-info` / `cloud-post` | MCC 云端拉取 | URL 白名单 4 个区域端点；curl POST，UA 伪装 `com.xiaomi.joyose/513` |
| `cat-odm` | 读 `/odm/etc/default_cloud.json`、`joyose_scene_recognize.json` | 白名单 2 文件，AES 密文原样输出由前端解密 |
| `cat-wasm / cat-vendor-wasm` | 绕过 WebView 不 fetch .wasm 的兼容问题 | — |

### 2.3 前端功能清单（12 视图）

| 视图 | 操作对象 | 核心逻辑 |
|---|---|---|
| 概览 | 双库 stat | 路径自适应、云控版本来源（smartp/teg 取较新者） |
| 云控锁定 | teg SP + 双库 version | 见 §3.4 |
| 去除锁帧 | `booster_config.params.game_booster` | `cgame_enable→false`、删 `dynamic_fps_global`、全树递归删 `PID_*`/`dynamic_fps*` 前缀键 |
| 温度解锁 | 同上所有字符串值 | 温度组 token 解析（分隔符 `#`、`_`、`\|`、`&`），连续 ≥2 个 30-59 段温度 → 首位改 9（45#43#43#41 → 95#93#93#91），不碰帧率数字 |
| 游戏列表 | `common_config.params.game_list / support_app` | 包名列表编辑器 |
| 高通 GPU(FRC) | `game_booster.frc_game_params` | 12 段下划线结构化字段编辑（pkg/res/fps/...） |
| MIFISR | `game_booster.customize_game_params.game_mifisr_config` | 17 系，7 段 `#` 结构（pkg_mifi_misr...），含 `disable_scene_list`、`fisr_config`、`fisr_mqs_v2` |
| Novatek 独显 | `game_booster.novatek_game_params`、`novatek_extend_config.novatek_non_playing_config`、`novatek_black_app`、`novatek_gex_fps_limit` | 红米独显通路，4 段下划线 + `#` 块 |
| MIVK/MIGL | `game_booster.mivk_settings / migl_settings` | Vulkan/GL 通路的 xrender_config（原神/星铁预设） |
| JSON 编辑 | 任意 params | CodeMirror + 实时校验 |
| 编辑历史 / 导入导出 | — | 导出 schema `joyose-edit.export/v1`，domains 分 enhance/render，键白名单合并 |
| 云控实验室 | MCC + odm | 见 §3.5、§3.6 |

---

## 3. 小米 Joyose 云控系统机制（含实测）

### 3.1 目标文件与 schema（songyuan 实测）

- DB 目录：`/data/user/0/com.xiaomi.joyose/databases/`，属主 `system:system`（uid 1000），模式 660；同目录还有 GameInfo/GameLight/GameSupportEffect/GameVisualEffectConfig.db（本工具不触碰）。
- **SmartP.db**（331KB，rollback journal 模式）→ 表 `cloud_config`：

```sql
CREATE TABLE cloud_config (
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  config_name TEXT UNIQUE,   -- booster_config / common_config / scene_recognize_config
  group_name TEXT, enable INTEGER, version INTEGER,
  with_model INTEGER, model TEXT,
  params TEXT,               -- 大 JSON（booster_config 实测 277KB）
  anchor TEXT, anchor_percents TEXT, anchor_values TEXT,
  value_type TEXT, final_value TEXT
)
```

  **实测注意**：该设备 cloud_config 只有 **1 行**（booster_config，version=2026072751，`header.version` 与之同步）——**common_config 不在 SmartP 里**，只存在于 teg rules。写路径必须处理行缺失时的 INSERT。
- **teg_config.db**（45KB）→ 表 `rules`：

```sql
CREATE TABLE rules (
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  rule_id INTEGER, rule_version INTEGER,
  rule_module TEXT,          -- 'common_config' 等
  rule_content TEXT          -- JSON
)
```

  实测 2 行，均 `rule_module='common_config'`，rule_id=**987654321**（即模块 CloudLab 的注入槽位）与 1222051，rule_version=467472 → **该设备已被同源工具修改过**，调研前提（可参考真实样本）成立。
- **teg SP**：`shared_prefs/teg_config_pref.xml`，实测 `pref_local_max_version=712444`（未冻结）、`pref_update_interval=780`（≈13 分钟）。

### 3.2 云控同步机制（覆盖根源）

Joyose 的运行时配置走 MIUI teg 云控 SDK（`com.xiaomi.teg.config`），与 SmartP 的 `cloud_config.version` **相互独立**。每 ~13 分钟（或广播触发）：

```
local = SP.pref_local_max_version
cloud = POST mcc端点 {version: local}
if cloud.maxVersion == local -> no-op
else -> 按 delete/insert 顺序重放规则到 teg_config.db（无逐条版本校验）
        -> SP 升版 -> 通知观察者 -> Joyose 从 teg_config.db 重新加载内存态
```

### 3.3 读取 / 解析 / 修改 / 生效链路（模块的处理方式）

1. **读取**：root shell `pull` 整库 base64 → 前端 sql.js `SELECT *`。
2. **解析**：cloud_config.params 与 rule_content 均为 JSON；`booster_config.params = {header:{version}, game_booster:{...}}`。同一份云控在 SmartP.params 与 teg.rule_content **双份镜像**，模块取版本号较新者为准，改动后**两份都写**保持一致。
3. **修改**：全在前端内存对 JSON 做变换（§2.3），SQL 层只做 `UPDATE cloud_config SET params=:p, version=:v WHERE config_name=:n`（无对应行则 INSERT）与 `UPDATE rules SET rule_content=:c, rule_version=:v WHERE rule_module=:m`（无则 INSERT）。
4. **生效**：`am force-stop com.xiaomi.joyose` → 下次进程启动按新 DB 加载。写入前自动 backup，提交后自动 restart。

### 3.4 防覆盖（锁定）机制 — 双保险

1. **teg 冻结（实测有效的主手段）**：把 `pref_local_max_version` 写成 `9223372036854775807`（Long.MAX_VALUE）→ teg 永远认为“已是最新”，不再重放规则。副作用：**所有**走 teg 云控的配置（不限 Joyose）都被冻结。解冻 = 写回 0。
2. **DB 版本锁（辅助）**：`cloud_config.version` 与 `params.header.version` 刷成 **2099 开头**（保留后 4 位），使任何旁路版本比较都认为本地较新。
3. 模块明确警示：只做版本锁**不可靠**，必须配合 teg 冻结。

### 3.5 CloudLab — MCC 云端拉取协议（可移植）

- 端点（4 区域）：`https://mcc.inf.miui.com/cloud/app/getData`（CN）等。
- 表单参数：`packageName=com.xiaomi.joyose, appVersion=513, versionName=2.5.13, deviceInfo=<JSON: {ihash随机, uid随机UUID, d:设备代号, r:'CN', l:'zh_CN', v:incremental, av:'16', p:'android'}>, version=<本地maxVersion>`。
- **签名**：参数按 key 排序 → `k=v&...&com.xiaomi.joyose` → UTF-8 → Base64 → **MD5 → 大写 hex**。
- 响应：`{code:200, data:{maxVersion, rules:[{moduleKey, ruleId, version, content}]}}`。
- 应用：备份 → 可选版本提升（默认 999999999999）→ `INSERT OR REPLACE INTO rules(rule_id, rule_version, rule_module, rule_content)`，固定槽位 `common_config=987654321, booster_config=987654322, scene_recognize_config=987654323` → force-stop → 建议随即冻结。

### 3.6 出厂还原（CloudLab 附加功能）

`/odm/etc/default_cloud.json` 为 **AES-256/ECB + PKCS5** 密文；密钥来自 Joyose `utils/a.c()`：32 字节数组逐字节 XOR 127 → 32 字符 hex 文本 → ASCII bytes 即 AES-256 key，实测常量为字符串 `"746865726d616c6f70656e73736c2e68"`。解密后重写 SmartP 三行（common_config/booster_config/scene_recognize_config）+ 清空 teg rules + 可选解冻。

---

## 4. 与 ColorOS 模型对比及整合评估

### 4.1 数据模型对比

| 维度 | ColorOS (com.oplus.cosa) | HyperOS (com.xiaomi.joyose) |
|---|---|---|
| 配置粒度 | **按包名**一行一 JSON | **按配置模块**（booster_config 等），游戏级嵌在 params 内部 |
| 表 | `PackageConfigBean` | `cloud_config`（SmartP）+ `rules`（teg） |
| 库数量 | 单库双路径（/data/data 与 /data/user_de/0） | 单路径**双库镜像** |
| 内部保护行 | `oplus.cosa.*` 2 个 EXCLUDED 包名 | 无对应概念（不动未知 rule_module 即可） |
| 防覆盖 | SQL 触发器 + `from_server=0` | teg SP 冻结 + 2099 版本锁 |
| 写后动作 | wal_checkpoint + sidecar chown | force-stop Joyose（写入前后） |
| 面向用户的“列表” | 游戏包列表 | 配置模块列表（≤3 个主配置 + rules 模块） |
| 一键变换 | 无（自由 JSON 编辑） | 去锁帧 / 温度解锁 / 游戏列表 / GPU 通路编辑 |

### 4.2 整合策略：扩展 Rust CLI（推荐）而非移植模块路线

参考模块的技术路线（argv 传 base64 整库 + 浏览器 sql.js 全量重写 + mv 原子替换 + chown/restorecon）是其宿主环境（KSU WebUI 无 stdin、无原生代码）的无奈之举。本项目已有更优基建：

- [rust/src/main.rs](../../../rust/src/main.rs) 用 rusqlite **就地编辑**（`UPDATE/INSERT`），不替换文件 → uid/gid/SELinux 上下文天然保留，零 chown/restorecon 负担；`finish()` 的 `wal_checkpoint(TRUNCATE)` + `chown_sidecars` 模式直接复用（SmartP 为 journal 模式，checkpoint 是无害 no-op）。
- SQLite bundled 编译，规避 Joyose 库版本差异问题（同 cosa 的历史教训）。
- 新增子命令即可，**不影响现有 cosa 子命令** → 与 ColorOS 功能零冲突（目标包、路径、表完全不相交）。

**建议新增的 Rust 子命令（`cosa` 二进制扩展）：**

```
joyose-stat                          # 双库+SP 指纹 JSON（对齐 cmd_stat）
joyose-list                          # cloud_config(config_name,version,enable) ∪ rules(rule_module,rule_version 去重)
joyose-read <config_name>            # 输出 params/rule_content JSON（来源自动：smartp 缺行时读 teg）
joyose-write <config_name> <json>    # 双库镜像写：cloud_config UPDATE/INSERT(params,version)
                                     #            + rules UPDATE/INSERT(rule_content,rule_version)
joyose-freeze / joyose-unfreeze      # teg SP pref_local_max_version ⇄ Long.MAX / 0（SP 原位改写）
joyose-backup [label] / joyose-revert <name>   # 快照/回滚（存 app 私有目录或 /data/adb/<our-dir>/）
joyose-factory                       # （远期）odm 出厂还原
```

写入语义（对齐模块实测行为）：
- `package_name` 等价物不存在 → 校验改为 config_name 白名单（`common_config/booster_config/scene_recognize_config` + 已存在 rule_module）。
- 写入时同步刷新 `header.version`（若 JSON 内含）；SmartP 无该行时按 cloud.html 的 INSERT 列集补行。
- `busy_timeout` + 要求调用方（Kotlin 层）在写入前 `am force-stop com.xiaomi.joyose`（libsu 直接执行，无需进 Rust）。
- teg SP 改写先 force-stop（SP 进程内缓存），与模块一致。

### 4.3 冲突与风险清单

| 风险 | 等级 | 缓解 |
|---|---|---|
| 双库不一致（只写一边） | 高 | `joyose-write` 单事务语义内双写；read 自动取较新源 |
| Joyose 进程持有 DB → SQLITE_BUSY | 高 | 写入流程前置 force-stop（Kotlin 层）+ busy_timeout 兜底 |
| teg 冻结全局副作用 | 中 | UI 明示（对齐模块文案）；设置里提供一键解冻 |
| 写 SP XML 时 Joyose 又拉了云控 | 低 | freeze/unfreeze 前后 force-stop；写入时序与模块一致 |
| 恢复误伤（revert 错版本） | 低 | 沿用 backup 时间戳目录 + safe_name 校验模式 |
| 未知 rule_module 被误改 | 低 | write 白名单 + 未知字段忽略告警（对齐 cosa 的 skipped 语义） |
| 平台误判（双刷机/类原生） | 低 | 属性检测 + 设置内手动覆盖 |

---

## 5. App 框架演进建议（多平台抽象）

### 5.1 平台检测（Phase 0）

[OemHelper.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/ui/util/OemHelper.kt) 已有 `getSystemProperty`（注释里也点名 KernelSU 有 isMiui/isHyperOS/isColorOS）。新增：

```kotlin
enum class Platform { ColorOS, HyperOS }
// HyperOS: ro.mi.os.version.name 非空 或 ro.miui.region 非空
// ColorOS: ro.oplus.version* / ro.vendor.oplus.* 非空
// 设置内可手动覆盖（auto/coloros/hyperos）
```

### 5.2 数据层抽象（Phase 0-1）

新增 `data/platform/`：

```kotlin
interface CloudControlBackend {
    val platform: Platform
    fun checkRoot(): Boolean
    fun listConfigured(): List<ConfigSummary>       // 包名 或 配置模块
    fun loadConfig(key: String): String?            // 原始 JSON
    fun writeConfig(key: String, json: String): WriteResult
    fun deleteConfig(key: String): WriteResult?     // HyperOS 不提供删除（或仅限注入槽位）
    fun enableProtection(): WriteResult             // cosa: protect 触发器 / joyose: freeze
    fun backup(): WriteResult
}
```

- `CosaBackend`：薄封装现有 [DatabaseManager.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/data/DatabaseManager.kt)（保留原类，避免大迁移）。
- `JoyoseBackend`：新增，调 `joyose-*` 子命令；`WriteResult` 附带 freeze 状态。
- [MainViewModel.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/viewmodel/MainViewModel.kt) 注入 backend（`ViewModelProvider.Factory`），`gameList/editingJson/baselineJson` 等 StateFlow 语义不变，仅数据源换成接口 → **编辑器脏标记/防丢稿逻辑直接复用**。

### 5.3 UI / 导航（Phase 1-2）

- **路由**：[Routes.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/navigation/Routes.kt) 的 `ConfigEditor(packageName)` 参数语义改为 `configKey`（类型不变）；新增 `HyperOsTools` 路由。
- **配置列表**：HyperOS 下列表项 = 配置模块（booster_config/common_config/scene_recognize_config + rules），显示 version/enable 而非应用图标；`GameConfigSummary` 泛化为 `ConfigSummary(platform 通用字段)`，图标逻辑按 platform 门控。
- **编辑器**：[ConfigEditorScreen.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/ui/screens/ConfigEditorScreen.kt) + NativeJsonEditorView 完全复用（joyose 的 params 就是 JSON 文档）；booster_config 277KB 大文档注意 `rememberContentReady` 门控已有基建。
- **HyperOS 工具页**（对齐模块三大一键功能）：云控锁定（freeze 状态卡 + 2099 锁）、去除锁帧、温度解锁 —— 变换算法用 Kotlin 实现（纯 JSON 变换，可单测），不依赖 sql.js。
- **首页**：SystemStatus 卡按 platform 分支（cosa 版本 dumpsys / joyose 冻结状态 + teg 版本）。

### 5.4 设置（Phase 0）

[SettingsRepository.kt](../../../android/app/src/main/kotlin/com/remoteconfig/override/settings/SettingsRepository.kt) 增加 `platformMode: String`（auto/coloros/hyperos）+ HyperOS 的 `freezeOnWrite: Boolean`（默认 true：写后自动冻结，对齐“本地配置保护”产品语义）。

### 5.5 可扩展性收束

- 每个平台一个 Rust 子命令族 + 一个 Backend 实现，UI 层只认接口 → 未来再加平台（如 MCUX/其他 ROM）是纯增量。
- 一键变换算法独立成 `domain/transform/`（输入 JSON → 输出 JSON + 变更报告），与 Backend/平台解耦，模块里 FRC/MIFISR/Novatek/MIVK 的结构化编辑器可按需逐个移植。

---

## 6. 分阶段实施计划

| 阶段 | 内容 | 交付判据 |
|---|---|---|
| P0 抽象层 | Platform 检测 + Backend 接口 + 设置项 + ViewModel 注入改造；ColorOS 行为回归零变化 | 现有功能全部通过；songyuan 上显示 HyperOS 平台 |
| P1 核心读写 | Rust `joyose-stat/list/read/write/freeze/unfreeze/backup/revert` + JoyoseBackend + HyperOS 配置列表/编辑器/锁定页 | songyuan 实机：读 booster_config → 改 params → 双库写入 → force-stop → 冻结 → 重启 Joyose 后配置保持 |
| P2 一键工具 | 去锁帧/温度解锁/游戏列表（Kotlin 纯变换 + 单元测试）+ 编辑历史/导入导出对齐 | 变换结果与模块行为一致（用 §3 实测 DB 样本做 golden test） |
| P3 远期可选 | CloudLab MCC 拉取/注入（Kotlin MD5 签名 + HttpURLConnection，无需 curl）、出厂还原（AES-ECB）、vision-status 展示 | 拉取规则可注入并可回滚 |

## 7. 实测样本备注

调研期间从 songyuan 拉取的只读样本（SmartP.db / teg_config.db / teg_config_pref.xml）存于 `/tmp/joyose-research/`，可作 P2 golden test 输入；勿提交进仓库。
