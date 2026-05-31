# dsl-task-runner PoC：Rust Shell 轻量任务执行器

## 背景

DolphinScheduler Worker 的 Shell 任务执行链路：
```
PhysicalTaskPluginFactory → ShellTask.handle() → ShellCommandExecutor.run() → Runtime.exec()
```
整个链路依赖 JVM（Spring Boot，heap 1-2GB），但 Shell 任务本质上只需 fork 子进程。
本任务用 Rust 实现一个独立 `dsl-task-runner` 二进制，通过 gRPC/Unix Socket 接收任务请求，
执行 shell 命令，流式回传 stdout/stderr，为后续 Java Worker 集成铺路。

## 目标

实现一个可独立运行的 Rust 二进制 `dsl-task-runner`，完成：
1. gRPC 服务端（Unix Socket 传输），暴露 `TaskRunner.Execute` 和 `TaskRunner.Cancel` 接口
2. Shell 命令执行：`tokio::process::Command`，流式读取 stdout/stderr，支持超时和取消
3. 完整测试套件：三个核心场景通过

## 范围

**PoC 阶段仅覆盖 Shell 任务**，不实现 HTTP/Python（接口预留）。
不修改任何 Java 代码，不集成到 Worker，作为独立进程验证可行性。

## Proto 接口

```protobuf
syntax = "proto3";
package dsl.runner.v1;

service TaskRunner {
  rpc Execute (TaskRequest) returns (stream TaskEvent);
  rpc Cancel  (CancelRequest) returns (CancelResponse);
}

message TaskRequest {
  string task_instance_id = 1;
  string task_type        = 2;   // "SHELL"（PoC 阶段）
  string script           = 3;   // shell 脚本内容
  map<string, string> env = 4;
  string working_dir      = 5;
  string tenant_user      = 6;   // 执行用户（sudo 支持）
  int32  timeout_seconds  = 7;
}

message TaskEvent {
  oneof payload {
    LogLine    log_line    = 1;
    ExitResult exit_result = 2;
  }
}

message LogLine {
  int64  timestamp_ms = 1;
  string line         = 2;
}

message ExitResult {
  int32 exit_code  = 1;
  int32 process_id = 2;
}

message CancelRequest  { string task_instance_id = 1; }
message CancelResponse { bool ok = 1; }
```

Unix Socket 路径：`/tmp/dsl-runner-{port}.sock`（端口从启动参数传入）。

## 技术选型

| 组件 | Crate | 理由 |
|------|-------|------|
| 异步运行时 | `tokio` | DS 社区标准，与 tonic 集成最好 |
| gRPC | `tonic` + `tonic-build` | 生成 stub，proto 定义驱动 |
| 进程管理 | `tokio::process` | 异步 spawn，流式读取 stdout/stderr |
| 序列化 | `serde` + `serde_json` | 将来与 Extract JSON 协议对齐 |
| 日志 | `tracing` + `tracing-subscriber` | 结构化日志，便于集成 |

## 项目结构

```
dsl-task-runner/
├── Cargo.toml
├── proto/
│   └── dsl_runner.proto
├── build.rs           # tonic-build 代码生成
├── src/
│   ├── main.rs        # 启动入口，解析参数，绑定 Unix Socket
│   ├── runner.rs      # TaskRunnerService 实现
│   ├── executor.rs    # shell 进程管理（spawn/kill/stream）
│   └── cancel.rs      # 取消令牌管理（task_id → tokio CancellationToken）
└── tests/
    └── integration.rs # 集成测试（spawn server + gRPC client）
```

## 约束

- Rust edition 2021，stable toolchain（不使用 nightly）
- 无 unsafe 代码
- 不依赖任何 JVM 库

## 验收标准

- [ ] `cargo build --release` 成功，产出 `dsl-task-runner` 二进制
- [ ] `protoc` 可编译 `proto/dsl_runner.proto`（CI 验证）
- [ ] `cargo test` 通过以下三个集成测试：
  - `shell_exec_exit_zero`：执行 `echo hello`，收到 LogLine + ExitResult(exit_code=0)
  - `shell_exec_exit_nonzero`：执行 `exit 1`，收到 ExitResult(exit_code=1)
  - `shell_exec_cancel`：执行 `sleep 60`，Cancel RPC 后进程终止，ExitResult(exit_code≠0)
- [ ] `cargo clippy -- -D warnings` 无警告
- [ ] `cargo fmt --check` 通过

## 非目标

- 不实现 HTTP/Python 任务（接口预留 task_type 字段，PoC 仅处理 SHELL）
- 不实现 Java Worker 侧集成（`RustRunnerTaskDelegate`）
- 不做性能基准测试（延迟对比在集成阶段进行）
- 不处理 tenant_user / sudo（PoC 以当前用户执行）
