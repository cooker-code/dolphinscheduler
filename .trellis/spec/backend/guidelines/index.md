# 后端开发规范 — DolphinScheduler

> 约束来源：各模块 `CLAUDE.md` + `AGENTS.md`。AI 辅助开发时优先读本目录。

---

## 规范索引

| 规范文件 | 说明 |
|---------|------|
| [目录结构](./directory-structure.md) | 模块分层、职责划分、依赖规则 |
| [数据库规范](./database-guidelines.md) | MyBatis-Plus、方言、Schema、事务 |
| [错误处理](./error-handling.md) | 异常模式、事务边界、状态机约束 |
| [代码质量](./quality-guidelines.md) | 禁止模式、编码标准、常见错误 |
| [日志规范](./logging-guidelines.md) | 日志级别、结构化日志 |

---

## 技术栈（不可偏离）

- **Java 1.8** — 禁止使用 Java 11+ API，唯一例外是 `dolphinscheduler-api-test`
- **Spring Boot 2.6.1** + **Jetty**（Tomcat 已被传递性排除，禁止引入）
- **MyBatis-Plus** ORM + **HikariCP** 连接池（元数据 DB 专用）
- **Maven** 多模块 reactor（26 个模块）
- **Quartz** 负责 cron 调度，通过 `dolphinscheduler-scheduler-plugin` 接入
- **Netty / gRPC** 负责 master ↔ worker RPC 通信

---

## 开发前检查清单

编写或修改后端代码前，必须确认：

- [ ] 修改的符号已通过 `gitnexus_impact` 做影响分析
- [ ] HIGH/CRITICAL 风险已告知用户并获得确认
- [ ] 新写方法遵循所在层的职责（不跨层调用 mapper、不在 master 里写业务逻辑）
- [ ] 写操作已加 `@Transactional(rollbackFor = Exception.class)`
- [ ] 涉及枚举改名时已全局 grep，确认 DB 序列化不会损坏
- [ ] Schema 变更同时更新了 MySQL / PostgreSQL / H2 三份 SQL 文件

---

## 质量检查清单

提交前必须确认：

- [ ] `gitnexus_detect_changes()` 确认变更范围在预期内
- [ ] 所有 d=1（直接调用方）依赖已同步更新
- [ ] 新增/修改的 mapper XML 已验证三种方言兼容性
- [ ] 没有直接从 master/service 调用 mapper（应通过 repository）
- [ ] 没有在 master engine 里新增 ad-hoc 状态转换
