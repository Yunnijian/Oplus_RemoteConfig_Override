//! Per-app aggregation view over a Joyose cloud config document.
//!
//! `booster_config.params` is organised *by feature*: each feature category
//! stores its per-app entries in its own shape (object arrays keyed by
//! `game_name` — which may be a group alias —, plain package arrays, token
//! strings delimited by `_ : @ ;`, alias+cmdlines objects, package-keyed
//! maps). [`collect`] walks every known structure, joins it against the
//! package's alias set (see [`super::resolve`]) and produces one
//! [`AppView`] listing every feature hit with its concrete parameters.
//!
//! Override semantics represented in the output:
//! - `dynamic_fps` / `dynamic_fps_M` on a per-game `ovrride_config` entry
//!   override the `dynamic_fps_global` baseline → reported in `overrides`.
//! - Multiple matching `ovrride_config` entries are *not* silently merged;
//!   all of them are reported and their paths collected in `conflicts`.
//! - `scene_ovrride` is the third (per-scene) layer inside a game entry and
//!   is flattened into per-scene params.

use crate::joyose::resolve::{alias_set, token_matches};
use serde_json::{json, Map, Value};

// ── model ───────────────────────────────────────────────────────────────────

/// One concrete config item inside a feature hit.
pub struct Param {
    pub name: String,
    pub value: Value,
}

/// Where a hit's key comes from.
pub enum Source {
    /// Key equals the package name itself.
    Direct,
    /// Key is a group alias resolved through `game_group_mapping_config`.
    Group,
    /// Wildcard/fallback entry (e.g. fisr `game_list` containing "OTHER").
    Fallback,
}

/// Master switch state gating a feature hit.
pub struct Gate {
    pub key: String,
    pub enabled: bool,
}

/// One feature category touching the package.
pub struct FeatureHit {
    pub category: &'static str,
    pub label: &'static str,
    pub source: Source,
    /// Raw key as found in the cloud config (package, alias or token head).
    pub key: String,
    /// RFC 6901 JSON Pointer into the booster_config params document, e.g.
    /// `/game_booster/booster_config/ovrride_config/3`.  `joyose-scoped`
    /// resolves it to extract and write back exactly this fragment.
    pub path: String,
    pub params: Vec<Param>,
    /// Baseline entries overridden by this hit's values, if any.
    pub overrides: Vec<String>,
    /// Global boolean master switch gating this feature, when one exists.
    pub gate: Option<Gate>,
}

/// Aggregated per-app view across every feature category.
pub struct AppView {
    pub package: String,
    pub group: Option<String>,
    pub in_game_list: Option<bool>,
    pub in_support_app: Option<bool>,
    /// Every top-level boolean switch of `game_booster` (plus the nested
    /// `mivk_settings.enable` / `migl_settings.enable`), in document order.
    pub global_switches: Vec<Param>,
    pub features: Vec<FeatureHit>,
    pub conflicts: Vec<String>,
}

// ── helpers ─────────────────────────────────────────────────────────────────

fn param(name: impl Into<String>, value: Value) -> Param {
    Param { name: name.into(), value }
}

fn get<'a>(v: &'a Value, key: &str) -> &'a Value {
    v.get(key).unwrap_or(&Value::Null)
}

fn arr<'a>(v: &'a Value, key: &str) -> &'a [Value] {
    const EMPTY: &[Value] = &[];
    v.get(key)
        .and_then(Value::as_array)
        .map(Vec::as_slice)
        .unwrap_or(EMPTY)
}

fn empty_map() -> Map<String, Value> {
    Map::new()
}

/// Every scalar (non object/array) field of an entry.
fn scalars(entry: &Value) -> Vec<Param> {
    entry
        .as_object()
        .map(|obj| {
            obj.iter()
                .filter(|(_, v)| !v.is_object() && !v.is_array())
                .map(|(k, v)| param(k.clone(), v.clone()))
                .collect()
        })
        .unwrap_or_default()
}

/// Flattened representation of an `ovrride_config[].scene_ovrride` array.
fn scene_params(entry: &Value) -> Vec<Param> {
    arr(entry, "scene_ovrride")
        .iter()
        .enumerate()
        .map(|(i, scene)| {
            let label = scene
                .get("scene_name")
                .and_then(Value::as_str)
                .map(str::to_owned)
                .or_else(|| {
                    scene
                        .get("scene_id")
                        .map(|id| format!("scene {id}"))
                })
                .unwrap_or_else(|| i.to_string());
            param(format!("scene_ovrride[{i}].{label}"), scene.clone())
        })
        .collect()
}

/// Raw payload of a token-string entry.
fn token_param(raw: &str) -> Vec<Param> {
    vec![param("raw", Value::String(raw.to_owned()))]
}

