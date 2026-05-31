# 明确不建议用 Rust 重写的组件清单

> 取证日期：2026-05-31
> 所有 jar 包版本来自真实 `dolphinscheduler-bom/pom.xml` 及各插件 pom.xml，未做任何估算。

---

## 核心原则

任何重写组件，如果满足以下任一条件，则**不建议**：
1. 强依赖 JVM 生态（Hadoop/Hive/Spark/Flink/Quartz 等无 Rust 替代品）
2. 是项目核心调度 IP 且 corner case 极多（master DAG 引擎）
3. 重写后破坏现有插件契约或上游 SDK 兼容（task-plugin / datasource-plugin）
4. 收益 < 改造代价的数量级（dao 全套 Mapper 迁移）

---

## 具体清单

### 1. master 调度引擎

**不可绕过的 JVM 依赖**

- Spring Boot `2.7.11`（`spring-boot.version`，`dolphinscheduler-bom/pom.xml:32`）
- 整个模块 278 个 Java 源文件，合计 **23,455 行**（`wc -l` 实测）
- 最重的类（行数）：
  - `DependentExecute.java` 468 行 — 跨 workflow 依赖拓扑判断
  - `TaskGroupCoordinator.java` 467 行 — 任务组令牌管理
  - `WorkflowExecutionGraph.java` 354 行 — 运行时 DAG 图
  - `AbstractTaskStateAction.java` 309 行 — 状态机基类（十几个子类）
  - `FailoverCoordinator.java` 282 行 — master/worker 故障转移

**是否有 Rust 等价物**

- DAG 执行引擎：无直接等价物。`petgraph`（crates.io）提供图算法，但持久化调度上下文、失败重试、补数（backfill）、子 workflow、参数透传逻辑无任何现成 crate。
- 8 年社区积累的 corner case 不可能通过引入新 crate 自动覆盖。

**若强行重写的最低代价**

- 用 fork 子进程调用 Java 方案（`std::process::Command`）：每次调度决策都需要 JVM 冷启动或保持常驻，进程边界导致内存共享模型完全丧失，状态一致性需要序列化/反序列化，性能倒退。
- 需同步重写失败转移、补数、子流程、人工审批、参数替换等所有上层逻辑，工作量估算 > 5 人年。

**决策：永久 no-go**

---

### 2. datasource-plugin — Hive / Presto / Trino / Kyuubi

**不可绕过的 JVM 依赖**（均来自 `dolphinscheduler-bom/pom.xml`）

| 组件 | Jar 包 | 版本 |
|---|---|---|
| Hive | `org.apache.hive:hive-jdbc` | **2.3.9** |
| Kyuubi | `org.apache.kyuubi:kyuubi-hive-jdbc-shaded` | **1.7.0** |
| Presto | `com.facebook.presto:presto-jdbc` | **0.238.1** |
| Trino | `io.trino:trino-jdbc` | **402** |

以 Hive 为例（`dolphinscheduler-datasource-hive/pom.xml`）：该插件同时依赖 `org.apache.hadoop:hadoop-client`（`hadoop.version=3.2.4`），因为 HiveServer2 认证需要 Hadoop UGI（`UserGroupInformation`）——这是一个 Kerberos/SPNEGO 实现，完全 JVM 原生，无 Rust 绑定。

**是否有 Rust 等价物**

- `hive-jdbc` — 无。HiveServer2 Thrift wire protocol 理论上可用 Rust Thrift 生成代码实现，但需自建连接池、Kerberos 握手、`TTransport` 层。
- `presto-jdbc` / `trino-jdbc` — Trino 有非官方 Rust 客户端（`prusto`，crates.io，最后更新 2022 年，未维护），不能用于生产。
- `kyuubi-hive-jdbc-shaded` — 无。Kyuubi 扩展了 HiveServer2 协议，无 Rust 实现。

**若强行重写的最低代价**

- fork 子进程运行 Java JDBC 代理：需要维护一个轻量 Java JDBC proxy 服务（类似 datahub/trino-gateway 模式），Rust 通过 HTTP/gRPC 调用它。这等于新增一个 JVM sidecar，维护成本不减反增。
- Kerberos 认证在 Rust 中需要 `libkrb5` 系统库绑定（`cross-krb5` crate），并自行实现 HiveServer2 SASL 握手，工程风险极高。

