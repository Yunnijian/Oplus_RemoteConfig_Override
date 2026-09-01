//! Device capability snapshot for the HyperOS feature screens.
//!
//! `joyose-device-caps` — one-shot root collection of the hardware knobs the
//! per-feature editors need: CPU cluster frequency tables, GPU frequency
//! table + governor, migt module parameters, cpuset layout, thermal zone
//! types and display refresh rates. Missing nodes degrade to null fields
//! (the command itself never fails on absent hardware); Kotlin consumes the
//! nulls for field-level show/hide.

use serde_json::{json, Map, Value};
use std::error::Error;
use std::fs;
use std::process::Command;

type CliResult<T> = Result<T, Box<dyn Error>>;

const CPUFREQ: &str = "/sys/devices/system/cpu/cpufreq";
const KGSL: &str = "/sys/class/kgsl/kgsl-3d0";
const MIGT_PARAMS: &str = "/sys/module/migt/parameters";
const CPUSET: &str = "/dev/cpuset";
const THERMAL: &str = "/sys/class/thermal";

// ── helpers ─────────────────────────────────────────────────────────────────

fn read_trim(path: &str) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

fn parse_i64_list(text: &str) -> Vec<i64> {
    text.split_whitespace()
        .filter_map(|s| s.parse::<i64>().ok())
        .collect()
}

fn parse_word_list(text: &str) -> Vec<String> {
    text.split_whitespace().map(String::from).collect()
}

// ── collectors ──────────────────────────────────────────────────────────────

/// cpufreq policy directories each describe one cluster. Sorted by policy
/// number so the small cluster always comes first (display order contract).
fn cpu_clusters() -> Value {
    let Ok(rd) = fs::read_dir(CPUFREQ) else {
        return Value::Null;
    };
    let mut policies: Vec<(u64, std::path::PathBuf)> = rd
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter_map(|p| {
            let name = p.file_name()?.to_string_lossy().into_owned();
            let num = name.strip_prefix("policy")?.parse::<u64>().ok()?;
            Some((num, p))
        })
        .collect();
    policies.sort_by_key(|(num, _)| *num);
    let mut clusters = Vec::new();
    for (_, dir) in policies {
        let cpus = read_trim(&dir.join("related_cpus").to_string_lossy())
            .map(|t| parse_i64_list(&t))
            .unwrap_or_default();
        let frequencies = read_trim(&dir.join("scaling_available_frequencies").to_string_lossy())
            .map(|t| parse_i64_list(&t))
            .unwrap_or_default();
        let governors = read_trim(&dir.join("scaling_available_governors").to_string_lossy())
            .map(|t| parse_word_list(&t))
            .unwrap_or_default();
        clusters.push(json!({
            "cpus": cpus,
            "frequencies": frequencies,
            "governors": governors,
        }));
    }
    if clusters.is_empty() {
        Value::Null
    } else {
        Value::Array(clusters)
    }
}

/// GPU frequency table is normalized to Hz (freq_table_mhz variant is MHz).
fn gpu_caps() -> Value {
    let frequencies = read_trim(&format!("{KGSL}/gpu_available_frequencies"))
        .map(|t| parse_i64_list(&t))
        .filter(|v| !v.is_empty())
        .or_else(|| {
            read_trim(&format!("{KGSL}/freq_table_mhz")).map(|t| {
                parse_i64_list(&t).into_iter().map(|mhz| mhz * 1_000_000).collect()
            })
        });
    let governor = read_trim(&format!("{KGSL}/dev_governor"));
    let governors = read_trim(&format!("{KGSL}/gpu_available_governors"))
        .map(|t| parse_word_list(&t));
    if frequencies.is_none() && governor.is_none() && governors.is_none() {
        return Value::Null;
    }
    json!({
        "frequencies": frequencies,
        "governor": governor,
        "governors": governors,
    })
}