/// `pkg_minFps#targetFps#…` → parsed leading fps pair, when parseable.
fn novatek_params(raw: &str) -> Vec<Param> {
    let mut params = vec![param("raw", Value::String(raw.to_owned()))];
    if let Some(seg) = raw.split('_').nth(1) {
        let nums: Vec<&str> = seg.split('#').take(2).collect();
        if nums.len() == 2 {
            params.push(param("min_fps", Value::from(nums[0])));
            params.push(param("target_fps", Value::from(nums[1])));
        }
    }
    params
}

// ── package index (P1.5, single pass) ───────────────────────────────────────

/// One app in the per-app feature index.
pub struct PackageIndexEntry {
    pub package: String,
    pub group: Option<String>,
    pub features: usize,
}

/// Build the per-app feature index in a single pass over `game_booster`.
///
/// Counting rules mirror [`collect`]'s scans: group-alias entries
/// (`booster_config.ovrride_config[].game_name` referring to a
/// `game_group_mapping_config` group) expand to every member package;
/// token strings count their head token; whitelist arrays / package-keyed
/// objects / pkg fields count each mention. Global structures and the
/// common_config whitelists (`game_list`/`support_app`) are excluded —
/// membership there is not a per-app config.
pub fn package_index(gb: &Value) -> Vec<PackageIndexEntry> {
    use std::collections::HashMap;

    // group name → member packages
    let mut groups: HashMap<&str, Vec<&str>> = HashMap::new();
    for entry in arr(gb, "game_group_mapping_config") {
        let Some(name) = get(entry, "game_group_name").as_str() else {
            continue;
        };
        let list: Vec<&str> = arr(entry, "package_list")
            .iter()
            .filter_map(Value::as_str)
            .collect();
        if !list.is_empty() {
            groups.insert(name, list);
        }
    }
    let group_of = |pkg: &str| -> Option<String> {
        groups
            .iter()
            .find(|(_, list)| list.contains(&pkg))
            .map(|(g, _)| g.to_string())
    };

    let mut counts: HashMap<&str, usize> = HashMap::new();
    fn bump<'a>(counts: &mut HashMap<&'a str, usize>, key: &'a str) {
        if !key.is_empty() {
            *counts.entry(key).or_insert(0) += 1;
        }
    }

    // ① ovrride_config: group alias → expand to member packages.
    for entry in arr(get(gb, "booster_config"), "ovrride_config") {
        let Some(key) = get(entry, "game_name").as_str() else {
            continue;
        };
        match groups.get(key) {
            Some(pkgs) => {
                for pkg in pkgs {
                    bump(&mut counts, pkg);
                }
            }
            None => bump(&mut counts, key),
        }
    }

    // ② token strings: `_` `:` `@` `;` heads are package names.
    for (field, sep) in [
        ("novatek_game_params", '_'),
        ("migt", ';'),
        ("cgame_df", '@'),
        ("support_highfps_app", ':'),
        ("mqs_enhance_list", ':'),
        ("force_scale_app_list", ':'),
    ] {
        for raw in arr(gb, field) {
            if let Some(raw) = raw.as_str() {
                bump(&mut counts, raw.split(sep).next().unwrap_or(""));
            }
        }
    }
    let ext = get(gb, "novatek_extend_config");
    for raw in arr(ext, "novatek_non_playing_config") {
        if let Some(raw) = raw.as_str() {
            bump(&mut counts, raw.split('_').next().unwrap_or(""));
        }
    }
    for raw in arr(ext, "novatek_gex_fps_limit") {
        if let Some(raw) = raw.as_str() {
            bump(&mut counts, raw.split(':').next().unwrap_or(""));
        }
    }

    // ③ plain package arrays.
    for field in [
        "novatek_black_app",
        "SOC_GameList",
        "support_vrs_app",
        "support_dynamic_refresh_rate_games",
        "invalid_low_display_scenes_games",
        "game_light_support_list",
        "support_predownload_app_list",
        "support_motor_app",
        "support_scale_app_list",
    ] {
        for value in arr(gb, field) {
            if let Some(pkg) = value.as_str() {
                bump(&mut counts, pkg);
            }
        }
    }

    // ④ alias+cmdlines objects.
    for (container, inner) in [("mivk_settings", "app_params"), ("migl_settings", "game_params")] {
        for entry in arr(get(gb, container), inner) {
            for cmdline in arr(entry, "app_cmdlines")
                .iter()
                .chain(arr(entry, "game_cmdlines").iter())
            {
                if let Some(pkg) = cmdline.as_str() {
                    bump(&mut counts, pkg);
                }
            }
        }
    }

    // ⑤ fisr game_list (OTHER is a wildcard, not a package).
    for entry in arr(get(gb, "fisr_config"), "enhance_config") {
        for value in arr(entry, "game_list") {
            if let Some(pkg) = value.as_str() {
                if pkg != "OTHER" {
                    bump(&mut counts, pkg);
                }
            }
        }
    }

    // ⑥ package-keyed objects / pkg fields / package_list.
    if let Some(m) = get(gb, "self_gpu_tuner_config").as_object() {
        for key in m.keys() {
            bump(&mut counts, key);
        }
    }
    if let Some(m) = get(gb, "low_display_refresh_rate_scenes_by_single_game").as_object() {
        for key in m.keys() {
            bump(&mut counts, key);
        }
    }
    for entry in arr(gb, "support_resolution_enhance_config") {
        if let Some(pkg) = get(entry, "pkg").as_str() {
            bump(&mut counts, pkg);
        }
    }
    for value in arr(get(gb, "game_scenario_control_config"), "package_list") {
        if let Some(pkg) = value.as_str() {
            bump(&mut counts, pkg);
        }
    }

    let mut entries: Vec<PackageIndexEntry> = counts
        .into_iter()
        .map(|(pkg, features)| PackageIndexEntry {
            package: pkg.to_owned(),
            group: group_of(pkg),
            features,
        })
        .collect();
    entries.sort_unstable_by(|a, b| b.features.cmp(&a.features).then(a.package.cmp(&b.package)));
    entries
}