**决策：永久 no-go（新建 Rust 服务可用原生 PG/MySQL 客户端，但不替换现有 JDBC 插件）**

---

### 3. storage-plugin — HDFS

**不可绕过的 JVM 依赖**（来自 `dolphinscheduler-storage-hdfs/pom.xml`）

```
org.apache.hadoop:hadoop-common    3.2.4   (scope: provided)
org.apache.hadoop:hadoop-hdfs      3.2.4   (scope: provided)
```

**是否有 Rust 等价物**

- `libhdfs` 的 Rust 绑定：`hdfs-native`（crates.io，2023 年，活跃但小众）支持 HDFS native protocol，**不支持 NameNode HA / Federation / Kerberos SPNEGO**（生产环境几乎必需）。
- Hadoop `CompatibilityChecker`、`FileSystem` 抽象接口在 Rust 侧完全缺失。

**若强行重写的最低代价**

- fork 子进程调用 `hdfs dfs` 命令：每次文件操作都需启动 JVM，延迟 > 1s，不可接受。
- `WebHDFS` REST API + Rust `reqwest`：支持度有限，Token/Kerberos 刷新复杂，NameNode HA 需自行实现重试路由。

**决策：5 年内 no-go（HDFS 使用下降趋势，若项目整体迁移到对象存储则可退出，但现有插件不动）**

---

### 4. authentication — Spring Security + Casdoor + LDAP

**不可绕过的 JVM 依赖**（来自 `dolphinscheduler-api/pom.xml` 及 `dolphinscheduler-bom/pom.xml`）

| 组件 | Jar 包 | 版本 |
|---|---|---|
| Casdoor SSO | `org.casbin:casdoor-spring-boot-starter` | **1.6.0** |
| LDAP | `org.springframework.ldap:spring-ldap-core` | **2.4.1** |
| Spring Security | `spring-boot-starter-security`（via Boot） | **2.7.11** |

**是否有 Rust 等价物**

- Casdoor：官方提供 `casdoor-rs-sdk`（crates.io），但其功能是 token 验证，不包含 Spring Boot 集成的用户上下文绑定、Session 管理、`@PreAuthorize` 注解权限模型。
- LDAP：`ldap3`（crates.io）可用，但需自行实现用户同步、组映射、连接池管理逻辑。
- Spring Security 的方法级权限（`@PreAuthorize("@ss.hasPermission(...)")`）散布在 API 层各 Controller，无法透明迁移。

**若强行重写的最低代价**

- fork 子进程验证 Token：latency 增加，且 Session 状态无法共享。
- 完整重实现：需重写用户上下文传播、Token 刷新、LDAP 同步定时任务、Casdoor OAuth2 回调处理，估算 > 3 人月且无测试覆盖。

**决策：永久 no-go**

---

### 5. scheduler-plugin — Quartz

**不可绕过的 JVM 依赖**（来自 `dolphinscheduler-bom/pom.xml:35` 及 `dolphinscheduler-scheduler-quartz/pom.xml`）

```
org.quartz-scheduler:quartz       2.3.2
org.springframework.boot:spring-boot-starter-quartz  (via Boot 2.7.11)
```

Quartz 通过数据库（`QRTZ_*` 表族）持久化触发器状态，支持集群模式下的任务锁定（`SELECT ... FOR UPDATE` 行锁）。DS 使用这张表记录 cron 触发器，依赖其集群安全语义。

**是否有 Rust 等价物**

- cron 表达式解析：`cron`、`croner`（crates.io）可用。
- 完整持久化调度框架（持久化触发器 + 集群锁 + 失误补偿 + 暂停/恢复）：无成熟 Rust 实现。`apalis`（crates.io）是任务队列框架，不等价于 Quartz 触发器语义。

**若强行重写的最低代价**

- 保留 Quartz JVM 进程，用 REST 桥接调用：增加进程间延迟，调度精度从 ms 级降到 100ms+ 级。
- 自研 Quartz 替代：需重新实现持久化触发器、集群互斥、失火处理（misfire）策略，工作量 > 2 人月，且需迁移现有 `QRTZ_*` 数据库表。

**决策：5 年内 no-go（若未来 DS 切换到自研调度内核，可用 Rust 重做，但须同步迁移数据库 schema）**

