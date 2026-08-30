use rusqlite::{
    params_from_iter,
    types::{Value as SqlValue, ValueRef},
    Connection,
};
use serde_json::{Map, Value};
use std::collections::BTreeSet;
use std::error::Error;
use std::fs;
use std::os::unix::fs::{chown, MetadataExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::ExitCode;

mod joyose;

const DB_PATHS: [&str; 2] = [
    "/data/data/com.oplus.cosa/databases/db_game_database",
    "/data/user_de/0/com.oplus.cosa/databases/db_game_database",
];
const TABLE: &str = "PackageConfigBean";
const EXCLUDED: [&str; 2] = [
    "oplus.cosa.common.model.config",
    "oplus.cosa.default.model.config",
];

type Result<T> = std::result::Result<T, Box<dyn Error>>;
type Columns = Vec<(String, String)>;

fn db_paths() -> Vec<PathBuf> {
    if let Ok(path) = std::env::var("COSA_DB_PATH") {
        let path = PathBuf::from(path);
        return path.exists().then_some(path).into_iter().collect();
    }

    let mut seen = BTreeSet::new();
    DB_PATHS
        .iter()
        .map(PathBuf::from)
        .filter(|path| path.exists())
        .filter(|path| seen.insert(path.clone()))
        .collect()
}

fn quote_identifier(value: &str) -> String {
    format!("\"{}\"", value.replace('"', "\"\""))
}

fn columns(conn: &Connection) -> Result<Columns> {
    let mut stmt = conn.prepare(&format!("PRAGMA table_info({})", quote_identifier(TABLE)))?;
    let rows = stmt.query_map([], |row| {
        Ok((
            row.get::<_, String>(1)?,
            row.get::<_, String>(2)
                .unwrap_or_else(|_| "TEXT".to_owned()),
        ))
    })?;
    Ok(rows.collect::<rusqlite::Result<Vec<_>>>()?)
}

fn column(columns: &Columns, name: &str) -> Option<String> {
    columns
        .iter()
        .find(|(actual, _)| actual.eq_ignore_ascii_case(name))
        .map(|(actual, _)| actual.clone())
}

fn column_type<'a>(columns: &'a Columns, name: &str) -> &'a str {
    columns
        .iter()
        .find(|(actual, _)| actual.eq_ignore_ascii_case(name))
        .map(|(_, sql_type)| sql_type.as_str())
        .unwrap_or("TEXT")
}

fn value_to_json(value: ValueRef<'_>, sql_type: &str) -> Value {
    match value {
        ValueRef::Null => Value::Null,
        ValueRef::Integer(number) => Value::from(number),
        ValueRef::Real(number) if number.is_finite() => {
            // Only convert whole numbers that fit into i64; Rust's float->int
            // `as` cast saturates out-of-range values, which would silently
            // corrupt the exported (and later re-written) data.
            if number.fract() == 0.0
                && number >= i64::MIN as f64
                && number < i64::MAX as f64
            {
                Value::from(number as i64)
            } else {
                Value::from(number)
            }
        }
        ValueRef::Real(_) => Value::Null,
        ValueRef::Text(bytes) => {
            let text = String::from_utf8_lossy(bytes);
            let trimmed = text.trim();
            if sql_type.to_ascii_uppercase().contains("INT") {
                if let Ok(number) = trimmed.parse::<i64>() {
                    return Value::from(number);
                }
            }
            if trimmed.starts_with('{') || trimmed.starts_with('[') {
                if let Ok(json) = serde_json::from_str::<Value>(trimmed) {
                    return json;
                }
            }
            Value::String(text.into_owned())
        }
        ValueRef::Blob(_) => Value::String("<blob>".to_owned()),
    }
}