// ── collection ──────────────────────────────────────────────────────────────

/// Scan a booster_config params document (or its `{config_name,…,params}`
/// row wrapper) and aggregate everything that belongs to `package`.
///
/// `common` optionally carries the common_config params (from teg rules)
/// for `game_list` / `support_app` membership.
pub fn collect(
    params: &Value,
    package: &str,
    common: Option<&Value>,
) -> Result<AppView, String> {
    // Accept either bare params or the row wrapper stored in teg rules.
    let params = if params.get("game_booster").is_some() {
        params
    } else if params
        .get("params")
        .is_some_and(|p| p.get("game_booster").is_some())
    {
        get(params, "params")
    } else {
        return Err("缺少 game_booster：不是 booster_config 的 params 文档".into());
    };
    let gb = get(params, "game_booster");
    if !gb.is_object() {
        return Err("game_booster 不是对象".into());
    }

    let (aliases, group) = alias_set(gb, package);
    let mut features = Vec::new();
    let mut conflicts = Vec::new();

    // ⓪ global boolean switches (document-level master gates), in doc order.
    let mut global_switches: Vec<Param> = Vec::new();
    if let Some(obj) = gb.as_object() {
        for (k, v) in obj {
            if let Some(b) = v.as_bool() {
                global_switches.push(param(k.clone(), Value::Bool(b)));
            }
        }
    }
    for nested in ["mivk_settings", "migl_settings"] {
        if let Some(b) = get(gb, nested).get("enable").and_then(Value::as_bool) {
            global_switches.push(param(format!("{nested}.enable"), Value::Bool(b)));
        }
    }
    // Master-switch lookup for per-hit gates.
    let gate_of = |key: &str| -> Option<Gate> {
        get(gb, key)
            .as_bool()
            .map(|b| Gate { key: key.to_owned(), enabled: b })
    };
    let nested_gate = |container: &str| -> Option<Gate> {
        get(gb, container)
            .get("enable")
            .and_then(Value::as_bool)
            .map(|b| Gate { key: format!("{container}.enable"), enabled: b })
    };

    // ① per-game main entry: object array keyed by game_name (pkg or alias).
    let mut override_hits: Vec<FeatureHit> = Vec::new();
    for (i, entry) in arr(get(gb, "booster_config"), "ovrride_config")
        .iter()
        .enumerate()
    {
        let Some(key) = get(entry, "game_name").as_str() else {
            continue;
        };
        if !aliases.iter().any(|a| a == key) {
            continue;
        }
        let source = if key == package { Source::Direct } else { Source::Group };
        let mut overrides = Vec::new();
        let global = get(gb, "dynamic_fps_global");
        for base in ["dynamic_fps", "dynamic_fps_M"] {
            if entry.get(base).is_some() && global.get(base).is_some() {
                overrides.push(format!("{base} → 覆盖 dynamic_fps_global.{base}"));
            }
        }
        let mut params = scalars(entry);
        params.extend(scene_params(entry));
        override_hits.push(FeatureHit {
            category: "booster_override",
            label: "游戏主配置（锁帧/温度/场景/perflock）",
            source,
            key: key.to_owned(),
            path: format!("/game_booster/booster_config/ovrride_config/{i}"),
            params,
            overrides,
            gate: gate_of("booster_enable"),
        });
    }
    if override_hits.len() > 1 {
        conflicts.extend(override_hits.iter().map(|hit| hit.path.clone()));
    }
    features.extend(override_hits);

    // ② novatek (Redmi discrete display): `_`-token strings.  The two arrays
    // live in different containers, so each entry carries the container it is
    // read from plus the pointer prefix it writes back to — the path can then
    // never drift away from where the value actually is.
    let extend = get(gb, "novatek_extend_config");
    for (field, container, prefix, category, label) in [
        (
            "novatek_game_params",
            gb,
            "/game_booster",
            "novatek_main",
            "Novatek 独显游戏档",
        ),
        (
            "novatek_non_playing_config",
            extend,
            "/game_booster/novatek_extend_config",
            "novatek_non_playing",
            "Novatek 非游玩档",
        ),
    ] {
        for (i, raw) in arr(container, field).iter().enumerate() {
            let Some(raw) = raw.as_str() else { continue };
            if token_matches(raw, '_', package) {
                features.push(FeatureHit {
                    category,
                    label,
                    source: Source::Direct,
                    key: package.to_owned(),
                    path: format!("{prefix}/{field}/{i}"),
                    params: novatek_params(raw),
                    overrides: Vec::new(),
                    gate: None,
                });
            }
        }
    }

    // ③ remaining novatek sub-entries.
    for (i, raw) in arr(get(gb, "novatek_extend_config"), "novatek_gex_fps_limit")
        .iter()
        .enumerate()
    {
        let Some(raw) = raw.as_str() else { continue };
        if token_matches(raw, ':', package) {
            features.push(FeatureHit {
                category: "novatek_gex_limit",
                label: "Novatek GEX 帧率上限",
                source: Source::Direct,
                key: package.to_owned(),
                path: format!("/game_booster/novatek_extend_config/novatek_gex_fps_limit/{i}"),
                params: token_param(raw),
                overrides: Vec::new(),
                gate: None,
            });
        }
    }
    if arr(gb, "novatek_black_app")
        .iter()
        .any(|v| v.as_str() == Some(package))
    {
        features.push(FeatureHit {
            category: "novatek_blacklist",
            label: "Novatek 黑名单",
            source: Source::Direct,
            key: package.to_owned(),
            path: "/game_booster/novatek_black_app".into(),
            params: vec![param("blacklisted", Value::Bool(true))],
            overrides: Vec::new(),
            gate: None,
        });
    }

    // ④ mivk / migl: alias + cmdlines objects.
    for (container_key, inner_key, category, label) in [
        ("mivk_settings", "app_params", "mivk", "MIVK（Vulkan 过渲染）"),
        ("migl_settings", "game_params", "migl", "MIGL（OpenGL 过渲染）"),
    ] {
        for (i, entry) in arr(get(gb, container_key), inner_key).iter().enumerate() {
            let hit = arr(entry, "app_cmdlines")
                .iter()
                .chain(arr(entry, "game_cmdlines").iter())
                .any(|v| v.as_str() == Some(package));
            if !hit {
                continue;
            }
            let mut params = scalars(entry);
            let empty = empty_map();
            for (k, v) in get(entry, "xrender_config")
                .as_object()
                .unwrap_or(&empty)
            {
                params.push(param(format!("xrender_config.{k}"), v.clone()));
            }
            for (k, v) in get(entry, "drr").as_object().unwrap_or(&empty) {
                params.push(param(format!("drr.{k}"), v.clone()));
            }
            let drr_static = get(entry, "drr_static");
            if !drr_static.is_null() {
                params.push(param("drr_static", drr_static.clone()));
            }
            features.push(FeatureHit {
                category,
                label,
                source: Source::Direct,
                key: entry
                    .get("app")
                    .or(entry.get("game"))
                    .and_then(Value::as_str)
                    .unwrap_or(package)
                    .to_owned(),
                path: format!("/game_booster/{container_key}/{inner_key}/{i}"),
                params,
                overrides: Vec::new(),
                gate: nested_gate(container_key),
            });
        }
    }

    // ⑤ fisr: direct membership, "OTHER" as fallback.
    {
        let mut direct = Vec::new();
        let mut fallback = Vec::new();
        for (i, entry) in arr(get(gb, "fisr_config"), "enhance_config")
            .iter()
            .enumerate()
        {
            let list = arr(entry, "game_list");
            if list.iter().any(|v| v.as_str() == Some(package)) {
                direct.push((i, entry));
            } else if list.iter().any(|v| v.as_str() == Some("OTHER")) {
                fallback.push((i, entry));
            }
        }
        let used_fallback = direct.is_empty();
        let chosen: Vec<(usize, &Value)> =
            if used_fallback { fallback } else { direct };
        for (i, entry) in chosen {
            let source =
                if used_fallback { Source::Fallback } else { Source::Direct };
            features.push(FeatureHit {
                category: "fisr",
                label: "FISR 插帧/超分策略",
                source,
                key: package.to_owned(),
                path: format!("/game_booster/fisr_config/enhance_config/{i}"),
                params: vec![param(
                    "enhance_policy_config",
                    get(entry, "enhance_policy_config").clone(),
                )],
                overrides: Vec::new(),
                gate: None,
            });
        }
    }

    // ⑥ package-keyed object: self GPU tuner modes.
    if let Some(entry) = get(gb, "self_gpu_tuner_config")
        .as_object()
        .and_then(|m| m.get(package))
    {
        let params = entry
            .as_object()
            .map(|m| {
                m.iter()
                    .map(|(mode, props)| param(mode.clone(), props.clone()))
                    .collect()
            })
            .unwrap_or_default();
        features.push(FeatureHit {
            category: "gpu_tuner",
            label: "高通 GPU tuner 分档属性",
            source: Source::Direct,
            key: package.to_owned(),
            path: format!("/game_booster/self_gpu_tuner_config/{package}"),
            params,
            overrides: Vec::new(),
            gate: gate_of("self_gpu_tuner_enable"),
        });
    }

    // ⑦ token-string lists: `;` `@` `:` separators.
    for (field, sep, category, label) in [
        ("migt", ';', "migt", "Migt 温控调频脚本"),
        ("cgame_df", '@', "cgame_df", "云控游戏接管动态帧率"),
        ("support_highfps_app", ':', "highfps", "高帧率白名单"),
        ("mqs_enhance_list", ':', "mqs_enhance", "MQS 增强名单"),
        ("force_scale_app_list", ':', "force_scale", "强制分辨率缩放"),
    ] {
        for (i, raw) in arr(gb, field).iter().enumerate() {
            let Some(raw) = raw.as_str() else { continue };
            if token_matches(raw, sep, package) {
                let params = if category == "highfps" {
                    raw.split(':')
                        .nth(1)
                        .map(|fps| {
                            vec![
                                param("raw", Value::String(raw.to_owned())),
                                param("fps", Value::from(fps)),
                            ]
                        })
                        .unwrap_or_else(|| token_param(raw))
                } else {
                    token_param(raw)
                };
                let gate = match category {
                    "cgame_df" => gate_of("cgame_enable"),
                    "force_scale" => gate_of("scale_app_enable"),
                    _ => None,
                };
                features.push(FeatureHit {
                    category,
                    label,
                    source: Source::Direct,
                    key: package.to_owned(),
                    path: format!("/game_booster/{field}/{i}"),
                    params,
                    overrides: Vec::new(),
                    gate,
                });
            }
        }
    }

    // ⑧ plain package-array whitelists.
    for (field, category, label) in [
        ("support_scale_app_list", "scale", "分辨率缩放白名单"),
        ("SOC_GameList", "soc", "SOC 调度名单"),
        ("support_vrs_app", "vrs", "VRS 可变着色率名单"),
        (
            "support_dynamic_refresh_rate_games",
            "dynamic_rr",
            "动态刷新率名单",
        ),
        (
            "invalid_low_display_scenes_games",
            "low_rr_invalid",
            "低刷场景排除名单",
        ),
        ("game_light_support_list", "game_light", "游戏光效名单"),
        ("support_predownload_app_list", "predownload", "资源预下载名单"),
        ("support_motor_app", "motor", "马达支持名单"),
        (
            "background_freeze_whitelist",
            "freeze_whitelist",
            "后台冻结白名单",
        ),
    ] {
        if arr(gb, field).iter().any(|v| v.as_str() == Some(package)) {
            let gate = match category {
                "soc" => gate_of("SOC_enable"),
                "scale" => gate_of("scale_app_enable"),
                "predownload" => gate_of("predownload_enable"),
                "freeze_whitelist" => gate_of("background_freeze_enable"),
                _ => None,
            };
            features.push(FeatureHit {
                category,
                label,
                source: Source::Direct,
                key: package.to_owned(),
                path: format!("/game_booster/{field}"),
                params: vec![param("enabled", Value::Bool(true))],
                overrides: Vec::new(),
                gate,
            });
        }
    }

    // ⑨ package-keyed object: per-game low-refresh-rate scene ids.
    if let Some(scenes) = get(gb, "low_display_refresh_rate_scenes_by_single_game")
        .as_object()
        .and_then(|m| m.get(package))
    {
        features.push(FeatureHit {
            category: "low_rr_scenes",
            label: "单游戏低刷场景表",
            source: Source::Direct,
            key: package.to_owned(),
            path: "/game_booster/low_display_refresh_rate_scenes_by_single_game"
                .into(),
            params: vec![param("scene_ids", scenes.clone())],
            overrides: Vec::new(),
            gate: None,
        });
    }

    // ⑩ entry arrays carrying a pkg field / package_list.
    for (i, entry) in arr(gb, "support_resolution_enhance_config")
        .iter()
        .enumerate()
    {
        if get(entry, "pkg").as_str() == Some(package) {
            features.push(FeatureHit {
                category: "resolution_enhance",
                label: "分辨率增强",
                source: Source::Direct,
                key: package.to_owned(),
                path: format!("/game_booster/support_resolution_enhance_config/{i}"),
                params: vec![param(
                    "isSupportHotSwap",
                    get(entry, "isSupportHotSwap").clone(),
                )],
                overrides: Vec::new(),
                gate: None,
            });
        }
    }
    let sc = get(gb, "game_scenario_control_config");
    if arr(sc, "package_list").iter().any(|v| v.as_str() == Some(package)) {
        let mut params: Vec<Param> = Vec::new();
        if let Some(obj) = sc.as_object() {
            for (k, v) in obj {
                if k.as_str() != "package_list" {
                    params.push(param(k.clone(), v.clone()));
                }
            }
        }
        features.push(FeatureHit {
            category: "scenario_control",
            label: "游戏场景控制",
            source: Source::Direct,
            key: package.to_owned(),
            path: "/game_booster/game_scenario_control_config".into(),
            params,
            overrides: Vec::new(),
            gate: None,
        });
    }

    // ⑪ common_config membership (teg side), when provided.
    let (in_game_list, in_support_app) = common
        .map(|c| {
            (
                Some(arr(c, "game_list").iter().any(|v| v.as_str() == Some(package))),
                Some(
                    arr(c, "support_app")
                        .iter()
                        .any(|v| v.as_str() == Some(package)),
                ),
            )
        })
        .unwrap_or((None, None));

    Ok(AppView {
        package: package.to_owned(),
        group,
        in_game_list,
        in_support_app,
        global_switches,
        features,
        conflicts,
    })
}

