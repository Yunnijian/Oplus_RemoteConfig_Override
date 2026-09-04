package com.remoteconfig.override.data

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * HyperOS（com.xiaomi.joyose）云控数据源。
 *
 * 经 root shell 调用 libcosa.so 的 joyose-* 命令族（见 rust/src/joyose/store.rs）：
 * 成功时 stdout 为单个 JSON 文档；失败时 stderr 携带可读错误文本。
 *
 * 备份目录使用 App 私有目录（filesDir/joyose），与参考 KernelSU 模块的
 * /data/adb/joyose-edit 完全独立 —— App 为独立设计，模块仅作参考；
 * App 卸载自动清理，备份列举无需 root（CLI 已把文件 chown 回 App uid）。
 */
class JoyoseManager(context: Context) {

    // ── wire models（对齐 Rust stdout 协议，字段宽容缺省）──

    @Serializable
    data class StatEntry(
        val exists: Boolean = false,
        val path: String = "",
        val size: Long = 0,
        val mtime: Long? = null,
    )

    @Serializable
    data class SpInfo(
        val exists: Boolean = false,
        @SerialName("pref_local_max_version") val prefLocalMaxVersion: String = "0",
        val frozen: Boolean = false,
    )

    @Serializable
    data class Stat(
        val ok: Boolean = false,
        val smartp: StatEntry = StatEntry(),
        val teg: StatEntry = StatEntry(),
        val sp: SpInfo = SpInfo(),
        val backups: Int = 0,
    )

    @Serializable
    data class CloudRow(
        @SerialName("config_name") val configName: String,
        val version: Long = 0,
        val enable: Long = 0,
    )

    @Serializable
    data class RuleRow(
        @SerialName("rule_module") val ruleModule: String,
        @SerialName("rule_version") val ruleVersion: Long = 0,
        val rows: Long = 0,
    )

    @Serializable
    data class ListResult(
        val ok: Boolean = false,
        @SerialName("cloud_config") val cloudConfig: List<CloudRow> = emptyList(),
        val rules: List<RuleRow> = emptyList(),
    )

    @Serializable
    data class AppIndexEntry(
        @SerialName("package") val pkg: String,
        val group: String? = null,
        val features: Int = 0,
    )

    @Serializable
    data class AppsResult(val ok: Boolean = false, val apps: List<AppIndexEntry> = emptyList())

    /** 开关/参数项：value 为原始 JSON（bool / string / 对象均可）。 */
    @Serializable
    data class ParamItem(val name: String, val value: kotlinx.serialization.json.JsonElement)

    @Serializable
    data class Gate(val key: String, val enabled: Boolean)

    @Serializable
    data class FeatureHit(
        val category: String,
        val label: String,
        val source: String,
        val key: String,
        /** RFC 6901 指针；null = 只读成员类（黑名单/整表类别），
         *  其指针会指向全局容器，Rust 侧不产出、作用域编辑也不收录。 */
        val path: String? = null,
        val params: List<ParamItem> = emptyList(),
        val overrides: List<String> = emptyList(),
        val gate: Gate? = null,
    )

    @Serializable
    data class CommonInfo(
        @SerialName("in_game_list") val inGameList: Boolean? = null,
        @SerialName("in_support_app") val inSupportApp: Boolean? = null,
    )

    /** AppView（joyose-app 输出；Rust 端 "package" 是 Kotlin 关键字，映射为 packageName）。 */
    @Serializable
    data class AppView(
        @SerialName("package") val packageName: String,
        val group: String? = null,
        val common: CommonInfo = CommonInfo(),
        @SerialName("global_switches") val globalSwitches: List<ParamItem> = emptyList(),
        val features: List<FeatureHit> = emptyList(),
        val conflicts: List<String> = emptyList(),
    )

    @Serializable
    data class CommandOk(val ok: Boolean = false)

    data class WriteResult(val success: Boolean, val message: String)

    // ── runtime ──

    private val appContext = context.applicationContext

    private val json = Json { ignoreUnknownKeys = true }

    private val binary: String
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libcosa.so").absolutePath

    /** 备份根目录（App 私有）：由 App 进程创建，切勿用 root 建以免破坏属主。 */
    val dataRoot: File get() = File(appContext.filesDir, "joyose").apply { mkdirs() }

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun run(vararg args: String): Shell.Result =
        Shell.cmd((listOf(binary) + args).joinToString(" ", transform = ::quote)).exec()

    private fun parseOut(result: Shell.Result, fallback: String): String =
        (result.out.asSequence() + result.err.asSequence())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
            .ifEmpty { fallback }

