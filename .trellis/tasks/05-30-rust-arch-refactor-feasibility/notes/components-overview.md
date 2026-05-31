# DolphinScheduler 组件全景（28 个 Maven 模块）

> 数据来源：`ls dolphinscheduler-*/pom.xml`（实测 28 个顶层 pom），各模块 `<dependencies>` 原文，
> `grep -r "@SpringBootApplication"` 确认主类，`grep -r "@RpcService"` 确认 RPC 接口，
> `ls dolphinscheduler-registry/dolphinscheduler-registry-plugins/` 确认注册中心后端。
> 时间：2026-05-31

---

## 一、进程角色（可独立部署的服务）

| 模块 | 主类（全限定） | 核心职责 | 关键依赖（取自 pom.xml） | 可独立部署 |
|------|--------------|---------|------------------------|----------|
| **dolphinscheduler-master** | `o.a.d.server.master.MasterServer` | 工作流编排引擎：消费 `t_ds_command` → 解析 DAG → 分配任务到 Worker；HA 选主；定时触发（Quartz）；failover | dolphinscheduler-service, dolphinscheduler-common, dolphinscheduler-meter, dolphinscheduler-registry-all, dolphinscheduler-scheduler-all, dolphinscheduler-datasource-api, dolphinscheduler-task-api, dolphinscheduler-storage-api, dolphinscheduler-extract-master, dolphinscheduler-extract-worker, dolphinscheduler-eventbus, dolphinscheduler-task-executor, hadoop-client (Hadoop YARN/HDFS 调度) | 是 |
| **dolphinscheduler-worker** | `o.a.d.server.worker.WorkerServer` | 物理任务执行：接收 RPC 分发 → `PhysicalTaskExecutorFactory` → 运行插件 → 回报生命周期事件；Worker 组注册；负载保护 | dolphinscheduler-common, dolphinscheduler-meter, dolphinscheduler-registry-all, dolphinscheduler-task-api, dolphinscheduler-task-all(provided), dolphinscheduler-datasource-api, dolphinscheduler-storage-api, dolphinscheduler-extract-alert, dolphinscheduler-extract-master, dolphinscheduler-extract-worker, dolphinscheduler-eventbus, dolphinscheduler-task-executor, dolphinscheduler-yarn-aop, aws-java-sdk-s3 | 是 |
| **dolphinscheduler-api** | `o.a.d.api.ApiApplicationServer` | REST API 服务器（Jetty）：工作流/任务/用户/数据源/资源管理 CRUD；swagger-ui；Py4J 网关（Python SDK）；多认证方式 | dolphinscheduler-service, dolphinscheduler-dao, dolphinscheduler-meter, dolphinscheduler-common, dolphinscheduler-spi, dolphinscheduler-datasource-api, dolphinscheduler-task-api, dolphinscheduler-registry-all, dolphinscheduler-scheduler-all, dolphinscheduler-storage-api, dolphinscheduler-extract-master, dolphinscheduler-extract-worker, dolphinscheduler-extract-alert, spring-boot-starter-jetty, py4j, kubernetes-client | 是 |
| **dolphinscheduler-alert** (子模块: alert-server) | `o.a.d.alert.AlertServer` | 告警服务：接收 RPC 告警请求 → 持久化 → 异步分发到各渠道插件（Email/DingTalk/Feishu/Slack/Webhook 等）；HA | dolphinscheduler-extract-alert, dolphinscheduler-dao, dolphinscheduler-registry-all, dolphinscheduler-meter, dolphinscheduler-spi | 是 |
| **dolphinscheduler-standalone-server** | `o.a.d.StandaloneServer` | 单机部署模式：将 master + worker + api + alert-server 合并为同一 JVM，内嵌 H2 数据库，无需外部 ZK | dolphinscheduler-master, dolphinscheduler-worker, dolphinscheduler-api, dolphinscheduler-alert-server, dolphinscheduler-alert-all, dolphinscheduler-task-all, dolphinscheduler-datasource-all, dolphinscheduler-storage-all | 是（单机） |

---

## 二、共享库（不独立部署）