fn to_sql(value: &Value) -> SqlValue {
    match value {
        Value::Null => SqlValue::Null,
        Value::Bool(value) => SqlValue::Integer(i64::from(*value)),
        Value::Number(value) => value
            .as_i64()
            .map(SqlValue::Integer)
            .or_else(|| value.as_f64().map(SqlValue::Real))
            .unwrap_or(SqlValue::Null),
        Value::String(value) => SqlValue::Text(value.clone()),
        Value::Array(_) | Value::Object(_) => SqlValue::Text(value.to_string()),
    }
}

fn install_protection(conn: &Connection, cols: &Columns) -> Result<()> {
    let Some(from_server) = column(cols, "from_server") else {
        return Ok(());
    };
    let Some(package_name) = column(cols, "package_name") else {
        return Ok(());
    };
    let table = quote_identifier(TABLE);
    let fs = quote_identifier(&from_server);
    let pkg = quote_identifier(&package_name);

    conn.execute_batch(&format!(
        "CREATE TRIGGER IF NOT EXISTS protect_local_pkg_update\n\
         BEFORE UPDATE ON {table}\n\
         WHEN COALESCE(CAST(OLD.{fs} AS INTEGER), 0) = 0\n\
           AND COALESCE(CAST(NEW.{fs} AS INTEGER), 0) != 0\n\
         BEGIN SELECT RAISE(IGNORE); END;\n\
         CREATE TRIGGER IF NOT EXISTS protect_local_pkg_insert\n\
         BEFORE INSERT ON {table}\n\
         WHEN COALESCE(CAST(NEW.{fs} AS INTEGER), 0) != 0\n\
           AND EXISTS (SELECT 1 FROM {table} WHERE {pkg} = NEW.{pkg}\n\
             AND COALESCE(CAST({fs} AS INTEGER), 0) = 0)\n\
         BEGIN SELECT RAISE(IGNORE); END;\n\
         CREATE TRIGGER IF NOT EXISTS protect_local_pkg_delete\n\
         BEFORE DELETE ON {table}\n\
         WHEN COALESCE(CAST(OLD.{fs} AS INTEGER), 0) = 0\n\
         BEGIN SELECT RAISE(IGNORE); END;"
    ))?;
    Ok(())
}

/// Restore the ownership of the SQLite sidecar files (-wal/-shm/-journal)
/// to the main database file's owner. We run as root while the target app
/// runs under its own uid, so any sidecar created by us must be given back
/// to the app, otherwise it fails with SQLITE_CANTOPEN.
fn chown_sidecars(db: &Path) {
    if let Ok(metadata) = fs::metadata(db) {
        for suffix in ["-wal", "-shm", "-journal"] {
            let sidecar = db.with_file_name(format!(
                "{}{}",
                db.file_name().unwrap_or_default().to_string_lossy(),
                suffix
            ));
            if sidecar.exists() {
                let _ = chown(&sidecar, Some(metadata.uid()), Some(metadata.gid()));
            }
        }
    }
}

fn finish(conn: Connection, db: &Path) {
    let _ = conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE)");
    drop(conn);
    chown_sidecars(db);
}

fn package_column(cols: &Columns) -> Result<String> {
    column(cols, "package_name").ok_or_else(|| "缺少 package_name 列".into())
}

fn cmd_list() -> Result<bool> {
    if db_paths().is_empty() {
        return Err("未找到数据库".into());
    }
    let mut packages = BTreeSet::new();
    for db in db_paths() {
        let conn = Connection::open(&db)?;
        let cols = columns(&conn)?;
        let package_name = package_column(&cols)?;
        let mut stmt = conn.prepare(&format!(
            "SELECT DISTINCT {} FROM {} ORDER BY {}",
            quote_identifier(&package_name),
            quote_identifier(TABLE),
            quote_identifier(&package_name)
        ))?;
        let rows = stmt.query_map([], |row| row.get::<_, String>(0))?;
        for package in rows {
            let package = package?;
            if !package.trim().is_empty() && !EXCLUDED.contains(&package.as_str()) {
                packages.insert(package);
            }
        }
    }
    for package in packages {
        println!("{package}");
    }
    Ok(true)
}

