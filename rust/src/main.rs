use rusqlite::{
    params_from_iter,
    types::{Value as SqlValue, ValueRef},
    Connection, OpenFlags,
};
use serde_json::{Map, Value};
use std::collections::BTreeSet;
use std::error::Error;
use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::os::unix::fs::{chown, MetadataExt, OpenOptionsExt, PermissionsExt};
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
/// Names of the local-config protection triggers installed by
/// `install_protection`; the rollback path must lift them to be able to put
/// a captured row back, and re-installs them right after (same pattern as
/// `delete_one`).
const PROTECTION_TRIGGERS: [&str; 3] = [
    "protect_local_pkg_update",
    "protect_local_pkg_insert",
    "protect_local_pkg_delete",
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

fn open_cosa_ro(db: &Path) -> Result<Connection> {
    match Connection::open_with_flags(db, OpenFlags::SQLITE_OPEN_READ_ONLY) {
        Ok(conn) => Ok(conn),
        Err(rusqlite::Error::SqliteFailure(err, _))
            if err.extended_code == 1034 /* SQLITE_READONLY_CANTINIT */ =>
        {
            let conn = Connection::open(db)?;
            chown_sidecars(db);
            Ok(conn)
        }
        Err(other) => Err(other.into()),
    }
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
        chown_sidecars(&db);
        let listed = (|| -> Result<()> {
            let conn = open_cosa_ro(&db)?;
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
            Ok(())
        })();
        chown_sidecars(&db);
        listed?;
    }
    for package in packages {
        println!("{package}");
    }
    Ok(true)
}

fn cmd_read(package: &str, output: Option<&str>) -> Result<bool> {
    for db in db_paths() {
        chown_sidecars(&db);
        let found = (|| -> Result<bool> {
            let conn = open_cosa_ro(&db)?;
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
                    write_export(path, &text)?;
                    println!("已导出: {}", path.display());
                } else {
                    println!("{text}");
                }
                return Ok(true);
            }
            Ok(false)
        })();
        chown_sidecars(&db);
        if found? {
            return Ok(true);
        }
    }
    eprintln!("未找到包名: {package}");
    Ok(false)
}

/// `read <包名> <输出文件>` 的落盘（P2-3 防御加固）。
///
/// 本进程以 uid 0 运行，输出路径直接来自命令行参数；`fs::write` 会跟随符号链接，
/// 等于把"覆写任意 root 可读文件"的能力交给任何能构造该参数的调用方。
///
/// std 没有稳定的 O_NOFOLLOW 开关（`OpenOptionsExt::follow_symlinks` 到 rustc 1.98
/// 仍是 unstable），且该标志的数值在 Linux/bionic（0o400000）与 Darwin（0o200）上
/// 不同，不能硬编码进 `custom_flags`。这里用可移植的等价三段式：先用不跟随链接的
/// `symlink_metadata` 拒绝符号链接；路径不存在时用 `O_CREAT|O_EXCL` 创建（竞态下
/// 目标变成链接也会失败）；只有已存在的普通文件才截断覆写 —— 重复导出到同一文件是
/// 正常用法，所以不能一律 `create_new`。
fn write_export(path: &Path, text: &str) -> Result<()> {
    if let Some(parent) = path
        .parent()
        .filter(|parent| !parent.as_os_str().is_empty())
    {
        fs::create_dir_all(parent)?;
    }
    let mut file = match fs::symlink_metadata(path) {
        Ok(meta) if meta.file_type().is_symlink() => {
            return Err(
                format!("导出目标是符号链接，已拒绝写入: {}", path.display()).into(),
            )
        }
        Ok(_) => OpenOptions::new().write(true).truncate(true).open(path)?,
        Err(err) if err.kind() == io::ErrorKind::NotFound => OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o644)
            .open(path)?,
        Err(err) => return Err(err.into()),
    };
    file.write_all(text.as_bytes())?;
    // open(2) 的 mode 会被 umask 削掉高位，导出件要能被内容提供器读走，显式补齐。
    fs::set_permissions(path, fs::Permissions::from_mode(0o644))?;
    Ok(())
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
    write_dbs(&dbs, package, object)
}

