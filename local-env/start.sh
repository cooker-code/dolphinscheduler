#!/usr/bin/env bash
# DolphinScheduler 本地开发统一启动脚本
#
# 用法：
#   ./start.sh                # all：docker → master → worker → api → ui
#   ./start.sh docker         # 仅启动依赖容器（PG + ZK）
#   ./start.sh services       # 仅启动 Java 服务（master + worker + api，假设 docker 已起）
#   ./start.sh master         # 单独启动 master
#   ./start.sh worker         # 单独启动 worker
#   ./start.sh api            # 单独启动 api
#   ./start.sh ui             # 启动前端 vite dev server
#
# 前置：
#   - 已 mvn package 各 server 模块（target/<server>/libs 必须存在）
#   - dolphinscheduler-ui 已 pnpm install

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$REPO_DIR/logs"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
mkdir -p "$LOG_DIR"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $1"; }
info() { echo -e "${BLUE}[→]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
fail() { echo -e "${RED}[✗]${NC} $1"; }

# --- docker compose 命令探测 ---
if command -v docker-compose &>/dev/null; then
    DC="docker-compose"
elif docker compose version &>/dev/null 2>&1; then
    DC="docker compose"
else
    fail "未找到 docker-compose / docker compose"
    exit 1
fi

# ---------- 容器层 ----------
start_docker() {
    info "启动依赖容器（PG + ZK）..."
    $DC -f "$COMPOSE_FILE" up -d

    info "等待 PostgreSQL 就绪（最多 60s）..."
    for i in $(seq 1 30); do
        if docker exec dolphinscheduler-postgresql pg_isready -U root -d dolphinscheduler &>/dev/null; then
            log "PostgreSQL 就绪"
            break
        fi
        sleep 2
        [ "$i" -eq 30 ] && { fail "PostgreSQL 启动超时"; exit 1; }
    done

    info "等待 ZooKeeper 就绪（最多 30s）..."
    for i in $(seq 1 15); do
        if docker exec dolphinscheduler-zookeeper zkServer.sh status &>/dev/null; then
            log "ZooKeeper 就绪"
            break
        fi
        sleep 2
        [ "$i" -eq 15 ] && { fail "ZooKeeper 启动超时"; exit 1; }
    done

    # 仅在表数为 0 时自动跑初始化（首次启动 PG 已通过 docker-entrypoint 跑过）
    local cnt
    cnt=$(docker exec dolphinscheduler-postgresql psql -U root -d dolphinscheduler -tAc \
          "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';" 2>/dev/null || echo "0")
    if [ "${cnt:-0}" -eq 0 ]; then
        warn "数据库表为空，执行初始化 SQL..."
        docker exec -i dolphinscheduler-postgresql psql -U root -d dolphinscheduler \
            < "$REPO_DIR/dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_postgresql.sql" >/dev/null
        log "数据库初始化完成"
    else
        log "数据库已就绪（$cnt 张表）"
    fi
}

# ---------- 前置检查 ----------
check_deps() {
    nc -z localhost 2181 2>/dev/null || { fail "ZooKeeper 2181 未运行，先跑 ./start.sh docker"; exit 1; }
    nc -z localhost 5433 2>/dev/null || { fail "PostgreSQL 5433 未运行，先跑 ./start.sh docker"; exit 1; }
    log "依赖检查通过（ZK 2181 + PG 5433）"
}

is_running() { pgrep -f "$1" >/dev/null 2>&1; }

# ---------- Java 服务 ----------
start_java() {
    local module=$1 main=$2 pidname=$3
    local target="$REPO_DIR/dolphinscheduler-$module/target/$module-server"
    local logfile="$LOG_DIR/dolphinscheduler-$module.log"

    if is_running "$main"; then
        warn "$main 已在运行，跳过"
        return
    fi

    if [ ! -d "$target/libs" ]; then
        fail "$module-server 未编译。请先："
        echo "    ./mvnw -pl dolphinscheduler-$module -am package -DskipTests"
        exit 1
    fi

    info "启动 $main..."
    nohup java -server -Xms512m -Xmx1g \
        -cp "$target/conf:$target/libs/*" "$main" \
        > "$logfile" 2>&1 &
    echo $! > "$LOG_DIR/$pidname.pid"
    sleep 2
    if is_running "$main"; then
        log "$main 已启动 (PID: $(cat "$LOG_DIR/$pidname.pid"))，日志: $logfile"
    else
        fail "$main 启动失败，最后 20 行日志："
        tail -20 "$logfile"
        exit 1
    fi
}

start_master() { start_java master org.apache.dolphinscheduler.server.master.MasterServer master; }
start_worker() { start_java worker org.apache.dolphinscheduler.server.worker.WorkerServer worker; }
start_api()    { start_java api    org.apache.dolphinscheduler.api.ApiApplicationServer api;     }

# ---------- 前端 ----------
start_ui() {
    local ui_dir="$REPO_DIR/dolphinscheduler-ui"
    if pgrep -f "$ui_dir/node_modules/.bin/vite" >/dev/null 2>&1; then
        warn "vite dev server 已在运行，跳过"
        return
    fi
    if [ ! -d "$ui_dir/node_modules" ]; then
        fail "dolphinscheduler-ui 未安装依赖。请先："
        echo "    cd dolphinscheduler-ui && pnpm install"
        exit 1
    fi
    info "启动 vite dev server..."
    (cd "$ui_dir" && nohup pnpm run dev > "$LOG_DIR/vite.log" 2>&1 &)
    sleep 3
    if pgrep -f "$ui_dir/node_modules/.bin/vite" >/dev/null 2>&1; then
        log "vite 已启动，日志: $LOG_DIR/vite.log"
    else
        fail "vite 启动失败，最后 20 行日志："
        tail -20 "$LOG_DIR/vite.log"
    fi
}

# ---------- 状态摘要 ----------
print_summary() {
    echo ""
    echo "========================================"
    echo " 服务状态"
    echo "========================================"
    is_running MasterServer         && echo -e "  MasterServer         ${GREEN}运行中${NC} (端口 5678/5679)" || echo -e "  MasterServer         ${RED}未运行${NC}"
    is_running WorkerServer         && echo -e "  WorkerServer         ${GREEN}运行中${NC} (端口 1234/1235)" || echo -e "  WorkerServer         ${RED}未运行${NC}"
    is_running ApiApplicationServer && echo -e "  ApiApplicationServer ${GREEN}运行中${NC} (端口 12345)"     || echo -e "  ApiApplicationServer ${RED}未运行${NC}"
    pgrep -f "$REPO_DIR/dolphinscheduler-ui/node_modules/.bin/vite" >/dev/null 2>&1 \
        && echo -e "  vite (UI dev)        ${GREEN}运行中${NC} (端口 5173)" \
        || echo -e "  vite (UI dev)        ${RED}未运行${NC}"
    echo ""
    echo "  API health:  http://localhost:12345/dolphinscheduler/actuator/health"
    echo "  Swagger:     http://localhost:12345/dolphinscheduler/doc.html"
    echo "  UI:          http://localhost:5173"
    echo "  日志目录:    $LOG_DIR/"
    echo "========================================"
}

# ---------- 入口 ----------
TARGET="${1:-all}"

echo ""
echo "========================================"
echo " DolphinScheduler 本地启动: $TARGET"
echo "========================================"

case "$TARGET" in
    docker)   start_docker ;;
    master)   check_deps; start_master ;;
    worker)   check_deps; start_worker ;;
    api)      check_deps; start_api ;;
    services) check_deps; start_master; start_worker; start_api ;;
    ui)       start_ui ;;
    all|*)
        start_docker
        check_deps
        start_master
        start_worker
        start_api
        start_ui
        ;;
esac

print_summary