    /** 成功 → 解析 stdout JSON；失败 → 抛出可读错误。 */
    private inline fun <reified T> exec(vararg args: String): T {
        val result = run(*args)
        if (!result.isSuccess) {
            error(parseOut(result, "joyose ${args.firstOrNull() ?: ""} 执行失败"))
        }
        val text = result.out.joinToString("\n").trim()
        return json.decodeFromString(text)
    }

    private fun execOk(vararg args: String, successMessage: String): WriteResult {
        val result = run(*args)
        val raw = parseOut(result, successMessage)
        return if (result.isSuccess) {
            val ok = runCatching { json.decodeFromString<CommandOk>(raw).ok }.getOrDefault(true)
            WriteResult(ok && result.isSuccess, raw)
        } else {
            WriteResult(false, raw)
        }
    }

    // ── 数据操作（阻塞调用，调用方须置于 IO 调度器）──

    /** Joyose 应用版本（PackageManager 查询，无需 root）。 */
    fun joyoseVersion(): String = runCatching {
        val info = appContext.packageManager.getPackageInfo(PKG, 0)
        info.versionName ?: "未知"
    }.getOrDefault("未知")

    /** 双库指纹 + 冻结状态；Joyose 不可用（缺 DB 等）时返回 null。 */
    fun stat(): Stat? = runCatching { exec<Stat>("joyose-stat", dataRoot.absolutePath) }.getOrNull()

    fun list(): ListResult? = runCatching { exec<ListResult>("joyose-list") }.getOrNull()

    /** per-app 功能索引（组别名已展开，features 降序）。 */
    fun apps(): List<AppIndexEntry> =
        runCatching { exec<AppsResult>("joyose-apps").apps }.getOrDefault(emptyList())

    /** joyose-app 输出外层包装。 */
    @Serializable
    data class AppViewResult(val ok: Boolean = false, val app: AppView)

    // ── 设备能力（joyose-device-caps 输出；Rust 侧节点缺失 → null，消费端做显隐）──

    /** CPU 单簇：成员核 + 频率表（Hz）+ 可用 governors。 */
    @Serializable
    data class CpuCluster(
        val cpus: List<Long> = emptyList(),
        val frequencies: List<Long> = emptyList(),
        val governors: List<String> = emptyList(),
    )

    /** GPU：频率表（Hz，freq_table_mhz 变体已统一为 Hz）+ 当前/可用 governor。 */
    @Serializable
    data class GpuCaps(
        val frequencies: List<Long>? = null,
        val governor: String? = null,
        val governors: List<String>? = null,
    )

    /** migt 内核模块参数清单；exists=false 时隐藏 migt 功能入口。 */
    @Serializable
    data class MigtCaps(
        val exists: Boolean = false,
        val parameters: List<String> = emptyList(),
    )

    /**
     * 设备能力快照（joyose-device-caps）。全部字段可空 —— 任何节点缺失时
     * Rust 输出 null，消费端按 null 隐藏/灰置对应编辑器字段。
     */
    @Serializable
    data class DeviceCaps(
        val ok: Boolean = false,
        @SerialName("cpu_clusters") val cpuClusters: List<CpuCluster>? = null,
        val gpu: GpuCaps? = null,
        val migt: MigtCaps? = null,
        val cpusets: Map<String, String>? = null,
        @SerialName("thermal_zones") val thermalZones: List<String>? = null,
        @SerialName("refresh_rates") val refreshRates: List<Int>? = null,
    ) {
        /** migt 是否有某参数（字段级显隐/灰置判断）。 */
        fun hasMigtParam(name: String): Boolean =
            migt?.exists == true && migt.parameters.any { it == name }

        /** 全部 CPU 频率档（去重升序，供限频曲线编辑器下拉）。 */
        val cpuFrequencies: List<Long>
            get() = cpuClusters.orEmpty().flatMap { it.frequencies }.distinct().sorted()

        /** 全部 CPU governor（去重，供调度器选择）。 */
        val cpuGovernors: List<String>
            get() = cpuClusters.orEmpty().flatMap { it.governors }.distinct()

        /** GPU 频率档（Hz 升序；null = 无 kgsl）。 */
        val gpuFrequencies: List<Long>
            get() = gpu?.frequencies.orEmpty().distinct().sorted()
    }

    /** 设备能力采集（root 一次性；调用方须置于 IO 调度器并进程内缓存）。 */
    fun deviceCaps(): DeviceCaps? =
        runCatching { exec<DeviceCaps>("joyose-device-caps") }.onFailure {
            android.util.Log.e(TAG, "deviceCaps() 失败", it)
        }.getOrNull()

