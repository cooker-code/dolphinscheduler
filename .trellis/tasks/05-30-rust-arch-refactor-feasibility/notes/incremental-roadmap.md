# 渐进式 Rust 化路线（不破坏现有架构）

> 总原则：**保持 Java 主体不动**，把 Rust 作为高频低延迟的"加速 sidecar / runner"嵌入。
> 每条路线必须可灰度、可回滚、可量化收益、有完整闭环测试方案。

---

## 路线 A：轻量任务 Rust Runner（**最先做**，收益最大）

### 范围

重写 worker 中的 **Shell / HTTP / Python 启动器** 三类执行器，做成独立 Rust 二进制 `dsl-task-runner`。
不影响 JVM 类任务（Spark/Flink/Hive 仍走原 `PhysicalTaskExecutor`）。

### 接入点（代码取证）

任务执行的完整调用链：

```
WorkerRpcServer (rpc 入口)
  → PhysicalTaskEngineDelegator
    → PhysicalTaskExecutorFactory.createTaskExecutor(TaskExecutionContext)
      → PhysicalTaskExecutorBuilder.build()
        → PhysicalTaskExecutor.initializeTaskPlugin()
          → PhysicalTaskPluginFactory.createPhysicalTask()
            → TaskPluginManager.getTaskChannel(taskType)           ← 按 taskType 分发
              → ShellTask.handle()
                → ShellCommandExecutor.run(shellActuatorBuilder)   ← 进程启动点
```

**Rust Runner 的接入点**：在 `PhysicalTaskPluginFactory.createPhysicalTask()` 返回前，
检测 `taskType ∈ {SHELL, HTTP, PYTHON}` 且灰度开关打开时，
返回一个代理实现 `RustRunnerTaskDelegate`（实现 `AbstractTask` 接口），
内部通过 Unix Socket gRPC 把实际执行委托给 `dsl-task-runner` 进程。

关键类路径：
- `dolphinscheduler-worker/src/main/java/org/apache/dolphinscheduler/server/worker/executor/PhysicalTaskPluginFactory.java`
- `dolphinscheduler-task-plugin/dolphinscheduler-task-shell/src/main/java/org/apache/dolphinscheduler/plugin/task/shell/ShellTask.java`（`handle()` → `ShellCommandExecutor.run()`）
- `dolphinscheduler-task-plugin/dolphinscheduler-task-api/src/main/java/org/apache/dolphinscheduler/plugin/task/api/ShellCommandExecutor.java`（当前进程启动实现）

### 通信协议：Unix Socket + gRPC

**Proto 接口草稿**（`dsl_runner.proto`）：

```protobuf
syntax = "proto3";
package dsl.runner.v1;

// Java worker → Rust runner: 提交任务
service TaskRunner {
  rpc Execute (TaskRequest) returns (stream TaskEvent);
  rpc Cancel  (CancelRequest) returns (CancelResponse);
}

message TaskRequest {
  string task_instance_id = 1;
  string task_type         = 2;   // SHELL | HTTP | PYTHON
  string script            = 3;   // 脚本内容或 HTTP URL
  map<string, string> env  = 4;   // 环境变量（来自 TaskExecutionContext.prepareParamsMap）
  string working_dir       = 5;
  string tenant_user       = 6;   // TenantUtils 解析后的系统用户
  int32  timeout_seconds   = 7;
}

message TaskEvent {
  oneof payload {
    LogLine    log_line   = 1;
    ExitResult exit_result = 2;
  }
}

message LogLine {
  int64  timestamp_ms = 1;
  string line         = 2;
}

message ExitResult {
  int32  exit_code   = 1;
  int32  process_id  = 2;
  string app_info    = 3;         // YARN ApplicationId（若有）
}

message CancelRequest {
  string task_instance_id = 1;
}

message CancelResponse {
  bool ok = 1;
}
```

Unix Socket 路径约定：`/tmp/dsl-runner-${worker.listen-port}.sock`（避免多 worker 实例冲突）。

### 灰度开关

基于 `worker.application.yaml` 已有配置模式：

