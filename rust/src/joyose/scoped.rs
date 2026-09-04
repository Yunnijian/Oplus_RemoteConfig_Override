//! Per-app scoped fragment extraction and surgical write-back.
//!
//! The advanced editor shows one package's cloud-control fragments as a flat
//! `{ "<JSON Pointer>": <fragment> }` document.  Splitting and merging happen
//! here so the app process never loads, parses or re-serializes the whole
//! booster_config: it receives a few KB of pretty JSON and hands back the same
//! shape.  Saving patches only those pointers inside the document read fresh
//! from the DB, so untouched apps and global switches keep their exact values
//! (`serde_json` member order is pinned by the `preserve_order` feature).
//!
//! Every failure mode here is an error or a reported list — never a silent
//! no-op.  A pointer that does not resolve, a key the user renamed, and a key
//! the user deleted are three different messages.

use std::fs;

use serde_json::{json, Map, Value};

use super::appview::{collect, load_doc};
use super::store;

type CliResult<T> = Result<T, Box<dyn std::error::Error>>;

fn type_of(value: &Value) -> &'static str {
    match value {
        Value::Null => "null",
        Value::Bool(_) => "布尔",
        Value::Number(_) => "数字",
        Value::String(_) => "字符串",
        Value::Array(_) => "数组",
        Value::Object(_) => "对象",
    }
}

/// RFC 6901 unescaping: `~1` becomes `/` first, then `~0` becomes `~`.
fn unescape(segment: &str) -> String {
    segment.replace("~1", "/").replace("~0", "~")
}

/// Split a pointer into unescaped reference tokens.
fn segments(pointer: &str) -> Result<Vec<String>, String> {
    let Some(rest) = pointer.strip_prefix('/') else {
        return Err(format!("指针必须以 / 开头：{pointer}"));
    };
    if rest.is_empty() {
        return Err(format!("指针为空：{pointer}"));
    }
    Ok(rest.split('/').map(unescape).collect())
}

/// Resolve a pointer, with the failing prefix in the message.
pub fn get<'a>(root: &'a Value, pointer: &str) -> Result<&'a Value, String> {
    let mut cur = root;
    let mut walked = String::new();
    for seg in segments(pointer)? {
        walked.push('/');
        walked.push_str(&seg);
        cur = match cur {
            Value::Object(map) => {
                map.get(&seg)
                    .ok_or_else(|| format!("{walked} 不存在"))?
            }
            Value::Array(list) => {
                let idx = seg
                    .parse::<usize>()
                    .map_err(|_| format!("{walked} 不是数组下标"))?;
                list.get(idx)
                    .ok_or_else(|| format!("{walked} 下标越界（长度 {}）", list.len()))?
            }
            other => return Err(format!("{walked} 的父节点不是容器（{}）", type_of(other))),
        };
    }
    Ok(cur)
}

/// Build the scoped document for `pointers`, keeping their order.
///
/// Returns the document plus one entry per pointer that no longer resolves;
/// callers surface those instead of dropping them quietly.
pub fn extract(root: &Value, pointers: &[String]) -> (Value, Vec<String>) {
    let mut doc = Map::new();
    let mut skipped = Vec::new();
    for pointer in pointers {
        match get(root, pointer) {
            Ok(value) => {
                doc.insert(pointer.clone(), value.clone());
            }
            Err(reason) => skipped.push(format!("{pointer}：{reason}")),
        }
    }
    (Value::Object(doc), skipped)
}

/// Replace the value at `pointer` in place.
fn set(root: &mut Value, pointer: &str, value: Value) -> Result<(), String> {
    let segs = segments(pointer)?;
    let mut cur = root;
    for seg in &segs[..segs.len() - 1] {
        cur = match cur {
            Value::Object(map) => map
                .get_mut(seg)
                .ok_or_else(|| format!("父节点 /{seg} 不存在"))?,
            Value::Array(list) => {
                let idx = seg
                    .parse::<usize>()
                    .map_err(|_| format!("父节点 /{seg} 不是数组下标"))?;
                list.get_mut(idx)
                    .ok_or_else(|| format!("父节点 /{seg} 下标越界"))?
            }
            other => return Err(format!("父节点不是容器（{}）", type_of(other))),
        };
    }
    let last = &segs[segs.len() - 1];
    match cur {
        Value::Object(map) => {
            if !map.contains_key(last) {
                return Err(format!("{last} 不存在：作用域编辑只能改已有片段，不能新增或改名"));
            }
            map.insert(last.clone(), value);
            Ok(())
        }
        Value::Array(list) => {
            let idx = last
                .parse::<usize>()
                .map_err(|_| format!("{last} 不是数组下标"))?;
            let len = list.len();
            let slot = list
                .get_mut(idx)
                .ok_or_else(|| format!("{last} 下标越界（长度 {len}）"))?;
            *slot = value;
            Ok(())
        }
        other => Err(format!("目标不是容器（{}）", type_of(other))),
    }
}

