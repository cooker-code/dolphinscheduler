# 渐进式 Rust 化路线（不破坏现有架构）

> 总原则：**保持 Java 主体不动**，把 Rust 作为高频低延迟的"加速 sidecar / runner"嵌入。
> 每条路线必须可灰度、可回滚、可量化收益。

## 路线 A：轻量任务 Rust Runner（**最先做**，收益最大）

### 范围
重写 worker 中的 **Shell / HTTP / Python 启动器** 三类执行器，做成独立 Rust 二进制 `dsl-task-runner`。
不影响 JVM 类任务（Spark/Flink/Hive 仍走原 PhysicalTaskExecutor）。

### 接入方式
- Java worker 通过本地 Unix Socket / gRPC 调用 Rust runner
- Rust runner 接收 `TaskExecutionContext`，启动子进程，流式回吐日志和退出码
- 配置开关 `worker.runner.rust.enabled=true/false`

### 灰度
1. 单 worker 节点开启，对比同样负载下 JVM 老路径
2. 按节点百分比逐步放量
3. 全量后，老路径保留 1 个版本周期作为回滚

### 回滚
- 关配置开关 → 立刻退回 JVM PhysicalTaskExecutor

### 预期收益
| 指标 | 当前（JVM） | Rust runner |
|------|-----|-----|
| 单 task 启动延迟 | 200-500ms | < 20ms |
| 单 worker 内存占用 | 1-2GB | 50-100MB（runner 进程） |
| 单节点并发 task 上限 | ~50 | ~500+ |

## 路线 B：审计日志 Rust Sidecar（与 audit-log-arch-rework 联动）

### 范围
audit-log-arch-rework Phase 3 异步队列方案的**外置版本**：
Java 进程通过 Unix Socket 把 AuditLog 投递给本地 Rust sidecar，由 sidecar 批量落库 + WAL 持久化。

### 接入方式
- Java AuditServiceImpl 改写为 socket client
- Rust sidecar：tokio + sqlx（批量 insert）+ sled（本地 WAL）
- sidecar 故障时 Java 端降级为同步直写（保留兜底）

### 灰度
- 与 audit-log-arch-rework Phase 3 同步上线
- 内部环境双跑 30 天验证

### 回滚
- 关闭 sidecar 进程 + Java 端配置改回直写

### 预期收益
- 业务接口 P99 不再受审计写入影响
- 审计零丢失（WAL 兜底 JVM crash 场景）
- DB 写入 QPS 通过批量降到 1/10

## 路线 C：Rust 版 Alert Server（替代品双跑）

### 范围
重写 dolphinscheduler-alert 为独立 Rust 二进制 `ds-alert-rs`，仅支持现有 channel 的 80% 场景（邮件/钉钉/飞书/HTTP webhook，先不做 SMS/语音）。

### 接入方式
- Java alert 通过 RPC 把待发告警委托给 Rust alert（按 channel 路由）
- 或直接独立部署，配置项二选一启用

### 灰度
- 测试环境单跑 1 个月
- 生产环境双跑（Java alert + Rust alert 同时收消息，对比一致性）
- 无差异后切流

### 回滚
- 配置改回 Java alert，停掉 Rust 进程

### 预期收益
- alert 进程内存从几百 MB 降到几十 MB
- 大量告警批量发送时延迟下降

## 优先级与时间线建议

| 路线 | 优先级 | 估算 | 预期 ROI |
|------|--------|------|---------|
| A. 轻量任务 Runner | P0 | 1-2 月 PoC + 2-3 月生产 | 极高（资源占用大幅下降） |
| B. 审计 Sidecar | P1 | 与 audit-log-arch-rework Phase 3 同步 | 中高（解决业务被阻塞 + 审计不丢失） |
| C. Alert 替代品 | P2 | 1 月 PoC | 中（资源收益明显，但 alert 总流量小） |

## 不在本路线（后续或永不）

- ❌ master DAG 引擎重写：见 no-go.md
- ❌ api/dao 重写：见 no-go.md
- ❌ Hadoop/Hive/Spark/Flink 任务的 Rust 化：JVM 锁定
- ⏸ Rust 实现 worker 主体（替代 dolphinscheduler-worker）：等路线 A 跑稳后再讨论