fn cmd_read(package: &str, output: Option<&str>) -> Result<bool> {
    for db in db_paths() {
        // Heal sidecars possibly left root-owned by a previous interrupted run.
        chown_sidecars(&db);
        let conn = Connection::open(&db)?;
        let cols = columns(&conn)?;
        let package_name = package_column(&cols)?;
        let mut stmt = conn.prepare(&format!(
            "SELECT * FROM {} WHERE {} = ?1 LIMIT 1",
            quote_identifier(TABLE),
            quote_identifier(&package_name)
        ))?;
        let names: Vec<String> = stmt
            .column_names()
            .iter()
            .map(ToString::to_string)
            .collect();
        let mut rows = stmt.query([package])?;
        if let Some(row) = rows.next()? {
            let mut object = Map::with_capacity(names.len());
            for (index, name) in names.iter().enumerate() {
                object.insert(
                    name.clone(),
                    value_to_json(row.get_ref(index)?, column_type(&cols, name)),
                );
            }
            let text = serde_json::to_string_pretty(&Value::Object(object))?;
            if let Some(path) = output {
                let path = Path::new(path);
                if let Some(parent) = path
                    .parent()
                    .filter(|parent| !parent.as_os_str().is_empty())
                {
                    fs::create_dir_all(parent)?;
                }
                fs::write(path, text)?;
                fs::set_permissions(path, fs::Permissions::from_mode(0o644))?;
                println!("已导出: {}", path.display());
            } else {
                println!("{text}");
            }
            return Ok(true);
        }
    }
    eprintln!("未找到包名: {package}");
    Ok(false)
}

fn write_one(
    db: &Path,
    package: &str,
    object: &Map<String, Value>,
    skipped: &mut BTreeSet<String>,
) -> Result<()> {
    let conn = Connection::open(db)?;
    let cols = columns(&conn)?;
    let package_name = package_column(&cols)?;
    let from_server = column(&cols, "from_server");
    let mut fields: Vec<(String, SqlValue, bool)> = Vec::new();
    let mut has_from_server = false;

    for (key, value) in object {
        let Some(actual) = column(&cols, key) else {
            skipped.insert(key.clone());
            continue;
        };
        if actual.eq_ignore_ascii_case(&package_name) {
            // Keep the package column at its JSON position, but never trust
            // a package value from the editable document.
            fields.push((actual, SqlValue::Text(package.to_owned()), true));
        } else if from_server
            .as_ref()
            .is_some_and(|name| actual.eq_ignore_ascii_case(name))
        {
            // A written document always becomes a local configuration.
            fields.push((actual, SqlValue::Integer(0), false));
            has_from_server = true;
        } else {
            fields.push((actual, to_sql(value), false));
        }
    }
    if let Some(from_server) = from_server.filter(|_| !has_from_server) {
        // New documents may omit the marker; append it without disturbing
        // the order of fields that were present in the document.
        fields.push((from_server, SqlValue::Integer(0), false));
    }
    if fields.iter().all(|(_, _, is_package)| *is_package) {
        return Err("没有匹配到任何列".into());
    }

    let table = quote_identifier(TABLE);
    let package_column = quote_identifier(&package_name);
    let update_fields: Vec<_> = fields
        .iter()
        .filter(|(_, _, is_package)| !is_package)
        .collect();
    let assignments = update_fields
        .iter()
        .map(|(name, _, _)| format!("{} = ?", quote_identifier(name)))
        .collect::<Vec<_>>()
        .join(", ");
    let existing: i64 = conn.query_row(
        &format!("SELECT COUNT(*) FROM {table} WHERE {package_column} = ?"),
        [package],
        |row| row.get(0),
    )?;
    let mut update_params: Vec<SqlValue> = update_fields
        .iter()
        .map(|(_, value, _)| (*value).clone())
        .collect();
    update_params.push(SqlValue::Text(package.to_owned()));
    if existing > 0 {
        conn.execute(
            &format!("UPDATE {table} SET {assignments} WHERE {package_column} = ?"),
            params_from_iter(update_params),
        )?;
    } else {
        let mut insert_fields = fields;
        if !insert_fields.iter().any(|(_, _, is_package)| *is_package) {
            insert_fields.push((
                package_name.clone(),
                SqlValue::Text(package.to_owned()),
                true,
            ));
        }
        let insert_columns = insert_fields
            .iter()
            .map(|(name, _, _)| quote_identifier(name))
            .collect::<Vec<_>>()
            .join(", ");
        let placeholders = std::iter::repeat_n("?", insert_fields.len())
            .collect::<Vec<_>>()
            .join(", ");
        let insert_params: Vec<SqlValue> = insert_fields
            .into_iter()
            .map(|(_, value, _)| value)
            .collect();
        conn.execute(
            &format!("INSERT INTO {table} ({insert_columns}) VALUES ({placeholders})"),
            params_from_iter(insert_params),
        )?;
    }

    let exists: i64 = conn.query_row(
        &format!("SELECT COUNT(*) FROM {table} WHERE {package_column} = ?"),
        [package],
        |row| row.get(0),
    )?;
    if exists == 0 {
        return Err(format!("写入后未找到包名: {package}").into());
    }
    install_protection(&conn, &cols)?;
    finish(conn, db);
    Ok(())
}

