# Rust 架构重构可行性研究：组件评估与渐进式替换路线

> **任务定位**：纯研究类任务（lightweight），仅产出文档结论与组件评估表，不包含任何代码实现。
> 后续若决定落地，会按各组件单独立项。

## 背景

DolphinScheduler 现状是 28 个 Maven 模块的 Java/Spring Boot 单体（master/worker/api/alert 四进程 + 共享库 + 插件体系），JVM 资源占用大（每节点 1-2GB heap）、轻量任务启动延迟高。
团队评估是否可用 Rust 重写部分组件以获得资源、延迟收益。本任务先把"组件全景 + 重写收益矩阵 + 渐进式路线"研究清楚，再决定是否启动具体改造。

## 研究目标

1. **组件全景**：把 28 个 Maven 模块按"进程角色 / 共享库 / 通信协调 / 插件体系 / 运维工具"五类梳清楚，每个模块写明职责与外部依赖
2. **Rust 重写收益评估**：对每个组件给出 [推荐 / 谨慎 / 不建议] 三档结论，附理由（收益 vs 代价 vs 生态锁定）
3. **渐进式落地路线**：给出至少一条不破坏现有架构、可灰度、可回滚的"Rust 加速 sidecar / runner"路径
4. **不包含**：任何 Rust 代码原型、任何 master/worker 协议改造的详细设计

## 约束

- 必须保留 JVM 主体不动作为兜底，Rust 仅作为可选加速路径
- 不为重写而重写：每条建议必须给出可量化（资源、延迟、吞吐）的预期收益
- 重写不应破坏现有 task-plugin / datasource-plugin / storage-plugin 的生态兼容性

## 验收标准

- [ ] `notes/components-overview.md`：28 个模块按类别整理为表格，含职责、依赖、是否独立部署
- [ ] `notes/rust-rewrite-matrix.md`：每个组件一行，列出 [当前痛点 / Rust 收益 / 改造代价 / 生态依赖 / 建议档位]
- [ ] `notes/incremental-roadmap.md`：至少 3 条渐进路线（轻量任务 runner / 审计 sidecar / alert 替代品），每条含范围、灰度方式、回滚方法、预期收益
- [ ] `notes/no-go.md`：明确列出"不建议重写"的组件清单及具体理由（如 Hadoop/Hive/Spark JDBC 客户端的 JVM 锁定）
- [ ] 主流程上的 4 类任务（Spark / Flink / Hive SQL / Shell）的执行链路依赖被显式标注（哪些必须 JVM、哪些可纯 Rust）
- [ ] 文档完成后由用户 review，决定是否拆分为具体落地子任务

## 非目标

- 不出 Rust 代码原型
- 不评估 UI 模块（前端不在 Rust 重写讨论范围）
- 不讨论 master DAG 引擎的重写细节（结论默认是"不建议"，仅在 no-go.md 列理由）
- 不讨论数据库迁移（MyBatis → sqlx/SeaORM 工作量超出本研究范围）

## 风险与权衡

- **生态锁定**：Hadoop/Hive/Spark/Flink 的客户端 SDK 是 JVM 独占，Rust 想跑这些任务必须 fork 子进程调用 Java，等于回到原点。这是 Rust 化的核心边界。
- **协议兼容**：Netty RPC 协议是 master/worker 通信基础，Rust 替换 worker 必须保证协议兼容（或新加 grpc 通道双跑）
- **维护成本翻倍**：双语言栈对中小团队是负担，必须有清晰的"哪部分用什么"边界

## 后续动作

研究完成后，预期产出至少 1 个具体落地子 task（例如"Rust 轻量任务 runner PoC"），不在本任务实施。
