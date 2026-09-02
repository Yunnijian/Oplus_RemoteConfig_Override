# HyperOS 功能页方案 v2 —— 按功能入口组织（基于完整反编译与实机验证）

日期：2026-09-02。范围：分析 + 逆向 + 方案设计，不含代码。
逆向证据：Joyose/PowerKeeper 全量 jadx 源码 + 4 个系统 so 符号/字符串挖掘 + migt 专项逆向（文件格式破解、消费链实机激活验证）+ songyuan 实机核验。decompiled 源码在 `/tmp/joyose-research/so_re/`。

***

## 〇、migt 专项逆向结论（2026-09-02 完成）

### 0.1 game\_booster.migt 键的真实形态

- 云端键 `migt` = **字符串数组**，每元素 `包名;[migt_freq;migt_ms;fps:thresh表;boost_policy;fps_variance_ratio;super_task_max_num;migt_ceiling_freq]`（4 字段或 8 字段，`;` 分隔；第 3 字段是 `fps1:val1 fps2:val2` 空格分隔映射表）。

- 解析：`f0/z.java:1868-1931` → `f0/b.java`（参数包）+ `f0/a0.java`（MigtFps2Thresh，按实测 fps 查 thresh）。

### 0.2 `/data/system/migt/migt` 文件格式（已破解）

- **逐字节 XOR 0x75（'u'），换行 0x0A 不混淆，一行一条记录**（`utils/p.java#e()`）。

- 内容 = 游戏包名名单（songyuan 实机 167 行：王者/原神/PUBG/网易系…+ `OTHERS` 兜底哨兵）；仅当云端下发 `migt_ceiling_freq` 非 `:0` 时才有 `pkg;<ceiling表>` 行。

- Joyose 只写不读回；无 md5 校验；SELinux 专用标签 `migt_file`。

### 0.3 消费链（实机确证，songyuan 全链路生效 ✅）

1. **mcd 守护进程（libboost.so）**：读名单 → 对前台游戏渲染/逻辑线程 sched\_setaffinity 绑核（ChooseCluster）；经 `/dev/migt` ioctl 送 vsync/FPS。
2. **内核 migt 模块（migt.ko，184KB，Live）**：吃 `/sys/module/migt/parameters/*`（实测 songyuan 有 \~100 个参数：migt\_freq/migt\_ms/migt\_thresh/boost\_policy/fps\_variance\_ratio/migt\_ceiling\_freq/flt\_*/glk\_* 等），做帧负载调频（FLA）、GLK 调频、VIP/super task 选核、超阈 boost。
3. Joyose 运行时行为：游戏进前台 `a0.h(pkg)` 明文写 parameters（实测 40s 内 boost\_policy 0→2、migt\_ms 0→25）；`control/k.java` 每 5 秒帧差测 fps 写 `migt_thresh`；退前台写 `boost_policy=0`。
4. **mi\_thermald 与 migt 无关**（strings 零命中，排除）。

- 缺口：本内核无 `target_fps`/`super_task_max_num`/`glk_ms` 参数，对应写入静默失败（无碍主链路）。

### 0.4 对工具的意义

- migt 配置的展示/修改按普通 JSON 片段走现有 `joyose-scoped-write` 即可（它在 game\_booster 下，属于同一作用域编辑体系）。

- migt 文件本身（XOR 名单）不建议作为 v2 编辑目标：由 Joyose 全量重写管理，工具改了也会被云控刷新覆盖；名单的修改入口就是 `game_booster.migt` 数组。

***

## 一、参数总表（语义 / 取值 / 消费点 / 生效条件）

> 全部结论来自 dex 消费点实读，非猜测。`消费点` = 反编译文件:行。通用生效条件：配置更新经 `d0.q5()` 清空重灌，**内存态即时生效，无需重启系统**；perflock/perfhint 命令要等下一次场景切换或游戏重启重放。

### 1.1 game\_booster 一级开关（节选有行为意义的）

| 参数                                                                       | 语义                             | 取值                 | 消费点                                   | 生效条件           |
| ------------------------------------------------------------------------ | ------------------------------ | ------------------ | ------------------------------------- | -------------- |
| booster\_enable                                                          | 游戏加速总闸，false 时 binder 入口拒绝全部命令 | bool               | control/i.java:830,1437               | 即时             |
| cpuset\_enable                                                           | 游戏 SDK 上报线程 id 时执行绑核           | bool               | control/i.java:1011                   | 需游戏 SDK 上报     |
| migl\_enable / mivk\_settings.enable                                     | MiGL/MiVK 查询接口开关               | bool               | utils/y.java、z.java                   | 下次 SDK 查询      |
| smart\_gaming\_enable(+v2)                                               | SmartGaming 采集+vendor HAL 调用   | bool               | w0/b.java:310                         | 前后台事件触发        |
| qfps\_enable                                                             | 温控限帧时自动暂停/恢复 QSync             | bool+QFPS\_List    | t0/o.java:300                         | 包名在 QFPS\_List |
| SOC\_enable                                                              | 限帧时自动停/恢复 SOC boost            | bool+SOC\_GameList | t0/o.java:302                         | 包名在列表          |
| powerGpu\_enable / aisr\_enable                                          | PowerGpu / AI-SR 控制器           | bool+名单            | t0/s.java、t0/c.java:165               | 包名在名单          |
| self\_gpu\_tuner\_enable                                                 | self\_gpu\_tuner\_config 的解析门  | bool               | z.java:2269                           | 与 config 成对    |
| dynamic\_fps\_global                                                     | 全局温控限帧兜底曲线                     | `温度:fps,...`       | t0/t.java:185                         | 每游戏曲线缺失时       |
| enhance\_default\_switch / force\_disable\_enhance                       | 视觉增强默认态 / 强制停用                 | int / bool         | z.java:2459-2465                      | 即时             |
| scene\_id\_reuse / action\_key\_optimized / game\_group\_mapping\_enable | 场景机制开关                         | bool               | control/j.java:646 / 305 / z.java:814 | 下次场景           |
| charge\_optimize\_enable                                                 | 充电中温度容差 +2°C                   | bool               | t0/t.java:403                         | isCharging     |
| background\_freeze\_enable(+whitelist)                                   | 退后台冻结进程组                       | bool               | control/i.java:1764                   | 包名在白名单         |
| dcs\_enable(+dcs\_config)                                                | 动态 CPU boost 分级                | bool+表             | t0/g.java:467                         | 游戏在 DCS 监控     |
| migt（见第〇章）                                                               | 游戏帧感知加速名单+参数包                  | 见 0.1              | a0.h()/k.java                         | 游戏进前台          |

