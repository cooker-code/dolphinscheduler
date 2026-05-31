# 本地开发环境与 Agent 协作指南

> 本仓库由人和 AI agent 共同开发。本文档是**进入项目的第一站**：环境怎么起、端口怎么分、日志怎么看、agent 怎么协作。
> 历史与项目背景见 `../AGENTS.md`，模块级细节见各模块的 `CLAUDE.md`，任务上下文见 `../.trellis/tasks/`，简明启动用法见同目录 `README.md`。

---

## 1. 环境前置

| 必备 | 版本 | 备注 |
|------|------|------|
| Java | JDK 8 | 项目目标版本，不要用 11+ 编译 |
| Maven Wrapper | 仓库自带 `./mvnw` | 不要用本机 mvn |
| Node.js | 16.x（推荐） | 18+ 可能因 OpenSSL 问题构建失败 |
| pnpm | 7.x | 前端包管理器 |
| Docker Desktop | 最新 | 跑 PostgreSQL + ZooKeeper |
| IDEA | 任意版本 | 推荐用于断点调试 master/worker/api |

---

## 2. 端口与服务总览

| 组件 | 端口 | 是否容器 | 说明 |
|------|------|---------|------|
| PostgreSQL | **5433**（非标准） | ✅ 容器 | 默认元数据库，避开本机 5432 冲突 |
| ZooKeeper | 2181 | ✅ 容器 | 注册中心 |
| API Server | 12345 | ❌ 本地 JVM | context: `/dolphinscheduler/` |
| Master RPC / HTTP | 5678 / 5679 | ❌ 本地 JVM | |
| Worker RPC / HTTP | 1234 / 1235 | ❌ 本地 JVM | |
| UI Dev Server | 5173 | ❌ vite | 配置见 `../dolphinscheduler-ui/.env.development` |
| Swagger UI | 12345/dolphinscheduler/swagger-ui/index.html | — | API 起来即可 |
| OpenAPI JSON | 12345/dolphinscheduler/v3/api-docs | — | 用于前端代码生成 |

**Web UI 默认账号**：`admin / dolphinscheduler123`

**PostgreSQL 连接**：

| 项目 | 值 |
|------|-----|
| Host | `127.0.0.1` |
| Port | `5433`（非标准，避开本机 5432） |
| Database | `dolphinscheduler` |
| User | `root` |
| Password | `root` |

```bash
psql -h 127.0.0.1 -p 5433 -U root -d dolphinscheduler
# 或：docker exec -it dolphinscheduler-postgresql psql -U root -d dolphinscheduler
```

---

## 3. 一键启动

```bash
cd local-env
./start.sh             # 全套：docker → master → worker → api → ui
```

启动顺序：
1. 起容器（PG + ZK），等健康检查通过；首次自动跑 `dolphinscheduler_postgresql.sql` 初始化
2. 检查 ZK 2181 / PG 5433 都监听
3. 起 master → worker → api（每步检查 PID 与日志）
4. 起 vite dev server（pnpm run dev）

启动完打印服务状态摘要 + 关键 URL。

### 分步启动

```bash
./start.sh docker      # 只起容器（IDEA 里调试 Java 时用）
./start.sh services    # 只跑 master+worker+api（docker 已起）
./start.sh master      # 单独某个 server
./start.sh worker
./start.sh api
./start.sh ui          # 只起前端
```

### 停止

```bash
./stop.sh              # 默认：停 UI + Java（容器保留）
./stop.sh services     # 只停 Java
./stop.sh ui           # 只停前端
./stop.sh docker       # 停容器（保留数据卷）
./stop.sh full         # 停所有（UI + Java + 容器，保留数据卷）
./stop.sh docker:wipe  # ⚠️ 清空数据库（删除数据卷，需输入 yes 确认）
```

前置：每个 Java 服务依赖编译产物 `dolphinscheduler-<module>/target/<module>-server/libs/`。没编译时 start.sh 会提示：

```bash
./mvnw -pl dolphinscheduler-<module> -am package -DskipTests
```

---

## 4. 日志监控

所有 Java 服务日志落在仓库根 `logs/`，已被 `.gitignore` 忽略。

### 日志文件位置

| 服务 | 当前日志 | 历史归档 |
|------|---------|---------|
| API | `logs/dolphinscheduler-api.log` | `logs/dolphinscheduler-api.YYYY-MM-DD_HH.0.log` |
| Master | `logs/dolphinscheduler-master.log` | `logs/dolphinscheduler-master.YYYY-MM-DD_HH.0.log` |
| Worker | `logs/dolphinscheduler-worker.log` | `logs/dolphinscheduler-worker.YYYY-MM-DD_HH.0.log` |
| Vite UI | `logs/vite.log` | — |
| PID 文件 | `logs/{master,worker,api}.pid` | — |

### 实时跟踪

