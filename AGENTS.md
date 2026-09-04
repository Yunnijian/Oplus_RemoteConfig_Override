# 项目 Agent 指南（最小版）

Oplus RemoteConfig Override：**双平台**游戏云控配置修改工具——ColorOS（`com.oplus.cosa` / `db_game_database`）与 HyperOS 3（`com.xiaomi.joyose` / `SmartP.db` + `teg_config.db`）。同一 APK 运行时自动识别平台。当前活跃分支 `MIUIX-test`。

## 权威文档（做任何实质性改动前先读）

- **`docs/PROJECT_GUIDE.md`** — 项目唯一维护者手册（950+ 行，v1.1）。关键章节速查：
  - §2 架构与核心设计决策 · §4/§5 Rust/Android 端模块详解 · §6 Kotlin⇄libcosa.so 接口契约
  - §8 关键问题与解决方案（历史踩坑，排障第一入口）· §9 常见错误排查手册
  - §11 构建与发版流程（含真机验收清单）· §12 遗留问题清单 + 维护者铁律
  - §13 **UI 开发规范（双皮肤）**：组件选型决策树、自研准入条件、PR 自检清单——UI 改动强制前置阅读
- `docs/superpowers/specs|archive/` — 逆向工程原始证据与方案文档（代码注释直接引用，勿删；`archive/` 是已落地或已挂起的历史方案，仅归档不删除）。
- 注：`PROJECT_GUIDE.md` 为本地文档（已 gitignore），若缺失，以本文件约束为准并向维护者确认。

## 架构概要（双端，无 JNI，唯一通信 = root shell + 命令行 + stdout JSON）

- `android/` — 主应用（Kotlin 2.4 + Compose，MVVM + Repository）。Navigation3 自研 Navigator（15 路由）；数据层 `DatabaseManager`(ColorOS) / `JoyoseManager`(HyperOS) 经 libsu 调 `libcosa.so`。
- `rust/` — 特权 CLI `cosa`：`main.rs`（ColorOS 5 命令）+ `joyose/` 8 模块（18 命令：store/appview/scoped/migt/caps/resolve/digest）。交叉编译为 `libcosa.so`（arm64，可执行形态随 jniLibs 分发），构建走 `rust/build-android.sh`（含产物自检，勿手工拷贝）。
- `KernelSU/` — vendored 第三方源码（已 gitignore，不入库）：**Miuix 皮肤的 UI 对齐基准**（组件选型决策树第②级"命中即移植"），**只读**，勿修改。

## 核心约束

1. **双 UI 皮肤并行**（Miuix + Material 3）：存量 7 对页面=分发器+两份实现；**新增页面一律用策略②**（共享 state holder + 皮肤分支组件，见 `HyperOsFeatureUi.kt`），回调签名两侧逐字一致。规范详见 `docs/PROJECT_GUIDE.md` §13（本地文档，不入库）。
2. **SQLite 必须 bundled**：`rusqlite` 仅用 `bundled` feature，禁止链接系统 `libsqlite.so`；`serde_json` 必须开 `preserve_order` + `arbitrary_precision`；`panic = "unwind"`（abort 会跳过 WAL checkpoint）。
3. **arm64-only**：ABI 仅 `arm64-v8a`，Rust 目标仅 `aarch64-linux-android`。
4. **三条铁律**（PROJECT_GUIDE §12.4）：① 任何写库失败必须显式报错，stdout 只放一个 JSON 文档；② root 触库收尾必须 `checkpoint → heal_sidecars → restorecon`；③ **改 Rust 必重跑 `build-android.sh` 并同步 Kotlin wire model**（两端靠字段名隐式对齐，`ignoreUnknownKeys` 会静默降级——最易踩的跨端陷阱）。
5. **root 命令一律走 `data/RootShell.kt` 的 `execRoot()`**，不要直接 `Shell.cmd().exec()`。libsu 6.0.0 的 `setTimeout()` 只管创建 shell 时的 `shellCheck()`，**命令级没有超时**，挂死即永久阻塞；`execRoot` 用 `Shell.Job.enqueue()` + `Future.get(30s)` 补上这个能力，超时返回 `RootResult.TIMEOUT`（语义是"结果未知"，不是"失败"——命令仍在 libsu 队列里跑完，故超时值必须远大于正常耗时，**不要**为了快速重试而调小）。