| 模块 | 职责 | 关键依赖（取自 pom.xml） | 可独立部署 |
|------|------|------------------------|----------|
| **dolphinscheduler-common** | 基础工具层：常量/枚举（`WorkflowExecutionStatus`, `TaskExecutionStatus`）、JSON/String/Date/File 工具、`ShellCommandExecutor`、DAG 图结构、`IStoppable` 生命周期接口 | dolphinscheduler-aws-authentication, commons-io, httpclient, guava, spring-context, spring-boot-starter-aop, jackson-databind, commons-collections4, commons-lang3, oshi-core, netty-all, okhttp, logback-classic, cloud SDKs (aliyun/huawei/qcloud/azure, optional) | 否 |
| **dolphinscheduler-dao** | 数据访问层：MyBatis-Plus Mapper + Entity（`t_ds_*` 全部表）+ Repository 接口；SQL 初始化/升级脚本（MySQL/PostgreSQL/H2） | dolphinscheduler-common, dolphinscheduler-task-api, dolphinscheduler-dao-plugin-all, spring-boot-starter, HikariCP, mybatis-plus, mybatis-plus-boot-starter, jackson | 否 |
| **dolphinscheduler-service** | 业务逻辑共享层（master/api 共用）：`ProcessServiceImpl`（工作流生命周期）、`CommandServiceImpl`（入队/消费 Command）、`CronService`（cron 解析）、告警桥接、参数展开 | dolphinscheduler-dao, dolphinscheduler-spi, dolphinscheduler-registry-api, dolphinscheduler-task-api, dolphinscheduler-extract-master, dolphinscheduler-extract-worker, cron-utils, micrometer-core(provided) | 否 |
| **dolphinscheduler-spi** | SPI 基础设施：`PrioritySPIFactory`、`DataSourceChannelFactory`、`PluginParamsTransfer`（UI 表单描述）、`DbType` 枚举 | dolphinscheduler-common(provided), slf4j-api | 否 |
| **dolphinscheduler-bom** | Maven BOM，统一全项目依赖版本，无代码 | — | 否 |

---

## 三、通信与协调

| 模块 | 职责 | 子模块 / 后端 | 关键依赖 | 可独立部署 |
|------|------|--------------|---------|----------|
| **dolphinscheduler-extract** | RPC 接口层（不含实现）：定义所有跨进程调用合约；基于 Netty 自研 RPC 框架（`@RpcService`/`@RpcMethod`）。子模块：`extract-base`（传输/序列化/注解）、`extract-common`（`ILogService`）、`extract-master`（`IWorkflowControlClient`, `IMasterContainerService`, `IWorkflowMetricService`）、`extract-worker`（`IPhysicalTaskExecutorOperator`, `IStreamingTaskInstanceOperator`, `ITaskExecutorQueryClient`）、`extract-alert`（`IAlertOperator`） | extract-base / extract-common / extract-master / extract-worker / extract-alert | netty-all（via extract-base）| 否（接口定义库） |
| **dolphinscheduler-registry** | 插件化服务注册发现：节点注册（临时节点）、KV 存储、Watch 订阅、分布式锁；子模块：`registry-api`（SPI）、`registry-plugins`（ZooKeeper/Etcd/JDBC 三套实现）、`registry-all`（聚合） | dolphinscheduler-registry-zookeeper（Curator）, dolphinscheduler-registry-etcd, dolphinscheduler-registry-jdbc | curator-framework (ZK), jetcd-core (Etcd) | 否 |
| **dolphinscheduler-eventbus** | 进程内延迟事件总线：`AbstractDelayEventBus`（`DelayQueue` 实现）；仅跨线程，不跨 JVM | — | 无外部依赖 | 否 |
| **dolphinscheduler-meter** | Micrometer 指标收集 + Spring Boot Actuator 暴露（`/actuator/prometheus`）；服务器负载保护 `ServerLoadProtection` 接口；Grafana 示例 dashboard | — | dolphinscheduler-common, spring-boot-starter-actuator, spring-boot-starter-jetty, micrometer-registry-prometheus | 否 |

---

## 四、插件体系