```yaml
worker:
  rust-runner:
    # 灰度开关；false = 回退到 JVM ShellCommandExecutor
    enabled: false
    # Unix Socket 路径（随 listen-port 区分实例）
    socket-path: /tmp/dsl-runner-${worker.listen-port}.sock
    # Rust runner 可执行文件路径
    binary-path: /opt/dolphinscheduler/bin/dsl-task-runner
    # 仅对这些 taskType 启用（可按需逐类放量）
    enabled-task-types:
      - SHELL
    # 单次 gRPC 连接超时
    connect-timeout-ms: 500
```

开关名称：`worker.rust-runner.enabled`

### 回滚 SLA

关闭 `worker.rust-runner.enabled=false` 后：
- `PhysicalTaskPluginFactory` 下一次新建 `PhysicalTaskExecutor` 时立即走 JVM 路径（无重启）
- **正在运行**的 Rust runner 任务继续执行直到结束，不强杀
- 完全切回 JVM 路径：**< 1 秒**（配置热加载，Spring `@ConfigurationProperties` + `@RefreshScope`）

### 闭环测试方案

#### 1. 单元测试：Rust runner 进程启动验证

测试目标：验证 `dsl-task-runner` 二进制正确响应 gRPC Execute 调用并返回正确退出码。

```rust
// tests/integration/shell_exec_test.rs
#[tokio::test]
async fn test_shell_exec_exit_zero() {
    let runner = start_test_runner().await;
    let resp = runner.execute(TaskRequest {
        task_instance_id: "ut-001".into(),
        task_type: "SHELL".into(),
        script: "echo hello && exit 0".into(),
        ..Default::default()
    }).await;

    let events: Vec<TaskEvent> = collect_stream(resp).await;
    let exit = extract_exit(&events);

    assert_eq!(exit.exit_code, 0);
    assert!(events.iter().any(|e| log_line_contains(e, "hello")));
}

#[tokio::test]
async fn test_shell_exec_exit_nonzero() {
    // 脚本 exit 42 → exit_code == 42
}

#[tokio::test]
async fn test_shell_exec_cancel() {
    // 发 Cancel 后 exit_code == -1 || 130
}
```

测试命令：`cargo test --test integration -- shell_exec`

#### 2. 集成测试：Java worker 调用 Rust runner 跑 Shell 任务，对比 stdout / 退出码

测试目标：`RustRunnerTaskDelegate`（Java 侧 gRPC 客户端）能把结果正确回传到 `TaskResponse`，与 `ShellCommandExecutor` 输出等价。

测试位置：`dolphinscheduler-worker/src/test/java/.../executor/RustRunnerIntegrationTest.java`

测试步骤：
1. `@BeforeAll` 启动 `dsl-task-runner` 进程（subprocess，固定 socket 路径）
2. 构造 `TaskExecutionContext`（task type = SHELL，script = `echo hello-rust && exit 0`）
3. 通过 `PhysicalTaskPluginFactory` 在开关打开时创建 `RustRunnerTaskDelegate`，调用 `handle()`
4. 断言：
   - `getExitStatusCode() == 0`
   - taskOutputParams 中 stdout 包含 `"hello-rust"`
5. 同样 TaskExecutionContext 走老路径 `ShellCommandExecutor.run()`，对比两个 `TaskResponse` 字段

```java
@Test
void testRustRunnerOutputMatchesJvmRunner() throws Exception {
    TaskExecutionContext ctx = buildShellContext("echo hello-rust && exit 0");

    TaskResponse rustResult = runViaRustRunner(ctx);
    TaskResponse jvmResult  = runViaJvmRunner(ctx);

    assertThat(rustResult.getExitStatusCode()).isEqualTo(jvmResult.getExitStatusCode());
    assertThat(rustResult.getProcessId()).isGreaterThan(0);
    // stdout 比对（通过 taskOutputParams 或 log 文件）
    assertThat(readLogFile(ctx)).contains("hello-rust");
}
```

#### 3. 回归测试：DolphinScheduler e2e 测试套件验证 Shell 任务不退化