```bash
# 单个服务
tail -f logs/dolphinscheduler-api.log
tail -f logs/dolphinscheduler-master.log
tail -f logs/dolphinscheduler-worker.log

# 同时看四路日志（每个加文件名前缀）
tail -f logs/dolphinscheduler-{api,master,worker}.log logs/vite.log

# 只看 ERROR/WARN
tail -f logs/dolphinscheduler-api.log | grep -E --line-buffered "ERROR|WARN"

# 只看某次会话之后（按时间戳过滤）
tail -f logs/dolphinscheduler-api.log | awk '$0 >= "2026-05-31 14:00:00"'
```

### 容器侧日志

```bash
# 仓库根目录或 local-env 目录都可，但 local-env 一致性更好
cd local-env

# 查看 PG / ZK 实时日志
docker compose -f docker-compose.yml logs -f dolphinscheduler-postgresql
docker compose -f docker-compose.yml logs -f dolphinscheduler-zookeeper

# 同时看两个
docker compose -f docker-compose.yml logs -f
```

### 健康检查

```bash
# API（最直接的"后端是否活着"信号）
curl -s http://localhost:12345/dolphinscheduler/actuator/health | jq

# 进程
pgrep -fl 'MasterServer|WorkerServer|ApiApplicationServer'

# 容器
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep dolphinscheduler

# PG 表数与版本
docker exec dolphinscheduler-postgresql psql -U root -d dolphinscheduler -c \
  "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE') AS tables, (SELECT version FROM t_ds_version);"
```

### 浏览器侧日志

UI 行为问题先看浏览器，**不要直接读 vue 源码**：

1. **首选：Chrome DevTools MCP**（`mcp__chrome-devtools__*`）— 直接读 console messages、network、performance、DOM
2. **次选：opencli browser**（复用本机已登录的 Chrome）：

```bash
opencli browser --session work state          # DOM 快照
opencli browser --session work get text body  # 页面文本
opencli browser --session work screenshot     # 视觉截图
```

详见 `../.trellis/spec/frontend/guidelines/quality-guidelines.md` 的"UI 问题排查强制顺序"。

---

## 5. 数据库重置

### 完全重置（删卷 + 重建）

```bash
cd local-env
./stop.sh docker:wipe   # 输入 yes 确认
./start.sh docker       # 重新起容器，首次自动跑初始化 SQL
```

### 仅清表（保留容器与卷）

```bash
docker exec dolphinscheduler-postgresql psql -U root -d dolphinscheduler -c \
  "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO root;"

docker exec -i dolphinscheduler-postgresql psql -U root -d dolphinscheduler \
  < ../dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_postgresql.sql
```

### Schema 升级（dev 比基线版本新时）

初始化脚本基线 `t_ds_version` 是 `3.3.0`，当前 dev 已等价于 3.4.1 schema。如果未来 dev 推到了 3.5.x 而 schema 不兼容，按顺序跑：

```bash
SRC=dolphinscheduler-dao/src/main/resources/sql/upgrade
for v in 3.4.2 3.5.0; do
  for kind in ddl dml; do
    docker exec -i dolphinscheduler-postgresql psql -U root -d dolphinscheduler \
      < $SRC/${v}_schema/postgresql/dolphinscheduler_${kind}.sql
  done
done
docker exec dolphinscheduler-postgresql psql -U root -d dolphinscheduler -c \
  "UPDATE t_ds_version SET version='3.5.0' WHERE id=1;"
```

---

## 6. 常见坑

| 坑 | 应对 |
|---|------|
| Postgres 端口是 **5433**，不是 5432 | application.yaml 已配 `jdbc:postgresql://127.0.0.1:5433/dolphinscheduler` |
| 默认容器是 **Jetty 不是 Tomcat** | 已在 `dolphinscheduler-meter` 排除，不要再引入 |
| `mvn test` 触发 jacoco "Cannot process instrumented class" | 必须用 `./mvnw verify`；详见 `dolphinscheduler-api/CLAUDE.md` |
| 改 dao/service 后 master/worker/api 行为不变 | 多模块 jar 缓存过期，重新 `./mvnw -pl <module> -am package -DskipTests` |
| 前端访问 12345 没反应 | 后端是 12345，前端开发用 5173；vite 已代理到后端 |
| Worker/Master 起不来报 registry | ZK 没就绪，先 `./start.sh docker` 等 healthy 再起 services |
| 清浏览器 cookie 仍跳不到 /login | sessionId 在 **localStorage**（pinia-plugin-persistedstate），不是 cookie。`localStorage.clear(); location.href='/login'` |
| 浏览器页面卡住、控制台 axios `TypeError` | request interceptor 写法不对（axios 1.x 在本项目里 `headers.set()` 会抛）。详见 `.trellis/spec/frontend/guidelines/request-interceptor.md` |
| 数据库刚清完前端 401 但跳不到登录 | 同上，是 request interceptor 异常导致 401 兜底失效 |

---

## 7. Swagger / OpenAPI 接入

后端：所有新 controller 必填 swagger 注解（参考 `LoggerController` 风格）。

前端代码生成（推荐）：

```bash
cd dolphinscheduler-ui
npx openapi-typescript-codegen \
  --input http://localhost:12345/dolphinscheduler/v3/api-docs \
  --output src/api/generated --client axios
```

接口契约固化：