---

### 6. task-plugin — JVM 类任务（Spark / Flink / Hive / DataX / SeaTunnel）

**不可绕过的 JVM 依赖**（来自 `dolphinscheduler-task-spark/pom.xml`）

```
org.apache.dolphinscheduler:dolphinscheduler-task-api   (项目内依赖)
org.apache.dolphinscheduler:dolphinscheduler-common     (项目内依赖)
io.fabric8:kubernetes-client                             (scope: provided)
```

Spark 任务实际通过 `spark-submit`（JVM 子进程）提交，但提交逻辑依赖 `dolphinscheduler-task-api` 中的 Java SPI 接口（`AbstractTask`、`TaskExecutionContext`），以及 Kubernetes 客户端。

**是否有 Rust 等价物**

- `spark-submit` CLI wrapper：Rust 可以 `std::process::Command` 调用，这是 路线 A（Rust runner）已经可以做到的。
- 但 `AbstractTask` SPI、日志回传、进度监控、资源释放钩子全部耦合在 Java 接口树中，在 Rust 中重新实现意味着重写整个 task-api 层。

**若强行重写的最低代价**

- Rust 只封装 CLI 调用，任务生命周期管理仍由 Java master 负责：这正是路线 A 的方向，不算"重写"。
- 若要将整个 task-plugin SPI 迁移到 Rust：需重建插件加载机制（目前是 ClassLoader 动态加载）、日志收集管道、参数替换框架，估算 > 4 人月。

**决策：永久 no-go（JVM 任务插件整体不动；Rust runner 仅接管 Shell/HTTP/Python 类轻量任务）**

---

### 7. dolphinscheduler-dao（MyBatis Mapper）

**不可绕过的 JVM 依赖**

- `mybatis-spring-boot-starter`（via Spring Boot 2.7.11）
- 所有业务 SQL 以 MyBatis XML Mapper 形式存在，散布在 `dolphinscheduler-dao/src/main/resources/mapper/` 目录（数十个 XML 文件）

**是否有 Rust 等价物**

- `sqlx`、`SeaORM`（crates.io）可替代 MyBatis，但需逐条重写 SQL 和 ResultMap 映射。
- MyBatis 的动态 SQL（`<if>`、`<foreach>`、`<choose>`）没有直接 Rust 等价，需手工拆解。

**若强行重写的最低代价**

- fork 子进程方案对 DAO 层完全不适用（每次数据库访问调 JVM 子进程代价无法接受）。
- 完整迁移：迁移量与业务表数量成正比，当前约 40+ 业务表，估算 > 3 人月且引入大量回归风险。

**决策：永久 no-go**

---

### 8. dolphinscheduler-api（REST 服务层）

**不可绕过的 JVM 依赖**

- Spring MVC + Spring Boot 2.7.11
- 方法级权限注解（`@PreAuthorize`）散布全部 Controller
- Swagger/OpenAPI 文档与 Java 注解耦合

**是否有 Rust 等价物**

- `axum`、`actix-web`（crates.io）可实现 REST，但需重写全部 Controller、DTO 绑定、参数校验（`@Valid`/JSR-380）、异常处理链。

**决策：永久 no-go（API 层 80% 是 CRUD 胶水，Rust 化无实质收益）**

---

### 9. dolphinscheduler-yarn-aop

**不可绕过的 JVM 依赖**

- 通过 AspectJ / Spring AOP 字节码拦截 Hadoop 客户端，纯 JVM 字节码工程范畴

**决策：永久 no-go（与 Rust 无任何交集）**

---

### 10. eventbus / meter（进程内基础设施）

**不可绕过的 JVM 依赖**

- Guava EventBus（进程内）、Micrometer（metrics）

**决策：永久 no-go（进程内通信跨语言无意义）**

---

