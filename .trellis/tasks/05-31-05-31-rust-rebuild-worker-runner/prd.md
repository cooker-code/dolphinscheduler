# Rust 模块重建：Worker Shell Runner + Extract Proto

> **任务定位**：父任务，统筹两个独立可验证的子任务。
> 父任务不直接实现代码，负责维护整体接受标准和组件边界契约。

## 背景

基于 `05-30-rust-arch-refactor-feasibility` 研究结论，Worker Runtime（2,044 LOC）是 Rust 化收益最高、风险最低的切入点：

- Worker 本质是进程启动器，Shell/HTTP/Python 任务无 JVM 专有依赖
- Extract 自研 Netty RPC（magic=0xbabe）是 Rust/Java 互操作的协议边界
- 两个子任务可并行推进，互不阻塞

## 子任务

| 优先级 | slug | 目标 |
|--------|------|------|
| P0 | `05-31-dsl-task-runner-poc` | Rust `dsl-task-runner` 二进制：gRPC 接收 Shell 任务，执行并流式回传 stdout |
| P1 | `05-31-extract-proto-draft` | 为 worker↔master 交互写 `.proto` 草稿，为 Runner 集成准备协议契约 |

## 整体约束

- **保持 Java 主体不动**：不修改 master/DAO/API/extract 任何现有代码
- **可灰度**：所有 Rust 路径必须有 `worker.rust-runner.enabled` 开关回退到 JVM 实现
- **可回滚**：关闭开关后立即生效，无需重启进程
- **不破坏现有插件**：Spark/Flink/Hive 任务完全不受影响

## 父任务验收标准

- [ ] P0：`cargo test` 通过 shell_exec_exit_zero / nonzero / cancel 三个用例
- [ ] P1：`.proto` 文件通过 `protoc` 编译，字段与 Extract JSON 协议（StandardRpcRequest/Response）对齐
- [ ] 两个子任务 review 确认无接口冲突

## 非目标

- 不实现 Java Worker 侧的 `RustRunnerTaskDelegate`（后续独立子任务）
- 不部署到生产或测试集群
- 不涉及 Alert Server 重写（路线 C，等 P0 完成后共享 Rust 工程基础设施）
- 不实现 HTTP/Python runner（PoC 阶段仅 Shell）