| 模块 | 职责 | 子模块 / 插件数 | 关键依赖 | 可独立部署 |
|------|------|----------------|---------|----------|
| **dolphinscheduler-task-plugin** | 任务类型插件族：SPI (`TaskChannelFactory` + `AbstractTask`) + 34 个具体插件（shell/python/java/sql/procedure/spark/flink/flink-stream/mr/hivecli/seatunnel/datax/chunjun/sqoop/linkis/k8s/kubeflow/emr/emr-serverless/sagemaker/dms/datasync/datafactory/aliyunserverlessspark/http/grpc/jupyter/zeppelin/mlflow/openmldb/dvc/dinky/remoteshell/pytorch） | task-api + task-all + 34 个 task-xxx | dolphinscheduler-spi; 各插件自带独立外部 SDK | 否 |
| **dolphinscheduler-datasource-plugin** | 数据源连接插件族：JDBC 连接池封装，支持 MySQL/PostgreSQL/Hive/ClickHouse/Oracle/Trino/Presto/StarRocks/DolphinDB/DB2/Dameng 等 20+ 种数据源 | datasource-api + datasource-all + 20+ datasource-xxx | HikariCP/Druid; 各 JDBC driver | 否 |
| **dolphinscheduler-storage-plugin** | 资源中心存储插件族：文件/Jar/资源上传下载；支持 S3/HDFS/OSS/GCS/Azure Blob/OBS/COS | storage-api + storage-all + storage-s3/hdfs/oss/gcs/abs/obs/cos | AWS SDK / Hadoop HDFS / 各云 SDK | 否 |
| **dolphinscheduler-scheduler-plugin** | 触发调度插件族：当前唯一实现 Quartz；`SchedulerApi` 接口注册/删除 Cron Job，触发后向 `t_ds_command` 写行 | scheduler-api + scheduler-all + scheduler-quartz | quartz; 独立 Quartz 数据源（`QRTZ_*` 表） | 否 |
| **dolphinscheduler-dao-plugin** | DAO 方言适配：MySQL/PostgreSQL/H2 SQL 差异处理 | dao-plugin-api + dao-plugin-mysql/postgresql/h2 | 对应 JDBC driver | 否 |

---

## 五、运维工具

| 模块 | 职责 | 主类（有则列出） | 关键依赖 | 可独立部署 |
|------|------|----------------|---------|----------|
| **dolphinscheduler-tools** | 运维 CLI 工具集：数据库升级（`UpgradeDolphinScheduler`）、血缘迁移（`MigrateLineage`）、资源迁移（`MigrateResource`）；不长期运行 | `o.a.d.tools.datasource.UpgradeDolphinScheduler` 等 | dolphinscheduler-dao, dolphinscheduler-storage-all, mysql-connector-j, postgresql | 是（一次性运行） |
| **dolphinscheduler-authentication** | 子模块 `dolphinscheduler-actuator-authentication`：保护 `/actuator/**` 端点；支持 LDAP/Casdoor/SSO | — | spring-ldap-core, casdoor-spring-boot-starter | 否（库） |
| **dolphinscheduler-yarn-aop** | AspectJ 拦截 `YarnClientImpl.submitApplication`，将 YARN ApplicationId 写入 `appInfo.log` 以便 master 追踪 YARN 任务状态 | — | aspectjweaver, aspectjrt, hadoop-yarn-client, hadoop-common | 否（织入库） |
| **dolphinscheduler-microbench** | JMH 微基准测试（RPC 性能等），不用于生产 | — | jmh-core; dolphinscheduler-extract-base | 否 |
| **dolphinscheduler-dist** | 打包 assembly：生成发布 tar.gz，无运行时代码 | — | — | 否（构建工件） |
| **dolphinscheduler-api-test** | 集成测试：Docker Compose + Testcontainers，测试 API Server 行为 | — | testcontainers | 否 |
| **dolphinscheduler-e2e** | End-to-end 测试：浏览器 UI 测试（Selenium/Playwright） | — | selenium/playwright | 否 |
| **dolphinscheduler-ui** | 前端：Vue 3 + TypeScript + Vite + Naive UI；不在 Rust 重写讨论范围 | — | node/npm | 否 |

---

## 模块依赖关系图

```
                ┌─────────────────────────────────────────────────┐
                │              进程层（可独立运行）                  │
                │  master ──────→ service ──→ dao ──→ common      │
                │  worker ──────→ (无service)──→ common           │
                │  api    ──────→ service, dao                    │
                │  alert  ──────→ dao                             │
                └─────────────────────────────────────────────────┘
                         │              │              │
                         ▼              ▼              ▼
               extract (RPC 接口)  registry (ZK/etcd/jdbc)  eventbus
                         │
               ┌─────────┴──────────────────┐
               ▼                            ▼
     extract-master / -worker / -alert   extract-base (Netty RPC)

  worker ──→ task-plugin (SPI) ──→ task-executor ──→ eventbus
             │
             ├── task-shell / -python / -http  (ShellCommandExecutor)
             ├── task-sql / -procedure          (DataSourceClientProvider → JDBC)
             ├── task-spark / -flink / -mr      (AbstractYarnTask → ShellCommandExecutor)
             ├── task-hivecli                   (ShellCommandExecutor → hive/beeline CLI)
             └── task-k8s / -kubeflow          (K8sTaskExecutor → fabric8 KubernetesClient)

  api, master, worker ──→ storage-plugin (S3/HDFS/OSS/GCS/ABS/OBS/COS)
  master ──────────────→ scheduler-plugin (Quartz)
  api ──────────────────→ datasource-plugin (20+ JDBC 驱动)
```

---

