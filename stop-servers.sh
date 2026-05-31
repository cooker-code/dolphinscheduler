#!/bin/bash
# 停止 DolphinScheduler 本地 Java 服务
# 用法:
#   ./stop-servers.sh          # 停止全部
#   ./stop-servers.sh master   # 只停止 master
#   ./stop-servers.sh worker   # 只停止 worker
#   ./stop-servers.sh api      # 只停止 api

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$REPO_DIR/logs"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

stop_proc() {
    local name=$1
    local class=$2
    local pidfile="$LOG_DIR/${name}.pid"

    local pids
    pids=$(pgrep -f "$class" 2>/dev/null || true)

    if [ -z "$pids" ]; then
        echo -e "${YELLOW}[!] $class 未运行${NC}"
        return
    fi

    echo -e "${GREEN}[✓] 停止 $class (PID: $pids)${NC}"
    kill $pids 2>/dev/null || true
    sleep 1

    # 如果还存在就 kill -9
    pids=$(pgrep -f "$class" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        kill -9 $pids 2>/dev/null || true
    fi

    rm -f "$pidfile"
    echo -e "${GREEN}[✓] $class 已停止${NC}"
}

TARGET="${1:-all}"

echo ""
echo "========================================"
echo " DolphinScheduler 本地服务停止脚本"
echo "========================================"
echo ""

case "$TARGET" in
    master)
        stop_proc "master" "MasterServer"
        ;;
    worker)
        stop_proc "worker" "WorkerServer"
        ;;
    api)
        stop_proc "api" "ApiApplicationServer"
        ;;
    all|*)
        stop_proc "master" "MasterServer"
        stop_proc "worker" "WorkerServer"
        stop_proc "api"    "ApiApplicationServer"
        ;;
esac

echo ""
echo "========================================"
