package com.remoteconfig.override.data

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.serialization.json.*

/**
 * 数据库管理器 — 通过 Root 权限与 com.oplus.cosa 的 SQLite 数据库交互。
 * 读写都以原始 JSON 字符串形式进行。
 */
class DatabaseManager(private val context: Context) {

    companion object {
        private const val DB_PATH_1 = "/data/data/com.oplus.cosa/databases/db_game_database"
        private const val DB_PATH_2 = "/data/user_de/0/com.oplus.cosa/databases/db_game_database"
        private const val SQLITE3_ASSET = "tool/sqlite3"
        private const val YT_ASSET = "tool/yt"

        private val colNameMap = mapOf(
            "package_name" to "Package_Name", "type_id" to "Type_Id",
            "feature_flag" to "Feature_Flag", "cpu_config" to "Cpu_Config",
            "gpu_config" to "Gpu_Config", "io_config" to "Io_Config",
            "dynamic_resolution" to "Dynamic_Resolution", "launch_boost" to "Launch_Boost",
            "usage_power_ratio" to "Usage_Power_Ratio", "memory_clear" to "Memory_Clear",
            "frameboost" to "FrameBoost", "frameBoost" to "FrameBoost",
            "refresh_rate" to "Refresh_Rate", "gpa_config" to "Gpa_Config",
            "touch_config" to "Touch_Config", "default_scene" to "Default_Scene",
            "game_scenes" to "Game_Scenes"
        )
    }

    private var sqlite3Path: String? = null

    fun checkRoot(): Boolean = Shell.cmd("su -c 'echo ok' 2>/dev/null || echo fail").exec().out.any { it.contains("ok") }

    fun findDatabase(): String? {
        val result = Shell.cmd(
            "if [ -f '$DB_PATH_1' ]; then echo '$DB_PATH_1'; " +
            "elif [ -f '$DB_PATH_2' ]; then echo '$DB_PATH_2'; " +
            "else exit 1; fi"
        ).exec()
        return if (result.isSuccess) result.out.firstOrNull() else null
    }

    fun checkDatabase(): Boolean = findDatabase() != null

    private fun ensureSqlite3(): String {
        sqlite3Path?.let { return it }
        val checkSys = Shell.cmd("which sqlite3").exec()
        if (checkSys.isSuccess && checkSys.out.firstOrNull()?.isNotEmpty() == true) {
            sqlite3Path = checkSys.out.first().trim(); return sqlite3Path!!
        }
        val localPath = File(context.filesDir, "sqlite3").absolutePath
        if (!File(localPath).exists()) {
            context.assets.open(SQLITE3_ASSET).use { i -> File(localPath).outputStream().use { o -> i.copyTo(o) } }
            Shell.cmd("chmod 755 '$localPath'").exec()
        }
        sqlite3Path = localPath; return localPath
    }

    private fun ensureYt(): String {
        val checkSys = Shell.cmd("which yt").exec()
        if (checkSys.isSuccess && checkSys.out.firstOrNull()?.isNotEmpty() == true) {
            return checkSys.out.first().trim()
        }
        val localPath = File(context.filesDir, "yt").absolutePath
        if (!File(localPath).exists()) {
            context.assets.open(YT_ASSET).use { i -> File(localPath).outputStream().use { o -> i.copyTo(o) } }
            Shell.cmd("chmod 755 '$localPath'").exec()
        }
        return localPath
    }

    fun listConfiguredPackages(): List<String> {
        val db = findDatabase() ?: return emptyList()
        val sqlite = ensureSqlite3()
        val result = Shell.cmd(
            "'$sqlite' '$db' \"SELECT DISTINCT Package_Name FROM PackageConfigBean WHERE Package_Name NOT IN ('oplus.cosa.common.model.config', 'oplus.cosa.default.model.config') AND Package_Name LIKE 'com.%' ORDER BY Package_Name;\""
        ).exec()
        return if (result.isSuccess) result.out.filter { it.isNotBlank() } else emptyList()
    }

    fun countConfiguredPackages(): Int {
        val db = findDatabase() ?: return 0
        val sqlite = ensureSqlite3()
        val result = Shell.cmd(
            "'$sqlite' '$db' \"SELECT COUNT(DISTINCT Package_Name) FROM PackageConfigBean WHERE Package_Name NOT IN ('oplus.cosa.common.model.config', 'oplus.cosa.default.model.config') AND Package_Name LIKE 'com.%';\"").exec()
        return if (result.isSuccess) result.out.firstOrNull()?.toIntOrNull() ?: 0 else 0
    }

    // ── 读取整理后的 JSON ───────────────────────────────────

