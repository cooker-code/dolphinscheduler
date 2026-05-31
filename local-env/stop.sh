#!/usr/bin/env bash
# DolphinScheduler 本地开发统一停止脚本
#
# 用法：
#   ./stop.sh                # all：停 ui + Java 服务（保留容器）
#   ./stop.sh docker         # 停依赖容器（保留数据卷）
#   ./stop.sh docker:wipe    # 停容器并删除数据卷（⚠️ 清空数据库）
#   ./stop.sh services       # 停 Java 服务
#   ./stop.sh master|worker|api
#   ./stop.sh ui             # 停 vite dev server
#   ./stop.sh full           # 停 UI + Java + 容器（保留数据卷）

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$REPO_DIR/logs"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }

if command -v docker-compose &>/dev/null; then
    DC="docker-compose"
elif docker compose version &>/dev/null 2>&1; then
    DC="docker compose"
else
    DC=""
fi

stop_proc() {
    local name=$1 class=$2
    local pids
    pids=$(pgrep -f "$class" 2>/dev/null || true)
    if [ -z "$pids" ]; then
        warn "$class 未运行"
        return
    fi
    log "停止 $class (PID: $pids)"
    kill $pids 2>/dev/null || true
    sleep 1
    pids=$(pgrep -f "$class" 2>/dev/null || true)
    [ -n "$pids" ] && kill -9 $pids 2>/dev/null || true
    rm -f "$LOG_DIR/$name.pid"
}

stop_master() { stop_proc master MasterServer; }
stop_worker() { stop_proc worker WorkerServer; }
stop_api()    { stop_proc api    ApiApplicationServer; }

stop_ui() {
    local pattern="$REPO_DIR/dolphinscheduler-ui/node_modules/.bin/vite"
    local pids
    pids=$(pgrep -f "$pattern" 2>/dev/null || true)
    if [ -z "$pids" ]; then
        warn "vite dev server 未运行"
        return
    fi
    log "停止 vite (PID: $pids)"
    kill $pids 2>/dev/null || true
}

stop_docker() {
    [ -z "$DC" ] && { warn "未找到 docker compose，跳过"; return; }
    log "停止依赖容器（保留数据卷）"
    $DC -f "$COMPOSE_FILE" stop
}

wipe_docker() {
    [ -z "$DC" ] && { warn "未找到 docker compose，跳过"; return; }
    echo -e "${RED}[!] 此操作将删除 PG + ZK 数据卷，所有数据丢失${NC}"
    read -r -p "确认输入 'yes' 继续: " confirm
    [ "$confirm" = "yes" ] || { warn "已取消"; return; }
    $DC -f "$COMPOSE_FILE" down -v
    log "容器与数据卷已删除"
}

TARGET="${1:-all}"

echo ""
echo "========================================"
echo " DolphinScheduler 本地停止: $TARGET"
echo "========================================"

case "$TARGET" in
    master)        stop_master ;;
    worker)        stop_worker ;;
    api)           stop_api ;;
    ui)            stop_ui ;;
    services)      stop_master; stop_worker; stop_api ;;
    docker)        stop_docker ;;
    docker:wipe)   wipe_docker ;;
    full)          stop_ui; stop_master; stop_worker; stop_api; stop_docker ;;
    all|*)         stop_ui; stop_master; stop_worker; stop_api ;;
esac

echo ""
echo "========================================"
