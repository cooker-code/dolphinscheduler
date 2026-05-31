# 日志规范 — 后端

## 日志框架

- **SLF4J + Logback**，配置文件为各模块的 `src/main/resources/logback-spring.xml`
- 每个模块有独立的 `logback-spring.xml`，可按模块调整日志级别

## 日志级别使用原则

| 级别 | 使用场景 |
|------|---------|
| `ERROR` | 需要立即关注的错误，影响业务正确性（任务失败、DB 异常、RPC 调用失败） |
| `WARN` | 异常但可恢复的情况（重试、负载保护触发、failover 启动） |
| `INFO` | 关键业务节点（工作流启动/完成/失败、任务派发、服务启停） |
| `DEBUG` | 调试信息，正常运行时不应产生大量输出 |

## 关键位置日志

**工作流引擎**：状态转换时必须记 INFO 级日志，包含 workflowInstanceId 和新旧状态。

**Failover 路径**：节点故障检测和恢复每一步都应记 WARN/INFO，便于事后审计。

**任务执行**：任务开始、结束、失败时记 INFO，包含 taskInstanceId、taskType、host。

## 禁止行为

- 禁止在循环内部打印大量 DEBUG 日志（影响性能）
- 禁止日志中记录密码、token 等敏感信息
- 禁止用 `System.out.println` 替代日志框架

## 远程日志

Worker 支持将任务日志推送到远程存储（S3/OSS 等），配置在 `common/src/main/resources/remote-logging.yaml`。  
修改任务日志路径逻辑时需同时考虑本地和远程两种场景。
