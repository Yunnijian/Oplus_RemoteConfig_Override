# cosa CLI 模块化设计（Phase 1 前置）

日期：2026-08-30
关联：[hyperos-platform-support-research](2026-08-30-hyperos-platform-support-research.md) §4.2 / [rust-cli-and-booster-config-analysis](2026-08-30-hyperos-rust-cli-and-booster-config-analysis.md) Part 1
状态：设计定稿；`joyose/` 解析模块已按本结构实现（见 §6），`common/`、`cmd/` 拆分为 P1 执行项。

## 1. 目标与非目标

- 目标：单二进制（cosa）承载多平台子命令族；模块边界使"平台数据访问（IO 层）"与"云控文档逻辑（纯逻辑层）"分离，后者可脱离设备独立单测。
- 非目标：不改变 CLI 对外协议（argv 位置参数 + stdout JSON + stderr 告警）；不引入新依赖；不做长驻服务。

## 2. 模块划分与职责

```
rust/src/
├── main.rs              # 入口：argv 分发 + die()。无业务逻辑（目标 <80 行）
├── common/              # 平台无关基建（IO 层共享底座）
│   ├── mod.rs
│   ├── error.rs         # CliResult 别名、die()、JSON 错误输出协议
│   ├── jsonio.rs        # value_to_json / to_sql / 值类型转换
│   └── dbfs.rs          # Connection 打开、columns/column/column_type、
│                        # chown_sidecars、finish(wal_checkpoint)、safe_name
├── cmd/                 # 子命令编排（IO 编排层）
│   ├── mod.rs           # pub use cosa::*; pub use joyose::*;
│   ├── cosa.rs          # list|read|write|delete|protect + install_protection
│   └── joyose.rs        # stat|list|read|write|freeze|unfreeze|backup|revert (P1)
└── joyose/              # HyperOS 云控文档纯逻辑（零 IO / 零 rusqlite）
    ├── mod.rs
    ├── resolve.rs       # 组名映射：包名 → 别名集合（game_group_mapping join）
    ├── appview.rs       # 按 App 聚合解析（AppView），已实现
    ├── model.rs         # (P1) CloudConfigDoc 行包装 / 双库镜像类型
    └── transform.rs     # (P2) 去锁帧 / 温度解锁等定点变换（Value→Value）
```

## 3. 依赖关系（单向，禁止反向）

```
main.rs ──► cmd/cosa.rs ────► common/*        (rusqlite, std::fs)
        └─► cmd/joyose.rs ─┬─► common/*
                           └─► joyose/*     (仅 serde_json)
joyose/*  ──► 无下游依赖（叶子模块；不 import common，不碰文件/DB）
common/*  ──► 无内部相互依赖（error 可被其余引用，其余互不引用）
```

高内聚低耦合论证：
- **joyose/ 是纯函数层**：`&Value` 进、结构体出，不接触文件系统 → 单元测试不需要设备/root/DB 样本数据库，inline fixture 即可。
- **cmd/ 只做编排**：打开 DB、取参数、调逻辑、格式化输出；任何 SQL 语句只出现在 cmd/ + common/。
- **common/ 无业务语义**：不含 "cosa"/"joyose" 字样，两平台共享的只有机制（SQLite 操作、文件属主、协议）。

## 4. 接口定义（关键签名）

```rust
// common/error.rs
pub type CliResult<T> = Result<T, Box<dyn Error>>;
pub fn die(msg: &str) -> !;                      // {"ok":false,"error":…} → exit 1

// common/dbfs.rs
pub fn open_rw(path: &Path) -> Result<Connection>;
pub fn columns(conn: &Connection, table: &str) -> Result<Columns>;
pub fn column(cols: &Columns, name: &str) -> Option<String>;
pub fn chown_sidecars(db: &Path);
pub fn finish(conn: Connection, db: &Path);      // wal_checkpoint(TRUNCATE) + sidecar 归还
pub fn safe_name(s: &str) -> bool;

// cmd/cosa.rs（现 main.rs 平移）
pub fn list() -> CliResult<bool>;
pub fn read(pkg: &str, out: Option<&str>) -> CliResult<bool>;
pub fn write(pkg: &str, json_path: &str) -> CliResult<bool>;
pub fn delete(pkg: &str) -> CliResult<bool>;
pub fn protect() -> CliResult<bool>;

// cmd/joyose.rs（P1；写路径前置 force-stop 由 Kotlin 层负责）
pub fn stat() -> CliResult<bool>;
pub fn list() -> CliResult<bool>;                // cloud_config ∪ rules 模块清单
pub fn read(config: &str) -> CliResult<bool>;    // smartp 缺行自动回落 teg
pub fn write(config: &str, json_path: &str) -> CliResult<bool>;  // 双库镜像写
pub fn freeze() -> CliResult<bool>;              // teg SP → Long.MAX_VALUE
pub fn unfreeze() -> CliResult<bool>;

// joyose/appview.rs（已实现）
pub fn collect(params: &Value, pkg: &str, common: Option<&Value>)
    -> Result<AppView, String>;
pub fn cmd_app_view(pkg: Option<&String>, booster: Option<&String>,
    common: Option<&String>) -> CliResult<bool>;   // 当前源=文件；P1 切 DB，输出协议不变
```

## 5. 数据流转

