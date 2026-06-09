#!/system/bin/sh
SKIPUNZIP=0

echo " "
echo "=== 云控数据覆盖模块安装 ==="
echo " "

# 关闭用户体验计划
echo -n "正在关闭用户体验计划 ... "
settings put system oplus_customize_cta_user_experience 0
if [ "$(settings get system oplus_customize_cta_user_experience)" -eq 0 ]; then
  echo "[OK]"
else
  echo "[FAIL]"
fi

# 芯片检测（天玑9400系列）
echo -n "芯片平台: $(getprop ro.board.platform) ... "
CHIP_PLATFORM=$(getprop ro.board.platform)
case "$CHIP_PLATFORM" in
  "mt6991Z"|"mt6991T"|"mt6992"|"mt8799"|"mt6991"|"mt6989")
    echo "[OK]"
    ;;
  *)
    echo "[FAIL]"
    echo "不支持的平台，仅支持天玑9400系列"
    abort
    ;;
esac

# 数据库检测（支持两个路径）
echo -n "应用增强服务数据库 ... "
if [ -f "/data/data/com.oplus.cosa/databases/db_game_database" ] || \
   [ -f "/data/user_de/0/com.oplus.cosa/databases/db_game_database" ]; then
  echo "[OK]"
else
  echo "[FAIL]"
  abort
fi

# SCX调速器检测
echo -n "SCX调速器支持 ... "
SCX_FOUND=0
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
  if grep -q "scx" "$policy/scaling_available_governors" 2>/dev/null; then
    SCX_FOUND=1
    break
  fi
done
if [ "$SCX_FOUND" -eq 1 ]; then
  echo "[OK]"
else
  echo "[FAIL]"
  echo "请使用支持风驰游戏内核的官方或GKI内核"
  abort
fi

# 给工具文件添加执行权限
echo -n "设置工具权限 ... "
chmod 777 "$MODPATH/tool/sqlite3"
chmod 777 "$MODPATH/tool/jq"
chmod 777 "$MODPATH/tool/yt"
chmod 755 "$MODPATH/action.sh"
chmod 755 "$MODPATH/auto.sh"
chmod 777 "$MODPATH/tool/inotify"
echo "[OK]"

echo " "
echo "=== 安装完成 ==="
echo " "
echo "请通过KernelSU的「执行」功能触发配置覆盖"