/// Dual-DB write with all-or-nothing semantics (P2-7): every database's
/// original row is captured up-front, and the first failure rolls back the
/// already-written databases, so a partial success can never leave the two
/// cosa copies permanently apart.
fn write_dbs(
    dbs: &[PathBuf],
    package: &str,
    object: &Map<String, Value>,
) -> Result<bool> {
    let mut originals = Vec::with_capacity(dbs.len());
    for db in dbs {
        chown_sidecars(db);
        match capture_row(db, package) {
            Ok(before) => originals.push(before),
            Err(err) => {
                return Err(format!("capture failed on {}: {}", db.display(), err).into())
            }
        }
    }

    let mut skipped = BTreeSet::new();
    for (index, db) in dbs.iter().enumerate() {
        // Heal sidecars possibly left root-owned by a previous interrupted run.
        chown_sidecars(db);
        if let Err(err) = write_one(db, package, object, &mut skipped) {
            let failure = format!("write failed on {}: {}", db.display(), err);
            // The write may have created/used sidecars owned by root before
            // failing; hand them back so the target app keeps working.
            chown_sidecars(db);
            let (written_errors, failed_error) = rollback_dbs(dbs, &originals, index);
            return Err(rollback_report(&failure, &written_errors, failed_error.as_ref()));
        }
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

/// Keep rollback messages readable: the app surfaces stderr verbatim.
fn shorten(text: &str) -> String {
    const MAX: usize = 160;
    if text.len() <= MAX {
        return text.to_owned();
    }
    let mut end = MAX;
    while end > 0 && !text.is_char_boundary(end) {
        end -= 1;
    }
    format!("{}…", &text[..end])
}

/// One package row captured before any dual-DB mutation (P2-7).
struct BeforeRow {
    package: String,
    existed: bool,
    columns: Vec<String>,
    values: Vec<SqlValue>,
}

fn capture_row(db: &Path, package: &str) -> Result<BeforeRow> {
    let conn = open_cosa_ro(db)?;
    let cols = columns(&conn)?;
    let package_name = package_column(&cols)?;
    let mut stmt = conn.prepare(&format!(
        "SELECT * FROM {} WHERE {} = ?1 LIMIT 1",
        quote_identifier(TABLE),
        quote_identifier(&package_name)
    ))?;
    let names: Vec<String> = stmt.column_names().iter().map(ToString::to_string).collect();
    let mut rows = stmt.query([package])?;
    let (existed, values) = match rows.next()? {
        Some(row) => (
            true,
            (0..names.len())
                .map(|index| row.get::<_, SqlValue>(index).unwrap_or(SqlValue::Null))
                .collect::<Vec<_>>(),
        ),
        None => (false, Vec::new()),
    };
    Ok(BeforeRow { package: package.to_owned(), existed, columns: names, values })
}

/// Put a captured row back verbatim (or delete the row the run inserted).
/// Protection triggers are lifted for the duration and re-installed after —
/// restoring a `from_server = 0` local row would otherwise be RAISE(IGNORE)d
/// by the very triggers this tool relies on.
fn restore_row(db: &Path, before: &BeforeRow) -> Result<()> {
    chown_sidecars(db);
    let conn = Connection::open(db)?;
    let cols = columns(&conn)?;
    let package_name = package_column(&cols)?;
    let table = quote_identifier(TABLE);
    let package_column = quote_identifier(&package_name);
    let mut stmts: Vec<(String, Vec<SqlValue>)> = Vec::new();
    if before.existed {
        stmts.push((
            format!("DELETE FROM {table} WHERE {package_column} = ?"),
            vec![SqlValue::Text(before.package.clone())],
        ));
        let columns = before
            .columns
            .iter()
            .map(|name| quote_identifier(name))
            .collect::<Vec<_>>()
            .join(", ");
        let placeholders = std::iter::repeat_n("?", before.values.len())
            .collect::<Vec<_>>()
            .join(", ");
        stmts.push((
            format!("INSERT INTO {table} ({columns}) VALUES ({placeholders})"),
            before.values.clone(),
        ));
    } else {
        stmts.push((
            format!("DELETE FROM {table} WHERE {package_column} = ?"),
            vec![SqlValue::Text(before.package.clone())],
        ));
    }
    let drop_triggers = PROTECTION_TRIGGERS
        .iter()
        .map(|name| format!("DROP TRIGGER IF EXISTS {name}"))
        .collect::<Vec<_>>()
        .join("; ");
    conn.execute_batch(&drop_triggers)?;
    let mut result: Result<()> = Ok(());
    for (sql, values) in &stmts {
        if let Err(err) = conn.execute(sql, params_from_iter(values.iter())) {
            result = Err(err.into());
            break;
        }
    }
    match result.and_then(|()| install_protection(&conn, &cols)) {
        Ok(()) => {
            finish(conn, db);
            Ok(())
        }
        Err(err) => {
            // Never swallow: without the triggers the vendor app could
            // overwrite local configs again, and the caller must know.
            let _ = install_protection(&conn, &cols);
            drop(conn);
            chown_sidecars(db);
            Err(err)
        }
    }
}

/// Restore every database up to and including the failing one to its captured
/// row (P2-7). Returns `(errors for databases that had already been written,
/// error for the failing database)` — reported apart, because a failing
/// database usually never changed at all (e.g. it could not be opened).
fn rollback_dbs(
    dbs: &[PathBuf],
    originals: &[BeforeRow],
    failed_index: usize,
) -> (Vec<String>, Option<String>) {
    let mut written_errors = Vec::new();
    let mut failed_error = None;
    for (index, (db, row)) in dbs.iter().zip(originals).enumerate() {
        if index > failed_index {
            break;
        }
        if let Err(err) = restore_row(db, row) {
            let text = format!("{}: {}", db.display(), shorten(&err.to_string()));
            if index == failed_index {
                failed_error = Some(text);
            } else {
                written_errors.push(text);
            }
        }
        chown_sidecars(db);
    }
    (written_errors, failed_error)
}

/// Turn a failed dual-DB mutation plus its rollback outcome into the one error
/// the CLI reports — 铁律 1: a partial success is never reported as success.
fn rollback_report(
    failure: &str,
    written_errors: &[String],
    failed_error: Option<&String>,
) -> Box<dyn Error> {
    if !written_errors.is_empty() {
        return format!(
            "{failure}; 回滚未完全成功（双库可能不一致，请立即用备份恢复）: {}",
            written_errors.join("; ")
        )
        .into();
    }
    match failed_error {
        None => format!("{failure}; 已回滚全部数据库，未做任何改动").into(),
        Some(err) => format!(
            "{failure}; 已回滚写入成功的数据库；失败库自身回滚未能确认（{err}），请校验该库后重试"
        )
        .into(),
    }
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
    delete_dbs(&dbs, package)
}

/// Dual-DB delete with all-or-nothing semantics (P2-7): rows are captured
/// before the first deletion and every database is restored on failure, so
/// one package can never end up deleted in one cosa copy only.
fn delete_dbs(dbs: &[PathBuf], package: &str) -> Result<bool> {
    let mut originals = Vec::with_capacity(dbs.len());
    for db in dbs {
        chown_sidecars(db);
        match capture_row(db, package) {
            Ok(before) => originals.push(before),
            Err(err) => {
                return Err(format!("capture failed on {}: {}", db.display(), err).into())
            }
        }
    }

    for (index, db) in dbs.iter().enumerate() {
        if let Err(err) = delete_one(db, package) {
            let failure = format!("delete failed on {}: {}", db.display(), err);
            chown_sidecars(db);
            let (written_errors, failed_error) = rollback_dbs(dbs, &originals, index);
            return Err(rollback_report(&failure, &written_errors, failed_error.as_ref()));
        }
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
     joyose 子命令一律带 joyose- 前缀（与 argv[1] 完全一致，照抄可执行）:\n\
     cosa joyose-stat [备份根目录] | joyose-list | joyose-read <配置名> | joyose-write <配置名> <json文件>\n\
     cosa joyose-freeze [备份根目录] | joyose-unfreeze [备份根目录] | joyose-backup <备份根目录> [标签]\n\
     cosa joyose-backup-list <备份根目录> | joyose-revert <备份根目录> <名称>\n\
     cosa joyose-apps | joyose-app <包名> [booster_params.json] [common_params.json]\n\
     cosa joyose-device-caps | joyose-migt-write <完整条目串> | joyose-migt-remove <包名>\n\
     cosa joyose-purge-dead | joyose-scoped <包名> [booster_params.json] [common_params.json]\n\
     cosa joyose-scoped-write <包名> <作用域json文件>"
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
        Some("joyose-freeze") => joyose::store::cmd_freeze(args.get(2)),
        Some("joyose-unfreeze") => joyose::store::cmd_unfreeze(args.get(2)),
        Some("joyose-backup") => joyose::store::cmd_backup(args.get(2), args.get(3)),
        Some("joyose-backup-list") => joyose::store::cmd_backup_list(args.get(2)),
        Some("joyose-revert") => joyose::store::cmd_revert(args.get(2), args.get(3)),
        Some("joyose-apps") => joyose::store::cmd_apps(),
        Some("joyose-purge-dead") => joyose::store::cmd_purge_dead(),
        Some("joyose-app") => {
            joyose::appview::cmd_app_view(args.get(2), args.get(3), args.get(4))
        }
        Some("joyose-device-caps") => joyose::caps::cmd_device_caps(),
        Some("joyose-migt-write") => joyose::migt::cmd_migt_write(args.get(2)),
        Some("joyose-migt-remove") => joyose::migt::cmd_migt_remove(args.get(2)),
        Some("joyose-scoped") => {
            joyose::scoped::cmd_scoped_read(args.get(2), args.get(3), args.get(4))
        }
        Some("joyose-scoped-write") => {
            joyose::scoped::cmd_scoped_write(args.get(2), args.get(3))
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

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_db(tag: &str) -> PathBuf {
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0);
        let dir = std::env::temp_dir().join(format!(
            "cosa-p27-{}-{tag}-{}",
            std::process::id(),
            nanos
        ));
        fs::create_dir_all(&dir).unwrap();
        dir.join("db_game_database")
    }

    /// `extra` appends column definitions; `rows` are (package, config,
    /// from_server) tuples inserted verbatim.
    fn make_db(path: &Path, extra: &str, rows: &[(&str, &str, i64)]) {
        let conn = Connection::open(path).unwrap();
        conn.execute_batch(&format!(
            "CREATE TABLE {TABLE} (\
               id INTEGER PRIMARY KEY, package_name TEXT, config TEXT, from_server INTEGER{extra}\
             );"
        ))
        .unwrap();
        for (package, config, from_server) in rows {
            conn.execute(
                &format!(
                    "INSERT INTO {TABLE} (package_name, config, from_server) VALUES (?1, ?2, ?3)"
                ),
                rusqlite::params![package, config, from_server],
            )
            .unwrap();
        }
        drop(conn);
    }

    fn read_back(path: &Path, package: &str) -> Option<String> {
        let conn = open_cosa_ro(path).unwrap();
        conn.query_row(
            &format!("SELECT config FROM {TABLE} WHERE package_name = ?1"),
            [package],
            |r| r.get::<_, String>(0),
        )
        .ok()
    }

    fn object(config: &str) -> Map<String, Value> {
        serde_json::from_str::<Value>(&format!(r#"{{"config":"{config}"}}"#))
            .unwrap()
            .as_object()
            .unwrap()
            .clone()
    }

    #[test]
    fn write_dbs_updates_every_database_on_success() {
        let first = temp_db("ok-a");
        let second = temp_db("ok-b");
        make_db(&first, "", &[("com.x", "old", 1)]);
        make_db(&second, "", &[("com.x", "old", 1)]);
        write_dbs(&[first.clone(), second.clone()], "com.x", &object("new")).unwrap();
        assert_eq!(read_back(&first, "com.x").as_deref(), Some("new"));
        assert_eq!(read_back(&second, "com.x").as_deref(), Some("new"));
        let _ = fs::remove_dir_all(first.parent().unwrap());
        let _ = fs::remove_dir_all(second.parent().unwrap());
    }

    /// P2-7：第二个库写失败时，第一个库必须回到写入前的行（不该变的没变）。
    #[test]
    fn write_dbs_rolls_back_the_written_database_on_second_failure() {
        let first = temp_db("rb-a");
        let second = temp_db("rb-b");
        make_db(&first, "", &[("com.x", "old", 1)]);
        // 无该包行 + NOT NULL 列 → write_one 的 INSERT 必然失败。
        make_db(&second, ", mandatory TEXT NOT NULL", &[]);
        let err = write_dbs(&[first.clone(), second.clone()], "com.x", &object("new"))
            .err()
            .expect("必须报错")
            .to_string();
        assert!(err.contains("write failed on"), "{err}");
        assert!(err.contains("回滚"), "{err}");
        assert_eq!(
            read_back(&first, "com.x").as_deref(),
            Some("old"),
            "已写入的库必须被回滚"
        );
        assert_eq!(read_back(&second, "com.x"), None, "失败库不得留下半写条目");
        let _ = fs::remove_dir_all(first.parent().unwrap());
        let _ = fs::remove_dir_all(second.parent().unwrap());
    }

    /// 捕获阶段就失败时，一个库都不许被改动。
    #[test]
    fn write_dbs_aborts_before_touching_anything_when_capture_fails() {
        let first = temp_db("cap-a");
        let second = temp_db("cap-b");
        make_db(&first, "", &[("com.x", "old", 1)]);
        fs::write(&second, b"not a sqlite database at all").unwrap();
        let err = write_dbs(&[first.clone(), second.clone()], "com.x", &object("new"))
            .err()
            .expect("必须报错")
            .to_string();
        assert!(err.contains("capture failed"), "{err}");
        assert_eq!(read_back(&first, "com.x").as_deref(), Some("old"));
        let _ = fs::remove_dir_all(first.parent().unwrap());
        let _ = fs::remove_dir_all(second.parent().unwrap());
    }

    /// 回滚必须能覆盖保护触发器（本地行 from_server=0 会被 RAISE(IGNORE) 拦住）。
    #[test]
    fn restore_row_put_back_a_protected_local_row() {
        let db = temp_db("trig-a");
        make_db(&db, "", &[("com.x", "old", 0)]);
        let before = capture_row(&db, "com.x").unwrap();
        write_one(&db, "com.x", &object("new"), &mut BTreeSet::new()).unwrap();
        assert_eq!(read_back(&db, "com.x").as_deref(), Some("new"));
        restore_row(&db, &before).unwrap();
        assert_eq!(read_back(&db, "com.x").as_deref(), Some("old"));
        let _ = fs::remove_dir_all(db.parent().unwrap());
    }

    /// P2-7：删除路径同样全有或全无。
    #[test]
    fn delete_dbs_restores_the_row_when_a_later_database_fails() {
        let first = temp_db("del-a");
        let second = temp_db("del-b");
        make_db(&first, "", &[("com.x", "old", 0)]);
        make_db(&second, "", &[("com.x", "old", 0)]);
        // 第二个库只读 → delete_one 打不开写事务。
        fs::set_permissions(&second, fs::Permissions::from_mode(0o444)).unwrap();
        let err = delete_dbs(&[first.clone(), second.clone()], "com.x")
            .err()
            .expect("必须报错")
            .to_string();
        assert!(err.contains("delete failed on"), "{err}");
        assert_eq!(
            read_back(&first, "com.x").as_deref(),
            Some("old"),
            "已删除的库必须被恢复"
        );
        let _ = fs::set_permissions(&second, fs::Permissions::from_mode(0o644));
        let _ = fs::remove_dir_all(first.parent().unwrap());
        let _ = fs::remove_dir_all(second.parent().unwrap());
    }

    /// P2-20：usage 文案里的每个子命令都必须在 main 里有分发分支，
    /// 否则照抄帮助文本必然 `Unknown command`。
    #[test]
    fn usage_lists_only_dispatched_subcommands() {
        const JOYOSE: [&str; 17] = [
            "joyose-stat",
            "joyose-list",
            "joyose-read",
            "joyose-write",
            "joyose-freeze",
            "joyose-unfreeze",
            "joyose-backup",
            "joyose-backup-list",
            "joyose-revert",
            "joyose-apps",
            "joyose-app",
            "joyose-device-caps",
            "joyose-migt-write",
            "joyose-migt-remove",
            "joyose-purge-dead",
            "joyose-scoped",
            "joyose-scoped-write",
        ];
        let source = include_str!("main.rs");
        for name in JOYOSE {
            assert!(usage().contains(name), "usage() 未列出 {name}");
            assert!(
                source.contains(&format!("Some(\"{name}\") =>")),
                "{name} 在 usage() 里有、在分发里没有"
            );
        }
        // 旧的裸名写法不得复活（`joyose: stat | list` 那类）。
        assert!(!usage().contains("joyose: stat"), "usage() 仍是旧的裸子命令文案");
        for bare in [" stat |", " freeze |"] {
            assert!(!usage().contains(bare), "usage() 含裸子命令名 {bare:?}");
        }
    }

    fn export_dir(tag: &str) -> PathBuf {
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0);
        let dir = std::env::temp_dir().join(format!(
            "cosa-p23-{}-{tag}-{}",
            std::process::id(),
            nanos
        ));
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn mode_of(path: &Path) -> u32 {
        fs::metadata(path).unwrap().permissions().mode() & 0o777
    }

    /// P2-3：不存在的目标按 0o644 创建（umask 不得把组/其他位的读权限削掉）。
    #[test]
    fn write_export_creates_file_readable_by_content_provider() {
        let dir = export_dir("create");
        let target = dir.join("nested/export.json");
        write_export(&target, "{\"a\":1}").unwrap();
        assert_eq!(fs::read_to_string(&target).unwrap(), "{\"a\":1}");
        assert_eq!(mode_of(&target), 0o644, "导出件必须全局可读");
        let _ = fs::remove_dir_all(&dir);
    }

    /// P2-3：重复导出到同一普通文件是正常用法，必须截断覆写而不是失败或追加。
    #[test]
    fn write_export_truncates_existing_regular_file() {
        let dir = export_dir("truncate");
        let target = dir.join("export.json");
        fs::write(&target, "aaaaaaaaaaaaaaaaaaaa").unwrap();
        write_export(&target, "short").unwrap();
        assert_eq!(fs::read_to_string(&target).unwrap(), "short");
        let _ = fs::remove_dir_all(&dir);
    }

    /// P2-3：目标是指向别处的符号链接时必须显式报错，且链接指向的文件一字未动。
    #[test]
    fn write_export_refuses_to_follow_symlink() {
        let dir = export_dir("symlink");
        let victim = dir.join("victim");
        fs::write(&victim, "keep me").unwrap();
        let link = dir.join("link");
        std::os::unix::fs::symlink(&victim, &link).unwrap();

        let err = write_export(&link, "boom").unwrap_err().to_string();
        assert!(err.contains("符号链接"), "{err}");
        assert_eq!(fs::read_to_string(&victim).unwrap(), "keep me");
        assert_eq!(fs::read_to_string(&link).unwrap(), "keep me");
        let _ = fs::remove_dir_all(&dir);
    }
}
