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
use crate::joyose::digest::sha256_hex_file;
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

#[cfg(test)]
pub(crate) static TEST_DB_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());


#[derive(Debug, Clone, PartialEq, Eq)]
enum WriteSide {
    Updated(usize),
    Inserted,
    Skipped(&'static str),
}

impl WriteSide {
    fn label(&self) -> String {
        match self {
            WriteSide::Updated(n) if *n > 1 => format!("updated:{n}"),
            WriteSide::Updated(_) => "updated".into(),
            WriteSide::Inserted => "inserted".into(),
            WriteSide::Skipped(why) => format!("skipped({why})"),
        }
    }
}

// ── helpers ─────────────────────────────────────────────────────────────────

fn smartp_db() -> String {
    std::env::var("JOYOSE_SMARTP_PATH").unwrap_or_else(|_| SMARTP.to_string())
}

fn teg_db() -> String {
    std::env::var("JOYOSE_TEG_PATH").unwrap_or_else(|_| TEG.to_string())
}

fn open_ro(path: &str) -> CliResult<Connection> {
    match Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY) {
        Ok(conn) => {
            conn.busy_timeout(Duration::from_secs(3))?;
            Ok(conn)
        }
        Err(rusqlite::Error::SqliteFailure(err, _))
            if err.extended_code == 1034 /* SQLITE_READONLY_CANTINIT */ =>
        {
            let conn = Connection::open(path)?;
            conn.busy_timeout(Duration::from_secs(3))?;
            heal_sidecars(Path::new(path));
            Ok(conn)
        }
        Err(other) => Err(other.into()),
    }
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

fn remove_sidecars(db: &Path) {
    let name = db.file_name().unwrap_or_default().to_string_lossy().into_owned();
    for suffix in ["-journal", "-wal", "-shm"] {
        let sidecar = db.with_file_name(format!("{name}{suffix}"));
        let _ = fs::remove_file(sidecar);
    }
}

fn restore_db_atomically(src: &Path, dest: &str) -> CliResult<()> {
    let dest_path = Path::new(dest);
    let mode = fs::metadata(dest)?.permissions().mode();
    // Sidecars must go before the main file is replaced, otherwise WAL
    // replay would mix the backup payload with leftover journals.
    remove_sidecars(dest_path);
    let tmp = dest_path.with_file_name(format!(
        "{}.restore.tmp",
        dest_path.file_name().unwrap_or_default().to_string_lossy()
    ));
    let _ = fs::remove_file(&tmp);
    fs::copy(src, &tmp)?;
    let src_len = fs::metadata(src)?.len();
    let tmp_len = fs::metadata(&tmp)?.len();
    if src_len != tmp_len {
        let _ = fs::remove_file(&tmp);
        return Err(format!(
            "restore size mismatch for {dest}: src={src_len} tmp={tmp_len}"
        )
        .into());
    }
    fs::rename(&tmp, dest)?;
    // fs::copy applies the source mode bits; restore the original dest mode.
    fs::set_permissions(dest, fs::Permissions::from_mode(mode))?;
    heal_sidecars(dest_path);
    restorecon(dest);
    Ok(())
}


fn db_mtime(path: &Path) -> Option<u64> {
    fs::metadata(path)
        .ok()
        .and_then(|m| m.modified().ok())
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs())
}

fn write_backup_manifest(dir: &Path, originals: &[(String, String)]) -> CliResult<()> {
    let mut files = serde_json::Map::new();
    for (orig, name) in originals {
        let dest = dir.join(name);
        if !dest.exists() {
            continue;
        }
        files.insert(
            name.clone(),
            json!({
                "sha256": sha256_hex_file(&dest)?,
                "size": fs::metadata(&dest)?.len(),
                "mtime": db_mtime(Path::new(orig)),
            }),
        );
    }
    let manifest = json!({
        "created": epoch(),
        "files": files,
    });
    fs::write(dir.join("manifest.json"), serde_json::to_vec_pretty(&manifest)?)?;
    Ok(())
}