e2e 测试目录：
- `dolphinscheduler-e2e/dolphinscheduler-e2e-case/src/test/java/org/apache/dolphinscheduler/e2e/cases/tasks/ShellTaskE2ETest.java`
- `dolphinscheduler-e2e/dolphinscheduler-e2e-case/src/test/java/org/apache/dolphinscheduler/e2e/cases/WorkflowE2ETest.java`

运行方式（开启 Rust runner 开关）：

```bash
# 启动测试环境（Docker Compose，位于 dolphinscheduler-e2e）
cd dolphinscheduler-e2e
docker compose -f docker/docker-compose.yaml up -d

# 以 worker.rust-runner.enabled=true 跑 e2e
mvn verify -pl dolphinscheduler-e2e/dolphinscheduler-e2e-case \
  -Dworker.rust-runner.enabled=true \
  -Dtest=ShellTaskE2ETest,WorkflowE2ETest
```

验收标准：
- `ShellTaskE2ETest` 全绿（与关闭开关时相同通过率）
- `WorkflowE2ETest` 中包含 Shell 节点的工作流用例全部成功

#### 4. 性能基准：量化 200-500ms → <20ms 启动延迟

基准工具：自定义 JMH benchmark + wrk2 脚本。

```java
// dolphinscheduler-benchmark/src/main/java/...TaskLaunchBenchmark.java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 5)
public class TaskLaunchBenchmark {

    @Benchmark
    public TaskResponse jvmShellLaunch(BenchmarkState state) throws Exception {
        return state.jvmRunner.run(state.echoCtx);
    }

    @Benchmark
    public TaskResponse rustShellLaunch(BenchmarkState state) throws Exception {
        return state.rustRunner.run(state.echoCtx);
    }
}
```

脚本位置（建议）：`dolphinscheduler-worker/src/test/scripts/bench-shell-launch.sh`

```bash
#!/usr/bin/env bash
# 连续提交 1000 个 echo 任务，记录 p50/p95/p99 启动延迟
for mode in jvm rust; do
  echo "=== mode=$mode ===" >> bench-results.txt
  for i in $(seq 1 1000); do
    start=$(date +%s%3N)
    curl -s -X POST "http://localhost:1235/api/bench/shell?mode=$mode" \
         -d '{"script":"echo x"}' > /dev/null
    end=$(date +%s%3N)
    echo $((end - start)) >> bench-results-$mode.txt
  done
done
# 使用 awk 计算 p50/p95/p99
awk 'BEGIN{n=0} {a[n++]=$1} END{
  asort(a); p50=a[int(n*0.50)]; p95=a[int(n*0.95)]; p99=a[int(n*0.99)];
  print "p50="p50"ms p95="p95"ms p99="p99"ms"
}' bench-results-jvm.txt
```

**验收标准**：Rust runner p99 < 20ms（当前 JVM p50 约 200-500ms）。

---

## 路线 B：审计日志 Rust Sidecar（与 audit-log-arch-rework 联动）

### 范围

`audit-log-arch-rework` Phase 3 异步队列方案的**外置版本**：
Java `OperatorLogAspect` 通过 Unix Socket 把 `AuditLog` 投递给本地 Rust sidecar，
由 sidecar 批量落库 + WAL 持久化。

### 接入点（代码取证）

审计写入的调用链：

```
@OperatorLog 注解方法（API Controller）
  → OperatorLogAspect.afterReturning()       ← AOP 切面
    → AuditOperator.recordAudit(auditContext, returnValue)
      → BaseAuditOperator.recordAudit()
        → AuditServiceImpl.addAudit(auditLog)  ← 当前同步写 DB 的位置（单参数）
          → AuditLogMapper.insert(auditLog)
```

关键类路径：
- `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/audit/OperatorLogAspect.java`（切面，`@AfterReturning` + `@AfterThrowing`）
- `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/audit/operator/AuditOperator.java`（接口：`recordAudit` + `setRequestParam`）
- `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/audit/operator/BaseAuditOperator.java`（基础实现）
- `dolphinscheduler-dao/src/main/java/org/apache/dolphinscheduler/dao/entity/AuditLog.java`（数据实体）

