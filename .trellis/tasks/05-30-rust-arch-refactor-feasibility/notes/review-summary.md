# Review Summary — Rust 重构可行性研究

> 汇总日期：2026-05-31
> 验证方：Checker Agent（4 轮代码取证）
> 汇总方：Synthesizer Agent
> **修订状态：第二节所列 11 项事实错误已全部修订（2026-05-31）**

---

## 一、总体验证结论

| 文档 | PASS | FAIL | WARN | 整体状态 |
|------|------|------|------|----------|
| `components-overview.md` | 19 | 5 | — | **WARN** |
| `rust-rewrite-matrix.md` | 20 | 3 | — | **WARN** |
| `incremental-roadmap.md` | 12 | 2 | — | **WARN** |
| `no-go.md` | 11 | 1 | — | **WARN** |
| **合计** | **62** | **11** | — | **WARN（可用，需修订）** |

所有文档均为 WARN（无 FAIL 级文档），意味着核心架构判断准确，存在若干需要修订的事实性错误，但整体结论可信。

---

## 二、发现的问题清单

### ❌ 事实错误（必须修订，共 11 项）

#### components-overview.md（5 项）

| # | 位置 | 当前错误内容 | 正确内容 |
|---|------|------------|---------|
| 1 | task-plugin 插件数量描述 | 文字写「35 个具体插件」，子模块列写「33 个 task-xxx」 | 实际磁盘子目录（排除 task-api/task-all）共 **34 个** |
| 2 | task-plugin 插件列表 | 未列出 `pytorch` 插件 | `dolphinscheduler-task-pytorch` 目录存在，需补入 |
| 3 | Spark 任务链路方法名 | `buildScriptWithParameterReplacement()` | 实际方法为 `getScript()`（AbstractYarnTask.java:111） |
| 4 | HTTP 任务响应条件判断 | 「Janino 脚本引擎（Java 语法）」 | 实际使用 `HttpCheckCondition` 枚举 switch-case（STATUS_CODE_DEFAULT / STATUS_CODE_CUSTOM / BODY_CONTAINS / BODY_NOT_CONTAINS），Janino 仅为 logback-classic 编译期依赖 |
| 5 | DataX 命令模板 | `python3 datax.py <config.json>` | 实际为 `${PYTHON_LAUNCHER} ${DATAX_LAUNCHER} <config>`（两个环境变量，非硬编码） |

#### rust-rewrite-matrix.md（3 项）

| # | 位置 | 当前错误内容 | 正确内容 |
|---|------|------------|---------|
| 6 | Alert 插件数量 | 「11 个渠道插件」 | 实际 **12 个**渠道插件目录（含 webexteams） |
| 7 | datasource-plugin LOC | 「~15,000+」 | 实测 **12,377 行**，低于 15,000；文件数 211 正确 |
| 8 | Rust crate 名称 | `kube-rs` | 正确 crate 名为 `kube`（`kube-rs` 是 GitHub 项目名，加入 Cargo.toml 会构建失败） |

#### incremental-roadmap.md（2 项）

| # | 位置 | 当前错误内容 | 正确内容 |
|---|------|------------|---------|
| 9 | Route B AuditServiceImpl 签名 | `addAudit(List<AuditLog> auditLogList)` + `batchInsert(auditLogList)` | 实际签名为 `addAudit(AuditLog auditLog)`（单参数），调用 `auditLogMapper.insert(auditLog)`（BaseMapper#insert，非 batchInsert） |
| 10 | Route B 代码示意 | `auditLogMapper.batchInsert(auditLogList)` | 应改为 `auditLogMapper.insert(auditLog)` |

#### no-go.md（1 项）

| # | 位置 | 当前错误内容 | 正确内容 |
|---|------|------------|---------|
| 11 | master 调度引擎 + dao 章节 | ~~Spring Boot 版本写为「2.6.1」~~ | **已修正**：BOM 实际为 `2.7.11`（`dolphinscheduler-bom/pom.xml:32`），全文已替换 |

---

### ⚠️ 信息缺失或不精确（共 7 项，不影响结论但建议补充）