fn cmd_write(package: &str, json_path: &str) -> Result<bool> {
    if EXCLUDED.contains(&package) {
        return Err("该包名为内部配置，禁止写入".into());
    }
    let json: Value = serde_json::from_str(&fs::read_to_string(json_path)?)?;
    let object = json.as_object().ok_or("JSON 顶层必须是对象")?;
    if object.is_empty() {
        return Err("JSON 内容为空".into());
    }

    let dbs = db_paths();
    if dbs.is_empty() {
        return Err("未找到数据库".into());
    }

    // 逐库独立执行：任一库失败不影响其他库的写入与结果收集；
    // 整体失败时错误信息标明具体失败的库，便于定位双库不一致。
    let mut skipped = BTreeSet::new();
    let mut failures: Vec<String> = Vec::new();
    for db in &dbs {
        // Heal sidecars possibly left root-owned by a previous interrupted run.
        chown_sidecars(db);
        if let Err(err) = write_one(db, package, object, &mut skipped) {
            failures.push(format!("write failed on {}: {}", db.display(), err));
            // The write may have created/used sidecars owned by root before
            // failing; hand them back so the target app keeps working.
            chown_sidecars(db);
        }
    }

    if !failures.is_empty() {
        // Report failures first: printing the skipped-field note beforehand
        // would be picked up as the only visible line by the app, hiding the
        // actual per-database failure details.
        return Err(failures.join("\n").into());
    }
    if !skipped.is_empty() {
        eprintln!(
            "已忽略未知字段: {}",
            skipped.into_iter().collect::<Vec<_>>().join(", ")
        );
    }
    println!("{package} 配置写入成功");
    Ok(true)
}

fn delete_one(db: &Path, package: &str) -> Result<()> {
    // Heal sidecars possibly left root-owned by a previous interrupted run.
    chown_sidecars(db);
    let conn = Connection::open(db)?;
    let cols = columns(&conn)?;
    let package_name = package_column(&cols)?;
    conn.execute_batch("DROP TRIGGER IF EXISTS protect_local_pkg_delete")?;
    let mut result: Result<()> = conn
        .execute(
            &format!(
                "DELETE FROM {} WHERE {} = ?",
                quote_identifier(TABLE),
                quote_identifier(&package_name)
            ),
            [package],
        )
        .map(|_| ())
        .map_err(|e| -> Box<dyn Error> { e.into() });
    if result.is_ok() {
        result = install_protection(&conn, &cols);
    }
    match result {
        Ok(()) => {
            finish(conn, db);
            Ok(())
        }
        Err(err) => {
            // The protection trigger was dropped before the operation; any
            // failure from this point on must restore it. Never swallow the
            // restore error, otherwise the local-config protection of this
            // database is left permanently broken without any signal.
            match install_protection(&conn, &cols) {
                Ok(()) => Err(err),
                Err(restore_err) => {
                    Err(format!("{err}; 保护触发器恢复失败: {restore_err}").into())
                }
            }
        }
    }
}