### 1.2 ovrride\_config 每游戏条目

> 通用条件：按 SP `TARGET_FPS_<pkg>`（默认60）选档；同一游戏按**运行时游戏类型单选一套**——TGAME 套用「无后缀/`_T`」键、MGAME 套用「`_M`」键，任一套内再按 TARGET_FPS 命中专属档；**不跨套回退**（本套缺失只在套内 per-game→全局默认链式回落）。温控/PID 每周期重算，**不需重启游戏**。双套判定与 UI 取舍见 1.2.1。

**帧率/温控曲线类**（消费：t0/t.java GameThermalMonitor、t0/o.java PID、t0/e.java 电池）：

| 参数                                           | 格式                                | 语义                                            |
| -------------------------------------------- | --------------------------------- | --------------------------------------------- |
| dynamic\_fps / \_M / \_multiWin              | `温度:fps,...`                      | 温控限帧基础/多窗曲线，缺省回落 global                       |
| dynamic\_targetfps / \_M                     | `fps#温度:fps,...`                  | 按游戏 targetFps 命中专属曲线                          |
| dynamicfps\_by\_battery\_T/\_M               | `fps#socLevel:limitFps,...`（0=解除） | 低电量限帧；充电或插帧开启时停用                              |
| dynamic\_fan\_targetfps / \_M                | 同 targetfps                       | 仅散热风扇开启（cooling\_fan\_enable==1）              |
| dynamic\_yuanshen\_high\_quality\_targetfps  | 同上                                | 仅原神系且 MIGL 状态关闭                               |
| dynamic\_targetfps\_cpufreq(\_speedmode)/\_M | `温度#fps:freq,...`                 | 温控 CPU 限频曲线；speedmode 变体需 speed\_mode=SPEEDON |
| dynamic\_fps\_hysteresis                     | int 秒                             | 温度档回切滞回防抖                                     |

**PID / 监控类**：

| 参数                                                        | 格式                                        | 语义                                         |
| --------------------------------------------------------- | ----------------------------------------- | ------------------------------------------ |
| PID\_T/\_M（及 \_FOLD/\_HQ1/\_HQ2/\_Wechat/\_RE{n} 变体）      | `fps#温度:参数串,...`                          | 温控限帧 PID 参数；按 游戏类型→RE→画质→分辨率→SR→折叠→小窗 优先级选 |
| badfps\_thresh1/2                                         | int（数组按 60/90/120 取位）                     | 低帧率统计阈值                                    |
| group\_fight\_thresh                                      | int ms                                    | 团战场景识别帧耗时阈值                                |
| monitor\_power                                            | `温度:功率,...`                               | 功耗监控阈值                                     |
| dsar / drr                                                | `sceneId:scale` / `refreshRate#fps:scale` | 动态分辨率；命中写 Settings `dynamic_scale_pkg`     |
| migl\_dr\_by\_temp/\_scene/\_RR(\_enhance/\_RE{n})\_T/\_M | 温度/场景/刷新率曲线                               | MIGL 动态分辨率                                 |
| execute\_cmd\_by\_df\_T/\_M                               | `df:cmd$df:cmd`                           | DF 越阈且温度>DcsInvalidTemp 时执行命令              |
| dfi\_interval\_by\_temp(\_SR)\_T/\_M                      | 温度档                                       | 丢帧检测采样间隔                                   |
| fstb\_cmds                                                | 命令数组                                      | 首启加速固定命令                                   |
| disable\_scenes / start\_scene / end\_scene               | 逗号 scene\_id                              | 场景生效窗口限定；start\_scene 是「生效起点门槛」，非场景注入，见 1.7 |

### 1.2.1 TGAME/MGAME = 狂暴/均衡 两套温控（2026-09-02 反编译实证；用户裁定双展示+中文标注）

**背景**：多数游戏在云控里**同时**配置 TGAME 与 MGAME 两套曲线——这不是「按游戏资产分组」，而是**同一游戏在两种用户功率模式下的温控**：**狂暴（性能）档 / 均衡（普通）档**，由用户在 GameTurbo/功率模式里**手动切换**（用户态），工具无需也不会去「猜当前处于哪一档」。

