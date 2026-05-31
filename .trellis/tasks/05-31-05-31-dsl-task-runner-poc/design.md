# dsl-task-runner PoC — 技术设计

## 组件边界

```
┌─────────────────────────────────────────────────────┐
│  Java Worker（现有，不修改）                          │
│  PhysicalTaskPluginFactory                          │
│      ↓ Unix Socket gRPC（未来集成点，PoC 阶段不接）  │
└─────────────────────────────────────────────────────┘
              │
              │  /tmp/dsl-runner-{port}.sock
              ▼
┌─────────────────────────────────────────────────────┐
│  dsl-task-runner（本任务实现）                        │
│                                                     │
│  main.rs                                            │
│  ├── 解析 CLI 参数（--port, --sock-path）            │
│  └── 绑定 UnixListener → tonic Server               │
│                                                     │
│  runner.rs  TaskRunnerService (tonic impl)          │
│  ├── Execute(TaskRequest) → stream<TaskEvent>       │
│  │   ├── executor.rs: spawn_shell()                 │
│  │   └── cancel.rs:   CancellationToken 注册        │
│  └── Cancel(CancelRequest) → CancelResponse         │
│      └── cancel.rs: signal_cancel()                 │
│                                                     │
│  executor.rs                                        │
│  ├── spawn_shell(script, env, cwd, timeout, tx)     │
│  │   ├── 写临时 .sh 文件                             │
│  │   ├── tokio::process::Command::new("bash")       │
│  │   ├── stdout/stderr → mpsc tx (LogLine)          │
│  │   ├── 超时: tokio::time::timeout                 │
│  │   └── 退出: tx.send(ExitResult)                  │
│  └── kill(pid)                                      │
│                                                     │
│  cancel.rs                                          │
│  └── HashMap<task_id, CancellationToken>            │
└─────────────────────────────────────────────────────┘
```

## 关键设计决策

### 1. Unix Socket 传输（非 TCP）

**原因**：Java Worker 与 Rust runner 在同一主机，Unix Socket 延迟 < 0.1ms，且无需端口管理。
`tonic` 支持通过 `tokio::net::UnixListener` 绑定：

```rust
let uds = UnixListener::bind(&sock_path)?;
let uds_stream = UnixListenerStream::new(uds);
Server::builder()
    .add_service(TaskRunnerServer::new(service))
    .serve_with_incoming(uds_stream)
    .await?;
```

### 2. 流式日志（streaming response）

`Execute` 返回 `stream<TaskEvent>`，不是单次 response。
理由：Shell 任务可能运行数小时，调用方需要实时日志流（用于 DS 日志查看）。

实现：`tokio::sync::mpsc` channel，executor 向 channel 发送 `LogLine`，
gRPC handler 从 channel 消费并 yield 给客户端。

```rust
let (tx, rx) = tokio::sync::mpsc::channel::<TaskEvent>(256);
tokio::spawn(async move { executor::spawn_shell(req, tx, token).await });
Ok(Response::new(ReceiverStream::new(rx)))
```

### 3. 取消机制

每个 `task_instance_id` 对应一个 `tokio_util::sync::CancellationToken`，
存储在 `Arc<DashMap<String, CancellationToken>>`（全局共享，线程安全）。

`Cancel` RPC 调用 `token.cancel()`，executor 在 `tokio::select!` 中监听：

```rust
tokio::select! {
    status = child.wait() => { /* 正常退出 */ }
    _ = token.cancelled() => {
        child.kill().await?;
        /* 发送 ExitResult(exit_code=-1) */
    }
}
```

### 4. 临时脚本文件

Shell 脚本内容写入 `{working_dir}/.dsl-runner-{task_id}.sh`，执行后删除。
文件权限 `0o700`（仅 owner 可执行）。

### 5. 错误处理策略

- executor panic → 向 channel 发送 `ExitResult(exit_code=-2)` 后关闭 stream
- gRPC 连接断开 → executor 进程继续运行直到超时，避免孤儿进程需 caller 负责 Cancel

## 数据流

```
Execute(TaskRequest)
   │
   ├─ 注册 CancellationToken
   ├─ spawn executor task (tokio::spawn)
   │   ├─ 写 .sh 文件
   │   ├─ tokio::process::Command::new("bash").arg(".sh 文件")
   │   ├─ spawn_blocking 或 BufReader 读 stdout/stderr
   │   │   └─ tx.send(TaskEvent::LogLine { ... })
   │   └─ wait() 结束
   │       └─ tx.send(TaskEvent::ExitResult { ... })
   └─ return ReceiverStream(rx) → gRPC stream

Cancel(CancelRequest)
   └─ token.cancel() → executor tokio::select! 触发 kill
```

## 兼容性

- 当前 PoC 不连接 Java Worker，独立测试
- Unix Socket 路径格式 `/tmp/dsl-runner-{port}.sock` 与 `incremental-roadmap.md` 中约定一致
- proto 字段命名与 P1 任务（`extract-proto-draft`）将来对齐；`task_instance_id` 字段名与 DS 内部 `TaskInstance.getId()` 对应

## 测试策略

集成测试（`tests/integration.rs`）：
1. 在测试 setup 中 spawn `dsl-task-runner` 进程（或在进程内直接启动 server）
2. 用 tonic 生成的 gRPC client 连接 Unix Socket
3. 验证三个场景（exit_zero / exit_nonzero / cancel）
4. teardown 关闭 server

单元测试（`src/executor.rs`）：
- `test_write_script_file`：脚本文件权限正确
- `test_env_passthrough`：环境变量正确透传给子进程