impl AppView {
    pub fn to_json(&self) -> Value {
        json!({
            "package": self.package,
            "group": self.group,
            "common": {
                "in_game_list": self.in_game_list,
                "in_support_app": self.in_support_app,
            },
            "global_switches": self.global_switches.iter().map(|p| json!({
                "name": p.name, "value": p.value,
            })).collect::<Vec<_>>(),
            "features": self.features.iter().map(|f| json!({
                "category": f.category,
                "label": f.label,
                "source": match f.source {
                    Source::Direct => "direct",
                    Source::Group => "group_alias",
                    Source::Fallback => "fallback",
                },
                "key": f.key,
                "path": f.path,
                "params": f.params.iter().map(|p| json!({
                    "name": p.name, "value": p.value,
                })).collect::<Vec<_>>(),
                "overrides": f.overrides,
                "gate": match &f.gate {
                    Some(g) => json!({"key": g.key, "enabled": g.enabled}),
                    None => Value::Null,
                },
            })).collect::<Vec<_>>(),
            "conflicts": self.conflicts,
        })
    }
}

// ── CLI ─────────────────────────────────────────────────────────────────────

type CliResult<T> = Result<T, Box<dyn std::error::Error>>;

/// Read a params document from a development file, or straight from the
/// on-device Joyose DBs (`config` is the DB-side config name).  A file may be
/// bare params or a stored `{config_name, …, params}` row wrapper.
pub fn load_doc(path: Option<&String>, config: &str) -> CliResult<Value> {
    match path {
        Some(path) => {
            let text = std::fs::read_to_string(path)
                .map_err(|e| format!("读取 {path} 失败: {e}"))?;
            let value: Value =
                serde_json::from_str(&text).map_err(|e| format!("JSON 解析失败: {e}"))?;
            Ok(match value.get("params") {
                Some(inner) if value.get("game_list").is_none() => inner.clone(),
                _ => value,
            })
        }
        None => Ok(super::store::read_params_any(config)?.1),
    }
}