**Rust Sidecar 接入点**：在 `AuditServiceImpl.addAudit()` 处注入条件分支：

```java
// AuditServiceImpl.addAudit() 改写示意
if (auditSidecarConfig.isEnabled()) {
    auditSocketClient.send(auditLog);        // 非阻塞投递，< 1ms
} else {
    auditLogMapper.insert(auditLog);         // 原始同步写（BaseMapper#insert）
}
```

### 通信协议（sidecar API）

```protobuf
syntax = "proto3";
package dsl.audit.v1;

service AuditSidecar {
  rpc Append (AppendRequest) returns (AppendResponse);
  rpc Flush  (FlushRequest)  returns (FlushResponse);
  rpc Health (HealthRequest) returns (HealthResponse);
}

message AuditRecord {
  string model_name    = 1;
  string operation     = 2;
  int64  user_id       = 3;
  string user_name     = 4;
  string resource_name = 5;
  int64  created_time  = 6;
  string description   = 7;
}

message AppendRequest  { repeated AuditRecord records = 1; }
message AppendResponse { bool wal_persisted = 1; }
message FlushRequest   {}
message FlushResponse  { int64 flushed_count = 1; }
message HealthResponse { bool ok = 1; string detail = 2; }
```

Sidecar 内部：
- 接收后先写 `sled` WAL（`/var/ds/audit-wal/`），再批量 flush 到 DB（每 100ms 或 500 条触发）
- Java 端降级条件：gRPC 连接失败 OR 超时 > 50ms → 直接同步写 DB

### 灰度开关

```yaml
# api/src/main/resources/application.yaml 追加（与现有 audit 配置同级）
audit:
  sidecar:
    # 灰度开关
    enabled: false
    socket-path: /tmp/ds-audit-sidecar.sock
    connect-timeout-ms: 50
    # 降级策略：sidecar 不可用时同步写 DB
    fallback-on-error: true
```

开关名称：`audit.sidecar.enabled`

### 回滚 SLA

关闭 `audit.sidecar.enabled=false` 后：
- `AuditServiceImpl` 下一个请求即走 DB 直写路径
- 切回无需重启（`@RefreshScope`）
- **< 1 秒**完全生效

### 闭环测试方案

#### 1. WAL 持久化测试

测试目标：验证 sidecar crash 后 WAL 中的数据可被恢复并重放到 DB。

```rust
// tests/integration/wal_recovery_test.rs
#[tokio::test]
async fn test_wal_survives_crash() {
    let wal_dir = tempdir().unwrap();
    let sidecar = start_sidecar_with_wal(wal_dir.path()).await;

    // 写入 100 条 AuditRecord
    sidecar.append(make_records(100)).await.unwrap();
    // 模拟 crash（kill sidecar 进程，不 flush）
    sidecar.kill();

    // 重启 sidecar，回放 WAL
    let recovered = start_sidecar_with_wal(wal_dir.path()).await;
    recovered.flush().await.unwrap();

    // 验证 DB 中有 100 条记录
    let count = db_count_audit_records().await;
    assert_eq!(count, 100, "WAL 恢复后 DB 应有 100 条记录");
}
```

#### 2. 故障注入测试：sidecar crash 不丢日志

测试目标：Java 端在 sidecar 不可用时自动降级，保证审计不丢失。

```java
// AuditSidecarFaultInjectionTest.java
@Test
void testFallbackWhenSidecarDown() {
    // 停止 sidecar 进程
    sidecarProcess.destroy();

    // 调用审计写入（通过 OperatorLogAspect 或直接调用 AuditServiceImpl）
    auditService.addAudit(buildAuditLog("test-op"));

    // 验证 DB 直写路径被触发（降级生效）
    List<AuditLog> logs = auditLogMapper.queryByDescription("test-op");
    assertThat(logs).hasSize(1);
}

@Test
void testResumeAfterSidecarRestart() {
    // 重启 sidecar 后，sidecar 路径自动恢复，不再走 DB 直写
}
```