## JVM 链路依赖标注

本节列出 Worker 侧各主流任务类型的实际执行链路，明确指出哪些环节**必须在 JVM 内完成**（即无法简单替换为原生/Rust 实现）。

### 1. Shell 任务

**链路：**
`ShellTask.handle()` → `ShellCommandExecutor.run(script)` → `ProcessBuilder.start()` → fork OS 进程

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.shell.ShellTask` — 参数展开、脚本文件生成
- `org.apache.dolphinscheduler.plugin.task.api.ShellCommandExecutor` — 进程管理、stdout 流读取、appId 解析
- `org.apache.dolphinscheduler.plugin.task.api.AbstractTask` — 生命周期事件发布

**JVM 必要原因：** ShellCommandExecutor 负责实时读取进程 stdout（含 YARN appId 捕获）、超时控制、kill 信号；输出解析逻辑（`${out}` 变量提取）在 JVM 中完成。

---

### 2. HTTP 任务

**链路：**
`HttpTask.handle()` → `OkHttpUtils.sendRequest()` → OkHttp3 HTTP 调用 → 响应结果校验

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.http.HttpTask` — 请求构建、响应解析、条件判断
- `org.apache.dolphinscheduler.common.utils.OkHttpUtils` — OkHttp3 调用层

**JVM 必要原因：** 整个请求生命周期在 JVM 内；响应体的条件判断使用 `HttpCheckCondition` 枚举（STATUS_CODE_DEFAULT / STATUS_CODE_CUSTOM / BODY_CONTAINS / BODY_NOT_CONTAINS）switch-case 实现，无脚本引擎依赖。

---

### 3. Python 任务

**链路：**
`PythonTask.handle()` → 生成 `.py` 脚本文件 → `ShellCommandExecutor.run("python3 <script>")` → fork python 进程

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.python.PythonTask` — 脚本内容生成（含参数替换）、命令行构建
- `org.apache.dolphinscheduler.plugin.task.api.ShellCommandExecutor` — 进程管理同 Shell

**JVM 必要原因：** Python 脚本本身在子进程中运行，但脚本内容生成（含 `${param}` 展开）、进程监控、输出变量提取（`${out}` 回写 VarPool）全在 JVM。

---

### 4. Hive SQL 任务（`hivecli`）

**链路：**
`HiveCliTask.handle()` → 读取 SQL 内容（从资源中心或内嵌 SQL 文本）→ 生成 `hive -e "..."` 或 `beeline` 命令 → `ShellCommandExecutor.run()` → fork hive CLI 进程

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.hivecli.HiveCliTask` — SQL 内容读取、命令行拼装
- `org.apache.dolphinscheduler.plugin.task.api.ShellCommandExecutor` — 进程管理
- 若通过 `task-sql` + JDBC 方式：`org.apache.dolphinscheduler.plugin.task.sql.SqlTask` → `org.apache.dolphinscheduler.plugin.datasource.api.plugin.DataSourceClientProvider` → Hive JDBC Driver（`HiveDriver`）

**JVM 必要原因：** 两条路径都在 JVM 内；CLI 路径依赖 JVM 进行 SQL 文件生成和 Kerberos Token 处理；JDBC 路径 Hive JDBC Driver 本身是纯 Java。

---

### 5. Spark 任务

**链路：**
`SparkTask.handle()` → `AbstractYarnTask.handle()` → `getScript()` → `ShellCommandExecutor.run("spark-submit ...")` → fork `spark-submit` 进程

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.spark.SparkTask` — 拼装 `spark-submit` 命令（`--master`, `--class`, `--jars`, 用户参数）
- `org.apache.dolphinscheduler.plugin.task.api.AbstractYarnTask` — YARN appId 从 stdout 解析、日志追踪
- `org.apache.dolphinscheduler.plugin.task.api.ShellCommandExecutor` — 进程管理
- `dolphinscheduler-yarn-aop`：`YarnClientAspect`（AspectJ Aspect）— 拦截 `YarnClientImpl.submitApplication`，捕获 ApplicationId 写入 `appInfo.log`

**JVM 必要原因：** YARN ApplicationId 追踪依赖 AspectJ 织入（`dolphinscheduler-yarn-aop`）；`spark-submit` 是子进程，但 appId 捕获、kill（`yarn application -kill`）、日志拉取全在 JVM 中协调。

---

### 6. Flink 任务

**链路：**
`FlinkTask.handle()` → `AbstractYarnTask.handle()` → `getScript()` → `ShellCommandExecutor.run("flink run ...")` → fork `flink run` 进程

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.flink.FlinkTask` — 拼装 `flink run`/`flink run-application` 命令
- `org.apache.dolphinscheduler.plugin.task.api.AbstractYarnTask` — 同 Spark，YARN appId 追踪
- `org.apache.dolphinscheduler.plugin.task.api.ShellCommandExecutor` — 进程管理