```bash
curl -s http://localhost:12345/dolphinscheduler/v3/api-docs > docs/openapi/snapshot.json
```

---

## 8. Agent 协作规范

### 8.1 进项目第一件事：读这 4 个文件

| 文件 | 作用 |
|------|------|
| `../AGENTS.md` | 全局协作规则、GitNexus、Trellis 工作流 |
| **本文件** `local-env/DEV_SETUP.md` | 端口、启动、日志监控、常见坑 |
| 当前模块的 `CLAUDE.md`（如 `dolphinscheduler-api/CLAUDE.md`） | 模块级 gotchas |
| `.trellis/tasks/<current>/prd.md` + `design.md` + `implement.md` | 当前任务上下文 |

### 8.2 已落地的协作基建

| 工具 | 用途 |
|------|------|
| **Trellis** (`.trellis/`) | 任务规划：prd → design → implement → start → check → finish |
| **GitNexus** MCP | 代码图谱：`gitnexus_impact` / `context` / `query` / `detect_changes` |
| **springdoc-openapi** | 后端接口契约自动生成 |
| **`local-env/start.sh`** | 一键起 docker + Java + UI |
| **各模块 CLAUDE.md** | 模块级提示（被 AGENTS.md 链接） |

### 8.3 强制工作流

```
用户需求
    ↓
[复杂任务] trellis-brainstorm 澄清需求
    ↓
prd.md（complex 任务再补 design.md / implement.md）
    ↓
implement.jsonl / check.jsonl 列 spec 参照
    ↓
task.py start ← 状态 → in_progress
    ↓
trellis-implement（按 implement.md 执行）
    ↓
gitnexus_impact 改前必查
    ↓
trellis-check（spec/lint/test/cross-layer）
    ↓
gitnexus_detect_changes 改后核对
    ↓
人工 review → commit → PR
```

### 8.4 编码硬规则（MUST）

- **改任何 symbol 前**：`gitnexus_impact({target, direction:"upstream"})`，HIGH/CRITICAL 必须告知用户
- **commit 前**：`gitnexus_detect_changes()` 校验影响面与预期一致
- **新 controller 必须有 swagger 注解**：`@Tag` / `@Operation` / `@Parameter` / `@Schema`
- **新审计场景必须有 `@OperatorLog`**：见 `dolphinscheduler-api/audit/`
- **数据库改动必须有升级脚本**：`dolphinscheduler-dao/src/main/resources/sql/upgrade/`
- **跨模块改动**：先看 `.trellis/spec/guides/cross-layer-thinking-guide.md`
- **UI 行为问题**：先看浏览器 console / network，再读源码（详见 `.trellis/spec/frontend/guidelines/quality-guidelines.md`）

### 8.5 Agent 不要做的事

- ❌ 跳过 `gitnexus_impact` 直接编辑
- ❌ 用 `mvn test`（会触发 jacoco 错误，用 `./mvnw verify`）
- ❌ 在 `dev` 分支直接 commit（个人改动走 `my-dev`，详见 `.trellis/spec/guides/git-fork-workflow.md`）
- ❌ 用 `--no-verify` / `--no-gpg-sign` 绕过 hook
- ❌ 加注释只为说明"做了什么"（命名能表达就不写注释）
- ❌ 不读现有任务就建新任务（先 `task.py list --mine`）
- ❌ 在没有用户授权时 push / 创建 PR / 删分支
- ❌ 用 `perl -pe` / `sed -i` 处理多行结构敏感文件（yaml / json / 多行字符串）—— 用 Read + Edit

---

## 9. Git 提交流程

参见 `.trellis/spec/guides/git-fork-workflow.md`。三句话总结：

- `dev` 永远 = `apache/dev` 镜像，不要在上面 commit
- 个人配置 / 实验 / 本地化改动 → `my-dev`（push 到你 fork 的 `origin/my-dev`）
- 给 apache 提 PR → 从 `dev` 切 `feat/xxx`，cherry-pick 相关 commit，PR 到 `apache:dev`

同步上游：`./scripts/sync-upstream.sh`

---

## 10. 相关文档

- `../AGENTS.md` —— GitNexus、Trellis 全局工作流
- `README.md` —— 本目录的简明启动手册
- `dolphinscheduler-api/CLAUDE.md` —— API 模块测试、Jetty、Py4J
- `dolphinscheduler-master/CLAUDE.md` —— Master 模块
- `dolphinscheduler-ui/CLAUDE.md` —— 前端模块
- `dolphinscheduler-ui/.env.development` —— 前端开发环境变量
- `docker-compose.yml` —— 依赖容器编排（PG + ZK）
- `start.sh` / `stop.sh` —— 一键启动 / 停止
- `seed-demo-data.sql` —— demo 数据填充（按需执行）
- `.trellis/tasks/` —— 当前所有任务规划
- `.trellis/spec/guides/git-fork-workflow.md` —— fork 协作流程
- `.trellis/spec/guides/tool-use-guide.md` —— 工具使用规范
- `.trellis/spec/frontend/guidelines/request-interceptor.md` —— axios 拦截器陷阱