#### 3. 性能对比测试

```java
// AuditWritePerformanceTest.java（JMH）
@Benchmark
public void directDbWrite(State s) {
    for (AuditLog log : s.batch100) { s.auditMapper.insert(log); }
}

@Benchmark
public void sidecarWrite(State s) {
    s.sidecarClient.send(s.batch100);   // 非阻塞，< 1ms
}
```

验收标准：sidecar 路径 p99 < 1ms（DB 直写 p99 通常 5-20ms）。

---

## 路线 C：Alert Rust 替代品（双跑验证）

### 范围

重写 `dolphinscheduler-alert` 为独立 Rust 二进制 `ds-alert-rs`，
优先支持：**HTTP webhook / SMTP 邮件 / 钉钉 / 飞书**。
不做 SMS / 语音（复杂度高、流量极低）。

### 接入点（代码取证）

Alert 发送的完整调用链：

```
AlertRpcServer (extract-alert RPC 入口)
  → AlertEventPendingQueue
    → AlertEventLoop.process()
      → AlertSender.send(alert, alertPluginInstances)
        → AbstractEventSender.doSend()
          → AlertChannel.process(AlertInfo)  ← SPI 接口，各插件实现此方法
```

关键接口：
- `dolphinscheduler-alert/dolphinscheduler-alert-plugins/dolphinscheduler-alert-api/src/main/java/org/apache/dolphinscheduler/alert/api/AlertChannel.java`
  - `AlertResult process(AlertInfo info)` ← 单次发送
- `dolphinscheduler-alert/dolphinscheduler-alert-plugins/dolphinscheduler-alert-api/src/main/java/org/apache/dolphinscheduler/alert/api/AlertChannelFactory.java`
  - `String name()` / `AlertChannel create()` / `List<PluginParams> params()`
- `dolphinscheduler-alert/dolphinscheduler-alert-server/src/main/java/org/apache/dolphinscheduler/alert/service/AlertSender.java`
  - `AlertSender extends AbstractEventSender<Alert>`
  - `syncHandler(int alertGroupId, String title, String content)` ← 同步发送入口

**Rust 替代品的两种接入模式**：

**模式 1（推荐，双跑验证用）**：新建 `RustAlertChannel implements AlertChannel`，
在 `process()` 内通过 HTTP/gRPC 把告警委托给 `ds-alert-rs` 进程，
`AlertChannelFactory` 返回此实现，配置页面可选 `channel_type=RUST_HTTP` 等。

**模式 2（完全替代）**：`ds-alert-rs` 独立部署，实现与 Java alert-server 相同的 RPC 接口，
在 `extract-alert` 层配置路由地址指向 Rust 进程。

### Rust 侧核心实现草稿

```rust
// src/channel/http.rs
pub struct HttpAlertChannel {
    client: reqwest::Client,
    url:    String,
}

impl AlertChannel for HttpAlertChannel {
    async fn process(&self, info: AlertInfo) -> AlertResult {
        let resp = self.client.post(&self.url)
            .json(&info)
            .send().await;
        match resp {
            Ok(r) if r.status().is_success() => AlertResult::success(),
            Ok(r)  => AlertResult::failed(r.status().to_string()),
            Err(e) => AlertResult::failed(e.to_string()),
        }
    }
}
```

### 灰度开关

```yaml
# alert/src/main/resources/application.yaml 追加
alert:
  rust-backend:
    # 双跑模式：true = Java + Rust 同时发；false = 只走 Java
    enabled: false
    # ds-alert-rs 服务地址
    endpoint: http://localhost:9300
    # 双跑时比对结果不一致是否告警
    diff-alert: true
```

开关名称：`alert.rust-backend.enabled`

### 回滚 SLA

关闭 `alert.rust-backend.enabled=false` 后：
- `AbstractEventSender` 下次发送时不再分流到 Rust 后端
- 配置热加载，**< 5 秒**完全生效（alert 事件循环轮询间隔约 1-2 秒）

### 闭环测试方案

#### 1. HTTP 告警渠道集成测试

