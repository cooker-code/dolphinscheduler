# Rust 重写收益矩阵

> 三档：✅ 推荐 / ⚠️ 谨慎 / ❌ 不建议
> 数据来源：真实代码量化（2026-05-31）
> 模块 LOC 均为去除测试后的生产代码行数

---

## 代码量基准（实测）

| 模块 | 实测生产 LOC | 文件数 | 备注 |
|------|------------|--------|------|
| `dolphinscheduler-worker` | 2,044 | ~20 | 仅 worker runtime；不含 task-plugin |
| `dolphinscheduler-alert`（server+plugins） | 8,018 | 99 | 含 12 个渠道插件 |
| `dolphinscheduler-extract`（全部 sub） | 5,521 | 106 | 自研 Netty RPC 框架（无 proto） |
| `task-plugin/dolphinscheduler-task-api` | 9,853 | ~60 | 含 AbstractCommandExecutor、AbstractYarnTask |
| `task-plugin/task-spark` | 634 | 6 | SparkTask(298) + SparkParameters(130) |
| `task-plugin/task-flink` | 892 | 9 | FlinkArgsUtils(332) + FlinkTask(120) |
| `task-plugin/task-hivecli` | 379 | 5 | HiveCliTask(212) |
| `task-plugin/task-shell` | 220 | 4 | ShellTask(100) |
| `task-plugin/task-sql` | 623 | 4 | SqlTask(501) — JDBC 重量级 |
| `dolphinscheduler-datasource-*` | ~12,400 | 211 | JDBC 驱动封装层（实测 12,377 行） |

---

## 组件评估详情

### 1. Worker Runtime（任务调度执行层）

**建议档位：✅ 推荐重写**（理由：代码量小、职责边界清晰、是资源消耗的主要来源）

**当前痛点**

- 生产 LOC 仅 **2,044 行**，但必须携带完整 Spring Boot + JVM 启动（通常 **8–15 秒**冷启动，heap 配置普遍 **1–2 GB**）
- `WorkerServer.java`（143 LOC）是 `@SpringBootApplication`，意味着 Spring IoC 容器、Netty RPC server、MyBatis 数据源全部随 worker 启动
- `PhysicalTaskExecutor`（154 LOC）本质是一个进程启动器 + 状态机；真正的任务执行委托给子进程（`AbstractCommandExecutor` 内的 `Process process`）
- `PhysicalTaskEngineFactory`（48 LOC）+ `PhysicalTaskExecutorFactory`（66 LOC）仅作插件分发，无 JVM 核心逻辑

**Rust 收益**

- 启动时间：Rust 二进制通常 **< 100 ms**（含 tokio runtime 初始化），对比 JVM 的 8–15 s
- 内存基线：Rust worker 进程 **30–80 MB RSS**，对比 JVM **400 MB–2 GB heap**（GC overhead 额外 30–50%）
- 可大量横向铺 worker（单机 20+ 个实例不再是内存瓶颈）
- `tokio::process::Command` 直接替代 Java `ProcessBuilder`，流式读取 stdout/stderr 无需额外线程池

**改造代价**

- 核心逻辑 LOC ~2,000，Rust 侧估算需 **1,500–2,500 LOC**（含 tokio 异步状态机、IPC 协议）
- 最大阻力：需对接 `dolphinscheduler-extract` 自研 Netty RPC 协议（Transporter 帧：magic=0xbabe，version=0，JSON 序列化 body），需在 Rust 侧实现相同编解码
- 依赖 `dolphinscheduler-dao` 写任务状态（可替换为 gRPC 回调 master，无需 Rust 直连 DB）
- 现有 crate：`tokio`（async runtime）、`tonic`（gRPC 可选）、`serde_json`（对齐现有 JSON 协议）

**生态依赖**

- 无 Hadoop/Spark/Flink JVM SDK 依赖：worker 本身只启动子进程，SDK 在子进程内
- 需自实现：Netty 自定义帧协议（TransporterHeader + TransporterDecoder/Encoder）解码，约 **200 LOC Rust**
- Spring Security / ZK 注册：可通过调 registry sidecar 解耦

---

### 2. Alert Server（告警服务）

