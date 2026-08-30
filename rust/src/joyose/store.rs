//! Joyose privileged store operations: SmartP.db / teg_config.db read-write,
//! teg SDK freeze (SharedPreferences), and backup/revert.
//!
//! IO layer of the joyose module (see docs/superpowers/specs/
//! 2026-08-30-cosa-cli-module-design.md). Pure document logic lives in
//! resolve.rs / appview.rs. All commands print a single JSON document to
//! stdout; human-readable errors go to stderr via the CLI error protocol.
//!
//! Backup storage lives in the **App's private directory** (passed in as an
//! argument by the Kotlin side), NOT under /data/adb — the App is an
//! independent design; the reference KernelSU module (and its
//! /data/adb/joyose-edit layout) is reference material only and is never
//! shared with or touched by this tool.

use crate::joyose::appview;
use rusqlite::{params, Connection, OpenFlags, OptionalExtension};
use serde_json::{json, Value};
use std::error::Error;
use std::fs;
use std::os::unix::fs::{chown, MetadataExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const PKG: &str = "com.xiaomi.joyose";
const SMARTP: &str = "/data/user/0/com.xiaomi.joyose/databases/SmartP.db";
const TEG: &str = "/data/user/0/com.xiaomi.joyose/databases/teg_config.db";
const TEG_SP: &str = "/data/user/0/com.xiaomi.joyose/shared_prefs/teg_config_pref.xml";
/// Sentinel pinning `pref_local_max_version`: teg SDK then always believes
/// it is up to date and never replays cloud rules (cloud overwrite freeze).
const TEG_MAX: &str = "9223372036854775807";
const SP_KEY: &str = "<long name=\"pref_local_max_version\" value=\"";

type CliResult<T> = Result<T, Box<dyn Error>>;

// ── helpers ─────────────────────────────────────────────────────────────────

fn open_ro(path: &str) -> CliResult<Connection> {
    let conn = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)?;
    conn.busy_timeout(Duration::from_secs(3))?;
    Ok(conn)
}

fn open_rw(path: &str) -> CliResult<Connection> {
    let conn = Connection::open(path)?;
    conn.busy_timeout(Duration::from_secs(3))?;
    Ok(conn)
}

fn checkpoint_and_close(conn: Connection, db: &str) {
    let _ = conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE)");
    drop(conn);
    heal_sidecars(Path::new(db));
    restorecon(db);
}

/// Root-created SQLite sidecars must be handed back to the target app's uid,
/// otherwise Joyose fails with SQLITE_CANTOPEN (same lesson as the cosa flow).
fn heal_sidecars(db: &Path) {
    if let Ok(meta) = fs::metadata(db) {
        let name = db.file_name().unwrap_or_default().to_string_lossy().into_owned();
        for suffix in ["-journal", "-wal", "-shm"] {
            let sidecar = db.with_file_name(format!("{name}{suffix}"));
            if sidecar.exists() {
                let _ = chown(&sidecar, Some(meta.uid()), Some(meta.gid()));
            }
        }
    }
}

fn restorecon(path: &str) {
    let _ = Command::new("restorecon").arg(path).output();
}

fn stop_joyose() {
    let _ = Command::new("am").args(["force-stop", PKG]).output();
}

fn epoch() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Backup / history names must never escape their directory.
fn safe_name(name: &str) -> CliResult<()> {
    let ok = !name.is_empty()
        && !name.contains("..")
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.'));
    if ok {
        Ok(())
    } else {
        Err(format!("非法名称: {name}").into())
    }
}

/// Extract the current `pref_local_max_version` value from SP XML text.
fn sp_current(xml: &str) -> Option<&str> {
    let start = xml.find(SP_KEY)? + SP_KEY.len();
    let rest = &xml[start..];
    let end = rest.find('"')?;
    Some(&rest[..end])
}

