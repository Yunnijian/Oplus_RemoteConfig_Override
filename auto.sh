#!/system/bin/sh

MODPATH="/data/adb/modules/RemoteConfigOverride"
JSON_DIR="$MODPATH/json"
BACK_DIR="$MODPATH/back"

mkdir -p "$JSON_DIR" "$BACK_DIR"

installed_pkgs=$(pm list packages -3 2>&1 | sed 's/package://')
if [ -z "$installed_pkgs" ]; then
  exit 1
fi

rule_pkgs=""
for json_file in "$BACK_DIR"/*.json; do
  [ -f "$json_file" ] && rule_pkgs="$rule_pkgs $(basename "$json_file" .json)"
done

EXTRA_MAP="\
com.miHoYo.Yuanshen:com.miHoYo.yuanshencb,com.miHoYo.GenshinImpact,com.miHoYo.ys.bilibili \
com.miHoYo.hkrpg:com.HoYoverse.hkrpgoversea \
com.miHoYo.Nap:com.miHoYo.NapCb,com.HoYoverse.Nap \
com.tencent.tmgp.cod:com.garena.game.codm \
com.tencent.tmgp.sgame:com.tencent.tmgp.sgamece,com.levelinfinite.sgameGlobal.midaspay \
com.tencent.mf.uam:com.proximabeta.mf.uamo \
com.pubg.imobile:com.tencent.ig,com.pubg.krmobile"

adapt_json() {
  local src_pkg="$1"
  local dst_pkg="$2"
  local src_json="$BACK_DIR/${src_pkg}.json"
  local dst_json="$JSON_DIR/${dst_pkg}.json"
  
  if [ -f "$src_json" ] && [ ! -f "$dst_json" ]; then
    sed "s/\"package_name\": \"$src_pkg\"/\"package_name\": \"$dst_pkg\"/" "$src_json" > "$dst_json"
    chmod 644 "$dst_json"
    echo "适配 $src_pkg → $dst_pkg"
  fi
}

pkg_in_list() {
  echo "$installed_pkgs" | grep -qxF "$1"
}

# 先处理 EXTRA_MAP 中明确定义的变体
for entry in $EXTRA_MAP; do
  rule_pkg="${entry%%:*}"
  aliases="${entry#*:}"
  
  IFS_OLD="$IFS"
  IFS=','
  for alias in $aliases; do
    pkg_in_list "$alias" && adapt_json "$rule_pkg" "$alias"
  done
  IFS="$IFS_OLD"
done

# 处理主包和前缀匹配的变体
for rule_pkg in $rule_pkgs; do
  # 处理主包本身
  if pkg_in_list "$rule_pkg"; then
    dst_file="$JSON_DIR/${rule_pkg}.json"
    if [ -f "$BACK_DIR/${rule_pkg}.json" ] && [ ! -f "$dst_file" ]; then
      cp "$BACK_DIR/${rule_pkg}.json" "$dst_file"
      echo "新增 $rule_pkg"
    fi
  fi
  
  # 处理前缀匹配的变体
  for pkg in $installed_pkgs; do
    [ -z "$pkg" ] && continue
    case "$pkg" in
      "${rule_pkg}."*)
        adapt_json "$rule_pkg" "$pkg"
        ;;
    esac
  done
done