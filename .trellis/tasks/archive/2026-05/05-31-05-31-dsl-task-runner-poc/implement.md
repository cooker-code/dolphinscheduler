# dsl-task-runner PoC — 执行计划

## 前置条件

- [ ] Rust stable toolchain 已安装（`rustup show`）
- [ ] `protoc` 已安装（`protoc --version`）
- [ ] `cargo` 可用

## 执行步骤

### Step 1：初始化 Cargo 工程

```bash
# 在 DolphinScheduler 根目录创建独立 Rust 工程
cargo new --bin dsl-task-runner
cd dsl-task-runner
```

`Cargo.toml` 依赖：

```toml
[package]
name = "dsl-task-runner"
version = "0.1.0"
edition = "2021"

[dependencies]
tokio        = { version = "1", features = ["full"] }
tonic        = { version = "0.11", features = ["transport"] }
prost        = "0.12"
tokio-stream = "0.1"
tokio-util   = { version = "0.7", features = ["net"] }
dashmap      = "5"
tracing      = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
tempfile     = "3"

[build-dependencies]
tonic-build = "0.11"
```

**验证**：`cargo build` 通过。

---

### Step 2：写 proto 文件 + build.rs

创建 `proto/dsl_runner.proto`（内容见 PRD 的接口定义）。

`build.rs`：

```rust
fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::compile_protos("proto/dsl_runner.proto")?;
    Ok(())
}
```

**验证**：`cargo build` 生成 `dsl.runner.v1` 模块，无编译错误。

---

### Step 3：实现 `executor.rs`

关键接口：

```rust
pub async fn spawn_shell(
    req: TaskRequest,
    tx: Sender<Result<TaskEvent, Status>>,
    token: CancellationToken,
) -> Result<()>
```

实现要点（见 design.md）：
- 写临时脚本 `{working_dir}/.dsl-runner-{task_id}.sh`，权限 `0o700`
- `tokio::process::Command::new("bash").arg(script_path)`
- `stdout(Stdio::piped())` + `BufReader` 逐行读取
- `tokio::select!` 监听 `child.wait()` 和 `token.cancelled()`
- 正常退出：发 `ExitResult { exit_code: status.code() }`
- 取消退出：`child.kill()` 后发 `ExitResult { exit_code: -1 }`

**验证**：`cargo test executor` 通过（脚本文件权限、环境变量透传两个单元测试）。

---

### Step 4：实现 `cancel.rs`

```rust
pub struct CancelRegistry {
    map: Arc<DashMap<String, CancellationToken>>,
}

impl CancelRegistry {
    pub fn register(&self, id: &str) -> CancellationToken { ... }
    pub fn cancel(&self, id: &str) -> bool { ... }
    pub fn remove(&self, id: &str) { ... }
}
```

**验证**：并发注册 + 取消不 panic（`cargo test cancel`）。

---

### Step 5：实现 `runner.rs`（gRPC 服务实现）

实现 `task_runner_server::TaskRunner` trait：

```rust
impl TaskRunner for TaskRunnerService {
    type ExecuteStream = ReceiverStream<Result<TaskEvent, Status>>;

    async fn execute(&self, req: Request<TaskRequest>) -> Result<Response<Self::ExecuteStream>, Status>
    async fn cancel(&self, req: Request<CancelRequest>) -> Result<Response<CancelResponse>, Status>
}
```

**验证**：`cargo build` 无类型错误。

---

### Step 6：实现 `main.rs`

```rust
#[tokio::main]
async fn main() -> Result<()> {
    // 解析 --sock-path 参数（默认 /tmp/dsl-runner-50053.sock）
    // 初始化 tracing
    // 绑定 UnixListener
    // 启动 tonic Server
}
```

**验证**：`./target/release/dsl-task-runner --sock-path /tmp/test.sock` 启动无报错，
另一终端 `ls /tmp/test.sock` 可见 socket 文件。

---

### Step 7：编写集成测试

`tests/integration.rs`：

```rust
// 辅助：启动 server（tokio::spawn），返回 sock_path
async fn start_server() -> (String, JoinHandle<()>) { ... }

#[tokio::test]
async fn shell_exec_exit_zero() {
    let (sock, _handle) = start_server().await;
    let mut client = TaskRunnerClient::connect(format!("unix://{sock}")).await?;
    let mut stream = client.execute(TaskRequest {
        task_instance_id: "t1".into(),
        task_type: "SHELL".into(),
        script: "echo hello".into(),
        ..Default::default()
    }).await?.into_inner();
    // 收集所有事件，断言最后一个是 ExitResult { exit_code: 0 }
}

#[tokio::test]
async fn shell_exec_exit_nonzero() { /* script: "exit 1" → exit_code=1 */ }

#[tokio::test]
async fn shell_exec_cancel() {
    // script: "sleep 60"，发 Cancel → 进程终止，exit_code != 0
}
```

**验证**：`cargo test --test integration` 三个测试全部通过。

---

### Step 8：质量收尾

```bash
cargo clippy -- -D warnings   # 无警告
cargo fmt --check              # 格式化通过
protoc --proto_path=proto proto/dsl_runner.proto   # proto 可编译
```

---

## 回滚点

- Step 1-2 失败（依赖不可用）→ 检查网络/代理，`cargo` 版本 ≥ 1.75
- Step 3 失败（进程管理）→ 降级为同步 `std::process::Command`，后续 PR 异步化
- Step 7 失败（Unix Socket 连接）→ 先用 TCP 127.0.0.1 替代，Socket 路径作为后续 PR

## 完成标准（与 PRD 一致）

```
cargo build --release ✅
cargo test             ✅ (3 integration + 2 unit)
cargo clippy           ✅
cargo fmt --check      ✅
protoc compile         ✅
```