    /** 单个 App 的聚合功能视图；解析失败返回 null（失败原因进 logcat）。 */
    fun appView(packageName: String): AppView? =
        runCatching { exec<AppViewResult>("joyose-app", packageName).app }.onFailure {
            android.util.Log.e(TAG, "appView($packageName) 失败", it)
        }.getOrNull()

    /** 原始 params JSON（booster_config 等），供写路径/高级编辑使用。 */
    fun readRaw(config: String): String? {
        val result = run("joyose-read", config)
        return if (result.isSuccess) result.out.joinToString("\n").trim().ifEmpty { null } else null
    }

    /**
     * 作用域片段文档（joyose-scoped 输出）：键 = JSON Pointer，值 = 该 App 名下
     * 的原始片段。取数、指针解析与格式化全部在 CLI 内完成，App 进程不接触整份文档。
     */
    @Serializable
    data class ScopedResult(
        val ok: Boolean = false,
        @SerialName("package") val packageName: String = "",
        val document: JsonObject,
        /** 本次未能解析的指针（正常恒为空；非空说明 Rust 侧指针与文档形状脱节）。 */
        val skipped: List<String> = emptyList(),
    )

    /** 单个 App 的云控片段；解析失败返回 null（失败原因进 logcat）。 */
    fun scoped(packageName: String): ScopedResult? =
        runCatching { exec<ScopedResult>("joyose-scoped", packageName) }.onFailure {
            android.util.Log.e(TAG, "scoped($packageName) 失败", it)
        }.getOrNull()

    /**
     * 双库镜像写入 params 文档。CLI 内部已完成 force-stop、双侧回读校验；
     * version 保持不变（不 bump 策略）。
     */
    fun writeConfig(config: String, jsonText: String): WriteResult =
        withTempJsonFile(jsonText) { path ->
            execOk("joyose-write", config, path, successMessage = "写入成功")
        }

    // ── migt 名单与运行态（功能页 v2；数组增删超出作用域编辑能力，走专用命令）──

    /** 替换/追加一个游戏的 migt 条目（CLI 带形状校验；同样双库镜像写）。 */
    fun migtWrite(entry: String): WriteResult =
        execOk("joyose-migt-write", entry, successMessage = "已写入 migt 配置")

    /** 从 migt 名单移除一个游戏（不存在时为无害 no-op）。 */
    fun migtRemove(packageName: String): WriteResult =
        execOk("joyose-migt-remove", packageName, successMessage = "已移除 migt 配置")