/// `joyose-app <pkg> [booster_params.json] [common_params.json]`
///
/// Without file arguments the documents are read from the on-device Joyose
/// DBs (P1 source swap; `collect()` and the output protocol are unchanged).
/// File arguments remain for development/golden testing.
pub fn cmd_app_view(
    package: Option<&String>,
    booster_path: Option<&String>,
    common_path: Option<&String>,
) -> CliResult<bool> {
    let Some(package) = package else {
        return Err("缺少包名".into());
    };
    let params = load_doc(booster_path, "booster_config")?;
    // Membership info is optional: an unreadable common_config must not stop
    // the feature page from rendering.
    let common = match common_path {
        Some(path) => Some(load_doc(Some(path), "common_config")?),
        None => load_doc(None, "common_config").ok(),
    };

    let view = collect(&params, package, common.as_ref())
        .map_err(|e| -> Box<dyn std::error::Error> { e.into() })?;
    println!(
        "{}",
        serde_json::to_string_pretty(&json!({ "ok": true, "app": view.to_json() }))?
    );
    Ok(true)
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use serde_json::json;

    /// Minimal but shape-faithful doc covering every scan branch.
    pub(crate) fn doc() -> Value {
        json!({
            "header": {"version": "2099000101"},
            "game_booster": {
                "booster_enable": true,
                "game_group_mapping_enable": true,
                "cgame_enable": false,
                "SOC_enable": true,
                "self_gpu_tuner_enable": true,
                "scale_app_enable": true,
                "background_freeze_enable": true,
                "game_group_mapping_config": [
                    {"game_group_name": "YUANSHEN",
                     "package_list": ["com.miHoYo.Yuanshen"]},
                    {"game_group_name": "SGAME",
                     "package_list": ["com.a.sgame", "com.a.sgamece"]}
                ],
                "booster_config": {"ovrride_config": [
                    {"game_name": "YUANSHEN",
                     "dynamic_fps": "10:0,46:121",
                     "scene_ovrride": [
                         {"scene_id": 3, "scene_name": "login", "booster": []}
                     ]},
                    {"game_name": "SGAME", "dynamic_fps": "10:0,43:120"}
                ]},
                "dynamic_fps_global": {"dynamic_fps": "10:0,43:120"},
                "novatek_game_params": [
                    "com.a.sgame_60#120#60,120,1#45#43_0#0_0#0",
                    "com.a.sgamece_90#144#90,144,1#45#43_0#0_0#0"
                ],
                "novatek_extend_config": {
                    "novatek_gex_fps_limit": ["com.a.sgame:60"],
                    "novatek_non_playing_config": ["com.a.sgame_49#144#48,144,1#45#43_49#144#48"]
                },
                "mivk_settings": {"enable": true, "app_params": [
                    {"app": "hkrpg", "app_cmdlines": ["com.miHoYo.hkrpg"],
                     "xrender_config": {"checkMainInfo": "1;1"}}
                ]},
                "cgame_df": ["com.a.sgame@120#10:0,42:90"],
                "fisr_config": {"enhance_config": [
                    {"game_list": ["com.a.sgame"],
                     "enhance_policy_config": [{"feature": "FPS_FIRST"}]},
                    {"game_list": ["OTHER"],
                     "enhance_policy_config": [{"feature": "PQ_FIRST"}]}
                ]},
                "support_highfps_app": ["com.a.sgame:120"],
                "support_scale_app_list": ["com.a.sgame"],
                "migt": ["com.a.sgame;0:384000;30"],
                "SOC_GameList": ["com.a.sgame"],
                "support_resolution_enhance_config": [
                    {"pkg": "com.a.sgame", "isSupportHotSwap": false}
                ]
            }
        })
    }

    fn gb(doc: &Value) -> &Value {
        doc.get("params").unwrap_or(doc).get("game_booster").unwrap()
    }

    #[test]
    fn group_alias_resolves_override_entry() {
        let view = collect(&doc(), "com.miHoYo.Yuanshen", None).unwrap();
        assert_eq!(view.group.as_deref(), Some("YUANSHEN"));
        let hit = view
            .features
            .iter()
            .find(|f| f.category == "booster_override")
            .expect("override hit via group alias");
        assert!(matches!(hit.source, Source::Group));
        assert_eq!(hit.key, "YUANSHEN");
        // dynamic_fps overrides the global baseline.
        assert!(hit
            .overrides
            .iter()
            .any(|o| o.starts_with("dynamic_fps ")));
        // scene_ovrride flattened per scene.
        assert!(hit
            .params
            .iter()
            .any(|p| p.name == "scene_ovrride[0].login"));
    }

    #[test]
    fn direct_package_hits_token_and_list_categories() {
        let view = collect(&doc(), "com.a.sgame", None).unwrap();
        let cats: Vec<&str> =
            view.features.iter().map(|f| f.category).collect();
        for expected in [
            "booster_override", // via SGAME group alias
            "novatek_main",
            "novatek_gex_limit",
            "fisr",
            "highfps",
            "migt",
            "soc",
            "resolution_enhance",
        ] {
            assert!(cats.contains(&expected), "missing {expected}: {cats:?}");
        }
        // novatek fps pair parsed.
        let novatek = view
            .features
            .iter()
            .find(|f| f.category == "novatek_main")
            .unwrap();
        let get = |n: &str| {
            novatek
                .params
                .iter()
                .find(|p| p.name == n)
                .map(|p| p.value.as_str().unwrap().to_owned())
        };
        assert_eq!(get("min_fps").as_deref(), Some("60"));
        assert_eq!(get("target_fps").as_deref(), Some("120"));
        // highfps parsed.
        let highfps = view
            .features
            .iter()
            .find(|f| f.category == "highfps")
            .unwrap();
        assert_eq!(
            highfps
                .params
                .iter()
                .find(|p| p.name == "fps")
                .unwrap()
                .value,
            json!("120")
        );
    }

    #[test]
    fn token_prefix_trap_sgamece_does_not_hit_sgame_categories() {
        // sgamece IS in the doc (own novatek entry) and in the SGAME group.
        let view = collect(&doc(), "com.a.sgamece", None).unwrap();
        let novatek: Vec<&str> = view
            .features
            .iter()
            .filter(|f| f.category == "novatek_main")
            .flat_map(|f| f.params.iter())
            .filter(|p| p.name == "raw")
            .filter_map(|p| p.value.as_str())
            .collect();
        assert_eq!(novatek.len(), 1);
        assert!(novatek[0].starts_with("com.a.sgamece_"));
    }

    #[test]
    fn fisr_falls_back_to_other_only_without_direct_hit() {
        // com.miHoYo.hkrpg: no fisr entry, but mivk hit + OTHER fallback.
        let view = collect(&doc(), "com.miHoYo.hkrpg", None).unwrap();
        let fisr = view
            .features
            .iter()
            .find(|f| f.category == "fisr")
            .expect("OTHER fallback hit");
        assert!(matches!(fisr.source, Source::Fallback));

        // com.a.sgame has a direct fisr entry → no fallback hit.
        let view = collect(&doc(), "com.a.sgame", None).unwrap();
        let fisr: Vec<&FeatureHit> =
            view.features.iter().filter(|f| f.category == "fisr").collect();
        assert_eq!(fisr.len(), 1);
        assert!(matches!(fisr[0].source, Source::Direct));
    }

    #[test]
    fn common_membership_is_reported() {
        let common = json!({
            "game_list": ["com.a.sgame"],
            "support_app": ["com.miHoYo.Yuanshen"]
        });
        let view = collect(&doc(), "com.a.sgame", Some(&common)).unwrap();
        assert_eq!(view.in_game_list, Some(true));
        assert_eq!(view.in_support_app, Some(false));
    }

    #[test]
    fn unknown_document_is_rejected() {
        assert!(collect(&json!({"foo": 1}), "com.a.sgame", None).is_err());
    }

    #[test]
    fn global_switches_are_collected_in_doc_order() {
        let view = collect(&doc(), "com.a.sgame", None).unwrap();
        let find = |name: &str| {
            view.global_switches
                .iter()
                .find(|p| p.name == name)
                .map(|p| p.value.as_bool().unwrap())
        };
        // top-level switches…
        assert_eq!(find("booster_enable"), Some(true));
        assert_eq!(find("cgame_enable"), Some(false));
        assert_eq!(find("SOC_enable"), Some(true));
        // …plus the nested enables.
        assert_eq!(find("mivk_settings.enable"), Some(true));
        assert_eq!(find("migl_settings.enable"), None);
        // document order preserved (preserve_order IndexMap).
        let names: Vec<&str> = view
            .global_switches
            .iter()
            .map(|p| p.name.as_str())
            .collect();
        assert_eq!(
            names,
            vec![
                "booster_enable",
                "game_group_mapping_enable",
                "cgame_enable",
                "SOC_enable",
                "self_gpu_tuner_enable",
                "scale_app_enable",
                "background_freeze_enable",
                "mivk_settings.enable"
            ]
        );
    }

    #[test]
    fn feature_hits_carry_their_master_switch_gate() {
        let view = collect(&doc(), "com.a.sgame", None).unwrap();
        let gate = |category: &str| {
            view.features
                .iter()
                .find(|f| f.category == category)
                .map(|f| {
                    f.gate
                        .as_ref()
                        .map(|g| (g.key.as_str(), g.enabled))
                })
        };
        // gated hits: key + current value from the doc.
        assert_eq!(gate("cgame_df"), Some(Some(("cgame_enable", false))));
        assert_eq!(gate("soc"), Some(Some(("SOC_enable", true))));
        assert_eq!(gate("scale"), Some(Some(("scale_app_enable", true))));
        assert_eq!(gate("migt"), Some(None)); // no master switch
        // booster_override gated by the master booster_enable.
        assert_eq!(
            gate("booster_override"),
            Some(Some(("booster_enable", true)))
        );
    }

    #[test]
    fn package_index_counts_features_and_expands_groups() {
        let doc = doc();
        let gb = doc.get("game_booster").unwrap();
        let apps = package_index(gb);
        let find = |pkg: &str| {
            apps.iter()
                .find(|e| e.package == pkg)
                .map(|e| (e.features, e.group.as_deref()))
        };
        // sgame: ovrride(组展开) + novatek + non_playing + gex + fisr + highfps + scale + migt + soc + cgame_df + resolution_enhance
        assert_eq!(find("com.a.sgame"), Some((11, Some("SGAME"))));
        // sgamece: ovrride(同组展开)+novatek = 2 —— 组条目对组内每个成员都生效
        assert_eq!(find("com.a.sgamece"), Some((2, Some("SGAME"))));
        assert_eq!(find("com.miHoYo.Yuanshen"), Some((1, Some("YUANSHEN"))));
        assert_eq!(find("com.miHoYo.hkrpg"), Some((1, None))); // mivk only
        // 排序：features 降序
        assert!(apps.windows(2).all(|w| w[0].features >= w[1].features));
    }
}
