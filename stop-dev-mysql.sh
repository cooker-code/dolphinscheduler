#!/bin/bash

echo "=========================================="
echo " DolphinScheduler 停止服务脚本"
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
    exit 1
fi

# 显示菜单
echo ""
echo "请选择停止方式："
echo "  1) 停止容器（保留数据）"
echo "  2) 停止并删除容器（保留数据卷）"
echo "  3) 停止并删除容器和数据（⚠️ 会清空所有数据）"
echo "  4) 仅重启容器"
echo "  5) 取消"
echo ""
read -p "请输入选项 [1-5]: " choice

case $choice in
    1)
        echo -e "${YELLOW}正在停止容器...${NC}"
        $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml stop
        echo -e "${GREEN}✓ 容器已停止（数据已保留）${NC}"
        ;;
    2)
        echo -e "${YELLOW}正在停止并删除容器...${NC}"
        $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml down
        echo -e "${GREEN}✓ 容器已删除（数据卷已保留）${NC}"
        echo ""
        echo "数据卷列表："
        docker volume ls | grep dolphinscheduler
        ;;
    3)
        echo -e "${RED}⚠️  警告：此操作将删除所有数据！${NC}"
        read -p "确认删除所有数据？输入 'yes' 继续: " confirm
        if [ "$confirm" = "yes" ]; then
            echo -e "${YELLOW}正在停止并删除容器和数据...${NC}"
            $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml down -v
            echo -e "${GREEN}✓ 容器和数据已全部删除${NC}"
        else
            echo -e "${YELLOW}操作已取消${NC}"
        fi
        ;;
    4)
        echo -e "${YELLOW}正在重启容器...${NC}"
        $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml restart
        echo -e "${GREEN}✓ 容器已重启${NC}"
        
        echo -e "${YELLOW}等待服务就绪...${NC}"
        sleep 10
        $DOCKER_COMPOSE -f docker-compose-dev-mysql.yml ps
        ;;
    5)
        echo -e "${YELLOW}操作已取消${NC}"
        ;;
    *)
        echo -e "${RED}✗ 无效选项${NC}"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "操作完成！"
echo "=========================================="