    /** 只读回显 /sys/module/migt/parameters/ 下的参数文件（缺失自动跳过）。 */
    fun readMigtRuntimeParams(names: List<String>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        // 名称来自常量表，但一律走 quote() 防御性转义（参数化拼接，禁裸插值）
        val script = names.joinToString(" ; ") { n ->
            val q = quote(n)
            "echo $q=\$(cat /sys/module/migt/parameters/$q 2>/dev/null)"
        }
        val result = Shell.cmd(script).exec()
        val map = mutableMapOf<String, String>()
        for (line in result.out) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                val value = line.substring(idx + 1).trim()
                if (value.isNotEmpty()) map[line.substring(0, idx).trim()] = value
            }
        }
        return map
    }

    /**
     * 在 Joyose 的 shared_prefs 里按键名取值（运行态只读，如 fisr_switch_<pkg>）。
     * 支持 boolean/int 的 value="..." 形态与 <string name="k">v</string> 形态。
     */
    fun readJoyoseSpKeys(keys: List<String>): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        // 键名可能携带云控侧包名（fisr_switch_<pkg> 等）——pattern 走 quote() 参数化，
        // 消除单引号闭合注入面（Regex.escape 只处理正则元字符，不处理 shell 引号）。
        val safeKeys = keys.map { it.replace("'", "") }.filter { it.isNotBlank() }
        if (safeKeys.isEmpty()) return emptyMap()
        val pattern = safeKeys.joinToString("|") { Regex.escape(it) }
        val result = Shell.cmd(
            "grep -h -s -E ${quote("name=\"($pattern)\"")} /data/user/0/$PKG/shared_prefs/*.xml",
        ).exec()
        val map = mutableMapOf<String, String>()
        for (line in result.out) {
            val nameIdx = line.indexOf("name=\"")
            if (nameIdx < 0) continue
            val nameStart = nameIdx + 6
            val nameEnd = line.indexOf('"', nameStart)
            if (nameEnd <= nameStart) continue
            val name = line.substring(nameStart, nameEnd)
            val vIdx = line.indexOf("value=\"")
            val value = if (vIdx >= 0) {
                val vStart = vIdx + 7
                val vEnd = line.indexOf('"', vStart)
                if (vEnd > vStart) line.substring(vStart, vEnd) else ""
            } else {
                val open = line.indexOf('>', nameEnd)
                val close = line.indexOf("</string>", open)
                if (open >= 0 && close > open) line.substring(open + 1, close) else ""
            }
            if (value.isNotEmpty()) map[name] = value
        }
        return map
    }

    /**
     * 作用域写回：CLI 按指针把片段补丁进当前库里的整份文档，只改本 App 名下的
     * 片段；新增/改名/删除键由 CLI 直接报错（不静默丢弃），同样走双库镜像写。
     */
    fun writeScoped(packageName: String, jsonText: String): WriteResult =
        withTempJsonFile(jsonText) { path ->
            execOk("joyose-scoped-write", packageName, path, successMessage = "写入成功")
        }

    private inline fun withTempJsonFile(
        jsonText: String,
        block: (path: String) -> WriteResult,
    ): WriteResult {
        if (jsonText.isBlank()) return WriteResult(false, "JSON 内容为空")
        val tmp = File(appContext.cacheDir, "joyose-write-${System.nanoTime()}.json")
        return try {
            tmp.writeText(jsonText)
            block(tmp.absolutePath)
        } catch (e: Exception) {
            WriteResult(false, "写入失败: ${e.message ?: "无法创建临时文件"}")
        } finally {
            tmp.delete()
        }
    }

    fun freeze(): WriteResult = execOk("joyose-freeze", successMessage = "已冻结 teg 云控")

    fun unfreeze(): WriteResult = execOk("joyose-unfreeze", successMessage = "已解冻")

    fun backup(label: String?): WriteResult =
        execOk(
            "joyose-backup", dataRoot.absolutePath, *listOfNotNull(label).toTypedArray(),
            successMessage = "已备份",
        )

    fun backupList(): List<BackupEntry> = runCatching {
        exec<BackupListResult>("joyose-backup-list", dataRoot.absolutePath).backups
    }.getOrDefault(emptyList())

    fun revert(name: String): WriteResult =
        execOk("joyose-revert", dataRoot.absolutePath, name, successMessage = "已回滚")

    /**
     * 翻转 booster_config params 中的一个布尔键（path 支持一层嵌套，
     * 如 ["mivk_settings", "enable"]），随后走双库镜像写入。
     */
    fun toggleBoolean(path: List<String>, newValue: Boolean): WriteResult {
        val raw = readRaw(CONFIG_BOOSTER) ?: return WriteResult(false, "无法读取 booster_config")
        android.util.Log.d(
            TAG,
            "toggleBoolean path=$path new=$newValue rawLen=${raw.length} " +
                "hasKey=${raw.contains("\"${path.first()}\"")} head=${raw.take(120)}",
        )
        return runCatching {
            val root = json.parseToJsonElement(raw)
            val updated = updateBoolean(root, path, newValue)
            writeConfig(CONFIG_BOOSTER, updated.toString())
        }.getOrElse {
            android.util.Log.e(TAG, "toggleBoolean 失败 raw 尾部=${raw.takeLast(200)}", it)
            WriteResult(false, "开关切换失败: ${it.message}")
        }
    }

    private fun updateBoolean(
        root: kotlinx.serialization.json.JsonElement,
        path: List<String>,
        newValue: Boolean,
    ): kotlinx.serialization.json.JsonElement {
        require(path.isNotEmpty()) { "空的键路径" }
        val key = path.first()
        val obj = root as? kotlinx.serialization.json.JsonObject ?: error("文档结构异常")
        if (path.size == 1) {
            val current = obj[key]
            require(current is kotlinx.serialization.json.JsonPrimitive) {
                "$key 不是标量（实际类型: ${current ?: "缺失"}）"
            }
            return kotlinx.serialization.json.JsonObject(obj + (key to kotlinx.serialization.json.JsonPrimitive(newValue)))
        }
        val child = updateBoolean(obj.getValue(key), path.drop(1), newValue)
        return kotlinx.serialization.json.JsonObject(obj + (key to child))
    }

    @Serializable
    data class BackupEntry(val name: String, val smartp: Boolean = false, val teg: Boolean = false, val valid: Boolean = true)

    @Serializable
    data class BackupListResult(val ok: Boolean = false, val backups: List<BackupEntry> = emptyList())

    companion object {
        private const val TAG = "JoyoseManager"
        private const val PKG = "com.xiaomi.joyose"
        const val CONFIG_BOOSTER = "booster_config"
        const val CONFIG_COMMON = "common_config"
    }
}