/// Rewrite `pref_local_max_version` in SP XML text; insert the element when
/// the key does not exist yet. Errors on malformed input (no marker + no
/// `</map>`), never silently returns unchanged text.
fn rewrite_sp(xml: &str, new_value: &str) -> CliResult<String> {
    if let Some(start) = xml.find(SP_KEY) {
        let vstart = start + SP_KEY.len();
        let end = vstart
            + xml[vstart..]
                .find('"')
                .ok_or("SP 格式异常：value 未闭合")?;
        let mut out = String::with_capacity(xml.len() + 32);
        out.push_str(&xml[..vstart]);
        out.push_str(new_value);
        out.push_str(&xml[end..]);
        Ok(out)
    } else if let Some(pos) = xml.find("</map>") {
        Ok(format!(
            "{}    <long name=\"pref_local_max_version\" value=\"{}\" />\n{}",
            &xml[..pos], new_value, &xml[pos..]
        ))
    } else {
        Err("SP 格式异常：既无目标键也无 </map>".into())
    }
}

fn stat_one(path: &str) -> Value {
    match fs::metadata(path) {
        Ok(m) => json!({
            "exists": true,
            "path": path,
            "mtime": m.modified().ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_secs()),
            "size": m.len(),
            "uid": m.uid(),
            "gid": m.gid(),
        }),
        Err(_) => json!({ "exists": false, "path": path }),
    }
}

/// Read one config's params document. SmartP first, teg rules as fallback
/// (e.g. common_config only exists in teg rules on real devices).
/// teg `rule_content` may be a pseudo-row wrapper `{config_name,…,params}`;
/// it is unwrapped so callers always see the bare params document.
pub fn read_params_any(config: &str) -> CliResult<(String, Value)> {
    if Path::new(SMARTP).exists() {
        let conn = open_ro(SMARTP)?;
        if let Ok(text) = conn.query_row(
            "SELECT params FROM cloud_config WHERE config_name = ?1",
            [config],
            |r| r.get::<_, String>(0),
        ) {
            let value: Value = serde_json::from_str(&text)
                .map_err(|e| format!("SmartP {config} params 解析失败: {e}"))?;
            return Ok(("smartp".into(), value));
        }
    }
    if Path::new(TEG).exists() {
        let conn = open_ro(TEG)?;
        if let Ok(text) = conn.query_row(
            "SELECT rule_content FROM rules WHERE rule_module = ?1 ORDER BY _id DESC LIMIT 1",
            [config],
            |r| r.get::<_, String>(0),
        ) {
            let value: Value = serde_json::from_str(&text)
                .map_err(|e| format!("teg {config} rule_content 解析失败: {e}"))?;
            return Ok(("teg".into(), unwrap_rule(value)));
        }
    }
    Err(format!("未找到配置: {config}").into())
}

/// teg rule_content wrapper → bare params (pass-through when not a wrapper).
fn unwrap_rule(value: Value) -> Value {
    if value.get("config_name").is_some() && value.get("params").is_some() {
        value.get("params").cloned().unwrap_or(value)
    } else {
        value
    }
}

/// Mirror the params document into a teg rule row, preserving each side's
/// storage shape: when the existing rule_content is the pseudo-row wrapper,
/// only its `params` member is replaced; otherwise the document is stored
/// verbatim. `rule_version` is intentionally left untouched (no bump policy).
fn mirror_rule_content(existing: &str, doc: &Value) -> CliResult<String> {
    let value: Value = serde_json::from_str(existing)
        .map_err(|e| format!("teg rule_content 解析失败: {e}"))?;
    if value.get("config_name").is_some() && doc.get("config_name").is_none() {
        let mut obj = value;
        obj["params"] = doc.clone();
        Ok(obj.to_string())
    } else {
        Ok(doc.to_string())
    }
}

// ── commands ────────────────────────────────────────────────────────────────