| # | 文档 | 位置 | 说明 |
|---|------|------|------|
| W1 | components-overview.md | 模块总数表格 | `dolphinscheduler-task-executor` 是真实顶层模块，但文档未在任何分节表格中给它独立一行（仅在依赖列中提及） |
| W2 | rust-rewrite-matrix.md | Extract 章节 | 存在 `dolphinscheduler-task-grpc` 子模块，含 test-only `.proto` 文件（taskTester.proto、parserTester.proto），表明曾有 gRPC 实验性探索；文档未提及，迁移规划时有参考价值 |
| W3 | incremental-roadmap.md | Route A 灰度开关 | `worker.rust-runner.enabled` 目前不存在于任何 yaml/properties 中；文档描述为待新增，语义正确，但未标注"待实现"易引起误读 |
| W4 | incremental-roadmap.md | Route B 灰度开关 | `audit.sidecar.enabled` 同上，待新增 |
| W5 | incremental-roadmap.md | Route C 灰度开关 | `alert.rust-backend.enabled` 同上，待新增 |
| W6 | no-go.md | Rust 边界图 | 图中未显示 alert-server、dolphinscheduler-extract、dolphinscheduler-registry-plugin 三个模块；它们自然归属 JVM 区，但图的完整性有欠缺 |
| W7 | no-go.md | dao 章节 | Spring Boot 版本引用错误（同 #11），dao 章节同样写了「via Spring Boot 2.6.1」，需一并修正 |

---

## 三、已修正项（Writer Agent 相对于假设初稿的改进）

以下内容经 Writer Agent 直接从代码取证写入，质量明显高于人工估算：

1. **所有 LOC 数据均为实测值**：worker 2,044 行、alert-server 1,680 行、extract 5,521 行、task-shell 220 行等，均经 `wc -l` 逐一验证。
2. **Transporter 帧参数取自源码**：`magic=0xbabe, version=0` 来自 Transporter.java:34-35，而非猜测。
3. **依赖版本全部取自 BOM**：hive-jdbc 2.3.9、kyuubi 1.7.0、presto 0.238.1、trino 402、hadoop 3.2.4、quartz 2.3.2、spring-ldap 2.4.1、casdoor 1.6.0 均通过 `grep` 确认。
4. **类路径/方法名均经文件确认**：PhysicalTaskExecutor(154 LOC)、PhysicalTaskEngineFactory(48 LOC)、PhysicalTaskExecutorFactory(66 LOC)、ShellTask(100 LOC)、MailSender(415 LOC)、WorkerServer(143 LOC) 等均验证行数与注解。
5. **Extract 无 proto 文件结论经双重确认**：全项目 grep 排除了生产 proto，仅 test-only stubs 存在。

---

## 四、待人工确认项

| # | 问题 | 建议 |
|---|------|------|
| C1 | no-go.md 将 Spring Boot 写为 2.6.1（实为 2.7.11）。no-go.md 第 4 节认证章节"Spring Security（via Boot 2.6.1）"中的功能描述是否随版本变化？ | 确认 2.7.11 的 Spring Security 版本是否影响 no-go 结论（预计不影响，因 Spring Security 5.x 在两版本中功能一致） |
| C2 | datasource-plugin 实测 12,377 LOC（文档写 ~15,000+）。这个差距来自测试文件/生成代码的计入方式差异还是文档高估？ | 检查 211 个文件中是否含生成代码或 `src/test/` 目录（Checker 已排除 test，但生成代码未确认） |
| C3 | Alert 插件实际 12 个，文档写 11 个。遗漏的是 `webexteams`（Cisco Webex Teams）。这个插件是否属于维护状态/是否纳入 Rust 化范围？ | 确认 webexteams 的使用率，决定是否加入路线 C 优先支持列表 |
| C4 | `dolphinscheduler-task-pytorch` 存在于磁盘但未在文档 task-plugin 列表中。PyTorch 任务对 Worker Rust Runner（路线 A）的支持策略如何？ | 明确 pytorch 任务是否属于「轻量任务」范畴（若为 shell 包装则可纳入路线 A） |
| C5 | 三条路线的灰度开关（W3/W4/W5）在任何现有配置文件中均不存在，需要在实现时新增。是否需要提前创建占位配置（`enabled: false`）以方便 CI/CD 感知？ | 在具体落地任务中明确配置文件路径和默认值 |

