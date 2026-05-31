# DolphinScheduler 组件全景（28 个 Maven 模块）

> 数据来源：根目录 `ls dolphinscheduler-*` + 各模块 `pom.xml` + 项目 README / CLAUDE.md。
> 时间：2026-05-30

## 一、顶层服务（4 个独立进程）

| 模块 | 进程角色 | 核心职责 | 关键依赖 |
|------|---------|---------|---------|
| **dolphinscheduler-master** | Master Server | 工作流编排引擎：扫 `t_ds_command` → 解析 DAG → 拆任务 → 分发；failover、容错、依赖判断、定时触发 | dolphinscheduler-service, registry, extract-master |
| **dolphinscheduler-worker** | Worker Server | 任务执行引擎：接收任务 → 启动 TaskExecutor → 跑 Shell/SQL/Spark/Flink → 回报状态 | task-plugin（多个）, registry, extract-worker |
| **dolphinscheduler-api** | API Server | REST 服务：工作流/任务 CRUD、用户/数据源/资源管理、审计、登录、Swagger | service, datasource-plugin, alert |
| **dolphinscheduler-alert** | Alert Server | 告警服务：邮件/钉钉/微信/飞书/Slack/Webhook，独立进程 | spi（插件加载） |

## 二、共享基础库（不独立部署）

| 模块 | 作用 |
|------|------|
| **dolphinscheduler-common** | 常量、枚举、通用工具（Date/JSON/File/Network）、配置类、线程池基类 |
| **dolphinscheduler-dao** | 数据访问层：MyBatis-Plus Mapper、Entity（t_ds_*）、SQL 脚本与升级脚本（mysql/postgres/h2 三方言） |
| **dolphinscheduler-service** | Service 层共享业务逻辑（master/worker/api 都依赖），如 process service、command service |
| **dolphinscheduler-spi** | SPI 抽象接口（Plugin Loader 基础设施） |
| **dolphinscheduler-bom** | Maven BOM 统一依赖版本 |

## 三、通信与协调

| 模块 | 作用 |
|------|------|
| **dolphinscheduler-extract** | RPC 子系统（基于 Netty）：master ↔ worker ↔ api 之间所有 RPC 接口定义 + 实现框架 |
| **dolphinscheduler-registry** | 服务注册发现：ZooKeeper / JDBC / Etcd 三套实现，节点心跳、leader 选举、failover 触发 |
| **dolphinscheduler-eventbus** | 进程内事件总线 |
| **dolphinscheduler-meter** | Micrometer 监控指标（Prometheus 暴露） |

## 四、插件体系（典型可扩展点）

| 模块 | 作用 |
|------|------|
| **dolphinscheduler-task-plugin** | **任务类型**插件：30+ 子模块（shell/sql/spark/flink/python/http/k8s/datax/seatunnel/mlflow…），每个实现 `TaskChannel` |
| **dolphinscheduler-datasource-plugin** | **数据源**插件：mysql/postgres/hive/clickhouse/oracle/redshift/presto/trino/starrocks…，连接池 + JDBC URL 拼装 |
| **dolphinscheduler-storage-plugin** | **资源中心存储**：HDFS/S3/OSS/GCS/Azure Blob/本地 FS |
| **dolphinscheduler-scheduler-plugin** | **调度策略**：当前主要是 Quartz |
| **dolphinscheduler-dao-plugin** | DAO 方言适配 |

## 五、运维 / 工具 / 部署

| 模块 | 作用 |
|------|------|
| **dolphinscheduler-tools** | DDL 升级、库初始化、数据迁移、JWT 密钥生成等 CLI 工具集 |
| **dolphinscheduler-dist** | 打包发布（assembly 二进制 tar.gz） |
| **dolphinscheduler-standalone-server** | 单机模式（master + worker + api + alert + h2 内嵌） |
| **dolphinscheduler-task-executor** | 任务执行器抽象层（Worker 内部执行框架，PhysicalTaskExecutor 等） |
| **dolphinscheduler-authentication** | 登录鉴权（账号密码、LDAP、Casdoor、OAuth） |
| **dolphinscheduler-yarn-aop** | YARN 日志 AOP（拦截 Hadoop 客户端，把任务执行日志统一到 ds 日志体系） |
| **dolphinscheduler-microbench** | JMH 基准测试 |
| **dolphinscheduler-api-test / e2e** | 集成与 e2e 测试 |
| **dolphinscheduler-ui** | Vue 3 + TS + Vite + Naive UI 前端（不在 Rust 重写讨论范围） |

## 模块依赖大图

```
dolphinscheduler-master ──┐
                          ├─→ service ─→ dao ─→ common
dolphinscheduler-worker ──┤              │
                          │              └─→ dao-plugin
dolphinscheduler-api ─────┘
                          │
                          ├─→ extract（RPC）
                          ├─→ registry（ZK/JDBC/Etcd）
                          └─→ alert
dolphinscheduler-worker ──→ task-plugin ─→ task-executor
dolphinscheduler-api ─────→ datasource-plugin
all ─────────────────────→ storage-plugin
```