/// Patch every member of `scoped` into `root`.  Returns how many were written.
pub fn merge(root: &mut Value, scoped: &Value) -> Result<usize, String> {
    let Value::Object(entries) = scoped else {
        return Err("作用域文档顶层必须是对象".into());
    };
    let mut written = 0;
    for (pointer, value) in entries {
        set(root, pointer, value.clone())
            .map_err(|reason| format!("{pointer}：{reason}"))?;
        written += 1;
    }
    Ok(written)
}

/// The pointer set the editor was built from; the write path validates
/// against a freshly collected one so a renamed key cannot pass as new.
/// Read-only membership hits (`path: None`, see P2-4 in appview.rs) are not
/// part of the editable set.
fn feature_pointers(params: &Value, package: &str, common: Option<&Value>) -> Result<Vec<String>, String> {
    Ok(collect(params, package, common)?
        .features
        .iter()
        .filter_map(|f| f.path.clone())
        .collect())
}

fn scoped_doc_and_skipped(
    params: Option<&String>,
    common_path: Option<&String>,
    package: &str,
) -> CliResult<(Value, Value, Vec<String>)> {
    let booster = load_doc(params, "booster_config")?;
    let common = match common_path {
        Some(path) => Some(load_doc(Some(path), "common_config")?),
        None => load_doc(None, "common_config").ok(),
    };
    let pointers = feature_pointers(&booster, package, common.as_ref())
        .map_err(|e| -> Box<dyn std::error::Error> { e.into() })?;
    let (document, skipped) = extract(&booster, &pointers);
    Ok((booster, document, skipped))
}

/// `joyose-scoped <pkg> [booster_params.json] [common_params.json]` — the
/// fragments owned by one package, as `{ pointer: fragment }`.
pub fn cmd_scoped_read(
    package: Option<&String>,
    booster_path: Option<&String>,
    common_path: Option<&String>,
) -> CliResult<bool> {
    let Some(package) = package else {
        return Err("缺少包名".into());
    };
    let (_, document, skipped) = scoped_doc_and_skipped(booster_path, common_path, package)?;
    println!(
        "{}",
        serde_json::to_string_pretty(&json!({
            "ok": true,
            "package": package,
            "document": document,
            "skipped": skipped,
        }))?
    );
    Ok(true)
}

