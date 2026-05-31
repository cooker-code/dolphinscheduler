#!/bin/bash
# 启动 DolphinScheduler 本地 Java 服务（Master / Worker / API）
# 用法:
#   ./start-servers.sh          # 启动全部（master + worker + api）
#   ./start-servers.sh master   # 只启动 master
#   ./start-servers.sh worker   # 只启动 worker
#   ./start-servers.sh api      # 只启动 api

set -e

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$REPO_DIR/logs"
mkdir -p "$LOG_DIR"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓] $1${NC}"; }
info() { echo -e "${BLUE}[→] $1${NC}"; }
warn() { echo -e "${YELLOW}[!] $1${NC}"; }
fail() { echo -e "${RED}[✗] $1${NC}"; }

# ---------- 前置检查 ----------
check_zookeeper() {
    if nc -z localhost 2181 2>/dev/null; then
        log "ZooKeeper 2181: 运行中"
    else
        fail "ZooKeeper 未运行！请先启动 ZooKeeper（端口 2181）"
        echo "    提示: docker compose -f docker-compose-dev.yml up -d zookeeper"
        exit 1
    fi
}

check_postgres() {
    if nc -z localhost 5433 2>/dev/null; then
        log "PostgreSQL 5433: 运行中"
    else
        fail "PostgreSQL 未运行！请先启动 PostgreSQL（端口 5433）"
        echo "    提示: docker compose -f docker-compose-dev.yml up -d postgres"
        exit 1
    fi
}

is_running() {
    local class=$1
    pgrep -f "$class" > /dev/null 2>&1
}

# ---------- 启动函数 ----------
start_master() {
    local target="$REPO_DIR/dolphinscheduler-master/target/master-server"
    local main="org.apache.dolphinscheduler.server.master.MasterServer"
    local logfile="$LOG_DIR/dolphinscheduler-master.log"

    if is_running "$main"; then
        warn "MasterServer 已在运行，跳过"
        return
    fi

    if [ ! -d "$target/libs" ]; then
        fail "master-server 未编译，请先执行: mvn package -pl dolphinscheduler-master -am -DskipTests"
        exit 1
    fi

    info "启动 MasterServer..."
    nohup java -server \
        -Xms512m -Xmx1g \
        -cp "$target/conf:$target/libs/*" \
        "$main" \
        > "$logfile" 2>&1 &
    echo $! > "$LOG_DIR/master.pid"
    sleep 2
    if is_running "$main"; then
        log "MasterServer 已启动 (PID: $(cat "$LOG_DIR/master.pid"))，日志: $logfile"
    else
        fail "MasterServer 启动失败，查看日志: $logfile"
        tail -20 "$logfile"
        exit 1
    fi
}

start_worker() {
    local target="$REPO_DIR/dolphinscheduler-worker/target/worker-server"
    local main="org.apache.dolphinscheduler.server.worker.WorkerServer"
    local logfile="$LOG_DIR/dolphinscheduler-worker.log"

    if is_running "$main"; then
        warn "WorkerServer 已在运行，跳过"
        return
    fi

    if [ ! -d "$target/libs" ]; then
        fail "worker-server 未编译，请先执行: mvn package -pl dolphinscheduler-worker -am -DskipTests"
        exit 1
    fi

    info "启动 WorkerServer..."
    nohup java -server \
        -Xms512m -Xmx1g \
        -cp "$target/conf:$target/libs/*" \
        "$main" \
        > "$logfile" 2>&1 &
    echo $! > "$LOG_DIR/worker.pid"
    sleep 2
    if is_running "$main"; then
        log "WorkerServer 已启动 (PID: $(cat "$LOG_DIR/worker.pid"))，日志: $logfile"
    else
        fail "WorkerServer 启动失败，查看日志: $logfile"
        tail -20 "$logfile"
        exit 1
    fi
}

start_api() {
    local target="$REPO_DIR/dolphinscheduler-api/target/api-server"
    local main="org.apache.dolphinscheduler.api.ApiApplicationServer"
    local logfile="$LOG_DIR/dolphinscheduler-api.log"

    if is_running "$main"; then
        warn "ApiApplicationServer 已在运行，跳过"
        return
    fi

    if [ ! -d "$target/libs" ]; then
        fail "api-server 未编译，请先执行: mvn package -pl dolphinscheduler-api -am -DskipTests"
        exit 1
    fi

    info "启动 ApiApplicationServer..."
    nohup java -server \
        -Xms512m -Xmx1g \
        -cp "$target/conf:$target/libs/*" \
        "$main" \
        > "$logfile" 2>&1 &
    echo $! > "$LOG_DIR/api.pid"
    sleep 3
    if is_running "$main"; then
        log "ApiApplicationServer 已启动 (PID: $(cat "$LOG_DIR/api.pid"))，日志: $logfile"
    else
        fail "ApiApplicationServer 启动失败，查看日志: $logfile"
        tail -20 "$logfile"
        exit 1
    fi
}

# ---------- 主逻辑 ----------
echo ""
echo "========================================"
echo " DolphinScheduler 本地服务启动脚本"
echo "========================================"
echo ""

TARGET="${1:-all}"

check_zookeeper
check_postgres
echo ""

case "$TARGET" in
    master)
        start_master
        ;;
    worker)
        start_worker
        ;;
    api)
        start_api
        ;;
    all|*)
        start_master
        start_worker
        start_api
        ;;
esac

echo ""
echo "========================================"
echo " 服务状态"
echo "========================================"

is_running "MasterServer"          && echo -e "  MasterServer        ${GREEN}运行中${NC} (端口 5678/5679)" \
                                    || echo -e "  MasterServer        ${RED}未运行${NC}"
is_running "WorkerServer"          && echo -e "  WorkerServer        ${GREEN}运行中${NC} (端口 1234/1235)" \
                                    || echo -e "  WorkerServer        ${RED}未运行${NC}"
is_running "ApiApplicationServer"  && echo -e "  ApiApplicationServer${GREEN}运行中${NC} (端口 12345)" \
                                    || echo -e "  ApiApplicationServer${RED}未运行${NC}"

echo ""
echo "  API:  http://localhost:12345/dolphinscheduler/actuator/health"
echo "  UI:   http://localhost:5173 (需单独启动: cd dolphinscheduler-ui && npm run dev)"
echo "  日志目录: $LOG_DIR/"
echo ""
echo "  停止所有服务: ./stop-servers.sh"
echo "========================================"
