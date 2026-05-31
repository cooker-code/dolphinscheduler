# Extract 协议文档化 + gRPC proto 草稿

## 背景

`dolphinscheduler-extract` 是 DS 内部 RPC 总线（5,521 LOC，106 文件），使用自研 Netty 协议：
- 帧格式：`magic=0xbabe, version=0`，body = JSON 序列化的 `StandardRpcRequest`
- 无 `.proto` 文件，接口定义散布在 ~70 个 Java interface + `@RpcService/@RpcMethod` 注解

当 `dsl-task-runner`（P0）需要与 Java Worker/Master 集成时，必须有一个明确的协议契约。
本任务的目标是把 worker↔master 关键交互路径文档化，并写出 gRPC proto 草稿。

## 目标

1. **协议文档**：把 worker↔master 核心 RPC 接口（任务状态上报、任务分发、心跳）的现有 JSON 字段整理成文档，作为 proto 字段定义的来源
2. **proto 草稿**：为上述接口写 `.proto` 文件，字段与现有 JSON 协议对齐（可编译，不要求实现）
3. **兼容性分析**：标注哪些字段 Rust runner 必须支持、哪些可以省略

## 研究范围

优先覆盖以下 Extract 接口（来自可行性研究取证）：

| 接口类 | 方向 | 用途 |
|--------|------|------|
| `PhysicalTaskExecutorOperator` | Master → Worker | 分发任务给 worker 执行 |
| `TaskExecutorQueryClient` | Worker → Master | 查询任务实例状态 |
| `WorkerRpcClient` | Worker → Master | 上报任务执行结果、日志 |
| `TaskInstanceOperator` | Worker → Master | 任务状态变更通知 |

## 取证方法

1. 读取 `dolphinscheduler-extract/dolphinscheduler-extract-master/` 和 `extract-worker/` 下的 Java interface
2. 找到对应的 `StandardRpcRequest` 和 `StandardRpcResponse` 序列化字段（Jackson JSON）
3. 找到实际使用这些接口的调用点，确认字段完整性

## 产出文件

```
notes/
├── extract-interfaces.md   # 现有 RPC 接口清单：接口名、方法、JSON 字段、调用方向
└── worker-master.proto     # gRPC proto 草稿（可 protoc 编译）
```

## 验收标准

- [ ] `notes/extract-interfaces.md`：覆盖上述 4 个接口类，每个方法列出 JSON 字段名和类型（取自真实 Java 代码）
- [ ] `notes/worker-master.proto`：`protoc --proto_path=notes notes/worker-master.proto` 编译通过
- [ ] proto 字段与 extract JSON 字段名称/类型一一对应（差异处需注释说明）
- [ ] 标注 `dsl-task-runner` 集成时必须实现的最小字段集（`// required by rust-runner`注释）

## 约束

- 只写 `.proto` 文件，不实现 gRPC 服务端或客户端
- 不修改任何现有 Java 代码
- proto 文件路径：`notes/worker-master.proto`（在任务目录内，不进入主代码库）

## 依赖

- P0（`dsl-task-runner-poc`）完成后需与本 proto 对齐字段，但本任务**不阻塞** P0 PoC 阶段
- proto 草稿在 P0 集成阶段（`RustRunnerTaskDelegate`）才会被真正使用
