//! Package-name resolution for Joyose cloud configs.
//!
//! `booster_config.ovrride_config[].game_name` frequently holds a *group
//! alias* (SGAME / PUBG / YUANSHEN / …) instead of a package name. The
//! mapping lives in `game_booster.game_group_mapping_config[]` entries of the
//! shape `{game_group_name, package_list[]}`. Locating every config fragment
//! that belongs to one app therefore requires joining that table first.

use serde_json::Value;

/// The set of names under which a package may appear in per-app structures:
/// its own package name plus every group alias whose `package_list` contains
/// it. `group` is the (first) matching group name, if any.
pub fn alias_set(game_booster: &Value, package: &str) -> (Vec<String>, Option<String>) {
    let mut aliases = vec![package.to_owned()];
    let mut group = None;

    if let Some(entries) = game_booster
        .get("game_group_mapping_config")
        .and_then(Value::as_array)
    {
        for entry in entries {
            let Some(name) = entry.get("game_group_name").and_then(Value::as_str) else {
                continue;
            };
            let Some(list) = entry.get("package_list").and_then(Value::as_array) else {
                continue;
            };
            if list.iter().any(|p| p.as_str() == Some(package)) {
                group = group.or_else(|| Some(name.to_owned()));
                if !aliases.iter().any(|a| a == name) {
                    aliases.push(name.to_owned());
                }
            }
        }
    }
    (aliases, group)
}

/// `true` when `s`'s first `sep`-delimited token equals `package`.
///
/// Splits (never prefix-matches) so `com.a.sgame_60#…` matches package
/// `com.a.sgame` but *not* package `com.a.sgamece`.
pub fn token_matches(s: &str, sep: char, package: &str) -> bool {
    s.split(sep).next() == Some(package)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn gb() -> Value {
        json!({
            "game_group_mapping_config": [
                {"game_group_name": "YUANSHEN", "package_list": [
                    "com.miHoYo.Yuanshen", "com.miHoYo.GenshinImpact"]},
                {"game_group_name": "SGAME", "package_list": [
                    "com.tencent.tmgp.sgame", "com.tencent.tmgp.sgamece"]}
            ]
        })
    }

    #[test]
    fn group_alias_is_collected() {
        let (aliases, group) = alias_set(&gb(), "com.miHoYo.Yuanshen");
        assert_eq!(group.as_deref(), Some("YUANSHEN"));
        assert!(aliases.contains(&"YUANSHEN".to_owned()));
        assert!(aliases.contains(&"com.miHoYo.Yuanshen".to_owned()));
    }

    #[test]
    fn unknown_package_has_no_group() {
        let (aliases, group) = alias_set(&gb(), "com.example.lonely");
        assert_eq!(group, None);
        assert_eq!(aliases, vec!["com.example.lonely".to_owned()]);
    }

    #[test]
    fn token_split_rejects_superstring_packages() {
        assert!(token_matches("com.a.sgame_60#120", '_', "com.a.sgame"));
        assert!(!token_matches("com.a.sgamece_60#120", '_', "com.a.sgame"));
        assert!(token_matches("com.miHoYo.hkrpg:60", ':', "com.miHoYo.hkrpg"));
        assert!(token_matches(
            "com.tencent.tmgp.pubgmhd@120#10:0",
            '@',
            "com.tencent.tmgp.pubgmhd"
        ));
        assert!(token_matches(
            "com.tencent.ig;0:384000;30",
            ';',
            "com.tencent.ig"
        ));
    }
}