**建议档位：✅ 推荐重写**（理由：纯 IO 密集、可完全独立部署、最轻的 DB 依赖）

**当前痛点**

- alert-server 核心 LOC **1,680 行**（含所有 service/rpc/config），携带 Spring Boot + MyBatis 却只做"轮询 DB → HTTP/SMTP 推送"
- `AlertSender`（131 LOC）依赖 `AlertDao` → `AlertMapper`（MyBatis）读取告警记录并回写状态；这是唯一 DB 接触点
- 12 个渠道 plugin（DingTalk/WeChat/Email/Slack/Webex Teams 等）各自 LOC 在 **100–415 行**，主要是 HTTP 客户端调用
- `MailSender`（415 LOC）是最重的 plugin，依赖 `jakarta.mail`（JavaMail）库

**Rust 收益**

- 内存：Java alert server 默认 **512 MB heap**，Rust 实现预计 **20–50 MB RSS**
- async HTTP 并发发送（tokio + reqwest）：并发 100 个渠道推送无需 Java ThreadPool 调优
- 独立部署：不依赖 Spring IoC，可作为单一静态二进制运维

**改造代价**

- 核心逻辑约 1,680 LOC → Rust 估算 **1,200–2,000 LOC**
- 唯一外部依赖：DB（读写 alert 状态）→ 可用 `sqlx`（PostgreSQL/MySQL 均支持）替代 MyBatis `AlertMapper`
- 渠道 plugin：DingTalk/Feishu/Telegram/HTTP 均为 REST API，`reqwest` 完全覆盖
- 阻力点：`jakarta.mail`（JavaMail SMTP）→ Rust 侧需用 `lettre` crate（成熟，支持 STARTTLS/SSL）
- 无 Hadoop/Spark/任何大数据 JVM SDK 依赖

**生态依赖**

| Java 类/库 | Rust 等价 |
|-----------|----------|
| `jakarta.mail` / `javax.mail` | `lettre` crate |
| `org.apache.commons.lang3.StringUtils` | 标准库 / `itertools` |
| `com.fasterxml.jackson` (JSON) | `serde_json` |
| Spring `@Component` / IoC | 无需，直接构造 |
| MyBatis `AlertMapper` | `sqlx` 原生 SQL |
| Netty RPC (`NettyRemotingServer`) | `tokio::net::TcpListener` 或 `tonic` |

---

### 3. Extract（自研 Netty RPC 框架）

**建议档位：⚠️ 谨慎**（理由：是内部 RPC 总线，所有组件都依赖；收益真实但改动面宽）

**当前痛点**

- 实测 LOC **5,521 行**，106 个文件，实现了完整 Netty 客户端/服务端 + 动态代理路由
- 自研协议（非 gRPC/proto）：帧格式 `magic=0xbabe, version=0`，body = JSON 序列化的 `StandardRpcRequest`
- `NettyRemotingClient`（259 LOC）管理连接池；`JdkDynamicRpcClientProxyFactory` 使用 JDK 动态代理自动路由
- 接口定义散布于各 `dolphinscheduler-extract-master/worker/alert` 子模块（~70 个 Java interface）
- 无 `.proto` 文件：所有类型由 Java interface + `@RpcMethod` + `@RpcService` 注解定义

**Rust 收益**

- Netty 本身已是高性能 NIO 框架，纯性能提升有限（10–30%）
- 真正收益在于：若 worker/alert Rust 化后，extract 必须提供 Rust 可调用的接口（当前没有 proto 文件，互操作代价高）
- 建议路径：**迁移 extract 到 gRPC/tonic**（先写 `.proto` 文件，再用 `tonic-build` 自动生成 Rust stub），而非重写整个框架

**改造代价**

- 5,521 LOC 全重写代价高；更务实是"增量 proto 化"：为 worker↔master 交互先写 proto，用 gRPC 桥接
- 若 Rust worker 与 Java master 共存，需在 Rust 侧实现自定义 Transporter 帧解码（估算 **300–500 LOC**）

**生态依赖**

- Netty（`io.netty:netty-all`）→ `tokio` + 自定义帧编解码，无直接等价 crate（需手写协议）
- JDK 动态代理 → Rust trait + `tonic` generated stubs（需先 proto 化）
- Jackson JSON → `serde_json`（JSON 格式兼容）