```
[输入]  root shell → cosa <sub> <argv…>
          cosa 路径: rusqlite 就地读写 PackageConfigBean（保留 uid/gid）
          joyose 路径: rusqlite 就地读写 SmartP.db/teg_config.db + SP XML
[纯逻辑] joyose/*: &Value(云控 JSON) → AppView / TransformResult（结构化、可序列化）
[输出]  stdout = 单一 JSON 文档（机器可读，App 据此渲染）
        stderr = 人类可读告警/多行错误（对齐 cosa "已忽略未知字段" 协议）
[错误]  {"ok":false,"error":…} exit 1；可跳过字段级问题 → stderr 告警不致命
```

## 6. 本次已实现：joyose 按应用解析

- [resolve.rs](../../../rust/src/joyose/resolve.rs)：`alias_set()` —— 从 `game_group_mapping_config` 构建包名 →（包名 + 组别名）集合，处理 `ovrride_config.game_name` 为组名（SGAME/YUANSHEN…）的继承关系。
- [appview.rs](../../../rust/src/joyose/appview.rs)：`collect()` 按 22 类功能结构扫描（§2.4 六类形态全覆盖：对象数组 / 直接包名数组 / 分隔符字符串 `_ : @ ;` / 别名+cmdlines / 包名键对象 / 白名单兜底 OTHER），输出 `AppView{package, group, common 成员关系, features[]{category,label,source,key,path,params[]}, conflicts[]}`。
- 覆盖语义：per-game `ovrride_config` 条目显式携带其覆盖的全局基线（如 `dynamic_fps` vs `dynamic_fps_global`）→ 在 hit 的 `overrides` 字段标注被覆盖的基线键；同包多条命中 → `conflicts` 列出全部 JSON 路径不静默择一；`scene_ovrride` 为条目内第三层覆盖，逐场景列出。
- **全局布尔开关**（2026-08-30 补充）：AppView 新增 `global_switches` 段——`game_booster` 全部顶层 bool（实测 23 个：`booster_enable`、`cgame_enable`、`SOC_enable`、`self_gpu_tuner_enable`、`scale_app_enable`、`predownload_enable`…）+ 嵌套 `mivk_settings.enable`/`migl_settings.enable`，按文档序输出。每个 feature hit 附 `gate` 字段指向其主开关及当前值（booster_override→`booster_enable`、mivk/migl→各自 `.enable`、cgame_df→`cgame_enable`、soc→`SOC_enable`、gpu_tuner→`self_gpu_tuner_enable`、scale/force_scale→`scale_app_enable`、predownload→`predownload_enable`、freeze_whitelist→`background_freeze_enable`；无主开关的类目为 null）。per-app 布尔（`ovrride_config` 条目内的 `change_release_perflock_inner`、`resend_last_scene_id` 等）本就经 `scalars()` 进 params，无需另列。
- CLI：`cosa joyose-app <pkg> <booster_params.json> [common_params.json]`（文件源演示入口，P1 换 DB 源，`collect()` 不变）。
- 测试：模块内单测覆盖组名展开、直接包名、前缀陷阱（`sgame` ≠ `sgamece`）、token 解析、OTHER 兜底、冲突检测；实测数据用 songyuan 样本跑 CLI 验证（记录见下）。

### 6.1 实测验证记录（songyuan 真实云控，2026-08-30）

`cargo test`：9 passed / 0 failed（resolve 3 + appview 6）。CLI 对真实样本（booster 277KB + teg common）验证：

| 包名 | group 解析 | 命中功能数 | 关键结果 |
|---|---|---|---|
| com.miHoYo.Yuanshen | YUANSHEN（组别名） | 13 | booster_override 经组别名命中；mivk/migt/cgame_df/highfps/soc/vrs/dynamic_rr/resolution_enhance 全中 |
| com.tencent.lolm | 无（直接包名） | 6 | **覆盖链上报**：`dynamic_fps 覆盖 dynamic_fps_global`（46:121 vs 全局 43:120）；fisr 走 OTHER 兜底 |
| com.tencent.tmgp.sgame | SGAME（组别名） | 15 | novatek 游戏/非游玩/GEX 三档齐中 + low_rr_scenes + scenario_control |

产物：`/tmp/joyose-research/view_{yuanshen,lolm,sgame}.json`（AppView 全文）。

## 7. 迁移步骤（P1 执行，每步可构建）

> **实施状态（2026-08-30）**：P1 已完成——实际以 `joyose/store.rs` 单文件承载 IO 层（stat/list/read/write/freeze/unfreeze/backup/backup-list/revert/apps），`common/`+`cmd/` 拆分顺延至后续清理；`joyose-apps`（P1.5）以 `appview::package_index` 单遍扫描实现。OS3 实机全套验证通过（stat/list/read/apps/backup/写入往返/冻结解冻/revert）。P2.0a 平台检测骨架（Platform.kt + platformMode 设置 + LocalPlatform + Routes）已由子代理完成并过编译。

1. 抽 `common/`（纯移动，零行为变化）→ `cargo test` + 构建通过。
2. 抽 `cmd/cosa.rs`（同上）。
3. 增 `cmd/joyose.rs` + `joyose/model.rs`（DB 子命令落地）。
4. `joyose-app` 文件源 → DB 源（`collect()` 不动）。
5. `build-android.sh` 自检串追加 joyose 功能字符串。
