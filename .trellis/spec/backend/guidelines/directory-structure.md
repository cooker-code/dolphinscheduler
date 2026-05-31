# 目录结构 — 后端

## 模块分层（从底到顶）

```
dolphinscheduler-common          ← 基础工具库（无内部依赖，底层）
dolphinscheduler-spi             ← 插件 SPI 契约
dolphinscheduler-dao             ← 实体、Mapper、Repository、SQL 迁移脚本
dolphinscheduler-service         ← 业务逻辑（dao 和各服务器之间的中间层）
dolphinscheduler-api             ← REST API 服务（Spring Boot + Jetty，端口 12345）
dolphinscheduler-master          ← 工作流编排引擎（RPC 端口 5679）
dolphinscheduler-worker          ← 任务执行服务器（RPC 端口 1235）
dolphinscheduler-alert           ← 告警服务器（HTTP 50053，RPC 50052）
dolphinscheduler-extract-*       ← RPC 接口契约（extract-master/worker/alert）
dolphinscheduler-task-executor   ← 任务生命周期通用框架
dolphinscheduler-task-plugin     ← 具体任务实现（shell/sql/spark/python…）
dolphinscheduler-registry-*      ← 注册中心插件（ZK / JDBC / Etcd）
dolphinscheduler-datasource-*    ← 数据源插件
dolphinscheduler-storage-*       ← 存储插件
dolphinscheduler-scheduler-*     ← 调度插件（Quartz）
```

## 各模块关键子包

### dolphinscheduler-common
- `common.utils` — 无状态工具方法（最常改动的包）
- `common.constants` — `Constants`、`DateConstants`
- `common.enums` — `WorkflowExecutionStatus`、`TaskExecutionStatus`、`Flag` 等（**改名会级联破坏，必须全局 grep**）
- `common.graph` — 通用 DAG 结构（被 master 用于工作流图遍历）
- `common.lifecycle` — `IStoppable`（每个长生命周期服务必须实现）

### dolphinscheduler-dao
- `dao.entity` — 持久化 POJO，1:1 对应数据库表
- `dao.mapper` — MyBatis `@Mapper` 接口，每张表一个
- `dao.repository` — **上层代码应调用这里，而非直接调 mapper**
- `dao.model` — DAO 专用 DTO（查询投影、聚合结果）

### dolphinscheduler-service
- `service.process` — `ProcessServiceImpl`：事实上的编排门面（上帝类，设计如此）
- `service.command` — `CommandServiceImpl`：`t_ds_command` 行的入队/消费
- `service.cron` — cron 解析（用 `cron-utils` 库，**不是直接用 Quartz 表达式**）
- `service.expand` — `CuringParamsServiceImpl`：执行时展开 `${paramName}` 占位符

### dolphinscheduler-api
- `api.controller` — 30+ `@RestController`，按域划分，URL 前缀均为 `/dolphinscheduler/*`
- `api.service` / `api.service.impl` — API 业务逻辑，包装 service 层
- `api.security` — 可插拔认证器（PASSWORD / LDAP / OIDC / CASDOOR）
- `api.interceptor` — `LoginHandlerInterceptor`（session cookie 检查）、`RateLimitInterceptor`
- `api.exceptions` — `ApiExceptionHandler`（`@RestControllerAdvice`）

### dolphinscheduler-master
- `server.master.engine` — 工作流执行引擎（命令处理器、状态机、事件总线）**编排逻辑的核心所在**
- `server.master.rpc` — 实现 `extract-master` 中的 RPC 契约
- `server.master.cluster` — Worker 集群视图和负载均衡（决定任务派发给哪个 worker）
- `server.master.failover` — **最高风险代码路径**（死节点检测和在途工作流恢复）

### dolphinscheduler-worker
- `server.worker.executor` — 与 `task-executor` 的桥接
- `server.worker.rpc` — 实现 `extract-worker` 中的 RPC 契约
- `server.worker.config` — 负载保护阈值（CPU/内存/任务数）

## 依赖规则（禁止违反）

| 模块 | 可依赖 | 禁止依赖 |
|------|--------|---------|
| `common` | 无内部依赖 | 任何 DS 模块 |
| `dao` | `common` | `service`、`api`、`master`、`worker` |
| `service` | `dao`、`extract-*` | `api`、`master`、`worker` 实现 |
| `api` | `service`、`dao` | `master`、`worker` 实现 |
| `master` | `service`、`dao`、`extract-worker`、`eventbus` | `api`、`worker` 实现 |
| `worker` | `task-executor`、`task-plugin`、`extract-master` | `api`、`master` 实现 |

> 服务模块间通过 `extract-*` 接口通信，禁止直接依赖对方的实现模块。
