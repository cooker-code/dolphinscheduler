# Rust 重写收益矩阵

> 三档：✅ 推荐 / ⚠️ 谨慎 / ❌ 不建议
> 评估维度：当前痛点、Rust 收益、改造代价、生态依赖

## ✅ 推荐用 Rust 重写（高收益、低耦合）

| 组件 | 当前痛点 | Rust 收益 | 改造代价 | 生态依赖 |
|------|---------|----------|---------|---------|
| **worker（任务执行 runtime）** | JVM 启动慢、内存占用大（每 worker 1-2GB heap）、PhysicalTaskExecutor 频繁 fork 子进程 | 启动 ms 级、内存 50MB 内、`tokio::process` 比 Java ProcessBuilder 高效；可大量铺 worker | **中**：需重新对齐 RPC 协议（Netty → tonic/grpc 或自定义），任务插件机制需重设计 | 仅 RPC 协议，无 JVM 库锁定 |
| **task-executor 中的 Shell/Python/HTTP 类轻量任务** | 轻量任务也走 JVM，资源浪费严重 | 直接 fork + 流式日志 + cgroups 资源限制，适合 sidecar | **低**：可作为独立 runner 嵌入现有 worker，渐进替换 | 无 |
| **alert（告警）** | 简单 IO 密集型，跑在 JVM 上太重 | async 模型完美匹配；几十 MB 内存可顶 Java 几百 MB | **低**：协议简单（HTTP/SMTP），可独立替换 | 无 |
| **registry 客户端层** | ZK/Etcd 客户端 Java 实现的心跳/重连成本高 | etcd-client / zookeeper-async 体验更好，资源开销低 | **中**：DS 内部多处依赖，需保持 RPC 兼容 | 客户端有 Rust 实现 |
| **审计日志写入 sidecar**（呼应 audit-log-arch-rework） | JVM 同步写入阻塞业务 | Rust 本地 sidecar，监听 unix socket，批量入库 + 异步落盘 | **低**：作为新增组件而非替换 | 无 |

## ⚠️ 谨慎评估（中收益、高耦合）

| 组件 | 评估 |
|------|------|
| **master（DAG 引擎）** | 调度逻辑是项目最核心的 IP，含大量历史 corner case 和 failover 逻辑；Rust 重写收益（CPU、内存）有限，但**风险极高**。除非整体 fork，否则不值得 |
| **api（REST 服务）** | 业务逻辑 80% 是 CRUD，Rust（axum + sqlx）能跑，但和前端、SDK、生态强耦合，迁移工作量大 |
| **dao** | MyBatis 是项目的 SQL 真理来源，迁移到 sqlx/SeaORM 需要重写所有 Mapper，工作量巨大 |
| **service** | master/worker/api 三处共享，重写涉及全栈联动，纯重写不现实；可选"逐方法切换至 grpc 调用 Rust 服务" |

## ❌ 不建议重写（低收益、高代价、生态锁定）

| 组件 | 原因 |
|------|------|
| **task-plugin（30+ 任务类型）** | Spark/Flink/Hive/DataX 任务类型本身依赖 JVM 客户端 SDK，Rust 重写要么 fork 子进程调用 Java（绕回原点），要么只能支持 Shell/HTTP 这种"无生态依赖"的任务 |
| **datasource-plugin** | 大数据数据源 JDBC 生态在 JVM 上最完整（Hive/Presto/Trino/StarRocks 等 Rust 客户端不成熟） |
| **storage-plugin** | HDFS 客户端 Rust 不可用；S3/OSS/Azure Blob 有 `object_store` crate，但替换收益不抵改造代价 |
| **dao-plugin** | 强依赖 MyBatis 方言机制 |
| **authentication** | 强依赖 Spring Security、Casdoor JWT、LDAP Java 客户端 |
| **yarn-aop** | 直接 AOP 拦截 Hadoop 客户端，纯 JVM 范畴 |
| **scheduler-plugin（Quartz）** | Quartz 是 JVM 独占调度库，无对应 Rust 替代 |
| **eventbus** | 进程内事件总线，跨语言无意义 |
| **meter** | Micrometer 已与 Java 生态深度绑定 |
| **standalone-server / dist / tools** | 打包/启动/工具脚本，没有重写价值 |
| **ui** | 前端纯 Vue/TS，与 Rust 无关 |

## 主流程任务依赖标注

| 任务类型 | 执行链路依赖 | 是否可纯 Rust |
|---------|-------------|--------------|
| **Shell** | 直接 fork 进程 | ✅ 可 |
| **HTTP** | HTTP 客户端 | ✅ 可（reqwest） |
| **Python** | 启动 python interpreter | ✅ 可（仅启动器，python 仍需在系统） |
| **SQL（MySQL/Postgres）** | JDBC 驱动 | ⚠️ 可（sqlx/tokio-postgres），但 DS 内部统一走 datasource-plugin，跨语言要重写 |
| **Hive SQL** | Hive JDBC（JVM） | ❌ 必须 JVM |
| **Spark** | spark-submit + spark-client（JVM） | ❌ 必须 JVM |
| **Flink** | flink CLI + flink-client（JVM） | ❌ 必须 JVM |
| **DataX** | DataX 框架本身是 JVM | ❌ 必须 JVM |
| **K8s / Docker** | kubernetes-client | ✅ 可（kube-rs） |
| **MLflow / SageMaker** | Python SDK | ⚠️ 可（启动器层面） |
