# 本地开发环境与 Agent 协作指南

> 本仓库由人和 AI agent 共同开发。本文档是**进入项目的第一站**：环境怎么起、端口怎么分、agent 怎么协作。
> 历史与项目背景见 `AGENTS.md`，模块级细节见各模块的 `CLAUDE.md`，任务上下文见 `.trellis/tasks/`。

---

## 1. 环境前置

| 必备 | 版本 | 备注 |
|------|------|------|
| Java | JDK 8 | 项目目标版本，不要用 11+ 编译 |
| Maven Wrapper | 跟仓库 `./mvnw` | 不要用本机 mvn |
| Node.js | 18+ | 前端 vite |
| Docker Desktop | 最新 | 跑 Postgres/MySQL/ZK |
| IDEA | 任意版本 | 推荐用于跑 Master/Worker/Api |

## 2. 端口与服务总览

| 组件 | 端口 | 是否容器 | 说明 |
|------|------|---------|------|
| PostgreSQL | **5433**（注意非标准） | ✅ | 默认元数据库，避开本机 5432 冲突 |
| MySQL | 3306 | ✅（可选） | 用 `start-dev-mysql.sh` 时启用 |
| ZooKeeper | 2181 | ✅ | 注册中心 |
| API Server | 12345 | ❌ 本地 JVM | context: `/dolphinscheduler/` |
| Master RPC / HTTP | 5678 / 5679 | ❌ 本地 JVM | |
| Worker RPC / HTTP | 1234 / 1235 | ❌ 本地 JVM | |
| UI Dev Server | 5173 | ❌ vite | 配置见 `dolphinscheduler-ui/.env.development` |
| Swagger UI | 12345/dolphinscheduler/swagger-ui/index.html | — | API 起来即可 |
| OpenAPI JSON | 12345/dolphinscheduler/v3/api-docs | — | 用于前端代码生成 |

**Web UI 默认账号**：`admin / dolphinscheduler123`

**PostgreSQL 连接信息**：

| 项目 | 值 |
|------|-----|
| Host | `127.0.0.1` |
| Port | `5433`（非标准，避开本机 5432） |
| Database | `dolphinscheduler` |
| User | `root` |
| Password | `root` |

```bash
# psql 快速连接
psql -h 127.0.0.1 -p 5433 -U root -d dolphinscheduler
```

## 3. 一次性启动顺序

```bash
# 1) 起依赖（Postgres + ZK）
docker compose -f docker-compose-dev.yml up -d
docker compose -f docker-compose-dev.yml ps        # 等 healthy

# 2) IDEA 依次启动（顺序不能错）：
#    - org.apache.dolphinscheduler.server.master.MasterServer
#    - org.apache.dolphinscheduler.server.worker.WorkerServer
#    - org.apache.dolphinscheduler.api.ApiApplicationServer

# 3) 起前端
cd dolphinscheduler-ui
npm install        # 首次或依赖变化时
npm run dev        # → http://localhost:5173

# 4) 验证
open http://localhost:5173                                                  # UI
open http://localhost:12345/dolphinscheduler/swagger-ui/index.html          # 接口文档
```

## 4. MySQL 模式（可选）

```bash
bash start-dev-mysql.sh    # 自动起 mysql + zk + 初始化 schema
bash stop-dev-mysql.sh     # 停止
```

> 切换模式后，需要修改各 server 模块 `application.yaml` 的 spring profile / datasource，或参考脚本输出指引。

## 5. 数据库重置

```bash
# 清卷 + 重建（自动跑 init SQL）
docker compose -f docker-compose-dev.yml down -v
docker compose -f docker-compose-dev.yml up -d

# 仅重建表（保留卷）
docker exec -i dolphinscheduler-postgresql \
  psql -U root -d dolphinscheduler \
  < dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_postgresql.sql
```

## 6. 常见坑