---

### 4. Task-Plugin：Shell 任务

**建议档位：✅ 推荐重写**（理由：代码量最小、零 JVM 生态依赖）

**当前痛点**

- LOC **220 行**（`ShellTask`=100, `ShellParameters`=43, channel 类各~40）
- 整个执行链路：JSON 反序列化参数 → 写 `.sh` 文件 → `IShellInterceptorBuilder` 构造命令 → `AbstractCommandExecutor.runCommand()` fork 子进程
- `AbstractCommandExecutor`（281 LOC）内核是 `Process process = Runtime.getRuntime().exec()`，携带 JVM 仅为了 `Process` 管理

**Rust 收益**

- `tokio::process::Command` 原生异步子进程管理，无需 Java `CompletableFuture` + `ExecutorService` 模式
- 流式 stdout/stderr 采集：`tokio::io::AsyncBufReadExt` 零拷贝读取
- 支持 cgroups v2 直接限制子进程资源（Java 层无此能力）

**改造代价**

- 核心逻辑估算 **200–400 LOC Rust**
- 唯一依赖：IShellInterceptor 的 tenant/sudo 包装 → Rust `Command::uid()/gid()` 原生支持

**生态依赖**

无 Java 专有依赖，完全可 Rust 化。

---

### 5. Task-Plugin：Spark 任务

**建议档位：❌ 不建议重写**（理由：执行链路需要 spark-submit CLI，但 YARN AppID 追踪依赖 JVM 日志解析）

**当前痛点**

- LOC **634 行**（`SparkTask`=298, `SparkParameters`=130, `SparkConstants`=99）
- `SparkTask` 继承 `AbstractYarnTask`（112 LOC），后者调用 `LogUtils.getAppIds()` 从日志文件用正则提取 YARN `application_xxx_xxx`
- 执行路径：构造 `spark-submit` 参数列表 → `AbstractCommandExecutor.runCommand()` fork subprocess
- Spark on K8s 路径额外依赖 `io.fabric8:kubernetes-client`（`Config.fromKubeconfig()`）

**JVM 链路依赖（具体类名）**

| 类 / JAR | 职责 | Rust 等价 |
|---------|------|----------|
| `AbstractYarnTask` | YARN AppID 追踪 + 中断处理 | 需手写日志正则解析 |
| `LogUtils.getAppIdsFromLogFile()` | 正则扫描 stdout 提取 `application_[0-9]+_[0-9]+` | Rust `regex` crate 可替代 |
| `AbstractCommandExecutor` | subprocess fork + 流式日志 | `tokio::process` 可替代 |
| `io.fabric8:kubernetes-client` | K8s driver label 查询 | `kube` crate 可替代（Cargo.toml 中 crate 名为 `kube`，非 `kube-rs`） |
| `SparkParameters`（Jackson JSON） | 参数反序列化 | `serde_json` 可替代 |
| `spark-submit` / `spark-sql` CLI | 实际 Spark 执行器（子进程） | 无需替换（子进程调用） |

**结论**：Spark 任务本身的 Java 部分**技术上可 Rust 化**（仅作 CLI 包装器），但收益极低。真正重要的是 `spark-submit` 子进程，该子进程本身是 Spark 的 JVM 进程，Rust 无法替代。

---

### 6. Task-Plugin：Flink 任务

**建议档位：❌ 不建议重写**（理由：FlinkArgsUtils 复杂度中等，收益低）

**当前痛点**

- LOC **892 行**（`FlinkArgsUtils`=332 是主要复杂度来源）
- 3 种部署模式（session/per-job/application）× 2 种运行模式（yarn/k8s/standalone），参数矩阵复杂
- `FileUtils`（108 LOC）处理 jar 文件复制和路径拼接

**JVM 链路依赖（具体类名）**

| 类 / JAR | 职责 |
|---------|------|
| `FlinkArgsUtils` | 构造 `flink run` / `flink run-application` 参数 |
| `AbstractYarnTask` | YARN AppID 追踪（同 Spark） |
| `AbstractCommandExecutor` | subprocess fork |
| `org.apache.commons.io.FileUtils` | jar 文件操作 |
| `flink run` / `flink run-application` CLI | 实际 Flink 执行器（子进程，JVM） |

