#!/bin/bash
set -e

echo "=========================================="
echo " DolphinScheduler 本地开发环境启动脚本"
echo " 数据库：MySQL 8.0"
echo " 注册中心：ZooKeeper 3.8"
echo "=========================================="

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检测 Docker Compose 命令（兼容新旧版本）
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
elif docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    echo -e "${RED}✗ 错误：未找到 docker-compose 或 docker compose 命令${NC}"
    echo "请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
fi

echo -e "${GREEN}✓ 使用命令: ${DOCKER_COMPOSE}${NC}"
echo ""

echo -e "${YELLOW}[1/5] 启动Docker依赖服务...${NC}"
$DOCKER_COMPOSE -f docker-compose-dev-mysql.yml up -d

echo -e "${YELLOW}[2/5] 等待MySQL就绪（最多60秒）...${NC}"
timeout=60
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if docker exec dolphinscheduler-mysql mysqladmin ping -h localhost -uroot -proot --silent &> /dev/null; then
        echo -e "${GREEN}✓ MySQL已就绪${NC}"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    if [ $elapsed -eq $timeout ]; then
        echo -e "${RED}✗ MySQL启动超时${NC}"
        exit 1
    fi
done

echo -e "${YELLOW}[3/5] 等待ZooKeeper就绪（最多30秒）...${NC}"
timeout=30
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if docker exec dolphinscheduler-zookeeper zkServer.sh status &> /dev/null; then
        echo -e "${GREEN}✓ ZooKeeper已就绪${NC}"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    if [ $elapsed -eq $timeout ]; then
        echo -e "${RED}✗ ZooKeeper启动超时${NC}"
        exit 1
    fi
done

echo -e "${YELLOW}[4/5] 验证服务状态...${NC}"
$DOCKER_COMPOSE -f docker-compose-dev-mysql.yml ps

echo -e "${YELLOW}[5/5] 检查数据库表...${NC}"
TABLE_COUNT=$(docker exec dolphinscheduler-mysql mysql -uroot -proot dolphinscheduler -se "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dolphinscheduler';" 2>/dev/null || echo "0")
echo -e "数据库表数量: ${GREEN}${TABLE_COUNT}${NC}"

if [ "$TABLE_COUNT" -eq "0" ]; then
    echo -e "${YELLOW}⚠ 数据库表未初始化，正在执行初始化...${NC}"
    if [ -f "dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_mysql.sql" ]; then
        docker exec -i dolphinscheduler-mysql mysql -uroot -proot dolphinscheduler < dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_mysql.sql
        echo -e "${GREEN}✓ 数据库初始化完成${NC}"
    else
        echo -e "${RED}✗ 未找到SQL初始化文件${NC}"
        echo "请手动初始化数据库"
    fi
fi

echo ""
echo "=========================================="
echo -e "${GREEN}✓ Docker服务已全部启动！${NC}"
echo "=========================================="
echo ""
echo "接下来请在IDEA中按顺序启动："
echo "  1. MasterServer (org.apache.dolphinscheduler.server.master.MasterServer)"
echo "  2. WorkerServer (org.apache.dolphinscheduler.server.worker.WorkerServer)"
echo "  3. ApiApplicationServer (org.apache.dolphinscheduler.api.ApiApplicationServer)"
echo ""
echo "=========================================="
echo "访问地址："
echo "  API Swagger: http://localhost:12345/dolphinscheduler/doc.html"
echo "  API健康检查:  http://localhost:12345/dolphinscheduler/actuator/health"
echo "  UI (需手动启动): http://localhost:5173"
echo ""
echo "=========================================="
echo "数据库连接信息："
echo "  类型: MySQL"
echo "  主机: localhost"
echo "  端口: 3306"
echo "  数据库: dolphinscheduler"
echo "  用户名: root"
echo "  密码: root"
echo ""
echo "=========================================="
echo "注册中心信息："
echo "  类型: ZooKeeper"
echo "  主机: localhost"
echo "  端口: 2181"
echo "  命名空间: dolphinscheduler"
echo ""
echo "=========================================="
echo "常用命令："
echo "  查看容器状态: $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml ps"
echo "  查看MySQL日志: $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml logs -f dolphinscheduler-mysql"
echo "  查看ZK日志:   $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml logs -f dolphinscheduler-zookeeper"
echo "  停止服务:     $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml stop"
echo "  停止并删除:   $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml down"
echo "=========================================="