| 坑 | 应对 |
|---|------|
| Postgres 端口是 **5433**，不是 5432 | application.yaml 已配 `jdbc:postgresql://127.0.0.1:5433/dolphinscheduler` |
| 默认容器是 **Jetty 不是 Tomcat** | 不要把 Tomcat 配往 ServletContext 塞，已在 `dolphinscheduler-meter` 排除 |
| `mvn test` 触发 jacoco 报错 | 必须用 `./mvnw verify`；详见 `dolphinscheduler-api/CLAUDE.md` |
| 改 dao/service 后 master/worker/api 老行为 | 多模块 jar 缓存过期，重新 install 上游模块 |
| 前端访问 12345 没反应 | 后端是 12345，前端开发用 5173；vite 已代理到后端 |
| Swagger 注解漏了 | 前端代码生成会拿不到类型；新增 controller 必须给 `@Tag/@Operation/@Parameter/@Schema` |
| Worker 起不来报 registry | ZK 没就绪，等 docker compose `ps` 显示 healthy 再起 |

## 7. Swagger / OpenAPI 接入

后端：所有新 controller 必填 swagger 注解（参考 `LoggerController` 风格）。

前端代码生成（推荐）：
```bash
cd dolphinscheduler-ui
npx openapi-typescript-codegen \
  --input http://localhost:12345/dolphinscheduler/v3/api-docs \
  --output src/api/generated --client axios
```

接口契约固化（推荐）：
```bash
# 把 OpenAPI JSON 落档到仓库，PR diff 评审时一眼看到破坏性变更
curl -s http://localhost:12345/dolphinscheduler/v3/api-docs > docs/openapi/snapshot.json
```

---

## 8. Agent 协作规范

### 8.1 进项目第一件事：读这 3 个文件

| 文件 | 作用 |
|------|------|
| `AGENTS.md` | 全局协作规则、GitNexus、Trellis 工作流 |
| 当前模块的 `CLAUDE.md`（如 `dolphinscheduler-api/CLAUDE.md`） | 模块级 gotchas（构建命令、Jetty、Py4J、测试） |
| `.trellis/tasks/<current>/prd.md` + `design.md` + `implement.md` | 当前任务上下文 |

### 8.2 已落地的协作基建（直接使用）

| 工具 | 用途 |
|------|------|
| **Trellis** (`.trellis/`) | 任务规划：prd → design → implement → start → check → finish |
| **GitNexus** MCP | 代码图谱：`gitnexus_impact` / `context` / `query` / `detect_changes` |
| **springdoc-openapi** | 后端接口契约自动生成 |
| **docker-compose-dev*.yml + start-dev-mysql.sh** | 依赖一键起 |
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

### 8.5 Agent 不要做的事

- ❌ 跳过 `gitnexus_impact` 直接编辑
- ❌ 用 `mvn test`（会触发 jacoco 错误，用 `./mvnw verify`）
- ❌ 在 master 分支直接 commit
- ❌ 用 `--no-verify` / `--no-gpg-sign` 绕过 hook
- ❌ 加注释只为说明"做了什么"（命名能表达就不写注释）
- ❌ 不读现有任务就建新任务（先 `task.py list --mine`）
- ❌ 在没有用户授权时 push / 创建 PR / 删分支

---

## 9. 推荐补齐的自动化（路线图）

以下当前**还没有**，但是会显著提升 agent 协作效率：

| 项目 | 状态 | 价值 |
|------|------|------|
| 单一入口 `dev.sh up\|reset-db\|status\|down` | 待做 | agent 不必每次现学 docker compose |
| OpenAPI snapshot + PR diff 检查 | 待做 | 破坏性接口变更可见 |
| 前端 `openapi-typescript-codegen` | 待做 | TS 类型与后端 spec 同步 |
| `scripts/smoke.sh` | 待做 | agent 自我验收脚本（端口、登录、关键接口） |
| `.trellis/spec/api-conventions.md` | 待做 | 把 swagger 注解必填等规则结构化 |
| `.trellis/spec/audit-conventions.md` | 待做 | 把 @OperatorLog 必加规则结构化 |

---

## 10. 相关文档

- `AGENTS.md` —— GitNexus、Trellis 全局工作流
- `dolphinscheduler-api/CLAUDE.md` —— API 模块测试、Jetty、Py4J
- `dolphinscheduler-master/CLAUDE.md` —— Master 模块（如有）
- `dolphinscheduler-ui/.env.development` —— 前端开发环境变量
- `docker-compose-dev.yml` / `docker-compose-dev-mysql.yml` —— 依赖编排
- `start-dev-mysql.sh` / `stop-dev-mysql.sh` —— MySQL 模式启动脚本
- `.trellis/tasks/` —— 当前所有任务规划
