# 本地开发环境

DolphinScheduler 本地启动统一入口，依赖容器（PostgreSQL + ZooKeeper）+ Java 三服务（master / worker / api）+ 前端 vite dev server。

> 数据库统一用 **PostgreSQL @5433**，与 `application.yaml` 配置一致。MySQL 路径已废弃。

---

## 文件清单

| 文件 | 作用 |
|------|------|
| `docker-compose.yml` | PG + ZK 容器定义；首次启动 PG 自动跑 `dolphinscheduler_postgresql.sql` 初始化 |
| `start.sh` | 启动入口，整合容器 / Java 服务 / UI |
| `stop.sh` | 停止入口（粒度可控，含 docker:wipe 清数据卷） |
| `seed-demo-data.sql` | demo 数据填充脚本，按需手动跑 |

---

## 一键启动

```bash
cd local-env
./start.sh        # 全套：docker → master → worker → api → ui
```

启动顺序：
1. 起容器（PG + ZK），等健康检查通过；首次会自动初始化数据库
2. 检查 ZK 2181 / PG 5433 都监听
3. 起 master → worker → api（每步检查 PID）
4. 起 vite dev server（pnpm run dev）

启动完会打印一份服务状态摘要 + 关键 URL。

---

## 分步启动

```bash
./start.sh docker      # 只起容器（IDEA 里调试 Java 时用）
./start.sh services    # docker 已起，只跑 master+worker+api
./start.sh master      # 单独某个 server
./start.sh worker
./start.sh api
./start.sh ui          # 只起前端
```

每个 Java 服务都依赖编译产物 `dolphinscheduler-<module>/target/<module>-server/libs/`。
没编译会提示：

```
./mvnw -pl dolphinscheduler-<module> -am package -DskipTests
```

---

## 停止

```bash
./stop.sh              # 默认：停 UI + Java（容器保留）
./stop.sh services     # 只停 Java
./stop.sh ui           # 只停前端
./stop.sh docker       # 停容器（保留数据卷）
./stop.sh full         # 停所有（UI + Java + 容器，保留数据卷）

./stop.sh docker:wipe  # ⚠️ 清空数据库（删除数据卷，需输入 yes 确认）
```

---

## 关键端口

| 服务 | 端口 | 用途 |
|------|------|------|
| PostgreSQL | 5433 | 宿主机 5433 → 容器 5432 |
| ZooKeeper | 2181 | 注册中心 |
| Master RPC | 5678 / 5679 | 内部通信 |
| Worker RPC | 1234 / 1235 | 内部通信 |
| API | 12345 | REST + Swagger |
| Vite UI | 5173 | 前端 dev server |

---

## 常用入口

- API health: http://localhost:12345/dolphinscheduler/actuator/health
- Swagger:    http://localhost:12345/dolphinscheduler/doc.html
- UI:         http://localhost:5173
- 默认账号:   `admin / dolphinscheduler123`

---

## 数据库连接

```
jdbc:postgresql://127.0.0.1:5433/dolphinscheduler
user: root
password: root
```

容器内执行 SQL：

```bash
docker exec -it dolphinscheduler-postgresql psql -U root -d dolphinscheduler
```

---

## 完全重置数据库

如果需要恢复出厂状态（删表 + 重建）：

```bash
./stop.sh docker:wipe         # 删除容器与数据卷
./start.sh docker             # 重新起容器，首次自动跑初始化 SQL
```

或者保留容器、只清表（schema 重置）：

```bash
docker exec dolphinscheduler-postgresql psql -U root -d dolphinscheduler -c \
  "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO root;"
docker exec -i dolphinscheduler-postgresql psql -U root -d dolphinscheduler \
  < ../dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_postgresql.sql
```

---

## 注意事项

- **首次启动 PG**：通过 `docker-entrypoint-initdb.d/` 自动跑初始化脚本。如果你之前已经起过容器（数据卷存在），脚本不会再跑 —— 此时 `start.sh docker` 会检查表数为 0 时主动补跑，避免出现"容器起来但库是空的"。
- **dev 分支可能比初始化脚本基线（3.3.0）新**：实测 dev HEAD 工作良好，无需额外跑 upgrade。如果未来 dev 推到了 3.5.x 而 schema 不兼容，需要手动跑 `dolphinscheduler-dao/src/main/resources/sql/upgrade/` 下的脚本。
- **logs 目录**：所有 Java 服务和 vite 的日志都落在仓库根 `logs/`，已经被 `.gitignore` 忽略。
- **PID 文件**：`logs/master.pid` `logs/worker.pid` `logs/api.pid`，stop 后会清理。
