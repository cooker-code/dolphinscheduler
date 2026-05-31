# 错误处理 — 后端

## API 层

- `ApiExceptionHandler`（`@RestControllerAdvice`）统一把异常映射为结构化 JSON
- 响应格式：`{ code, msg, data }`，`code != 0` 表示错误
- 新建 Controller 异常必须能被 `ApiExceptionHandler` 捕获，禁止裸异常泄漏给调用方

## Service 层

- 写操作用 `@Transactional(rollbackFor = Exception.class)` 包裹
- 禁止静默吞掉异常——记日志后要么重抛、要么转换成域异常

## Master 引擎（最关键）

**状态机约束**：
- 禁止在 service 里随意做状态转换
- 新的状态转换必须走**生命周期事件 + 处理器**，让整个引擎感知到变化
- 禁止在事件处理器中使用 `Thread.sleep`，改用发布延迟事件

**Failover 变更约束**：
- `server.master.failover` 是最高风险代码路径
- 该包的任何变更必须在 `AbstractMasterIntegrationTestCase` 场景中验证

## 工作流执行（命令驱动）

工作流运行的完整链路：
```
Controller
  → CommandService.insertCommand（写 t_ds_command 行）
    → Master CommandEngine 消费
      → WorkflowEngine 处理
```

如果工作流"没有任何反应"，沿此链路排查，而不是直接看 service 层。

## Worker / Task

- 远程提交型任务（Spark、Flink、K8s）：Worker 必须保持存活直到远程作业完成；Worker 中途重启会触发 master 的 failover 路径
- 负载保护拒绝派发：正常行为，master 会自动选择其他 Worker，不是错误

## Cron 解析

- Quartz 负责触发调度，但 cron **解析**用 `cron-utils` 库
- 两者的 DOW（星期几）约定略有不同，归一化逻辑见 `CronService`
- 修改 cron 相关代码前必须先读 `CronService` 了解转换细节