**JVM 必要原因：** 与 Spark 相同，YARN 模式下依赖 AspectJ 织入；Flink standalone/K8s 模式命令行拼装、结果解析也在 JVM。

---

### 7. K8s 任务

**链路：**
`K8sTask.handle()` → `AbstractK8sTask.handle()` → `K8sTaskExecutor.submitJob2k8s()` → fabric8 `KubernetesClient` 创建 Batch/Job → Watch 回调等待 Job 完成

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.k8s.K8sTask` — K8s Job 参数构建（镜像、资源限制、环境变量）
- `org.apache.dolphinscheduler.plugin.task.api.k8s.impl.K8sTaskExecutor` — `submitJob2k8s()`、fabric8 `Watcher<Job>` 状态轮询、`LogWatch` 日志流
- `io.fabric8.kubernetes.client.KubernetesClient`（fabric8）— K8s API Server 通信

**JVM 必要原因：** fabric8 KubernetesClient 是纯 Java；Job 创建/Watch/删除全部走 JVM 内 HTTP 长连接（fabric8 管理）；Worker 进程必须存活直到 K8s Job 完成（异步 Watch 回调在 Worker JVM 线程中）。

---

### 8. DataX 任务

**链路：**
`DataxTask.handle()` → `buildDataxJsonFile()` 生成 DataX 配置 JSON → `buildCommand()` 拼装 `${PYTHON_LAUNCHER} ${DATAX_LAUNCHER} <config>` → `ShellCommandExecutor.run()` → fork python 进程运行 DataX

**必须走 JVM 的类：**
- `org.apache.dolphinscheduler.plugin.task.datax.DataxTask` — JSON 配置生成（Reader/Writer 配置从 JDBC 元数据查询）、命令拼装
- `org.apache.dolphinscheduler.plugin.datasource.api.plugin.DataSourceClientProvider` — DataxTask 在生成配置时通过 JDBC 获取源端/目标端表结构元数据
- `org.apache.dolphinscheduler.plugin.task.api.ShellCommandExecutor` — 进程管理

**JVM 必要原因：** DataX 配置 JSON 中的 Reader/Writer 参数（列名、JDBC URL、密码脱敏）由 Java 代码查询数据库元数据后填充，JVM 是 DataX 配置的**唯一生成者**；DataX Python 引擎本身在子进程中运行。

---

### 汇总：必须保留 JVM 组件的任务维度

| 任务类型 | Worker 侧最小 JVM 职责 | 能否剥离到 Rust 的部分 |
|---------|----------------------|----------------------|
| Shell | 脚本生成、进程管理、stdout 解析、VarPool 回写 | 进程 fork/kill 可用 Rust，但输出变量解析涉及 Java 表达式需保留 JVM |
| HTTP | 全部（`HttpCheckCondition` 枚举条件判断） | HTTP 发送可用 Rust，条件判断为枚举 switch-case，技术上可移植 |
| Python | 脚本模板生成、参数展开、进程管理 | 进程管理可用 Rust，模板展开可移植 |
| Hive SQL (hivecli) | SQL 文件生成、CLI 命令拼装、进程管理 | 进程管理可用 Rust，SQL 展开可移植 |
| Hive SQL (task-sql/JDBC) | JDBC 连接（HiveDriver 是 Java）、ResultSet 处理 | **无法剥离**，HiveDriver 是纯 Java 库 |
| Spark | `spark-submit` 命令拼装、YARN appId 追踪（AspectJ）、进程管理 | 命令拼装可移植；AspectJ 织入无法用 Rust 替代，必须保留 JVM |
| Flink | 同 Spark | 同 Spark |
| K8s | fabric8 KubernetesClient 全部调用 | **无法剥离**，K8s Watch 回调在 JVM 线程中；可替换为 Rust k8s-openapi，但需完全重写 |
| DataX | JDBC 元数据查询 + JSON 配置生成 + 进程管理 | JDBC 元数据查询需保留 JVM（或重写为 Rust JDBC bridge） |

**结论：** 所有任务类型在 Worker 侧都依赖 JVM 用于至少一个核心操作（参数展开、JDBC 调用、AspectJ 织入或 fabric8 K8s SDK）。**完全去除 Worker JVM 不可行**；可行方向是将任务调度分发（Master 的 DAG 状态机部分）提取为 Rust 侧 sidecar，Worker 保留 JVM。