/// `joyose-stat [backup_root]` — DB/SP fingerprints + freeze state.
pub fn cmd_stat(backup_root: Option<&String>) -> CliResult<bool> {
    let sp_text = fs::read_to_string(TEG_SP).ok();
    let (current, frozen) = match sp_text.as_deref().and_then(sp_current) {
        Some(v) => (v.to_owned(), v == TEG_MAX),
        None => (String::new(), false),
    };
    let backups = backup_root
        .and_then(|root| fs::read_dir(Path::new(root).join("backup")).ok())
        .map(|rd| rd.filter_map(Result::ok).count())
        .unwrap_or(0);
    println!(
        "{}",
        json!({
            "ok": true,
            "pkg": PKG,
            "smartp": stat_one(SMARTP),
            "teg": stat_one(TEG),
            "sp": {
                "exists": sp_text.is_some(),
                "path": TEG_SP,
                "pref_local_max_version": current,
                "frozen": frozen,
            },
            "backups": backups,
        })
    );
    Ok(true)
}

/// `joyose-list` — cloud_config rows ∪ rules modules.
pub fn cmd_list() -> CliResult<bool> {
    let mut cloud_config = Vec::new();
    if Path::new(SMARTP).exists() {
        let conn = open_ro(SMARTP)?;
        let mut stmt =
            conn.prepare("SELECT config_name, COALESCE(version, 0), COALESCE(enable, 0) FROM cloud_config ORDER BY config_name")?;
        let rows = stmt.query_map([], |r| {
            Ok(json!({
                "config_name": r.get::<_, String>(0)?,
                "version": r.get::<_, i64>(1)?,
                "enable": r.get::<_, i64>(2)?,
                "source": "smartp",
            }))
        })?;
        for row in rows {
            cloud_config.push(row?);
        }
    }
    let mut rules = Vec::new();
    if Path::new(TEG).exists() {
        let conn = open_ro(TEG)?;
        let mut stmt = conn.prepare(
            "SELECT rule_module, MAX(rule_version), COUNT(*) FROM rules GROUP BY rule_module ORDER BY rule_module",
        )?;
        let rows = stmt.query_map([], |r| {
            Ok(json!({
                "rule_module": r.get::<_, String>(0)?,
                "rule_version": r.get::<_, i64>(1)?,
                "rows": r.get::<_, i64>(2)?,
                "source": "teg",
            }))
        })?;
        for row in rows {
            rules.push(row?);
        }
    }
    println!("{}", json!({ "ok": true, "cloud_config": cloud_config, "rules": rules }));
    Ok(true)
}

/// `joyose-read <config>` — print the params document (pretty JSON).
pub fn cmd_read(config: Option<&String>) -> CliResult<bool> {
    let Some(config) = config else {
        return Err("缺少配置名".into());
    };
    let (_, value) = read_params_any(config)?;
    println!("{}", serde_json::to_string_pretty(&value)?);
    Ok(true)
}