    /**
     * 读取指定包名的配置，返回格式化 JSON 字符串。
     * 先用 yt 1 整理模式输出到 /storage/emulated/0/output/，再读取该文件。
     */
    fun loadRawConfig(packageName: String): String? {
        // 方式一：用 yt 整理模式（不依赖原模块，yt 从 assets 解压）
        try {
            val yt = ensureYt()
            val outDir = "/storage/emulated/0/output"
            Shell.cmd("mkdir -p '$outDir' 2>/dev/null").exec()
            Shell.cmd("'$yt' 1 '$packageName'").exec()

            val outputPath = "$outDir/${packageName}.json"
            val catResult = Shell.cmd("cat '$outputPath' 2>/dev/null").exec()
            if (catResult.isSuccess && catResult.out.isNotEmpty()) {
                val output = catResult.out.joinToString("\n").trim()
                if (output.isNotEmpty() && (output.startsWith("{") || output.startsWith("["))) {
                    return output
                }
            }
        } catch (_: Exception) { /* 回退 */ }

        // 方式二：sqlite3 -json + jq 展开嵌套字段 + pretty-print
        return loadRawViaJq(packageName)
    }

    /**
     * 用 sqlite3 -json 读取 + jq 将字符串字段展开为嵌套对象。
     * 效果等同 yt 1 的整理模式。
     */
    private fun loadRawViaJq(packageName: String): String? {
        val db = findDatabase() ?: return null
        val sqlite = ensureSqlite3()
        val result = Shell.cmd(
            "'$sqlite' -json '$db' \"SELECT * FROM PackageConfigBean WHERE Package_Name='$packageName';\""
        ).exec()
        if (!result.isSuccess || result.out.isEmpty()) return null
        val raw = result.out.joinToString("").trim()
        if (raw.length < 2) return null
        val jsonStr = raw.substring(1, raw.length - 1)

        // 在 Kotlin 侧将所有 JSON 字符串字段展开为嵌套对象
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(jsonStr).jsonObject.toMutableMap()
            for ((key, el) in obj) {
                if (el is JsonPrimitive && el.isString && el.content.startsWith("{")) {
                    try {
                        obj[key] = json.parseToJsonElement(el.content)
                    } catch (_: Exception) { /* 保持原样 */ }
                }
            }
            val expanded = JsonObject(obj)
            val pretty = Json { prettyPrint = true }
            pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), expanded)
        } catch (_: Exception) {
            jsonStr
        }
    }

    // ── 写入配置（原始 JSON） ───────────────────────────────

    fun writeRawConfig(packageName: String, rawJson: String): WriteResult {
        val db = findDatabase() ?: return WriteResult(false, "未找到数据库文件")
        val sqlite = ensureSqlite3()

        val json = Json { ignoreUnknownKeys = true }
        val obj = try { json.parseToJsonElement(rawJson).jsonObject } catch (e: Exception) {
            return WriteResult(false, "JSON 解析失败: ${e.message}")
        }

        try {
            // 1. 生成列名和值（与 action.sh 的 jq 逻辑一致）
            //    字符串 → "value"（内部 " → ""）
            //    对象/数组 → tojson → 再转义 " → "" → 包 ""
            //    数字 → tostring → 包 ""
            //    null → "NULL"
            val cols = mutableListOf<String>()
            val vals = mutableListOf<String>()

            for ((key, value) in obj) {
                val col = sqliteColumnName(key)
                cols += "\"$col\""
                val v = when {
                    value is JsonNull -> "NULL"
                    value is JsonPrimitive && value.isString ->
                        "\"${value.content.replace("\"", "\"\"")}\""
                    value is JsonPrimitive -> {
                        // 数字或布尔
                        "\"${value.content}\""
                    }
                    else -> {
                        // 嵌套对象/数组 → tojson → 转义
                        val raw = value.toString()
                        "\"${raw.replace("\"", "\"\"")}\""
                    }
                }
                vals += v
            }

            if (cols.isEmpty()) return WriteResult(false, "JSON 内容为空")

            val columns = cols.joinToString(", ")
            val values = vals.joinToString(", ")

            // 2. 清理 PackageInfoBean
            val infoCount = Shell.cmd(
                "'$sqlite' '$db' \"SELECT COUNT(*) FROM PackageInfoBean WHERE package_name = '$packageName';\""
            ).exec().let { it.out.firstOrNull()?.toIntOrNull() ?: 0 }
            if (infoCount > 0) {
                Shell.cmd("'$sqlite' '$db' \"DELETE FROM PackageInfoBean WHERE package_name = '$packageName';\"").exec()
            }

            // 3. 检查现有记录
            val existing = Shell.cmd(
                "'$sqlite' '$db' \"SELECT COUNT(*) FROM PackageConfigBean WHERE Package_Name = '$packageName';\""
            ).exec().let { it.out.firstOrNull()?.toIntOrNull() ?: 0 }

            // 4. 写 SQL 到临时文件，用重定向执行
            val tmpSql = File(context.filesDir, "_s_${packageName}.sql")
            val sql = if (existing > 0) {
                "UPDATE PackageConfigBean SET ($columns) = ($values) WHERE Package_Name = \"$packageName\";"
            } else {
                "INSERT INTO PackageConfigBean (Package_Name, $columns) VALUES (\"$packageName\", $values);"
            }
            tmpSql.writeText(sql)

            val result = Shell.cmd("'$sqlite' '$db' < '${tmpSql.absolutePath}'").exec()
            tmpSql.delete()

            return if (result.isSuccess) WriteResult(true, "$packageName 配置写入成功")
            else WriteResult(false, "写入失败: ${result.err.joinToString("; ")}")

        } catch (e: Exception) {
            return WriteResult(false, "写入失败: ${e.message}")
        }
    }

    fun deleteConfig(packageName: String): WriteResult {
        val db = findDatabase() ?: return WriteResult(false, "未找到数据库文件")
        val sqlite = ensureSqlite3()
        val result = Shell.cmd("'$sqlite' '$db' \"DELETE FROM PackageConfigBean WHERE Package_Name='$packageName';\"").exec()
        return if (result.isSuccess) WriteResult(true, "$packageName 已删除")
        else WriteResult(false, "删除失败: ${result.err.joinToString("; ")}")
    }

    /** 获取 com.oplus.cosa 版本号 */
    fun getCosaVersion(): String {
        val result = Shell.cmd("dumpsys package com.oplus.cosa | grep versionName | head -1").exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            val line = result.out.first().trim()
            // 格式: versionName=16.0.174
            val v = line.substringAfter("=").trim()
            if (v.isNotEmpty()) return v
        }
        // 备用方案
        val result2 = Shell.cmd("pm list packages --show-versioncode | grep com.oplus.cosa").exec()
        if (result2.isSuccess && result2.out.isNotEmpty()) {
            val parts = result2.out.first().trim().split(" ")
            if (parts.size >= 2) return parts.last()
        }
        return "未知"
    }

    /** 重启应用增强服务 */
    fun restartGameService(): WriteResult {
        val r1 = Shell.cmd("setprop persist.sys.oplus.gameswitch.enable 0").exec()
        sleep(1000)
        val r2 = Shell.cmd("setprop persist.sys.oplus.gameswitch.enable 1").exec()
        return if (r2.isSuccess) WriteResult(true, "应用增强服务已重启")
        else WriteResult(false, "重启失败: ${r2.err.joinToString("; ")}")
    }

    /** 清除应用增强服务数据 */
    fun clearGameData(): WriteResult {
        val result = Shell.cmd("pm clear com.oplus.cosa").exec()
        return if (result.isSuccess) WriteResult(true, "应用增强服务数据已清除")
        else WriteResult(false, "清除失败: ${result.err.joinToString("; ")}")
    }

    /** 导出配置到 /storage/emulated/0/cosa_json/<pkg>.json */
    fun exportConfig(packageName: String, json: String): WriteResult {
        val outDir = "/storage/emulated/0/cosa_json"
        val outFile = "$outDir/${packageName}.json"
        val tmpFile = File(context.filesDir, "_export_${packageName}.json")
        tmpFile.writeText(json)
        val result = Shell.cmd(
            "mkdir -p '$outDir' && cp '${tmpFile.absolutePath}' '$outFile' && chmod 644 '$outFile' && rm '${tmpFile.absolutePath}'"
        ).exec()
        return if (result.isSuccess) WriteResult(true, "已导出到 $outDir/${packageName}.json")
        else WriteResult(false, "导出失败: ${result.err.joinToString("; ")}")
    }

    /** 从 /storage/emulated/0/cosa_json/<pkg>.json 读取配置 */
    fun importConfig(packageName: String): String? {
        val inFile = "/storage/emulated/0/cosa_json/${packageName}.json"
        val result = Shell.cmd("cat '$inFile' 2>/dev/null").exec()
        if (!result.isSuccess || result.out.isEmpty()) return null
        val text = result.out.joinToString("\n").trim()
        return text.ifEmpty { null }
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    private fun sqliteColumnName(field: String): String = colNameMap[field] ?: field.replaceFirstChar { it.uppercase() }

    data class WriteResult(val success: Boolean, val message: String)
}