/// `joyose-scoped-write <pkg> <scoped.json>` — patch the edited fragments into
/// the document currently stored in the DB, then mirror-write it.
pub fn cmd_scoped_write(package: Option<&String>, path: Option<&String>) -> CliResult<bool> {
    let Some(package) = package else {
        return Err("缺少包名".into());
    };
    let Some(path) = path else {
        return Err("缺少 JSON 文件路径".into());
    };
    let text = fs::read_to_string(path).map_err(|e| format!("读取 {path} 失败: {e}"))?;
    let edited: Value =
        serde_json::from_str(&text).map_err(|e| format!("作用域文档 JSON 解析失败: {e}"))?;
    let Value::Object(entries) = &edited else {
        return Err("作用域文档顶层必须是对象".into());
    };

    // Read the document again at save time: the fragments must land in the
    // current document, not in the one the editor was opened with.
    let booster = store::read_params_any("booster_config")?.1;
    let common = store::read_params_any("common_config")
        .ok()
        .map(|(_, value)| value);
    let pointers = feature_pointers(&booster, package, common.as_ref())
        .map_err(|e| -> Box<dyn std::error::Error> { e.into() })?;

    let unknown: Vec<&String> = entries
        .keys()
        .filter(|key| !pointers.iter().any(|p| p == *key))
        .collect();
    if !unknown.is_empty() {
        return Err(format!(
            "以下键不是该应用现有的云控片段（不能新增或改名）：{}",
            unknown
                .iter()
                .map(|p| p.as_str())
                .collect::<Vec<_>>()
                .join("、")
        )
        .into());
    }
    let removed: Vec<&String> = pointers
        .iter()
        .filter(|pointer| !entries.contains_key(pointer.as_str()))
        .collect();
    if !removed.is_empty() {
        return Err(format!(
            "以下片段被删除，作用域编辑不支持删除片段：{}",
            removed
                .iter()
                .map(|p| p.as_str())
                .collect::<Vec<_>>()
                .join("、")
        )
        .into());
    }

    let mut merged = booster;
    let written = merge(&mut merged, &edited)?;
    let mut report = store::write_document("booster_config", merged)?;
    report["written"] = json!(written);
    println!("{}", serde_json::to_string_pretty(&report)?);
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::joyose::appview::tests::doc;
    use serde_json::json;

    fn pointers(view: &Value) -> Vec<String> {
        view["features"]
            .as_array()
            .expect("features 应为数组")
            .iter()
            .filter_map(|f| f["path"].as_str())
            .map(str::to_owned)
            .collect()
    }

    /// Guards the `{{i}}` class of bug at the source: every pointer the
    /// collector emits must resolve inside the very document it scanned.
    #[test]
    fn every_collected_path_resolves() {
        let params = doc();
        let common = json!({"game_list": [], "support_app": []});
        for package in [
            "com.a.sgame",
            "com.a.sgamece",
            "com.miHoYo.Yuanshen",
            "com.nope.missing",
        ] {
            let view = collect(&params, package, Some(&common))
                .unwrap()
                .to_json();
            let (_, skipped) = extract(&params, &pointers(&view));
            assert!(skipped.is_empty(), "{package} 有未解析指针: {skipped:?}");
        }
    }

    /// P2-4：只读成员类（黑名单/整表类别）不进入可编辑指针集；
    /// 拿容器指针来写必须被拒绝。
    #[test]
    fn container_pointers_are_not_editable() {
        let mut params = doc();
        params["game_booster"]["novatek_black_app"] = json!(["com.a.sgame"]);
        let view = collect(&params, "com.a.sgame", None).unwrap().to_json();
        let list = pointers(&view);
        assert!(
            !list.iter().any(|p| p.contains("novatek_black_app")),
            "黑名单容器指针泄漏进编辑集: {list:?}"
        );
        assert!(
            !list.iter().any(|p| p.ends_with("game_scenario_control_config")),
            "场景控制整表指针泄漏进编辑集: {list:?}"
        );
        // 编辑器外部伪造容器指针时，写入入口的指针集校验必须挡下它
        // （feature_pointers 不再包含该指针 → "不是该应用现有的云控片段"）。
        let fresh = collect(&params, "com.a.sgame", None).unwrap();
        let editable: Vec<String> = fresh
            .features
            .iter()
            .filter_map(|f| f.path.clone())
            .collect();
        assert!(
            !editable
                .iter()
                .any(|p| p == "/game_booster/novatek_black_app"),
            "伪造的容器指针不应出现在校验集里: {editable:?}"
        );
    }

    #[test]
    fn scoped_round_trip_is_value_preserving() {
        let params = doc();
        let view = collect(&params, "com.a.sgame", None).unwrap().to_json();
        let list = pointers(&view);
        let (scoped, skipped) = extract(&params, &list);
        assert!(skipped.is_empty());
        let mut merged = params.clone();
        assert_eq!(merge(&mut merged, &scoped).unwrap(), list.len());
        assert_eq!(merged, params, "原样回写必须逐字节等价");
    }

    #[test]
    fn array_index_terminated_pointer_is_replaced() {
        let mut params = json!({"game_booster": {"booster_config": {"ovrride_config": [
            {"game_name": "A", "dynamic_fps": "10:0"},
            {"game_name": "B", "dynamic_fps": "20:0"},
        ]}}});
        let pointer = "/game_booster/booster_config/ovrride_config/1";
        let scoped = json!({pointer: {"game_name": "B", "dynamic_fps": "30:0"}});
        assert_eq!(merge(&mut params, &scoped).unwrap(), 1);
        assert_eq!(
            get(&params, pointer).unwrap()["dynamic_fps"].as_str(),
            Some("30:0")
        );
        // The sibling entry must be untouched.
        assert_eq!(
            get(&params, "/game_booster/booster_config/ovrride_config/0").unwrap()["dynamic_fps"]
                .as_str(),
            Some("10:0")
        );
    }

    #[test]
    fn object_key_pointer_is_replaced() {
        let mut params = json!({"game_booster": {"self_gpu_tuner_config": {
            "com.a.sgame": {"STANDARD": 1}
        }}});
        let pointer = "/game_booster/self_gpu_tuner_config/com.a.sgame";
        let scoped = json!({pointer: {"STANDARD": 2}});
        assert_eq!(merge(&mut params, &scoped).unwrap(), 1);
        assert_eq!(get(&params, pointer).unwrap()["STANDARD"].as_i64(), Some(2));
    }

    #[test]
    fn renamed_or_invented_key_is_rejected() {
        let mut params = json!({"game_booster": {"novatek_black_app": ["com.a.sgame"]}});
        let err = merge(&mut params, &json!({"/game_booster/novatek_white_app": []}))
            .unwrap_err();
        assert!(err.contains("不能新增或改名"), "{err}");
        assert_eq!(
            params,
            json!({"game_booster": {"novatek_black_app": ["com.a.sgame"]}}),
            "失败的回写不得改动文档"
        );
    }

    #[test]
    fn out_of_range_and_non_container_are_rejected() {
        let mut params = json!({"game_booster": {"a": [{"b": 1}]}});
        assert!(merge(&mut params, &json!({"/game_booster/a/9": {}})).is_err());
        assert!(merge(&mut params, &json!({"/game_booster/a/0/b/c": 1})).is_err());
        assert!(merge(&mut params, &json!({"/game_booster/a/x": {}})).is_err());
    }

    #[test]
    fn pointer_escaping_matches_rfc6901() {
        let params = json!({"a": {"b/c~d": 1}});
        assert_eq!(get(&params, "/a/b~1c~0d").unwrap(), &json!(1));
        assert!(get(&params, "/a/b/c").is_err());
    }
}