/// `joyose-write <config> <json文件>` — dual-DB mirror write of the params
/// document. force-stop of Joyose is performed here (before touching the
/// DBs); `version` is preserved (no bump policy for local edits).
pub fn cmd_write(config: Option<&String>, path: Option<&String>) -> CliResult<bool> {
    let Some(config) = config else {
        return Err("缺少配置名".into());
    };
    let Some(path) = path else {
        return Err("缺少 JSON 文件路径".into());
    };
    let text = fs::read_to_string(path).map_err(|e| format!("读取 {path} 失败: {e}"))?;
    let doc: Value =
        serde_json::from_str(&text).map_err(|e| format!("JSON 解析失败: {e}"))?;
    if !doc.is_object() {
        return Err("JSON 顶层必须是对象".into());
    }
    let doc = unwrap_rule(doc);

    stop_joyose();

    // ── SmartP.cloud_config upsert ──
    let mut smartp_result = "skipped(missing db)".to_owned();
    if Path::new(SMARTP).exists() {
        let conn = open_rw(SMARTP)?;
        let existing: Option<i64> = conn
            .query_row(
                "SELECT version FROM cloud_config WHERE config_name = ?1",
                [config],
                |r| r.get(0),
            )
            .optional()?;
        // No bump policy: keep the stored version unless the document itself
        // carries an explicit header.version (kept identical on round-trips).
        let version = doc
            .get("header")
            .and_then(|h| h.get("version"))
            .and_then(Value::as_str)
            .and_then(|s| s.parse::<i64>().ok())
            .or(existing)
            .or_else(|| {
                Path::new(TEG).exists().then_some(()).and_then(|_| {
                    open_ro(TEG).ok().and_then(|c| {
                        c.query_row(
                            "SELECT MAX(rule_version) FROM rules WHERE rule_module = ?1",
                            [config],
                            |r| r.get::<_, Option<i64>>(0),
                        )
                        .ok()
                        .flatten()
                    })
                })
            })
            .unwrap_or(0);
        let doc_str = doc.to_string();
        match existing {
            Some(_) => {
                conn.execute(
                    "UPDATE cloud_config SET params = ?1, version = ?2 WHERE config_name = ?3",
                    params![doc_str, version, config],
                )?;
                smartp_result = "updated".into();
            }
            None => {
                conn.execute(
                    "INSERT INTO cloud_config (config_name, group_name, enable, version, with_model, model, params) VALUES (?1, ?1, 1, ?2, 0, '{}', ?3)",
                    params![config, version, doc_str],
                )?;
                smartp_result = "inserted".into();
            }
        }
        checkpoint_and_close(conn, SMARTP);
    }

    // ── teg.rules mirror (shape-preserving, only existing rows) ──
    let mut teg_result = "skipped(missing db)".to_owned();
    if Path::new(TEG).exists() {
        let conn = open_rw(TEG)?;
        let mut stmt = conn.prepare("SELECT _id, rule_content FROM rules WHERE rule_module = ?1")?;
        let rows: Vec<(i64, String)> = stmt
            .query_map([config], |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        drop(stmt);
        if rows.is_empty() {
            teg_result = "skipped(no rule rows)".into();
        } else {
            for (id, content) in &rows {
                let mirrored = mirror_rule_content(content, &doc)?;
                conn.execute("UPDATE rules SET rule_content = ?1 WHERE _id = ?2", params![mirrored, id])?;
            }
            teg_result = format!("updated:{}", rows.len());
        }
        checkpoint_and_close(conn, TEG);
    }

    // ── read-back verification (both sides must equal the document) ──
    let verify = |db: &str, text: &str| -> CliResult<()> {
        let value: Value = serde_json::from_str(text).map_err(|e| format!("{db} 回读解析失败: {e}"))?;
        if unwrap_rule(value) != doc {
            return Err(format!("{db} 回读校验不一致").into());
        }
        Ok(())
    };
    if smartp_result != "skipped(missing db)" {
        let conn = open_ro(SMARTP)?;
        let text: String = conn.query_row(
            "SELECT params FROM cloud_config WHERE config_name = ?1",
            [config],
            |r| r.get(0),
        )?;
        verify("SmartP", &text)?;
    }
    if let Some(n) = teg_result.strip_prefix("updated:") {
        let conn = open_ro(TEG)?;
        let mut stmt = conn.prepare("SELECT rule_content FROM rules WHERE rule_module = ?1")?;
        let rows: Vec<String> = stmt
            .query_map([config], |r| r.get(0))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        let _ = n;
        for text in rows {
            verify("teg", &text)?;
        }
    }

    println!(
        "{}",
        json!({ "ok": true, "config": config, "smartp": smartp_result, "teg": teg_result })
    );
    Ok(true)
}

/// `joyose-freeze` / `joyose-unfreeze` — pin (or release) the teg SDK cloud
/// sync via `pref_local_max_version`. Joyose is force-stopped first because
/// SharedPreferences has a per-process cache.
fn teg_rewrite(new_value: &'static str) -> CliResult<bool> {
    if !Path::new(TEG_SP).exists() {
        return Err(format!(
            "teg SP 尚未初始化: {TEG_SP}。请先让 Joyose 完成一次云控拉取"
        )
        .into());
    }
    stop_joyose();
    let xml = fs::read_to_string(TEG_SP).map_err(|e| format!("读取 SP 失败: {e}"))?;
    let meta = fs::metadata(TEG_SP)?;
    let new_xml = rewrite_sp(&xml, new_value)?;
    if new_xml == xml {
        return Err("SP 改写失败：内容未发生变化".into());
    }
    let tmp = format!("{TEG_SP}.cosa-tmp");
    fs::write(&tmp, new_xml)?;
    let _ = chown(Path::new(&tmp), Some(meta.uid()), Some(meta.gid()));
    let _ = fs::set_permissions(Path::new(&tmp), fs::Permissions::from_mode(0o660));
    fs::rename(&tmp, TEG_SP)?;
    restorecon(TEG_SP);
    println!("{}", json!({ "ok": true, "path": TEG_SP, "pref_local_max_version": new_value }));
    Ok(true)
}

pub fn cmd_freeze() -> CliResult<bool> {
    teg_rewrite(TEG_MAX)
}

pub fn cmd_unfreeze() -> CliResult<bool> {
    teg_rewrite("0")
}

/// `joyose-backup <data_root> [label]` — snapshot both DBs into
/// `<data_root>/backup/<epoch>[-label]/` (App-private dir passed by Kotlin).
pub fn cmd_backup(data_root: Option<&String>, label: Option<&String>) -> CliResult<bool> {
    let Some(root) = data_root else {
        return Err("缺少备份根目录参数（App 私有目录）".into());
    };
    if let Some(label) = label {
        safe_name(label)?;
    }
    let dir: PathBuf = match label {
        Some(label) => Path::new(root).join(format!("backup/{}-{}", epoch(), label)),
        None => Path::new(root).join(format!("backup/{}", epoch())),
    };
    fs::create_dir_all(&dir)?;
    let root_meta = fs::metadata(root)?;
    for (db, name) in [(SMARTP, "SmartP.db"), (TEG, "teg_config.db")] {
        if Path::new(db).exists() {
            let dest = dir.join(name);
            fs::copy(db, &dest)?;
            // Hand the backup back to the app uid so Kotlin can list/read it
            // without a root shell (same ownership-return pattern as the
            // cosa SQLite sidecars).
            let _ = chown(&dest, Some(root_meta.uid()), Some(root_meta.gid()));
        }
    }
    let _ = fs::set_permissions(&dir, fs::Permissions::from_mode(0o700));
    let _ = chown(&dir, Some(root_meta.uid()), Some(root_meta.gid()));
    let name = dir.file_name().unwrap_or_default().to_string_lossy().into_owned();
    println!("{}", json!({ "ok": true, "dir": dir.display().to_string(), "name": name }));
    Ok(true)
}

/// `joyose-backup-list <data_root>` — newest first.
pub fn cmd_backup_list(data_root: Option<&String>) -> CliResult<bool> {
    let Some(root) = data_root else {
        return Err("缺少备份根目录参数".into());
    };
    let mut backups = Vec::new();
    if let Ok(rd) = fs::read_dir(Path::new(root).join("backup")) {
        let mut names: Vec<String> = rd
            .filter_map(Result::ok)
            .filter(|e| e.path().is_dir())
            .map(|e| e.file_name().to_string_lossy().into_owned())
            .collect();
        names.sort_unstable_by(|a, b| b.cmp(a));
        for name in names {
            let dir = Path::new(root).join("backup").join(&name);
            backups.push(json!({
                "name": name,
                "smartp": dir.join("SmartP.db").exists(),
                "teg": dir.join("teg_config.db").exists(),
            }));
        }
    }
    println!("{}", json!({ "ok": true, "backups": backups }));
    Ok(true)
}

/// `joyose-revert <data_root> <name>` — restore both DBs from a backup.
pub fn cmd_revert(data_root: Option<&String>, name: Option<&String>) -> CliResult<bool> {
    let Some(root) = data_root else {
        return Err("缺少备份根目录参数".into());
    };
    let Some(name) = name else {
        return Err("缺少备份名称".into());
    };
    safe_name(name)?;
    let dir = Path::new(root).join("backup").join(name);
    if !dir.is_dir() {
        return Err(format!("备份不存在: {name}").into());
    }
    stop_joyose();
    let mut restored = Vec::new();
    for (db, fname) in [(SMARTP, "SmartP.db"), (TEG, "teg_config.db")] {
        let src = dir.join(fname);
        if src.exists() && Path::new(db).exists() {
            // fs::copy truncates the existing destination, preserving its
            // owner/SELinux context — exactly what we want for in-place
            // restore under the Joyose uid.
            fs::copy(&src, db)?;
            heal_sidecars(Path::new(db));
            restorecon(db);
            restored.push(fname);
        }
    }
    println!("{}", json!({ "ok": true, "restored": restored }));
    Ok(true)
}

/// `joyose-apps` — single-pass per-app feature index over booster_config
/// (plus common whitelist counts are intentionally excluded).
pub fn cmd_apps() -> CliResult<bool> {
    let (_, params) = read_params_any("booster_config")?;
    let gb = params
        .get("game_booster")
        .ok_or("缺少 game_booster：不是 booster_config 的 params 文档")?;
    let apps = appview::package_index(gb);
    println!(
        "{}",
        json!({
            "ok": true,
            "apps": apps.iter().map(|e| json!({
                "package": e.package,
                "group": e.group,
                "features": e.features,
            })).collect::<Vec<_>>(),
        })
    );
    Ok(true)
}

// ── tests ───────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_SP: &str = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <long name=\"pref_last_update_time\" value=\"1788033343899\" />\n    <long name=\"pref_local_max_version\" value=\"712444\" />\n</map>\n";

    #[test]
    fn sp_current_extracts_value() {
        assert_eq!(sp_current(SAMPLE_SP), Some("712444"));
        assert_eq!(sp_current("<map></map>"), None);
    }

    #[test]
    fn rewrite_sp_replaces_in_place() {
        let out = rewrite_sp(SAMPLE_SP, TEG_MAX).unwrap();
        assert_eq!(sp_current(&out), Some(TEG_MAX));
        // untouched neighbours survive
        assert!(out.contains("pref_last_update_time"));
        assert_eq!(out.matches("pref_local_max_version").count(), 1);
    }

    #[test]
    fn rewrite_sp_inserts_when_missing() {
        let xml = "<map>\n    <int name=\"x\" value=\"1\" />\n</map>\n";
        let out = rewrite_sp(xml, "42").unwrap();
        assert_eq!(sp_current(&out), Some("42"));
        assert!(out.contains("<int name=\"x\" value=\"1\" />"));
    }

    #[test]
    fn rewrite_sp_rejects_malformed() {
        assert!(rewrite_sp("no map at all", "1").is_err());
    }

    #[test]
    fn safe_name_blocks_traversal_and_metachars() {
        assert!(safe_name("1756570000-p1test").is_ok());
        assert!(safe_name("../etc").is_err());
        assert!(safe_name("a b").is_err());
        assert!(safe_name("").is_err());
        assert!(safe_name("a;b").is_err());
    }

    #[test]
    fn mirror_keeps_wrapper_shape() {
        let wrapper = r#"{"config_name":"common_config","version":467472,"params":{"game_list":[1]}}"#;
        let doc: Value = serde_json::from_str(r#"{"game_list":[1,2]}"#).unwrap();
        let out: Value = serde_json::from_str(&mirror_rule_content(wrapper, &doc).unwrap()).unwrap();
        assert_eq!(out.get("config_name").and_then(Value::as_str), Some("common_config"));
        assert_eq!(out.get("version").and_then(Value::as_i64), Some(467472));
        assert_eq!(out.get("params").unwrap(), &doc);
        // non-wrapper content stored verbatim
        let plain = r#"{"header":{"version":"1"}}"#;
        let out = mirror_rule_content(plain, &doc).unwrap();
        assert_eq!(serde_json::from_str::<Value>(&out).unwrap(), doc);
    }
}