## 验证命令

```bash
# Rust 测试（宿主执行，65 用例；改 Rust 必跑，勿用交叉 target 跑测试）
cd rust && cargo test

# Android 单元测试（38 用例）
cd android && ./gradlew :app:testDebugUnitTest
# 若当前 shell 的 JAVA_HOME 指向 jdk 发行包外层目录（见下），Gradle 会拒绝启动，
# 需临时覆盖：JAVA_HOME=<下面的 JDK 路径> ./gradlew …

# 改 Rust 后的产物重建（含 DT_NEEDED 与特征串自检；NDK 路径经环境变量传入）
cd rust && NDK=/Users/tubi/Desktop/mifan/.toolchains/android-sdk/ndk/26.3.11579264 ./build-android.sh
```

本机工具链实际位置（同一台机器上有**两份** SDK，NDK 只在第二份里）：

- Gradle 用的 SDK：`/Users/tubi/Library/Android/sdk`（`android/local.properties` 的 `sdk.dir`，只有 platforms/build-tools，**无 NDK**，所以 Gradle 构建正常但交叉编译找不到 clang）
- `$ANDROID_HOME` / `$ANDROID_SDK_ROOT`：`/Users/tubi/Desktop/mifan/.toolchains/android-sdk`
- NDK：`/Users/tubi/Desktop/mifan/.toolchains/android-sdk/ndk/26.3.11579264`（另有 `29.0.14206865`；构建用 26.3，其 `toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang` 即脚本要求的编译器，本机 `uname -m` = x86_64 故 HOST_TAG 走 `darwin-x86_64`）
- JDK：`/Users/tubi/Desktop/mifan/.toolchains/jdk-21/jdk-21.0.12.1+1/Contents/Home`（Kotlin `jvmTarget = JVM_21`）。**注意 `JAVA_HOME` 不能只写到 `jdk-21/`**——那是发行包外层目录，Gradle 会报 `JAVA_HOME is set to an invalid directory`；必须指到 `Contents/Home`。另一份等价可用路径：`/Users/tubi/.local/share/toolchains/jdk-21/Contents/Home`（即 `PATH` 上那个 `java`）。

上面三条已封装为 Qoder Command **`/validate`**（`.qoder/commands/validate.md`，本地资产，`.qoder/` 已 gitignore）：按本次改动范围选跑对应测试、区分「编译失败」与「断言失败」并给出可推送结论。改动 `android/` 或 `rust/` 后先跑它。

- **测试源集类名必须唯一**：同名 `class` 分散在不同文件（如 `CodecTest.kt` 里的 `CurveCodecTest` 与独立的 `CurveCodecTest.kt`）会撞 duplicate JVM class，`compileDebugUnitTestKotlin` 直接失败、整套用例静默不可用。新增/拆分测试文件前先 `grep -rn "^class " android/app/src/test`。

## 无线连接设备（adb via mDNS，免 USB，已实测）

前提：设备与主机同一 WLAN，设备端开启「开发者选项 → 无线调试」。

```bash
adb mdns services            # 发现 _adb-tls-connect._tcp 服务，得到 IP:端口
adb devices -l               # 新版 platform-tools 会自动注册 mDNS 设备（服务名即 serial）
adb connect <IP:端口>        # 备选：按 mdns services 输出的地址直连
adb -s '<服务名>' shell id -u # 服务名含空格/括号，必须加引号
```

实测基准机：Redmi songyuan（M098FE，HyperOS 3.0 / Android 16），serial 形如 `adb-xxxxxx-yy (N)._adb-tls-connect._tcp`；`libcosa.so` 真机验证与 §11.4 验收清单均走此连接。

## 不可变边界与仓库卫生

- **`KernelSU/` 只读**：只读取移植/对齐，任何改动落在 `android/` 或 `rust/`。其他 vendored 参考目录（`KernelSU-Next-dev/`、`ReSukiSU-main/` 等，见 `.gitignore`）同样不可编辑。
- **`docs/superpowers/specs|archive/` 勿删**：逆向工程原始证据，代码注释直接引用其路径；`archive/` 只归档不删除。
- **文档默认不入 git**（§10.4）：仅 `README.md` / `CHANGELOG.md` 属仓库门面；`AGENTS.md` 属 agent 协作配置，随仓库分发。