fn cmd_delete(package: &str) -> Result<bool> {
    if EXCLUDED.contains(&package) {
        return Err("该包名为内部配置，禁止删除".into());
    }
    let dbs = db_paths();
    if dbs.is_empty() {
        return Err("未找到数据库".into());
    }
    // 逐库独立执行：任一库失败不影响其他库的删除与结果收集；
    // 整体失败时错误信息标明具体失败的库，与 write 语义保持一致。
    let mut failures: Vec<String> = Vec::new();
    for db in &dbs {
        if let Err(err) = delete_one(db, package) {
            failures.push(format!("delete failed on {}: {}", db.display(), err));
            chown_sidecars(db);
        }
    }
    if !failures.is_empty() {
        return Err(failures.join("\n").into());
    }
    println!("{package} 已删除");
    Ok(true)
}

fn cmd_protect() -> Result<bool> {
    let dbs = db_paths();
    if dbs.is_empty() {
        return Err("未找到数据库".into());
    }
    for db in &dbs {
        chown_sidecars(db);
        let conn = Connection::open(db)?;
        let cols = columns(&conn)?;
        install_protection(&conn, &cols)?;
        finish(conn, db);
    }
    println!("已启用本地配置保护");
    Ok(true)
}

fn usage() -> &'static str {
    "用法: cosa list | read <包名> [输出文件] | write <包名> <json文件> | delete <包名> | protect\n\
     joyose: stat [备份根目录] | list | read <配置名> | write <配置名> <json文件> | freeze | unfreeze\n\
             backup <备份根目录> [标签] | backup-list <备份根目录> | revert <备份根目录> <名称>\n\
             apps | app <包名> [booster_params.json] [common_params.json]"
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();
    let result = match args.get(1).map(String::as_str) {
        Some("list") => cmd_list(),
        Some("read") => match (args.get(2), args.get(3)) {
            (Some(package), output) => cmd_read(package, output.map(String::as_str)),
            _ => Err(usage().into()),
        },
        Some("write") => match (args.get(2), args.get(3)) {
            (Some(package), Some(path)) => cmd_write(package, path),
            _ => Err(usage().into()),
        },
        Some("delete") => match args.get(2) {
            Some(package) => cmd_delete(package),
            None => Err(usage().into()),
        },
        Some("protect") => cmd_protect(),
        Some("joyose-stat") => joyose::store::cmd_stat(args.get(2)),
        Some("joyose-list") => joyose::store::cmd_list(),
        Some("joyose-read") => joyose::store::cmd_read(args.get(2)),
        Some("joyose-write") => joyose::store::cmd_write(args.get(2), args.get(3)),
        Some("joyose-freeze") => joyose::store::cmd_freeze(),
        Some("joyose-unfreeze") => joyose::store::cmd_unfreeze(),
        Some("joyose-backup") => joyose::store::cmd_backup(args.get(2), args.get(3)),
        Some("joyose-backup-list") => joyose::store::cmd_backup_list(args.get(2)),
        Some("joyose-revert") => joyose::store::cmd_revert(args.get(2), args.get(3)),
        Some("joyose-apps") => joyose::store::cmd_apps(),
        Some("joyose-app") => {
            joyose::appview::cmd_app_view(args.get(2), args.get(3), args.get(4))
        }
        Some("help") | Some("--help") | Some("-h") | None => Err(usage().into()),
        Some(_) => Err(usage().into()),
    };

    match result {
        Ok(true) => ExitCode::SUCCESS,
        Ok(false) => ExitCode::FAILURE,
        Err(error) => {
            eprintln!("{error}");
            ExitCode::FAILURE
        }
    }
}