/// migt module parameter inventory; `exists=false` hides the migt screen.
fn migt_caps() -> Value {
    let Ok(rd) = fs::read_dir(MIGT_PARAMS) else {
        return json!({ "exists": false, "parameters": [] });
    };
    let mut parameters: Vec<String> = rd
        .filter_map(Result::ok)
        .map(|e| e.file_name().to_string_lossy().into_owned())
        .collect();
    parameters.sort();
    json!({ "exists": true, "parameters": parameters })
}

fn cpusets() -> Value {
    let Ok(rd) = fs::read_dir(CPUSET) else {
        return Value::Null;
    };
    let mut map = Map::new();
    for entry in rd.filter_map(Result::ok) {
        let name = entry.file_name().to_string_lossy().into_owned();
        if let Some(cpus) = read_trim(&entry.path().join("cpus").to_string_lossy()) {
            map.insert(name, json!(cpus));
        }
    }
    if map.is_empty() {
        Value::Null
    } else {
        Value::Object(map)
    }
}

fn thermal_zones() -> Value {
    let Ok(rd) = fs::read_dir(THERMAL) else {
        return Value::Null;
    };
    let mut zones: Vec<String> = rd
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| {
            p.file_name()
                .map(|n| n.to_string_lossy().starts_with("thermal_zone"))
                .unwrap_or(false)
        })
        .filter_map(|p| read_trim(&p.join("type").to_string_lossy()))
        .collect();
    zones.sort();
    zones.dedup();
    if zones.is_empty() {
        Value::Null
    } else {
        Value::Array(zones.into_iter().map(Value::String).collect())
    }
}

/// Scan `dumpsys display` output for `refreshRate=<float>` tokens (both the
/// `=` and `:` separator variants occur across Android versions), round to
/// whole fps, dedup + sort. Sanity-filtered to 10..=240 to skip bogus tokens.
fn extract_refresh_rates(text: &str) -> Vec<i64> {
    let mut rates: Vec<i64> = Vec::new();
    let marker = "refreshRate";
    let mut rest = text;
    while let Some(pos) = rest.find(marker) {
        rest = &rest[pos + marker.len()..];
        let tail = rest.trim_start_matches(['=', ':', ' ', '\t']);
        let num: String = tail
            .chars()
            .take_while(|c| c.is_ascii_digit() || *c == '.')
            .collect();
        if let Ok(v) = num.parse::<f64>() {
            let fps = v.round() as i64;
            if (10..=240).contains(&fps) && !rates.contains(&fps) {
                rates.push(fps);
            }
        }
    }
    rates.sort_unstable();
    rates
}

fn refresh_rates() -> Value {
    let output = Command::new("dumpsys").arg("display").output();
    let rates = output
        .ok()
        .filter(|o| o.status.success())
        .map(|o| extract_refresh_rates(&String::from_utf8_lossy(&o.stdout)))
        .unwrap_or_default();
    if rates.is_empty() {
        Value::Null
    } else {
        Value::Array(rates.into_iter().map(Value::from).collect())
    }
}

// ── command ─────────────────────────────────────────────────────────────────

/// `joyose-device-caps` — print the capability snapshot as one JSON document.
pub fn cmd_device_caps() -> CliResult<bool> {
    println!(
        "{}",
        json!({
            "ok": true,
            "cpu_clusters": cpu_clusters(),
            "gpu": gpu_caps(),
            "migt": migt_caps(),
            "cpusets": cpusets(),
            "thermal_zones": thermal_zones(),
            "refresh_rates": refresh_rates(),
        })
    );
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn refresh_rate_extraction_dedups_and_sorts() {
        let text = "supportedModes=[{id=1, width=1216, height=2680, \
                    refreshRate=120.00240325927734}, {id=2, refreshRate=60.000004}] \
                    mDefaultMode refreshRate: 120.002403 bogus refreshRate=999999";
        assert_eq!(extract_refresh_rates(text), vec![60, 120]);
    }

    #[test]
    fn refresh_rate_colon_separator_is_parsed() {
        assert_eq!(extract_refresh_rates("a refreshRate: 90.5 b"), vec![91]);
    }

    #[test]
    fn missing_marker_yields_empty() {
        assert!(extract_refresh_rates("nothing here").is_empty());
    }
}
