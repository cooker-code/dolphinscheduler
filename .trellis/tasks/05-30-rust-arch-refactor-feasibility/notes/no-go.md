# 明确不建议用 Rust 重写的组件清单

> 这份清单是 Rust 化的**边界**，定下来后避免后续无效讨论。

## 核心原则

任何重写组件，如果满足以下任一条件，则**不建议**：
1. 强依赖 JVM 生态（Hadoop/Hive/Spark/Flink/Quartz 等无 Rust 替代品）
2. 是项目核心调度 IP 且 corner case 极多（master DAG 引擎）
3. 重写后破坏现有插件契约或上游 SDK 兼容（task-plugin / datasource-plugin）
4. 收益 < 改造代价的数量级（dao 全套 Mapper 迁移）

## 具体清单

### 1. master 调度引擎
- **理由**：DolphinScheduler 的核心 IP；含失败重试、依赖判断、补数、子流程、参数透传等复杂逻辑；社区 8 年沉淀的 corner case 不可能短期重写覆盖
- **替代方案**：保留 Java，只在性能瓶颈点（如命令扫描、状态机切换）通过 JNI 或进程间通信调用 Rust 加速

### 2. dolphinscheduler-dao（MyBatis Mapper）
- **理由**：是项目的"SQL 真理来源"，所有业务 SQL 集中在 XML；迁移到 sqlx/SeaORM 等于重写所有数据访问层
- **替代方案**：不动，最多在 audit log / metric 这种新增子系统用 Rust 直连 DB

### 3. dolphinscheduler-api（REST 服务）
- **理由**：80% 是 CRUD，业务价值不在 Rust 化；前端 / OpenAPI / SDK / 文档全要重做
- **替代方案**：保留 Java；如某个高频接口（如审计查询）成为瓶颈，单点用 Rust 微服务旁路

### 4. task-plugin 中的 JVM 类任务
- **不重写清单**：Spark / Flink / Hive / DataX / SeaTunnel / Sqoop / Sql / MLflow（部分） / DataX
- **理由**：依赖 spark-submit / flink-cli / Hive JDBC / DataX framework 这些 JVM 独占客户端
- **替代方案**：Rust runner（路线 A）只接管 Shell/HTTP/Python，JVM 任务路径完全不动

### 5. datasource-plugin
- **不重写清单**：Hive / Presto / Trino / StarRocks / Kyuubi / SqlServer / Oracle / Redshift
- **理由**：JDBC 驱动是 JVM 独占，部分商用驱动甚至闭源
- **替代方案**：Rust 微服务里如需访问数据，用 Rust 原生客户端（但这是新建服务，不是替换）

### 6. storage-plugin
- **不重写清单**：HDFS（核心）
- **理由**：libhdfs 的 Rust 绑定不成熟；社区主流仍是 hadoop-client（JVM）
- **替代方案**：S3/OSS/GCS/Azure 这类对象存储如果新建 Rust 服务可以用 `object_store`，但不替换现有 plugin

### 7. dolphinscheduler-authentication
- **理由**：Spring Security + Casdoor + LDAP 全套 JVM 体系，Rust 重写没有任何工程价值
- **替代方案**：不动

### 8. dolphinscheduler-yarn-aop
- **理由**：直接 AOP 拦截 Hadoop 客户端字节码，纯 JVM 范畴
- **替代方案**：不动

### 9. dolphinscheduler-scheduler-plugin（Quartz）
- **理由**：Quartz 是 Java 独占调度库；Rust 侧无对等替代品（cron 表达式解析能做，但完整调度框架（持久化/集群/持久化触发器）需要重写）
- **替代方案**：保留

### 10. eventbus / meter
- **理由**：进程内事件总线 + Micrometer，跨语言无意义
- **替代方案**：保留

### 11. ui
- **理由**：前端纯 Vue/TS，不在 Rust 讨论范围
- **替代方案**：保留

## 边界一句话总结

> **Rust 只用在"独立运行、协议清晰、生态自包含"的组件**：worker 中的轻量任务执行、独立的 alert/audit sidecar、本地 IPC 加速器。
> 一旦触及 master DAG 引擎、JVM 生态客户端、Spring/MyBatis 等 Java 框架，立即止步。
