//! migt membership + parameter-pack management on the `game_booster.migt`
//! string array (see the spec, chapter 0.1).
//!
//! The array elements are `pkg;[migt_freq;migt_ms;fps:thresh;boost_policy;
//! fps_variance_ratio;super_task_max_num;migt_ceiling_freq]` strings —
//! structural add/remove that the scoped editor intentionally rejects.
//! These two commands perform the guarded membership edit and hand the
//! patched document to the same mirror-write path as everything else.

use serde_json::{json, Value};

use super::store;
use std::error::Error;

type CliResult<T> = Result<T, Box<dyn Error>>;

/// Maximum segments observed on real devices (pkg + 7 numeric fields);
/// anything beyond that is a malformed entry.
const MAX_SEGMENTS: usize = 8;

/// Validate one migt entry string. Returns the package (segment 0).
fn validate_entry(raw: &str) -> Result<String, String> {
    let segments: Vec<&str> = raw.split(';').collect();
    if segments.len() < 2 {
        return Err("migt 条目至少需要 包名;migt_freq 两段".into());
    }
    if segments.len() > MAX_SEGMENTS {
        return Err(format!(
            "migt 条目最多 {} 段（实际 {} 段）",
            MAX_SEGMENTS,
            segments.len()
        ));
    }
    let pkg = segments[0].trim();
    if pkg.is_empty() || pkg.contains(|c: char| c.is_whitespace()) {
        return Err("migt 条目首段必须是包名".into());
    }
    for (i, seg) in segments.iter().enumerate().skip(1) {
        if seg.trim().is_empty() {
            return Err(format!("migt 条目第 {i} 段为空"));
        }
    }
    // Segment 1 (and 7 when present) must be a `cpu:freq` mapping table.
    for idx in [1usize, 7usize] {
        if let Some(seg) = segments.get(idx) {
            let mut pairs = 0;
            for pair in seg.split_whitespace() {
                let mut it = pair.splitn(2, ':');
                let cpu = it.next().unwrap_or_default();
                let freq = it.next().unwrap_or_default();
                if cpu.parse::<u32>().is_err() || freq.parse::<i64>().is_err() {
                    return Err(format!("migt 第 {idx} 段不是 cpu:频率 映射表：{pair}"));
                }
                pairs += 1;
            }
            if pairs == 0 {
                return Err(format!("migt 第 {idx} 段为空映射表"));
            }
        }
    }
    // Segment 3 when present must be a `fps:thresh` table.
    if let Some(seg) = segments.get(3) {
        let mut pairs = 0;
        for pair in seg.split_whitespace() {
            let mut it = pair.splitn(2, ':');
            let fps = it.next().unwrap_or_default();
            let thresh = it.next().unwrap_or_default();
            if fps.parse::<i64>().is_err() || thresh.parse::<i64>().is_err() {
                return Err(format!("migt 第 3 段不是 fps:阈值 映射表：{pair}"));
            }
            pairs += 1;
        }
        if pairs == 0 {
            return Err("migt 第 3 段为空映射表".into());
        }
    }
    // Scalar segments 2/4/5/6 must be numeric when present.
    for idx in [2usize, 4usize, 5usize, 6usize] {
        if let Some(seg) = segments.get(idx) {
            if seg.parse::<i64>().is_err() {
                return Err(format!("migt 第 {idx} 段必须是数字（实际 {seg}）"));
            }
        }
    }
    Ok(pkg.to_owned())
}

fn migt_array_mut<'a>(booster: &'a mut Value) -> Result<&'a mut Vec<Value>, String> {
    let gb = booster
        .get_mut("game_booster")
        .and_then(Value::as_object_mut)
        .ok_or("缺少 game_booster 对象")?;
    let slot = gb
        .get_mut("migt")
        .ok_or("缺少 game_booster.migt 数组（该云控版本无 migt 配置）")?;
    slot.as_array_mut()
        .ok_or_else(|| "game_booster.migt 不是数组".to_string())
}

/// Drop every entry whose package matches; returns how many were removed.
fn drop_entries(list: &mut Vec<Value>, pkg: &str) -> usize {
    let before = list.len();
    let prefix = format!("{pkg};");
    list.retain(|v| {
        v.as_str()
            .map(|s| !s.starts_with(&prefix))
            .unwrap_or(true)
    });
    before - list.len()
}

/// `joyose-migt-write <完整条目串>` — replace (or append) the entry for the
/// package named in segment 0, leaving every other game untouched.
pub fn cmd_migt_write(entry: Option<&String>) -> CliResult<bool> {
    let Some(entry) = entry else {
        return Err("缺少 migt 条目串".into());
    };
    let entry = entry.trim();
    let pkg = validate_entry(entry).map_err(|e| -> Box<dyn Error> { e.into() })?;
    let (name, mut booster) = store::read_params_any("booster_config")?;
    let list = migt_array_mut(&mut booster).map_err(|e| -> Box<dyn Error> { e.into() })?;
    let removed = drop_entries(list, &pkg);
    list.push(Value::String(entry.to_owned()));
    let mut report = store::write_document(&name, booster)?;
    report["replaced"] = json!(removed);
    println!("{}", serde_json::to_string_pretty(&report)?);
    Ok(true)
}

/// `joyose-migt-remove <包名>` — drop the package's entry (if any).
pub fn cmd_migt_remove(package: Option<&String>) -> CliResult<bool> {
    let Some(pkg) = package.map(String::as_str) else {
        return Err("缺少包名".into());
    };
    if pkg.is_empty() || pkg.contains(|c: char| c.is_whitespace()) {
        return Err("非法包名".into());
    }
    let (name, mut booster) = store::read_params_any("booster_config")?;
    let list = migt_array_mut(&mut booster).map_err(|e| -> Box<dyn Error> { e.into() })?;
    let removed = drop_entries(list, pkg);
    let mut report = store::write_document(&name, booster)?;
    report["removed"] = json!(removed);
    println!("{}", serde_json::to_string_pretty(&report)?);
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = "com.tencent.ig;0:384000 1:384000 6:1017600;30;90:18 60:20;2;10;1;0:0 1:0";

    #[test]
    fn valid_entry_yields_package() {
        assert_eq!(validate_entry(SAMPLE).unwrap(), "com.tencent.ig");
    }

    #[test]
    fn minimal_two_segment_entry_is_valid() {
        assert_eq!(
            validate_entry("com.a.b;0:384000 1:384000").unwrap(),
            "com.a.b"
        );
    }

    #[test]
    fn non_numeric_scalar_segment_is_rejected() {
        assert!(validate_entry("com.a.b;0:384000;abc").is_err());
    }

    #[test]
    fn bad_mapping_table_is_rejected() {
        assert!(validate_entry("com.a.b;junk").is_err());
        assert!(validate_entry("com.a.b;0:384000;30;x:y").is_err());
    }

    #[test]
    fn too_many_segments_is_rejected() {
        let mut raw = String::from("com.a.b");
        for _ in 0..8 {
            raw.push_str(";1");
        }
        assert!(validate_entry(&raw).is_err());
    }

    #[test]
    fn drop_entries_removes_only_matching_package() {
        let mut list: Vec<Value> = vec![
            "com.a;0:384000".into(),
            "com.a.mi;0:384000".into(),
            "com.b;0:384000".into(),
        ];
        assert_eq!(drop_entries(&mut list, "com.a"), 1);
        assert_eq!(list.len(), 2);
        assert_eq!(
            list.iter().filter_map(Value::as_str).collect::<Vec<_>>(),
            vec!["com.a.mi;0:384000", "com.b;0:384000"]
        );
    }
}