**结论**：参数构造逻辑较复杂（332 LOC），但无 JVM SDK 锁定。技术上可重写，但无显著收益（仍需调 flink CLI 子进程）。

---

### 7. Task-Plugin：Hive SQL（HiveCLI）

**建议档位：❌ 不建议重写**（理由：执行命令是 hive/beeline CLI，本质 JVM 子进程）

**当前痛点**

- LOC **379 行**，`HiveCliTask`（212 LOC）是主要逻辑
- 两种执行方式：`hive -e "<sql>"` 或 `hive -f <sqlfile>`，通过 `AbstractCommandExecutor` fork
- 无 Hive JDBC 依赖（hive CLI 方式），但 SQL task 走 Hive JDBC 时依赖 `hive-jdbc.jar`

**JVM 链路依赖（具体类名）**

| 类 / JAR | 职责 |
|---------|------|
| `HiveCliTask` | 构造 hive 命令行参数，写 SQL 文件 |
| `AbstractCommandExecutor` | subprocess fork |
| `hive` CLI（子进程） | 实际 HiveQL 执行（Hive Server2 JVM） |
| `hive-jdbc` JAR（SQL task 路径） | JDBC 连接 Hive，无 Rust 等价 |

---

### 8. Task-Plugin：SQL 任务（通用 JDBC）

**建议档位：❌ 不建议重写**（理由：JDBC 生态锁定最深、datasource-plugin 体量大）

**当前痛点**

- `SqlTask`（501 LOC）是最重的单文件 task；直接持有 `java.sql.Connection`
- 依赖链：`DataSourceClientProvider.getAdHocConnection()` → `DataSourceUtils.buildConnectionParams()` → `datasource-plugin`（211 个文件，~12,400 LOC）
- 支持 MySQL/PostgreSQL/Hive/Presto/Trino/StarRocks/ClickHouse 等 20+ 种数据源的 JDBC 驱动

**JVM 链路依赖（具体类名）**

| 类 / JAR | 职责 | Rust 等价 |
|---------|------|----------|
| `java.sql.Connection` / `PreparedStatement` | JDBC 接口 | `sqlx`（仅支持 MySQL/PG/SQLite） |
| `DataSourceClientProvider` | 多数据源连接池管理 | 无统一等价，需逐一适配 |
| `DataSourceUtils.buildConnectionParams()` | 连接参数构造（含加解密） | 需重写 |
| `hive-jdbc` / `presto-jdbc` / `trino-jdbc` | Hive/Presto/Trino 专有 JDBC | **Rust 无等价**，这些数据源无原生 Rust 客户端 |
| `com.zaxxer.hikari.HikariCP` | 连接池 | `deadpool` / `bb8`（但只支持 sqlx 数据源） |

---

## 汇总矩阵（量化版）

| 组件 | 生产 LOC | 启动成本 | 内存基线 | 档位 | 理由一句话 |
|------|---------|---------|---------|------|-----------|
| **worker runtime** | 2,044 | JVM 8–15s / **Rust <100ms** | JVM 1–2GB / **Rust 50MB** | ✅ | 代码量小，职责单一，资源收益最大 |
| **alert server** | 1,680 | JVM 5–10s / **Rust <50ms** | JVM 512MB / **Rust 20MB** | ✅ | IO 密集、零大数据依赖、独立可部署 |
| **Shell/Python/HTTP task** | ~300–400 | 随 worker 启动 | 随 worker 分担 | ✅ | 零 JVM 依赖，纯进程包装 |
| **extract（RPC 框架）** | 5,521 | — | — | ⚠️ | proto 化优先于重写；worker Rust 化后必须解决互操作 |
| **master（DAG 引擎）** | ~30,000+ | — | — | ⚠️ | 核心 IP，failover 逻辑复杂，风险远超收益 |
| **api（REST）** | ~20,000+ | — | — | ⚠️ | 80% CRUD，前端强耦合，迁移工作量巨大 |
| **Spark task** | 634 | — | — | ❌ | CLI 包装器，spark-submit 仍是 JVM |
| **Flink task** | 892 | — | — | ❌ | flink run 仍是 JVM，参数构造复杂度不值得重写 |
| **HiveCLI task** | 379 | — | — | ❌ | hive CLI 是 JVM，SQL 路径有 hive-jdbc 锁定 |
| **SQL task** | 623 | — | — | ❌ | Hive/Presto/Trino JDBC 无 Rust 等价 |
| **datasource-plugin** | ~12,400 | — | — | ❌ | 20+ 数据源 JDBC 生态，Rust 无法覆盖 |
| **storage-plugin** | — | — | — | ❌ | HDFS Rust 客户端不可用 |
| **task-plugin（Spark/Flink/Hive/DataX 类）** | 35 个子模块 | — | — | ❌ | JVM 生态锁定，Rust 只能做 CLI 包装（绕回原点） |

