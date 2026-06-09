#!/system/bin/sh
MODPATH="/data/adb/modules/RemoteConfigOverride"

echo "=== 配置覆盖开始 ==="
echo " "

SQLITE="$MODPATH/tool/sqlite3"
JQ="$MODPATH/tool/jq"

if [ -f "/data/data/com.oplus.cosa/databases/db_game_database" ]; then
  DB="/data/data/com.oplus.cosa/databases/db_game_database"
elif [ -f "/data/user_de/0/com.oplus.cosa/databases/db_game_database" ]; then
  DB="/data/user_de/0/com.oplus.cosa/databases/db_game_database"
else
  echo "❌ 未找到数据库文件"
  echo "请确保："
  echo "1. 已启用游戏助手和应用增强服务"
  echo "2. 至少打开过一次游戏中心"
  exit 1
fi

JSON_DIRS="$MODPATH/json"

TOTAL=0
SUCCESS=0
FAIL=0

for JSON_DIR in $JSON_DIRS; do
  for json in "$JSON_DIR"/*.json; do
    [ -f "$json" ] || continue
    TOTAL=$((TOTAL+1))
    
    pkg=$(basename "$json" .json)
    echo " "
    echo "🔄 正在处理: $pkg.json"
    
    columns=$($JQ -r 'keys_unsorted | join(",")' "$json" 2>/dev/null)
    if [ $? -ne 0 ]; then
      echo "❌ $pkg.json 解析失败 (列名)"
      FAIL=$((FAIL+1))
      continue
    fi
    
    values=$($JQ -r '
      [to_entries[] | 
      if .value == null then "NULL"
      elif (.value | type) == "string" then 
          "\"" + (.value | gsub("\""; "\"\"")) + "\""
      else
          .value | tojson | gsub("\""; "\"\"") | "\"" + . + "\""
      end] | join(",")' "$json" 2>/dev/null)
    
    if [ $? -ne 0 ]; then
      echo "❌ $pkg.json 解析失败 (值)"
      FAIL=$((FAIL+1))
      continue
    fi
    
    existing_info=$($SQLITE "$DB" "SELECT COUNT(*) FROM PackageInfoBean WHERE package_name = '$pkg';" 2>/dev/null)
    if [ "$existing_info" -gt 0 ]; then
      $SQLITE "$DB" "DELETE FROM PackageInfoBean WHERE package_name = '$pkg';" 2>/dev/null
    fi
    
    existing=$($SQLITE "$DB" "SELECT COUNT(*) FROM PackageConfigBean WHERE Package_Name = '$pkg';" 2>/dev/null)
    
    if [ "$existing" -gt 0 ]; then
      sql="UPDATE PackageConfigBean SET ($columns) = ($values) WHERE Package_Name = '$pkg';"
    else
      sql="INSERT INTO PackageConfigBean (Package_Name, $columns) VALUES (\"$pkg\", $values);"
    fi
    
    if echo "$sql" | $SQLITE "$DB" 2>/dev/null; then
      echo "✅ $pkg.json 写入成功"
      SUCCESS=$((SUCCESS+1))
    else
      echo "❌ $pkg.json 写入失败"
      FAIL=$((FAIL+1))
    fi
  done
done

echo " "
echo "📊 处理结果统计"
echo "================================"
echo "配置文件总数: $TOTAL"
echo "成功: $SUCCESS"
echo "失败: $FAIL"
echo "--------------------------------"
if [ $FAIL -gt 0 ]; then
  echo "⚠️ 部分配置处理失败"
  echo "请检查JSON文件格式是否正确"
fi
echo "是否生效请启动游戏验证"
echo "================================"
echo " "
echo "✅ 操作完成"