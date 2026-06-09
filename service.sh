#!/system/bin/sh

MODPATH="/data/adb/modules/RemoteConfigOverride"

LOG_FILE="$MODPATH/log/boot.log"

touch "$LOG_FILE"
chmod 644 "$LOG_FILE"

echo "服务启动于 $(date)" > "$LOG_FILE" 2>&1

{
    echo "等待数据库文件..."
    
    MAX_WAIT=300
    WAIT_INTERVAL=5
    elapsed=0
    
    while [ $elapsed -lt $MAX_WAIT ]; do
        if [ -f "/data/data/com.oplus.cosa/databases/db_game_database" ] || \
           [ -f "/data/user_de/0/com.oplus.cosa/databases/db_game_database" ]; then
            echo "数据库文件已找到，等待60秒..."
            sleep 60
            
            echo "开始执行 auto.sh..."
            sh "$MODPATH/auto.sh"
            
            echo "启动 action.sh..."
            sh "$MODPATH/action.sh"
            exit 0
        fi
        
        sleep $WAIT_INTERVAL
        elapsed=$((elapsed + WAIT_INTERVAL))
        echo "已等待 ${elapsed} 秒，继续检查数据库..."
    done
    
    echo "错误：在 ${MAX_WAIT} 秒内未找到数据库文件"
} >> "$LOG_FILE" 2>&1 &