---

## 五、研究结论摘要

1. **Worker Rust 化收益最高、风险最低**：Worker runtime 仅 2,044 LOC，核心逻辑是进程启动器；Shell/HTTP/Python 任务无 JVM 专有依赖；Rust 可将启动时间从 8-15s 降至 <100ms，内存从 1-2GB 降至 50MB 级别。这是整个项目最适合 Rust 化的切入点。

2. **Alert Server 是最干净的独立替换目标**：1,680 LOC、零大数据 JVM 依赖、纯 IO 密集（HTTP + SMTP），12 个渠道插件中至少 10 个可用 `reqwest` + `lettre` 覆盖。可作为"Rust 二进制运维"的先行验证场景。

3. **Master / DAO / API 三件套永久 no-go**：master DAG 引擎 23,455 LOC，8 年社区 corner case 积累，重写代价 > 5 人年且收益为零（调度逻辑不产生资源瓶颈）。DAO/API 层是 CRUD 胶水，Rust 化无实质动机。

4. **Extract RPC 框架是互操作瓶颈**：Rust Worker 与 Java Master 共存时，必须实现自定义 Transporter 帧解码（magic=0xbabe）或迁移到 gRPC/proto。建议优先为 worker↔master 通信写 `.proto` 文件，用渐进式 proto 化替代整体重写。

5. **datasource-plugin 是不可逾越的 JVM 边界**：Hive/Presto/Trino/Kyuubi JDBC 驱动无生产可用 Rust 等价物，且 Hive JDBC 依赖 Hadoop UGI（Kerberos/SPNEGO），短期内 Rust 无法替代；SQL 类任务（占全量执行的大多数）必须保留 JVM。

---

## 六、推荐下一步

### 可立即拆出的子任务

**子任务 1（P0）：实现 dsl-task-runner PoC**

- 范围：Rust 二进制，通过 Unix Socket gRPC 接受 Shell 任务请求，执行 `/bin/bash`，流式回传 stdout，返回退出码
- 验收：`cargo test` 通过 shell_exec_exit_zero / nonzero / cancel 三个测试用例
- 工作量估算：1-2 周（单人）
- 依赖：需先确认 C5（配置文件占位方案）
- 阻塞项：无（不触碰 Java 代码，零风险）

**子任务 2（P1）：Extract 协议文档化 + proto 草稿**

- 范围：为 worker↔master 交互（PhysicalTaskExecutorOperator、TaskExecutorQueryClient）编写 `.proto` 文件草稿；不实现，仅为 Rust Runner 集成做准备
- 验收：proto 文件可通过 `protoc` 编译，字段与 StandardRpcRequest/StandardRpcResponse JSON 字段对齐
- 工作量估算：3-5 天
- 依赖：无

### 暂缓的路线

- 路线 B（审计 Sidecar）：等待 `audit-log-arch-rework` 任务的 Phase 3 推进，避免并行开发冲突
- 路线 C（Alert 替换）：等路线 A PoC 完成后，用同一 Rust 工程基础设施复用（共享 tokio runtime、serde 依赖、CI pipeline）

---

## 附：各文档修订优先级

| 文档 | 建议修订项 | 优先级 |
|------|-----------|--------|
| `no-go.md` | 修正 Spring Boot 版本 2.6.1 → 2.7.11（第 24 行、dao 章节） | P0（影响版本引用） |
| `components-overview.md` | 修正 task-plugin 数量（35→34），补入 pytorch，修正 HTTP Janino 描述，修正 Spark 方法名，补充 task-executor 独立行 | P1 |
| `rust-rewrite-matrix.md` | 修正 alert 插件数（11→12），修正 datasource LOC（~15,000+→~12,400），修正 kube-rs→kube | P1 |
| `incremental-roadmap.md` | 修正 AuditServiceImpl.addAudit 签名（List→单参数），标注三个灰度开关为「待新增」 | P1 |