fn verify_backup_manifest(dir: &Path) -> CliResult<()> {
    let text = fs::read_to_string(dir.join("manifest.json"))
        .map_err(|_| "备份缺少 manifest.json，拒绝恢复".to_string())?;
    let man: Value = serde_json::from_str(&text).map_err(|e| format!("manifest 解析失败: {e}"))?;
    let files = man
        .get("files")
        .and_then(Value::as_object)
        .ok_or("manifest 缺少 files")?;
    if files.is_empty() {
        return Err("manifest files 为空".into());
    }
    for (name, meta) in files {
        let path = dir.join(name);
        if !path.exists() {
            return Err(format!("备份缺少文件: {name}").into());
        }
        let size = fs::metadata(&path)?.len();
        let expect_size = meta.get("size").and_then(Value::as_u64).unwrap_or(u64::MAX);
        if size != expect_size {
            return Err(format!("{name} 大小与 manifest 不一致").into());
        }
        let expect = meta.get("sha256").and_then(Value::as_str).unwrap_or("");
        let actual = sha256_hex_file(&path)?;
        if actual != expect {
            return Err(format!("{name} 校验失败（可能已被篡改）").into());
        }
    }
    Ok(())
}

fn backup_manifest_ok(dir: &Path) -> bool {
    verify_backup_manifest(dir).is_ok()
}

fn restorecon(path: &str) {
    if !Path::new("/system/bin/restorecon").exists() {
        return;
    }
    match Command::new("/system/bin/restorecon").arg(path).status() {
        Ok(status) if status.success() => {}
        Ok(status) => eprintln!("restorecon {path} failed: {status}"),
        Err(err) => eprintln!("restorecon {path} failed: {err}"),
    }
}