```rust
// tests/integration/http_alert_test.rs
#[tokio::test]
async fn test_http_webhook_delivery() {
    // 启动 mock HTTP 服务器
    let mock = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/webhook"))
        .respond_with(ResponseTemplate::new(200))
        .mount(&mock)
        .await;

    let channel = HttpAlertChannel::new(mock.uri() + "/webhook");
    let result = channel.process(AlertInfo {
        title:   "test-alert".into(),
        content: "task failed".into(),
        ..Default::default()
    }).await;

    assert!(result.is_success());
    // 验证 mock 收到了一次请求
    mock.verify().await;
}
```

#### 2. SMTP 邮件渠道集成测试

```rust
#[tokio::test]
async fn test_smtp_delivery() {
    // 使用 MailHog（Docker）作为本地 SMTP 服务器
    let channel = SmtpAlertChannel::new(SmtpConfig {
        host: "localhost",
        port: 1025,   // MailHog SMTP 端口
        from: "ds-test@local",
        to:   vec!["ops@local".into()],
    });

    let result = channel.process(make_alert_info("smtp-test")).await;
    assert!(result.is_success());

    // 通过 MailHog REST API 验证邮件到达
    let mails = reqwest::get("http://localhost:8025/api/v2/messages")
        .await.unwrap().json::<MailhogResponse>().await.unwrap();
    assert!(mails.items.iter().any(|m| m.subject == "smtp-test"));
}
```

#### 3. 双跑一致性验证测试

测试目标：Java alert 和 Rust alert 对相同 `AlertInfo` 的处理结果一致（成功/失败状态、耗时差 < 2 倍）。

```java
// AlertRustJavaConsistencyTest.java
@Test
void testDualRunConsistency() throws Exception {
    AlertInfo info = buildAlertInfo("dual-run-test");

    AlertResult javaResult = javaHttpChannel.process(info);
    AlertResult rustResult = rustHttpChannelProxy.process(info);

    assertThat(javaResult.isSuccess()).isEqualTo(rustResult.isSuccess());
    // 耗时对比（Rust 应更快或相当）
    assertThat(rustResult.getElapsedMs()).isLessThanOrEqualTo(javaResult.getElapsedMs() * 2);
}
```

#### 4. 告警渠道回归矩阵

| 渠道 | 测试类型 | 工具 | 验收标准 |
|------|----------|------|----------|
| HTTP webhook | 集成测试 | WireMock / mock HTTP server | 100% 发送成功，耗时 < 500ms |
| SMTP | 集成测试 | MailHog (Docker) | 邮件正确投递，收件人/主题匹配 |
| DingTalk | 集成测试 | mock HTTPS server | 请求体格式符合钉钉 webhook 规范 |
| Feishu | 集成测试 | mock HTTPS server | 请求体格式符合飞书 webhook 规范 |
| 全渠道 | e2e 双跑 | `alert.rust-backend.enabled=true` | 与 Java 结果一致率 100% |

---

## 优先级与时间线建议

| 路线 | 优先级 | 估算 | 预期 ROI | 测试项数 |
|------|--------|------|---------|----------|
| A. 轻量任务 Runner | P0 | 1-2 月 PoC + 2-3 月生产 | 极高（资源占用大幅下降） | 4 类测试，约 12 项 |
| B. 审计 Sidecar | P1 | 与 audit-log-arch-rework Phase 3 同步 | 中高（解除业务阻塞 + 审计零丢失） | 3 类测试，约 6 项 |
| C. Alert 替代品 | P2 | 1 月 PoC | 中（资源收益明显，alert 总流量小） | 4 类测试，约 8 项 |

总计测试项：**约 26 项**，覆盖单元、集成、回归、性能四个层次。

---

## 不在本路线（后续或永不）

- master DAG 引擎重写：见 no-go.md
- api/dao 重写：见 no-go.md
- Hadoop/Hive/Spark/Flink 任务的 Rust 化：JVM 锁定
- Rust 实现 worker 主体（替代 dolphinscheduler-worker）：等路线 A 跑稳后再讨论