## Rust 化边界图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      DOLPHINSCHEDULER 整体架构                           │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │               JVM 必须保留（no-go 区域）                          │   │
│  │                                                                  │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌───────────────────────┐   │   │
│  │  │  master     │  │  dao         │  │  api (Spring MVC)     │   │   │
│  │  │  DAG Engine │  │  MyBatis     │  │  Spring Security      │   │   │
│  │  │  23,455 LOC │  │  40+ tables  │  │  Casdoor / LDAP       │   │   │
│  │  └─────────────┘  └──────────────┘  └───────────────────────┘   │   │
│  │                                                                  │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │  datasource-plugin (JVM-only JDBC drivers)               │   │   │
│  │  │  hive-jdbc:2.3.9  kyuubi-jdbc:1.7.0                      │   │   │
│  │  │  presto-jdbc:0.238.1  trino-jdbc:402                     │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  │                                                                  │   │
│  │  ┌───────────────────┐  ┌───────────────┐  ┌────────────────┐   │   │
│  │  │  storage-hdfs     │  │  scheduler    │  │  task-plugin   │   │   │
│  │  │  hadoop-hdfs:3.2.4│  │  quartz:2.3.2 │  │  Spark/Flink   │   │   │
│  │  │  hadoop-common    │  │  QRTZ_* tables│  │  Hive/DataX    │   │   │
│  │  └───────────────────┘  └───────────────┘  └────────────────┘   │   │
│  │                                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                              │ 协议边界（IPC / HTTP / gRPC）            │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │               Rust 可实现区域（条件性 go）                         │   │
│  │                                                                  │   │
│  │  ┌────────────────────────┐  ┌──────────────────────────────┐   │   │
│  │  │  worker 轻量任务执行器  │  │  alert sidecar               │   │   │
│  │  │  Shell / HTTP / Python │  │  独立进程，协议清晰           │   │   │
│  │  │  std::process::Command │  │  reqwest + lettre             │   │   │
│  │  └────────────────────────┘  └──────────────────────────────┘   │   │
│  │                                                                  │   │
│  │  ┌────────────────────────┐  ┌──────────────────────────────┐   │   │
│  │  │  storage 新接入        │  │  audit log sidecar           │   │   │
│  │  │  S3/OSS/GCS: object_  │  │  高吞吐写入，无 JVM 依赖      │   │   │
│  │  │  store crate           │  │  tokio + serde               │   │   │
│  │  └────────────────────────┘  └──────────────────────────────┘   │   │
│  │                                                                  │   │
│  │  ┌────────────────────────────────────────────────────────┐     │   │
│  │  │  本地 IPC 加速器（可选）                                │     │   │
│  │  │  命令扫描热路径 / 状态机切换 via JNI 或 Unix socket     │     │   │
│  │  └────────────────────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘

图例：
  JVM 区（上半）= 触碰则止，永久 no-go 或 5 年内 no-go
  Rust 区（下半）= 独立进程 + 协议清晰 + 生态自包含，条件性可行
  协议边界       = IPC socket / HTTP / gRPC，两侧互不侵入
```

---

## 决策汇总表

| 组件 | 关键不可替代 JVM 依赖 | Rust 等价物 | 决策 |
|---|---|---|---|
| master DAG 引擎 | Spring Boot 2.7.11，23k LOC | 无 | 永久 no-go |
| dao (MyBatis) | mybatis-spring-boot，40+ XML | sqlx 可替代但需全重写 | 永久 no-go |
| api (Spring MVC) | Spring Security，Casdoor 1.6.0 | 无直接等价 | 永久 no-go |
| datasource hive-jdbc | hive-jdbc 2.3.9 + hadoop UGI | 无生产可用实现 | 永久 no-go |
| datasource presto/trino | presto-jdbc 0.238.1 / trino-jdbc 402 | prusto 未维护 | 永久 no-go |
| datasource kyuubi | kyuubi-hive-jdbc-shaded 1.7.0 | 无 | 永久 no-go |
| storage-hdfs | hadoop-hdfs 3.2.4，Kerberos/HA | hdfs-native 不支持 HA/Kerberos | 5 年内 no-go |
| authentication | spring-ldap 2.4.1，casdoor 1.6.0 | 部分 crate 可用但集成复杂 | 永久 no-go |
| scheduler-quartz | quartz 2.3.2，QRTZ_* 表 | apalis 不等价 | 5 年内 no-go |
| task-plugin JVM 类 | AbstractTask SPI + kubernetes-client | 路线 A 仅接管 Shell/HTTP | 永久 no-go |
| yarn-aop | AspectJ 字节码拦截 | 无 | 永久 no-go |
| eventbus/meter | Guava / Micrometer | 无意义 | 永久 no-go |