---

## 4 类主流任务的 JVM 链路依赖（详细版）

### Spark 任务

```
Master 调度
  → WorkerRPC（extract/Netty）
  → PhysicalTaskExecutor（worker）
  → SparkTask.handle()
      ↓ 继承
    AbstractYarnTask.handle()
      → AbstractCommandExecutor.runCommand()
          → ProcessBuilder / Runtime.exec("spark-submit ...")
          → LogUtils.getAppIdsFromLogFile()  [正则: application_\d+_\d+]
      ← 子进程退出码
    AbstractYarnTask.cancelApplication()
      → ProcessUtils.kill() / yarn application -kill <appId>
  ← 状态上报 via extract/Netty → Master
```

**JVM 专有依赖**：`io.netty:netty-all`（RPC）、`io.fabric8:kubernetes-client`（K8s 模式）、`com.fasterxml.jackson.core:jackson-databind`（参数反序列化）、`org.apache.commons:commons-lang3`、Spring Boot 容器

---

### Flink 任务

```
Master 调度
  → WorkerRPC（extract/Netty）
  → PhysicalTaskExecutor（worker）
  → FlinkTask.handle()
      ↓ 继承
    AbstractYarnTask.handle()
      → FlinkArgsUtils.buildRunCommand()  [332 LOC，构造 flink run 参数]
      → AbstractCommandExecutor.runCommand()
          → ProcessBuilder("flink run" / "flink run-application")
          → 流式读取 stdout，LogUtils 提取 AppID
      ← 子进程退出码
  ← 状态上报 via extract/Netty → Master
```

**JVM 专有依赖**：同 Spark；`org.apache.commons.io:commons-io`（FileUtils）；无 Flink Java SDK 直接依赖（走 CLI）

---

### Hive SQL 任务（HiveCLI 路径）

```
Master 调度
  → WorkerRPC（extract/Netty）
  → PhysicalTaskExecutor（worker）
  → HiveCliTask.handle()
      → 写 SQL 到临时文件
      → AbstractCommandExecutor.runCommand()
          → ProcessBuilder("hive -e '<sql>'" 或 "hive -f <file>")
          [子进程：hive CLI → Hive Server2 JVM → Hadoop YARN/HDFS]
      ← 子进程退出码
  ← 状态上报 via extract/Netty → Master
```

**JVM 专有依赖（子进程链）**：`hive-cli.jar`（子进程，无法绕过）、`hadoop-common.jar`（子进程内）、`hive-exec.jar`（子进程内）；DS 进程本身依赖 `io.netty`、`com.fasterxml.jackson`、Spring Boot

---

### Shell 任务

```
Master 调度
  → WorkerRPC（extract/Netty）
  → PhysicalTaskExecutor（worker）
  → ShellTask.handle()
      → ShellParameters 解析（Jackson JSON）
      → IShellInterceptorBuilder.addSystemEnvs().addTenantConfig().build()
          [tenant sudo 包装，BaseLinuxShellInterceptorBuilder 173 LOC]
      → AbstractCommandExecutor.runCommand()
          → ProcessBuilder("/bin/bash <script.sh>")
          → 流式读取 stdout/stderr（CompletableFuture + 线程池）
      ← 子进程退出码
  ← 状态上报 via extract/Netty → Master
```

**JVM 专有依赖**：`io.netty`（RPC）、`com.fasterxml.jackson`（参数反序列化）、Spring Boot；**子进程本身无 JVM 依赖**——这是 Shell 任务可 Rust 化的核心依据。