fn stop_joyose() {
    if !Path::new("/system/bin/am").exists() {
        return;
    }
    match Command::new("/system/bin/am").args(["force-stop", PKG]).status() {
        Ok(status) if status.success() => {}
        Ok(status) => eprintln!("am force-stop {PKG} failed: {status}"),
        Err(err) => eprintln!("am force-stop {PKG} failed: {err}"),
    }
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
        && name != "."
        && name != ".."
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
///
/// 读不到与不存在必须分开上报：Joyose 被 force-stop 后重启会短暂占住
/// SmartP.db，此时若统一回"未找到配置"，用户会去找一条其实存在的配置。
pub fn read_params_any(config: &str) -> CliResult<(String, Value)> {
    let read_side = |path: &str, sql: &str, label: &str, unwrap: bool| -> Result<Option<Value>, String> {
        match std::fs::metadata(path) {
            Ok(_) => {}
            Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(err) => return Err(format!("{label} 不可访问: {err}")),
        }
        let conn = open_ro(path).map_err(|e| format!("{label} 打开失败: {e}"))?;
        let text = query_optional(&conn, sql, config)
            .map_err(|e| format!("{label} 读取失败: {e}"))?;
        let Some(text) = text else { return Ok(None) };
        let value: Value = serde_json::from_str(&text)
            .map_err(|e| format!("{label} {config} 解析失败: {e}"))?;
        Ok(Some(if unwrap { unwrap_rule(value) } else { value }))
    };

    let mut problems = Vec::new();

    match read_side(
        &smartp_db(),
        "SELECT params FROM cloud_config WHERE config_name = ?1",
        "SmartP",
        false,
    ) {
        Ok(Some(value)) => return Ok(("smartp".into(), value)),
        Ok(None) => {}
        Err(reason) => problems.push(reason),
    }
    match read_side(
        &teg_db(),
        "SELECT rule_content FROM rules WHERE rule_module = ?1 ORDER BY _id DESC LIMIT 1",
        "teg",
        true,
    ) {
        Ok(Some(value)) => return Ok(("teg".into(), value)),
        Ok(None) => {}
        Err(reason) => problems.push(reason),
    }

    if problems.is_empty() {
        Err(format!("未找到配置: {config}").into())
    } else {
        Err(format!("读取 {config} 失败：{}", problems.join("；")).into())
    }
}

/// Single-row query that distinguishes "no such row" from "query failed".
fn query_optional(
    conn: &Connection,
    sql: &str,
    config: &str,
) -> Result<Option<String>, rusqlite::Error> {
    match conn.query_row(sql, [config], |r| r.get::<_, String>(0)) {
        Ok(value) => Ok(Some(value)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(other) => Err(other),
    }
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
            "smartp": stat_one(&smartp_db()),
            "teg": stat_one(&teg_db()),
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
    if Path::new(&smartp_db()).exists() {
        let conn = open_ro(&smartp_db())?;
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
    if Path::new(&teg_db()).exists() {
        let conn = open_ro(&teg_db())?;
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

/// `joyose-write <config> <json文件>` — read a params document from a file and
/// hand it to [`write_document`].
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
    println!("{}", write_document(config, doc)?);
    Ok(true)
}

/// Dual-DB mirror write of a params document: force-stop, SmartP upsert, teg
/// mirror, read-back verification.  Returns the report the caller prints.
///
/// `joyose-write` (whole document from a file) and `joyose-scoped-write`
/// (document patched pointer by pointer) share this one write path so neither
/// can drift away from the other's safety checks.
pub fn write_document(config: &str, doc: Value) -> CliResult<Value> {
    if !doc.is_object() {
        return Err("JSON 顶层必须是对象".into());
    }
    let doc = unwrap_rule(doc);

    stop_joyose();

    let smartp_path = smartp_db();
    let teg_path = teg_db();

    // Compute teg payloads first so a malformed row aborts before any write.
    let mut teg_payloads: Vec<(i64, String)> = Vec::new();
    let mut teg_result = WriteSide::Skipped("missing db");
    if Path::new(&teg_path).exists() {
        let conn = open_ro(&teg_path)?;
        let mut stmt = conn.prepare("SELECT _id, rule_content FROM rules WHERE rule_module = ?1")?;
        let rows: Vec<(i64, String)> = stmt
            .query_map([config], |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        drop(stmt);
        drop(conn);
        if rows.is_empty() {
            teg_result = WriteSide::Skipped("no rule rows");
        } else {
            let mut problems = Vec::new();
            for (id, content) in &rows {
                match mirror_rule_content(content, &doc) {
                    Ok(mirrored) => teg_payloads.push((*id, mirrored)),
                    Err(err) => problems.push(format!("_id={id}: {err}")),
                }
            }
            if !problems.is_empty() {
                return Err(format!(
                    "SmartP=not written, teg=rolled back: {}",
                    problems.join("; ")
                )
                .into());
            }
            teg_result = WriteSide::Updated(teg_payloads.len());
        }
    }

    let mut smartp_result = WriteSide::Skipped("missing db");
    if Path::new(&smartp_path).exists() {
        let mut conn = open_rw(&smartp_path)?;
        let smartp_write: CliResult<WriteSide> = (|| {
            let tx = conn.transaction()?;
            let existing: Option<i64> = tx
                .query_row(
                    "SELECT version FROM cloud_config WHERE config_name = ?1",
                    [config],
                    |r| r.get(0),
                )
                .optional()?;
            let version = doc
                .get("header")
                .and_then(|h| h.get("version"))
                .and_then(Value::as_str)
                .and_then(|s| s.parse::<i64>().ok())
                .or(existing)
                .or_else(|| {
                    Path::new(&teg_path).exists().then_some(()).and_then(|_| {
                        open_ro(&teg_path).ok().and_then(|c| {
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
            let side = match existing {
                Some(_) => {
                    tx.execute(
                        "UPDATE cloud_config SET params = ?1, version = ?2 WHERE config_name = ?3",
                        params![doc_str, version, config],
                    )?;
                    WriteSide::Updated(1)
                }
                None => {
                    tx.execute(
                        "INSERT INTO cloud_config (config_name, group_name, enable, version, with_model, model, params) VALUES (?1, ?1, 1, ?2, 0, '{}', ?3)",
                        params![config, version, doc_str],
                    )?;
                    WriteSide::Inserted
                }
            };
            tx.commit()?;
            Ok(side)
        })();
        match smartp_write {
            Ok(side) => {
                smartp_result = side;
                checkpoint_and_close(conn, &smartp_path);
            }
            Err(err) => {
                drop(conn);
                heal_sidecars(Path::new(&smartp_path));
                return Err(format!("SmartP=rolled back, teg=not written: {err}").into());
            }
        }
    }

    if matches!(teg_result, WriteSide::Updated(_)) {
        let mut conn = open_rw(&teg_path)?;
        let teg_write: CliResult<()> = (|| {
            let tx = conn.transaction()?;
            for (id, mirrored) in &teg_payloads {
                tx.execute(
                    "UPDATE rules SET rule_content = ?1 WHERE _id = ?2",
                    params![mirrored, id],
                )?;
            }
            tx.commit()?;
            Ok(())
        })();
        match teg_write {
            Ok(()) => checkpoint_and_close(conn, &teg_path),
            Err(err) => {
                drop(conn);
                heal_sidecars(Path::new(&teg_path));
                return Err(format!(
                    "SmartP={}, teg=rolled back: {err}",
                    smartp_result.label()
                )
                .into());
            }
        }
    }

    let verify = |db: &str, text: &str| -> CliResult<()> {
        let value: Value = serde_json::from_str(text).map_err(|e| format!("{db} 回读解析失败: {e}"))?;
        if unwrap_rule(value) != doc {
            return Err(format!("{db} 回读校验不一致").into());
        }
        Ok(())
    };
    if !matches!(smartp_result, WriteSide::Skipped(_)) {
        let conn = open_ro(&smartp_path)?;
        let text: String = conn.query_row(
            "SELECT params FROM cloud_config WHERE config_name = ?1",
            [config],
            |r| r.get(0),
        )?;
        verify("SmartP", &text)?;
    }
    if matches!(teg_result, WriteSide::Updated(_)) {
        let conn = open_ro(&teg_path)?;
        let mut stmt = conn.prepare("SELECT rule_content FROM rules WHERE rule_module = ?1")?;
        let rows: Vec<String> = stmt
            .query_map([config], |r| r.get(0))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        for text in rows {
            verify("teg", &text)?;
        }
    }

    Ok(json!({
        "ok": true,
        "config": config,
        "smartp": smartp_result.label(),
        "teg": teg_result.label(),
    }))
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
    stop_joyose();
    let mut copied: Vec<(String, String)> = Vec::new();
    for (db, name) in [(smartp_db(), "SmartP.db"), (teg_db(), "teg_config.db")] {
        if Path::new(&db).exists() {
            let conn = open_rw(&db)?;
            let _ = conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE)");
            drop(conn);
            heal_sidecars(Path::new(&db));
            let dest = dir.join(name);
            fs::copy(&db, &dest)?;
            // Hand the backup back to the app uid so Kotlin can list/read it
            // without a root shell (same ownership-return pattern as the
            // cosa SQLite sidecars).
            let _ = chown(&dest, Some(root_meta.uid()), Some(root_meta.gid()));
            copied.push((db.clone(), name.to_string()));
        }
    }
    write_backup_manifest(&dir, &copied)?;
    let _ = chown(&dir.join("manifest.json"), Some(root_meta.uid()), Some(root_meta.gid()));
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
                "valid": backup_manifest_ok(&dir),
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
    verify_backup_manifest(&dir)?;
    stop_joyose();
    let mut restored = Vec::new();
    for (db, fname) in [(smartp_db(), "SmartP.db"), (teg_db(), "teg_config.db")] {
        let src = dir.join(fname);
        if src.exists() && Path::new(&db).exists() {
            restore_db_atomically(&src, &db)?;
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
        assert!(safe_name(".").is_err());
        assert!(safe_name("..").is_err());
    }

    fn make_smartp(path: &Path, params: &str) {
        let conn = Connection::open(path).unwrap();
        conn.execute_batch(
            "CREATE TABLE cloud_config (config_name TEXT, group_name TEXT, enable INTEGER, version INTEGER, with_model INTEGER, model TEXT, params TEXT);",
        )
        .unwrap();
        conn.execute(
            "INSERT INTO cloud_config (config_name, group_name, enable, version, with_model, model, params) VALUES ('booster_config','booster_config',1,1,0,'{}',?1)",
            [params],
        )
        .unwrap();
    }

    fn make_teg(path: &Path, rows: &[(i64, &str)]) {
        let conn = Connection::open(path).unwrap();
        conn.execute_batch(
            "CREATE TABLE rules (_id INTEGER PRIMARY KEY, rule_module TEXT, rule_version INTEGER, rule_content TEXT);",
        )
        .unwrap();
        for (id, content) in rows {
            conn.execute(
                "INSERT INTO rules (_id, rule_module, rule_version, rule_content) VALUES (?1, 'booster_config', 1, ?2)",
                rusqlite::params![id, *content],
            )
            .unwrap();
        }
    }

    fn with_dbs<T>(f: impl FnOnce(&Path, &Path) -> T) -> T {
        let _guard = TEST_DB_LOCK.lock().unwrap();
        let tmp = std::env::temp_dir().join(format!(
            "joyose-test-{}-{}",
            std::process::id(),
            SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()
        ));
        fs::create_dir_all(&tmp).unwrap();
        let smartp = tmp.join("SmartP.db");
        let teg = tmp.join("teg_config.db");
        std::env::set_var("JOYOSE_SMARTP_PATH", &smartp);
        std::env::set_var("JOYOSE_TEG_PATH", &teg);
        let out = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| f(&smartp, &teg)));
        std::env::remove_var("JOYOSE_SMARTP_PATH");
        std::env::remove_var("JOYOSE_TEG_PATH");
        let _ = fs::remove_dir_all(&tmp);
        match out {
            Ok(v) => v,
            Err(p) => std::panic::resume_unwind(p),
        }
    }

    #[test]
    fn write_document_rolls_back_when_teg_row_malformed() {
        with_dbs(|smartp, teg| {
            make_smartp(smartp, r#"{"game_booster":{"migt":[]}}"#);
            make_teg(
                teg,
                &[
                    (1, r#"{"config_name":"booster_config","params":{"game_booster":{"migt":[]}}}"#),
                    (2, "not-json{{{{"),
                ],
            );
            let doc: Value = serde_json::from_str(r#"{"game_booster":{"migt":["com.x;0:1"]}}"#).unwrap();
            let err = write_document("booster_config", doc).unwrap_err().to_string();
            assert!(err.contains("SmartP=not written"), "{err}");
            let conn = Connection::open(smartp).unwrap();
            let params: String = conn
                .query_row(
                    "SELECT params FROM cloud_config WHERE config_name='booster_config'",
                    [],
                    |r| r.get(0),
                )
                .unwrap();
            assert!(params.contains("migt"), "{params}");
        });
    }

    #[test]
    fn backup_includes_wal_committed_rows() {
        with_dbs(|smartp, teg| {
            make_smartp(smartp, r#"{"v":1}"#);
            make_teg(teg, &[]);
            let keep = Connection::open(smartp).unwrap();
            keep.execute_batch("PRAGMA journal_mode=WAL;").unwrap();
            keep.execute(
                "UPDATE cloud_config SET params = ?1 WHERE config_name='booster_config'",
                [r#"{"v":2}"#],
            )
            .unwrap();
            let root = smartp.parent().unwrap();
            cmd_backup(Some(&root.to_string_lossy().into_owned()), Some(&"t1".to_string())).unwrap();
            drop(keep);
            let backup_dir = fs::read_dir(root.join("backup")).unwrap().next().unwrap().unwrap().path();
            let bconn = Connection::open(backup_dir.join("SmartP.db")).unwrap();
            let params: String = bconn
                .query_row(
                    "SELECT params FROM cloud_config WHERE config_name='booster_config'",
                    [],
                    |r| r.get(0),
                )
                .unwrap();
            assert_eq!(params, r#"{"v":2}"#);
        });
    }

    #[test]
    fn revert_ignores_stale_wal() {
        with_dbs(|smartp, teg| {
            make_smartp(smartp, r#"{"v":"live"}"#);
            make_teg(teg, &[]);
            let root = smartp.parent().unwrap();
            cmd_backup(Some(&root.to_string_lossy().into_owned()), Some(&"snap".to_string())).unwrap();
            {
                let conn = Connection::open(smartp).unwrap();
                conn.execute(
                    "UPDATE cloud_config SET params = ?1 WHERE config_name='booster_config'",
                    [r#"{"v":"dirty"}"#],
                )
                .unwrap();
            }
            fs::write(
                smartp.with_file_name("SmartP.db-wal"),
                b"this is not a valid wal and must be discarded",
            )
            .unwrap();
            let name = fs::read_dir(root.join("backup"))
                .unwrap()
                .next()
                .unwrap()
                .unwrap()
                .file_name()
                .to_string_lossy()
                .into_owned();
            cmd_revert(Some(&root.to_string_lossy().into_owned()), Some(&name)).unwrap();
            let conn = Connection::open_with_flags(smartp, OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
            let params: String = conn
                .query_row(
                    "SELECT params FROM cloud_config WHERE config_name='booster_config'",
                    [],
                    |r| r.get(0),
                )
                .unwrap();
            assert_eq!(params, r#"{"v":"live"}"#);
        });
    }

    #[test]
    fn serde_keeps_u64_max() {
        let n = "18446744073709551615";
        let v: Value = serde_json::from_str(n).unwrap();
        assert_eq!(v.to_string(), n);
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