**判定机制（powerkeeper 实证）**：`game_id` 由 [GameProcessor.sendBroadcast](file:///tmp/pkdec/sources/com/miui/powerkeeper/thermal/processor/GameProcessor.java#L39-L48) 生成：
```
FLAG_TGAME=1; FLAG_MGAME=0;
id = ((normalized_scenario > POWER_MODE_BOUNDARY) || isEnableOptimize) ? TGAME : MGAME;
normalized_scenario = FoldedStatus ? cur : cur - 100;
POWER_MODE_BOUNDARY   = ScenarioConfig.NEW_DEVICES ? 50 : 30;   // 功率模式阈值
```
- 含义：**当前功率场景超「功率模式边界」（≈狂暴/性能档）或「游戏优化」开关开启 → TGAME（狂暴）；均衡/普通档 → MGAME（均衡）**。
- 外层消费：Joyose `i0.q()`=f1680c 据此在对应套内按 TARGET_FPS 选档（[i0.java:259-261](file:///tmp/joyose-research/so_re/joyose_src_full/sources/com/xiaomi/joyose/utils/i0.java#L259-L261)）；广播 `game_id`（`0→MGAME/1→TGAME`）同步（[i0.java:82-90](file:///tmp/joyose-research/so_re/joyose_src_full/sources/com/xiaomi/joyose/utils/i0.java#L82-L90)）。
- `isEnableOptimize = SimpleSettings["key_is_enable_optimize_game"]`（默认回退云控 `needOptimize`，[FeedbackControlService.java:244-250](file:///tmp/pkdec/sources/com/miui/powerkeeper/feedbackcontrol/FeedbackControlService.java#L244-L250)）；`isOptimizeGame(pkg)` = pkg ∈ `key_optimize_game_names`（`;` 名单，[FeedbackControlService.java:253-262](file:///tmp/pkdec/sources/com/miui/powerkeeper/feedbackcontrol/FeedbackControlService.java#L253-L262)）。

**场景/开关本体（ScenarioConfig.java:118-143）**：本机 `NEW_DEVICES=true` → `SCONFIG_TGAME=18`、`SCONFIG_MGAME=19`、`SCONFIG_YUANSHEN=20`、`SCONFIG_PERFORMANCE_BOOST_GAME=25`、`SCONFIG_JKCHESS=29`；tgame 场景组 `tgame:[18,19,68,69,25,75]`（Thermal.xml `setting_dto` GenCGame groups）。`key_optimize_game_names` 实机 `com.tencent.tmgp.cf,com.tencent.jkchess,com.tencent.tmgp.sgame`（全腾讯,即默认走狂暴优化档的游戏集合）。

**UI 取舍（用户裁定：双展示 + 中文标注，不做单选、不判当前档）**：
- 同一游戏 **两套都展示**——因为狂暴/均衡是用户态手动切换，用户自己清楚当前处于哪一档；工具若去猜套反而误导。
- 分组标题由「(TGAME)/(MGAME)」改为 **「狂暴模式」/「均衡模式」**（对应高性能/普通功率档），不再用英文套名。
- 曲线族成对展示：`无后缀/_T` = 狂暴(TGAME) 套，`_M` = 均衡(MGAME) 套；两套同名曲线（如「帧率曲线」「温控限帧曲线」）分别挂在「狂暴模式」「均衡模式」分组下。
- root 判定通道（实机定论，见下）：读 powerkeeper provider `content://com.miui.powerkeeper.configure/SimpleSettings/misc` 的 `key_is_enable_optimize_game`（随狂暴/均衡翻转）+ `key_optimize_game_names`（狂暴优化候选名单）。这是「当前生效套」**唯一可靠且 root 实时可读**的判定源。

**实机双向定论（2026-09-02 songyuan 验证，游戏 com.tencent.tmgp.cf）**：

| 用户模式 | `key_is_enable_optimize_game` | Joyose 日志 | 生效套 | 曲线键 |
| --- | --- | --- | --- | --- |
| 均衡 | `false` | `game mode update: MGAME` / `thermalConfig: MGAME` | MGAME | `dynamic_fps_M` |
| 狂暴 | `true` | `game mode update: TGAME` | TGAME | `dynamic_fps`（无后缀/`_T`） |

- 实机日志（logcat）：均衡时 `SmartPhoneTag_i0: isEnableOptimizeGame: false` + `game mode update: MGAME`，温控实际用 `dynamic_fps_M`（如 `dynamicTemp: {10.0=0, 40.0=120, 42.0=90, 44.0=60, 46.0=45}`）；切回狂暴后 `key_is_enable_optimize_game` 翻转 `true` + `game mode update: TGAME`。
- **定论**：判定 = `key_is_enable_optimize_game`（狂暴 `true` → TGAME 套 / 均衡 `false` → MGAME 套）。配置文件 `dynamic_fps`/`dynamic_fps_M` 始终并存、结构一致（狂暴档阈值通常更激进）；模式切换**仅翻转该开关、不改配置文本**——故「配置内切换无变化」是预期。UI 双套展示时可读该布尔在分组标题旁标注「当前生效」，无值则不标注。

### 1.3 scene\_config / scene\_ovrride 条目

- 结构字段：`scene_id/start/scene_name/timeout/default_need/change_end/change_release_perflock_inner/scene_id_list/extra_data`（语义见消费点 control/j.java:580-661：timeout>0 启动定时释放；change\_end 切场景释放旧 handle）。

- 命令组：`booster`/`booster#<fps>`/`booster#<fps>#MGAME`/`end`/`thermal_high|middle|normal`，元素 `{permission, cmd}`。

- **permission 枚举（f0/a.java，完整）**：`block`（阻塞同步）/ `restore`（恢复释放）/ `tmp`（临时）/ 其它=普通异步。

- **cmd 格式（c0/l.java:172-249 + c0/n.java:125-250）**：`perflock#<id:value对>#<秒>`、`perfhint#<hint>_<userData>_<data1>#<data3>`、`motor#/gun_motor#/vsf#/rebind_tid#<tid>/setprop#<k>#<v>`；支持 `cmd_prefix` 占位符替换。

- 命令组 key 的完整形态：`booster#<TARGET_FPS>#<TGAME|MGAME#GameAndWechat>#SPEEDON#HDR#<机型>#<GPUTunerMode>`（control/j.java D():313-403）。

### 1.4 self\_gpu\_tuner\_config

- `{包名:{profile名:{键:值}}}`，值全字符串；消费在 GPUTunerService.saveProfile（368-462）+ ProfileManager 下发，qgl 别名键做系统属性同值覆盖。

- TunerMode=CUSTOMIZE 时写 SP `CUSTOMIZE_<pkg>`；TextureFilteringQuality 写 `TF_MODE_<pkg>`；DisablePrivateProfileData 为 TRUE/FALSE。

- 生效条件：tuner\_enable 开 + 下次 profile 保存/游戏重启。

### 1.5 fisr\_config（插帧超分）

- `enhance_policy_config[]` 取值枚举：feature=`FI/SR/FISR/RE`；strategy=`FRC/FSR/FSR3/XAISR/XFI/AFME/NT#FI/NT#SR/NT#FISR`（可 `-` 组合）；support\_max\_refresh=数字（默认 60，上限 min(targetFps×倍率, maxFps)）；disable\_scene\_list=scene\_id 数组（命中即停增强）；switch\_default\_status（默认 "0"）。

- 模式语义：1=只插帧 / 2=只超分 / 4=FISR（FI+SR），运行态 SP `fisr_switch_<pkg>`/`fisr_enhance_status_<pkg>`。
- **songyuan 语义修正（2026-09-02）**：本机 `support_dual_dpu=NT`，`fisr_config` 实际作为 **Novatek 独显的策略/支持表**被消费（strategy 限 `NT#FI/NT#SR/NT#FISR`，GameTurbo PQ_FIRST→NT#FISR、FPS_FIRST→NT#FI），不是高通 FISR。详见 1.6。

### 1.6 Novatek 独显插帧超分（2026-09-02 二轮增补；**songyuan 实际激活**，此前"无消费路径"判断作废）

**songyuan 生效性已实证**：`device_features/songyuan.xml` `support_dual_dpu=NT` + `enhance_version=3`；`vendor.xiaomi.hardware.framemaster.IFrameMaster/default` 与 `vendor.novatek.hardware.visdisplaysrv` HAL 均在运行；`/data/system/mcd/fi` 有真实前台游戏包名写入；云控含 **115 条** `novatek_game_params`。boot 决策树（enhance/a.java c():319-356）单选上下文：`support_dual_dpu=NT → NovaTekEnhanceContext`（songyuan 命中），frc/iris_x7/MIFI_SR 均不选中。

配置键全表：

| 键 | 语义 | 格式 | 实机值 | 消费点 |
|---|---|---|---|---|
| `novatek_game_params` | 每游戏独显配置 | JSON 数组，元素 `包名_FI段_SR段_FISR段`（4 token，`_` 分隔；3 token=旧格式无 FISR 段）；`OTHER` 键兜底 | 115 条全 4 token | z.java:2349-2386 → d0.H()/I() → `T2(pkg)` → NovaTekEnhanceContext 全链 |
| `novatek_black_app` | 独显黑名单（云游戏/串流） | 字符串数组（13 条：云游戏/limelight/steamlink/ps/xbox/手柄模拟器） | 13 条 | d0.k1:366，命中=不支持 |
| `novatek_extend_config.novatek_non_playing_config` | 非对局（大厅/加载）降级配置 | 元素同 game_params 格式 | sgame 样例在库 | v/h.n() ← v.f.q()/v.c.q()，enhance_version>1 且非 PLAYING 且 policy==0 |
| `…novatek_perf_policy_config` | GameTurbo 性能策略参数覆盖 | `[{"package_list":[...],"extra_params":"SR段_FI段"}]`（存储时 FI/SR 反序） | 实机未下发 | t/k.java:123,151（policy 1/2 时替换等级参数） |
| `…novatek_gex_fps_limit` | GEX 超分引擎最低帧数限制 | `包名:帧数`（冒号） | sgame:60、pubgmhd:60、cf:60、dfm:60 | t.k.e()（目标帧≤限值剔除 GEX 字段）+ controlSRParams 防抖（v3） |
| `…novatek_bypass_delay` | 退后台 bypass 兜底延迟 | int ms | 2000 | notifyPackageChange 延迟消息 → t.k.o() |
| `…novatek_cmd_control_enable` / `_interval` | FrameMaster 命令节流 | bool / int ms | true / 600（task1004=interval/3） | control/e0 队列，t.k.n()/t.k.t() |
| `…novatek_bypass_refreshrate` | bypass 期间刷新率不下降 | bool | true | t.k.g()：bypass 帧率取 max(当前, user_refresh_rate) |

三段 token 逐段格式（4 token = `包名_FI段_SR段_FISR段`）——每段 = `|` 分隔的温度等级链，每级 7 字段 `dynamicFps#targetFps#params#tgTh#tgRec#mgTh#mgRec`：

- `dynamicFps`（float）：动态帧率门限，游戏真实帧率低于 `ceil(v)-1` → 不插帧降级 bypass
- `targetFps`：输出刷新率，要求 `user_refresh_rate ≥ targetFps`
- `params`：逗号串按序映射 17 键命令：`Category,InputFPS,TargetFPS,MEMC,MEMCMode,LDSR,LDSRMode,SDR2HDR,SDR2HDRMode,Sharpness,SharpnessMode,3DLUT,3DLUTMode,LDSRV2Mode,GEX,GEXMode,EMV,EMVMode`（不足 17 取前 N）
- `tgTh/tgRec`（TGAME）与 `mgTh/mgRec`（MGAME）：温度降档/回档阈值（10s 轮询虚拟温度；最高档仍超温整体停止增强）

实机样例（sgame）：FI 段两级 `73#144#72,144,1,0x2514#45#43#43#41|60.2#120#60,120,1,0x2514#45#43#43#41`（真实帧 ≥72 插到 144 / ≥60 插到 120）；SR 段一级（LDSR:1(0x66)、SDR2HDR:1(0x222)、LDSRV2Mode:0x62…）；FISR 段=FI+SR 合并 15 键全量；温度档统一 45/43/43/41。MEMCMode 低两位=插帧倍率档（0x2012/0x2002/0x2514…）。

消费链路：`d0.T2(pkg) → NovaTekEnhanceContext`：前台切换（旧游戏 stop+延迟 bypass SceneID:0000、写 `/data/system/mcd/fi`←包名、新游戏选策略 doEnhance）；运行态 1001 省电/1002 目标帧率(TGPA)/1003 NT 温控/1004 温度轮询升降档/1006 场景 ID(GEX 防抖)；下发 = binder `vendor.xiaomi.hardware.framemaster.IFrameMaster/default`（cmd 串经 control/e0 节流）→ Novatek 独显 DPU（visdisplaysrv/novavis 配合）。`/data/system/mcd/*`、migt 名单均为运行时派生文件自动重写，无需手改。用户开关 SP：`novatek_switch_<pkg>`、`novatek_enhance_status_<pkg>`。

### 1.6.1 增强器三选与门禁差异（v.f / v.k / v.c，2026-09-02 实机定论）

**增强器选择（两条路）**：
- 策略名映射（[r/d.java:393-394](file:///tmp/joyose-research/so_re/joyose_src_full/sources/r/d.java#L393-L394)）：`NT#FI→v.f`、`NT#SR→v.k`、`NT#FISR→v.c`（fisr_config NT 策略表 / GameTurbo 增强档）。
- 状态映射（[u/c.java:59-67](file:///tmp/joyose-research/so_re/joyose_src_full/sources/u/c.java#L59-L67)）：mode 1→v.f（FI）；mode 2/4→`bean.j() ? v.c : v.k`，其中 `j()` = SR 段任一档 `ceil(dynamicFps)-1 > 0`（[t/b.java:95-110](file:///tmp/joyose-research/so_re/joyose_src_full/sources/t/b.java#L95-L110)）——本机两游戏 SR 段 `dynamicFps=0` → `j()=false` → 走 v.k。

**三条路径门禁差异（关键）**：
| 增强器 | 档位 | 下发内容 | PLAYING 门禁 |
|---|---|---|---|
| v.f（[v/f.java](file:///tmp/joyose-research/so_re/joyose_src_full/sources/v/f.java)） | 只插帧 | MEMC（无 LDSR） | 无 |
| **v.k（[v/k.java](file:///tmp/joyose-research/so_re/joyose_src_full/sources/v/k.java#L87-L131)）** | 纯超分 SR | LDSR/GEX/SDR2HDR | **无**——段存在+开关+SR params 非空即可下发，大厅也生效 |
| v.c（[v/c.java](file:///tmp/joyose-research/so_re/joyose_src_full/sources/v/c.java#L108-L165)） | 插帧+超分 FISR | FI+SR 全量 `c.o()` | **有**——`t.k.w()==true → q()` 降级；`w()`=enhance_version>1 && Z2!=null && !n0.b.d() && policy==0（PLAYING 或 policy≠0 才放行） |

**实机观察（用户切档对比，2026-09-02）**：
- **只开超分**：CF 与 jkchess 都生效（v.k，无场景依赖；CF 大厅 19:10 实测 `LDSR:1/LDSRMode:0x22`+面板 `LDSR:2`）。
- **开插帧（FISR 档）**：出现 `fiAndsr` ↔ `frameInsert` **档位抖动**（SmartPhoneTag_c ↔ SmartPhoneTag_f），`frameInsert` 期间 cmd `LDSR:0`（超分被关）——用户观察到的「超分失效」即此形态（非完全失效，档位在斗）。
- **jkchess 无 sceneId 却全量**：本次（19:38）全程无 `sceneId` 键（仅 fps/"11"/"12" 遥测），但 v.c 全量下发照跑：`running strategy is fiAndsr` + FrameMaster cmd `LDSR:1,LDSRMode:2` + ASIC `LDSR(0x1)` + 面板 `LDSR:2,SR:1`。实机 `enhance_version=3`（[songyuan.xml:827](file:///product/etc/device_features/songyuan.xml)）→ 按 `w()` 需要 PLAYING 或 policy≠0 才放行 → 该放行环节（`getPolicy()` 实测值 / PLAYING 另一置位点）**未钉实**，见遗留。

### 1.7 场景事件协议（rawSceneId）与特化名单（2026-09-02 实证）

**rawSceneId 源头**：游戏客户端 SDK 经 `handleGameBoosterForOneway(uid, cmd=1, data)` 上报报文中的 `sceneId` 键（uid=游戏进程，[i.java:867-870](file:///tmp/joyose-research/so_re/joyose_src_full/sources/com/xiaomi/joyose/smartop/gamebooster/control/i.java#L867-L870)）。无系统侧兜底注入。

**协议表（双处一致）**：[q0/a.java e():66-119](file:///tmp/joyose-research/so_re/joyose_src_full/sources/q0/a.java#L66-L119) 与 i.java/n0.b 语义对齐：

| raw scene | GameSceneIdSender 映射 | n0.b / i.java 语义 |
|---|---|---|
| 5/6/7 | PLAYING | 7 = enter playing |
| 8 | WATCHING | 8 = enter replay |
| 4 | HALL | 4 = 退出全部 |
| 2 | DOWNLOADING | — |
| 3/其它 | OTHERS | — |

**特化名单（raw scene 落入 OTHERS 时按包名补判 PLAYING）**：`com.tencent.tmgp.cod`{701,702,711,712,731,732,751~757}、`com.tencent.tmgp.sgame`{102}、`com.tencent.tmgp.speedmobile`{101,102,103}、`com.tencent.lolm`{100}、`com.tencent.tmgp.pubgmhd`{100~906 大集合}。**cf、jkchess 不在名单** → 走通用映射。

**GameSceneIdSender 出口**：受 `scene_id_sender_enable`（实机 true，[z.java:1792](file:///tmp/joyose-research/so_re/joyose_src_full/sources/f0/z.java#L1792) → `d0.B6` → `Q4()`）控制，经反射 `miui.process.ProcessManager.reportGameScene` 上报系统（供 GameScene 服务）。本机当前无 `GameSceneIdSender` 日志（cf/jkchess 未触发出口二）。

**修正（2026-09-02 二轮推翻旧结论）**：原「超分必依赖 PLAYING」错误——PLAYING 门禁只约束 **v.c（FISR 档）**；纯超分 v.k 无门禁。`start_scene/end_scene/disable_scenes` 语义维持不变（策略段生效窗口门槛，非事件发生器）。jkchess 在 enhance_version=3 下无 sceneId 也全量 → **不再断言「jkchess FISR 永失效」**，待钉实放行环节。

***

## 二、无修改意义参数清单（不展示、不提供编辑）

| 参数                                                                                                                                    | 理由                                     |
| ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------- |
| `header`（lazy\_load\_booster\_config 除外）                                                                                              | 版本比对/下载流程元信息                           |
| `game_name`、`game_group_name`、`package_list`                                                                                          | 纯索引键                                   |
| `ovrride_config#90/#120` 的 `#` 后缀                                                                                                     | 运行时按 SP 查找的键本身                         |
| `scene_id`、`scene_name`、`start`                                                                                                       | 索引/显示/约定值                              |
| `gss_version`                                                                                                                         | 协议版本位（仅 ≥1 与否的布尔差异）                    |
| `resend_last_scene_id`                                                                                                                | 事件重放协议位                                |
| `excellent_fps`                                                                                                                       | 质量上报定义，无设备行为                           |
| `jank_percent`+`middle_level_soc`                                                                                                     | 仅影响上报门槛                                |
| `joint_action_cmd`、`special_ui_message_type`                                                                                          | SDK 对端协议字段                             |
| `migl_support_app_version`                                                                                                            | 版本闸协议字段                                |
| `BetaTraceEnable`、`GameSight`、`hero_die_fps_enable`、`winplay_mqs_enable`、`xgame_status_notifier_enable`、`scenario_recognition_enable` | 埋点/识别/通知类，不改加速行为                       |
| `support_vrs_type`                                                                                                                    | 位掩码能力索引                                |
| `iris_x7_game_params`/`frc_game_params`（songyuan 不消费，保留跨机型透传）                                                             | 仅对应硬件机型消费（iris_x7 属性机 / gpp.frc 机）          |
| PK perf\_engine 的 `sched_apps`/`adv_sched_apps`                                                                                       | RSA 加密 + 全量覆盖合并，不可安全改（且属 PK 体系，本工具不编辑） |

***

## 三、功能页 v2 信息架构（v2.2 修订：全局/每应用严格分界 + 场景命令并入性能调度）

**核心修订（2026-09-02 用户确认）**：HyperOS 云控的 `game_booster` 一级开关（booster_enable/cpuset_enable/smart_gaming_*/qfps_enable/SOC_enable 等）是**全局配置，不存在每应用形态**——每应用行为差异全部由"白名单列表成员资格"（QFPS_List/SOC_GameList/support_aisr_app…）和 ovrride 条目驱动。因此：

- **全局开关只出现在全局"通用配置"页**（现有 CommonConfig 页），从每应用功能页中**全部移除**（原每应用"性能调度"里的重复开关全部删除）；
- **场景命令不单独设入口**——场景命令页本质就是性能调度，scene_ovrride 结构化编辑并入每应用"性能调度"页；每应用入口由 7 个调整为 **6 个**；
- **perflock 命令不进功能页**（游戏性能大多不依赖此，编辑无意义）——场景命令编辑器不展示、不解析 perflock 类命令，保存时原样透传保留；
- **`end` 命令组不纳入**（用于恢复设备默认态，无修改价值）——不展示、不编辑、原样保留；
- 功能页不提供任何 `/sys/module/migt/parameters/*` 实时回显。

```
App 详情页（重设计）
├─ 顶部：应用卡（图标/包名/组别名/云控版本/冻结状态/conflicts 提示）
└─ 功能入口列表（按该 App 片段实际数据 + DeviceCaps 动态显隐）
   ├─ 🌡️ 温控与帧率        → ThermalFpsScreen（per-app：ovrride 条目曲线/阈值）
   ├─ 🎮 性能调度          → PerfScheduleScreen（per-app 项 + scene_ovrride 结构化编辑，见第五章）
   ├─ 🎞️ 插帧超分          → EnhanceScreen（fisr_config[NT 策略表] + Novatek 独显配置，见 1.5/1.6）
   ├─ 🖼️ 动态分辨率        → DynResScreen（MIGL，per-app 曲线）
   ├─ ⚙️ GPU 自研调参      → GpuTunerScreen（self_gpu_tuner_config 该 App profile）
   └─ 🔥 migt 帧感知加速    → MigtScreen（game_booster.migt 该 App 基线条目）
```

**"性能调度"页内容（per-app + 场景）**：
1. per-app 项：`dcs_enable`(+dcs_config)、`disable_scenes`/`start_scene`/`end_scene`（场景生效范围）、`group_fight_thresh`、`fstb_cmds`（只读）、`need_game_sdk`。
2. 场景管理（scene_ovrride 结构化编辑）：场景条目树（scene_id/scene_name/timeout/default_need/change_end/change_release_perflock_inner）+ 每场景 `booster*` 命令组的结构化编辑（**排除 perflock 类命令与 end 组**——perfhint/glk/setprop/裸路径等可编辑，perflock/end 原样透传不显示）；命令组 key 用 19 槽位词表构建器（5.4）。

**"插帧超分"页内容（songyuan = NT 独显路径）**：fisr_config NT 策略表（game_list 成员资格 + strategy 枚举 + switch_default_status）+ `novatek_game_params` 三级策略链编辑（FI/SR/FISR 段，等级列表 7 字段 + params 17 键）+ `novatek_black_app` 成员资格 + `novatek_extend_config`（non_playing 降级链/gex_fps_limit/bypass_delay/bypass_refreshrate/cmd_control 开关与间隔）+ 运行态只读 SP（`novatek_switch_<pkg>`/`novatek_enhance_status_<pkg>`）。**不编辑**：novatek_perf_policy_config（实机未下发且存储反序）；iris_x7/frc_game_params 跨机型透传。

其它入口页内容不变（温控与帧率/动态分辨率/GPU 自研调参/migt 见第一章）。显隐判断同时考虑 TARGET_FPS 选档（有 `ovrride_config#90/#120` 数据时提供档位切换）。

***

## 四、设备支持值自动识别（写入选择器的数据源）

统一由 CLI/Kotlin 层新增 `device-caps` 采集（root 一次性读取，缓存）：

| 能力           | 节点                                                                       | 用途                                                                |
| ------------ | ------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| CPU 各簇频率表    | `/sys/devices/system/cpu/cpu{N}/cpufreq/scaling_available_frequencies`   | 温控 CPU 限频曲线编辑器档位（songyuan：小核 384M-3.63G、大核 768M-4.4G）             |
| CPU governor | `.../scaling_available_governors`                                        | 调度器选择（songyuan：walt/conservative/powersave/performance/schedutil） |
| GPU 频率表      | `/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies`（或 freq\_table\_mhz） | GPU 档位选择（songyuan：160M-1.2G 共 18 档）                               |
| GPU governor | `/sys/class/kgsl/kgsl-3d0/dev_governor`、可用 gfs                           | GPU 调度选择                                                          |
| migt 参数存在性   | `ls /sys/module/migt/parameters/`                                        | migt 编辑器字段级显隐（无 target\_fps/super\_task\_max\_num 就灰置该字段）         |
| cpuset       | `/dev/cpuset/*/cpus`                                                     | 绑核选择器（songyuan：0-7）                                               |
| 温度传感         | `/sys/class/thermal/thermal_zone*/type`                                  | 温控曲线编辑器 x 轴校准与当前温度显示                                              |
| 刷新率          | `dumpsys display` supported modes                                        | fps 档位选择（60/90/120…）                                              |
| 帧率策略         | `TARGET_FPS_<pkg>` SP + 显示支持档                                            | 决定读/写 `ovrride_config` 还是 `#90/#120` 档                            |

编辑器行为：数值/频率/帧率字段从上述表取可选值（下拉或滑档），同时允许手输（校验在列表内）；migt fps:thresh 表编辑器的 fps 轴用 App 支持档位。

***

## 五、场景命令语义总表与结构化编辑（2026-09-02 增补，实机 541 条命令统计实证）

### 5.1 migt 三层通道关系（与"migt 帧感知加速"入口的关系）

实测存在**三层 migt 写入通道，相互独立、可写同一批 `/sys/module/migt/parameters/*` 节点，叠加 last-write-wins，无互斥亦无优先级管理**：

| 层 | 配置位置 | 行为 | 时机 |
|---|---|---|---|
| 游戏级基线 | `game_booster.migt` 数组（94 个游戏） | 写 XOR 名单文件 + 按 fps 查 thresh 表写参数（进前台 applyParam、退前台 boost_policy=0） | 配置加载 + 游戏前后台 |
| 场景级覆盖 | scene_config/scene_ovrride 的命令组（`glk#MF/BP` 占位符 + 裸 sysfs 路径） | 实测 41 个 migt 节点 1700+ 引用（flt_target_fps×93、frame_boost_enable×90、choose_cpu_exclusive_enable×78、stask_prefer_cpu×76…）+ `/sys/module/mist/*`、`/data/system/mcd/policy` | 进入对应场景 |
| 退出复位 | `global_config.end[]` | 实机是一条巨型 `glk#` 全复位命令（prefer_cpu/-1、enable/0…） | 游戏退后台 |

**工具层处理**：MigtScreen 只管游戏级基线（UI 标注"游戏级基线"）；场景命令中的 migt 类命令保留在性能调度页的命令编辑器内解析（标注"场景级覆盖"）。不合并、不去重、不做优先级仲裁；**功能页不提供 `/sys/module/migt/parameters/*` 实时回显**（2026-09-02 用户裁定）。

### 5.2 cmd 完整语法表（c0/l.java 分发 + c0/n.java 执行）

| 前缀 | 格式 | 参数语义 | 备注 |
|---|---|---|---|
| `perflock#` | `perflock#<id_val_id_val...>#<timeout秒>`（`_` 分隔，id/value 全 16 进制，对数偶数） | QCOM perfLockAcquire(ms, int[])；`timeout=0` = 持久锁（handle 存 SP `perflock/handle_id`，服务重启统一释放）| MTK 平台含 id∈{1,2,31,32,61,62,91,92} 时上报 CpuLimit |
| `perfhint#` | `perfhint#<hint_hex>_<userData>_<data1>#<data3>` | fire-and-forget；实机仅 0x1401（QSync 开关，data3=0/1）与 0x1095，userData/data1 恒 -1 | |
| `glk#` | `glk#<占位符>#<值>;...` | 占位符→节点映射（control/d.java:54-82）：MM=glk_minfreq、MA=glk_maxfreq、WA=glk_freq_limit_walt、BI=force_stask_to_big、RE=force_reset_runtime、BE=glk_fbreak_enable、**MF=migt_freq、BP=boost_policy**、GD=glk_disable、RM=re_marking_ai_task、FR=flt_report、MS=glk_ms、SCN=stask_candidate_num、FPM=fas_power_mod | 值可含 `cluster:freq` 映射或自由文本 |
| `raf#/rafe#` | 小窗频率开始/结束 | 运行时拼接节点与小窗标识 | |
| `sw#/swe#` | `sw#cpus#0;` 小窗 cpuset 绑定 | `cpus` 占位符运行时替换、追加 uid | |
| `DDR#` | `DDR#<freq_kHz>` | 展开为 3 条 memlat max_freq 节点写 | QCOM 专属 |
| `motor#` / `gun_motor#` | `motor#<强度>[#light]` / `gun_motor#<强度>#<JSON>` | 震动（effectId/strength/loop） | 实机未出现 |
| `vsf#` | `vsf#<0\|1>` | SurfaceFlinger binder 5200/5201 | |
| `rebind_tid#` | `rebind_tid#<tid>` | 5s 后重放该包 cpuset 绑核 | |
| `setprop#` | `setprop#<k>#<v>[#<k2>#<v2>...]` | 奇数段 ≥3，两两成对 SystemProperties.set | |
| `scx_daemon#` / `vip#` | kv 串 / MTK VIP2 | scx_daemon kv→JSON binder；vip 走 MtkPowerServiceManager | |
| 裸路径 | `/sys/.../node#<值>;...` | 通用 sysfs 通道：写 whetstone perf_data → `setprop mcd.extra.params` + `ctl.start mcd_init` 由 mcd 守护执行；**单段 ≤127 字符** | |

**permission 语义**（f0/a.java：17-27）：`block`=阻塞同步 / `restore`=恢复释放 / `tmp`=临时 / 缺省=异步。实机 541 条全部为 `"root"`（走异步分支）——permission 只是状态机标记，**不是能力校验**，编辑器按枚举下拉（block/restore/tmp/空）处理。

### 5.3 perflock id 实测语义表（songyuan 116 条统计，value 与频表交叉验证）

| id | 语义 | value 量纲/规律 |
|---|---|---|
| 0x40800000 / 0x40800200（±0100/0300） | CPU 集群 min freq | **MHz**（HAL 钳到频表最近档；3628→3628800✓、4185→4185600✓、4320 钳到 4396800） |
| 0x40804000 族 | CPU max freq clamp | MHz；**0xEFFF/0xFFF=不限制** |
| 0x40400000 | 核心保持（lock_min_cores） | 恒 1 |
| 0x40C00000 | 调度加速 sched_boost | 0/1 |
| 0x40C9C000 | 大核保持数 | 4 |
| 0x40C1C100/200、0x40CE0200 | 调度迁移/小任务阈值 | 20/30 或打包 0x1e0014（30:20 双 16 位） |
| 0x41800000、0x43000000、0x43400000 | 总线带宽/上限"放开"标记 | ff/ffff |
| 0x42C10000 | 调度增强（walt/RTG） | 1 |
| **0x42804000** | **GPU min freq 档位索引** | **0 基升序，与 gpu_available_frequencies 18 档对齐**（12=钳到顶 1200MHz） |
| 0x42838000~0x42860000 族 | GPU 阈值/档位/忙碌% | 小整数 |
| 0x42C44000 / 0x42C60000 | CPU 亲和掩码 | 位掩码 |
| 0x43C38000 / 0x43C38200 | DDR 最小带宽 | B/s（0x51f40000=1310MB/s） |
| 0x5FC00000 族 | L3 频率档 | 档位索引 |

timeout 分布：0=持续场景（coldLaunch/foreground），非 0（3~25s）=瞬态场景（target_fps_update 等）。**编辑器规则**：0x42804000 提供设备 GPU 档位下拉；0x408xxxxx 提供 MHz 输入（频表钳位提示）；**未知 id 照样透传**（新 SoC 新 id）。

### 5.4 场景命令组 key 匹配规则（key 构建器依据）

- `action_key_optimized=true`（实机）→ **V2 最长全段命中**：key 按 `#` 切段，每段值必须落在 19 槽位词表（1=FPS 20~185、2=TGAME/MGAME/CGAME、3=SPEEDON/OFF、4=HDR/OFF、5=RAM 8G/12G/16G、6=GPUTunerMode、7=ED/FI/SR/SP/FISR、8=多任务复合（含别名 video）、9=画质、10=DDR、11=密度、12=YS_RE、13=VK/GL、14=MISR、15=YSRS、16=FOLD、17=对局规模、18=P2P、19=FAN）；段值不在词表 → **该 key 永不命中**。
- V1（旧机）逐级降级前缀匹配（去 GPUTuner→去 RAM→去 HDR→去 SPEEDON→去类型→裸 booster）。
- 实机 key 样例：`booster`、`booster#120`、`booster#144`、`booster#video#TGAME`、`booster#30#ED#video`、`booster#60#FI#leave`、`booster#120#MGAME`、`end`。
- 编辑器新增/修改 key 时必须做词表校验；`cmd_prefix`（实机未下发）解析后透传展示。

### 5.5 结构化命令编辑器（CmdEditor）设计（v2.2 修订：并入性能调度页，perflock/end 不进功能页）

**作用域（2026-09-02 用户裁定）**：CmdEditor 只服务于每应用"性能调度"页的 scene_ovrride 命令组编辑（不再设独立场景命令入口）。编辑范围排除：

- **`perflock#` 类命令不进功能页**——游戏性能大多不依赖此，无编辑意义；编辑器不展示、不解析，保存时**原样透传保留**（绝不丢弃/重排）；
- **`end` 命令组整组不纳入**——用于恢复设备默认态，无修改价值；UI 不显示，数据原样保留；
- `default_config`/`global_config{start,end}` 属全局配置，不在每应用页出现。

按可解析程度三级（对纳入范围的命令）：

1. **完全结构化**：`perfhint#`（4 字段表单）；`setprop#`（k/v 对表格）；`permission`（枚举下拉 block/restore/tmp/空）。
2. **半结构化**：`glk#`（占位符段枚举下拉 MM/MA/WA/BI/RE/BE/MF/BP/GD/RM/FR/MS/SCN/FPM + 值域自由文本）；场景 key（19 槽位段构建器，段值枚举 = 词表 + 实机出现值）。
3. **透传**（不解析，仅段数/长度校验）：裸 sysfs 路径命令、`scx_daemon#/vip#/motor/gun_motor/vsf/rebind_tid/raf/sw/DDR`、一切未知前缀。

统一规则：每条命令保留"原文模式"切换（解析失败或用户选择时按整串文本编辑）；**不整条配置文本编辑**；perflock 命令在数据层必须逐字保留（diff 预览时呈现为"未变更"）。

### 5.6 UI 组件规范（强制项，2026-09-02 增补）

- **Material 布尔行必须用 `SegmentedSwitchItem`**（ui/component/material/SegmentedList.kt:435，KernelSU 对齐 + ExpressiveSwitch 末尾）——当前实现的 Material 分支渲染纯文本行无开关组件，属违规。
- Material 下拉/选择器用既有 SegmentedList 组件族；对话框沿用现有模式；Miuix 用 SwitchPreference/WindowDialog。
- 曲线编辑器行输入必须走 snapshot 状态（mutableStateOf），校验/确定门控随输入实时刷新。
- 子屏 Scaffold 底部内容消费 bottom inset（contentWindowInsets 含 Bottom 或末尾 Spacer），防手势栏遮挡。
- 底部导航/页脚遮挡检查对齐现有 Pager 页契约。

## 六、修改策略：无高危分级，统一写入路径

- **取消 A/B/C 高危分级**——所有可编辑参数一律同一套写入纪律，不设"只读+确认"档：

  1. 全部经 `joyose-scoped-write`（force-stop + 双库镜像 + 回读校验）；
  2. 写入前置校验只做**格式校验**（类型、串格式、枚举合法性、设备支持值域），不做主观风险判断；
  3. 保存前 diff 预览（键：旧值 → 新值）；
  4. version 不 bump（满足 cc\_version 闸）；持久生效依赖 teg 冻结（已有）；
  5. 结构性增删（场景条目/命令组数组项）在作用域编辑器完成——CLI 已有形状守卫与报错。

- 生效链认知（写后提示用）：内存态参数即时生效；perflock/perfhint 命令等下次场景切换；self\_gpu\_tuner 等下次 profile 保存；云重放覆盖由冻结挡住。

## 七、实施切片建议（代码阶段，未开始）

1. **S1 数据层**：`device-caps` 采集 + Kotlin 数据类（DeviceCaps）；JoyoseManager 增加 per-app ovrride 条目存在性查询。
2. **S2 功能页框架**：详情页改功能入口列表（双皮肤 Scaffold 复用，注意 Pager 页 bottomInnerPadding 契约）；路由注册 **6 个功能屏**（v2.2：无通用开关、无独立场景命令屏）。
3. **S3 各功能屏**：按 优先级 = 温控与帧率 → 性能调度（含 scene_ovrride 结构化编辑 + CmdEditor，见第五章；perflock/end 排除）→ 插帧超分（fisr NT 策略表 + Novatek 1.6 全部键）→ migt → GPU 自研调参 → 动态分辨率。
4. **S4 编辑器组件**（双皮肤复用）：曲线编辑器（`温度:值` 串 ↔ 表格 + 设备频率档下拉）+ **Novatek token 编辑器**（4 token 段 ↔ 三级策略链 ↔ 17 键 params 表格）+ CmdEditor（5.5）。
5. **S5 保存前 diff 预览**（文档六章第 3 条，单列切片）。

## 八、遗留

- migt 名单文件（XOR）不作为编辑目标（Joyose 全量重写管理）。
- Material 皮肤与 Miuix 同步实现（S3 每屏双写）。
- 广播 `profile_local` 轻量重载实测（替代 force-stop）。
- 曲线温度逆序/重复档位校验与归一（消费端行为未定义，先保持透传）。
- 多条 ovrride 冲突（conflicts）在功能子屏的提示。
- **jkchess 无 sceneId 也全量下发之谜（enhance_version=3，见 1.6.1）**：`t.k.w()` 理论上需 PLAYING 或 policy≠0 才放行 v.c，但实测无 sceneId 上报也全量。待钉实：实机 `NovaTekEnhanceContext.getPolicy()` 值、`n0.b.d()` 状态、是否另有 PLAYING 置位点（或 enhance_version 特性另有读取口径）。这可能同步推翻「FISR 必须对局」的残余表述。
- **无对局场景上报游戏的档位可用性（见 1.6.1/1.7）**：纯超分（v.k）无需 PLAYING，CF/jkchess 均可用；FISR（v.c）档位是否受 PLAYING 约束需以上一谜团定论后再评估「root 注入 sceneId:7 兜底」是否必要。当前 UI 不对「超分」档提示 PLAYING 缺失。
- decompiled 源码在 /tmp（重启丢失），是否归档由用户决定。

