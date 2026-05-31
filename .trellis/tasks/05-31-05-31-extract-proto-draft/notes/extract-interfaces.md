# DolphinScheduler Extract RPC Interfaces

This document covers all `@RpcService` interfaces relevant to task dispatching and lifecycle reporting,
organized by call direction.

---

## Direction Overview

| Direction        | Interface(s)                                           | Purpose                              |
|------------------|-------------------------------------------------------|--------------------------------------|
| master → worker  | `IPhysicalTaskExecutorOperator`                       | Dispatch/kill/pause physical tasks   |
| master → master  | `ILogicTaskExecutorOperator`                          | Dispatch/kill/pause logic tasks      |
| worker → master  | `ITaskExecutorEventListener`                          | Report task lifecycle events         |
| worker → master  | `ITaskInstanceController`                             | Notify task group slot acquired      |
| api/svc → master | `IWorkflowControlClient`                              | Trigger/stop/pause workflow instances|
| master → worker  | `ITaskExecutorQueryClient`                            | Query running task instances         |

---

## 1. IPhysicalTaskExecutorOperator

**Direction:** master → worker (physical task execution on worker node)

**File:** `dolphinscheduler-extract/dolphinscheduler-extract-worker/src/main/java/org/apache/dolphinscheduler/extract/worker/IPhysicalTaskExecutorOperator.java`

**Methods:**

| Method | Request | Response |
|--------|---------|----------|
| `dispatchTask` | `TaskExecutorDispatchRequest` | `TaskExecutorDispatchResponse` |
| `killTask` | `TaskExecutorKillRequest` | `TaskExecutorKillResponse` |
| `pauseTask` | `TaskExecutorPauseRequest` | `TaskExecutorPauseResponse` |
| `reassignWorkflowInstanceHost` | `TaskExecutorReassignMasterRequest` | `TaskExecutorReassignMasterResponse` |
| `ackPhysicalTaskExecutorLifecycleEvent` | `ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck` | `void` |

### TaskExecutorDispatchRequest

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskExecutionContext` | `TaskExecutionContext` | `TaskExecutionContext` (nested message) | 任务执行上下文，包含所有运行所需信息 |

#### TaskExecutionContext (embedded)

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` | `int` | `int32` | required by rust-runner — 任务实例唯一 ID |
| `taskName` | `String` | `string` | optional — 可读名称 |
| `taskType` | `String` | `string` | required by rust-runner — 插件类型 (SHELL/SPARK/…) |
| `workflowInstanceHost` | `String` | `string` | required by rust-runner — master 地址，用于回调 |
| `host` | `String` | `string` | required by rust-runner — worker 自身地址 |
| `executePath` | `String` | `string` | required by rust-runner — 任务工作目录 |
| `logPath` | `String` | `string` | required by rust-runner — 日志输出路径 |
| `taskParams` | `String` | `string` | required by rust-runner — 任务参数 JSON |
| `tenantCode` | `String` | `string` | required by rust-runner — OS 执行用户 |
| `environmentConfig` | `String` | `string` | optional — 环境变量脚本 |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner — 所属工作流实例 ID |
| `workflowDefinitionCode` | `Long` | `int64` | optional |
| `workflowDefinitionVersion` | `int` | `int32` | optional |
| `workflowDefinitionId` | `int` | `int32` | java-only |
| `projectCode` | `Long` | `int64` | optional |
| `firstSubmitTime` | `long` | `int64` | optional — 首次提交时间戳(ms) |
| `startTime` | `long` | `int64` | optional — 开始时间戳(ms) |
| `endTime` | `long` | `int64` | optional — 结束时间戳(ms) |
| `scheduleTime` | `long` | `int64` | optional |
| `workflowInstanceName` | `String` | `string` | optional — 工作流实例名称 |
| `globalParams` | `String` | `string` | optional — 全局参数 JSON |
| `prepareParamsMap` | `Map<String,Property>` | `map<string, Property>` | optional — 已解析参数 |
| `varPool` | `List<Property>` | `repeated Property` | optional — 前驱传递的变量 |
| `taskTimeoutStrategy` | `TaskTimeoutStrategy` | `string` (enum name) | optional |
| `taskTimeout` | `int` | `int32` | optional — 超时秒数 |
| `workerGroup` | `String` | `string` | java-only — 路由用，rust 端不需要 |
| `appIds` | `String` | `string` | optional — YARN/K8s application IDs |
| `processId` | `int` | `int32` | optional — OS 进程 PID |
| `executorId` | `int` | `int32` | java-only |
| `dryRun` | `int` | `int32` | optional — 0=正常, 1=dry-run |
| `cpuQuota` | `Integer` | `int32` | optional |
| `memoryMax` | `Integer` | `int32` | optional |
| `sqlTaskExecutionContext` | `SQLTaskExecutionContext` | `string` (JSON) | java-only |
| `k8sTaskExecutionContext` | `K8sTaskExecutionContext` | `string` (JSON) | java-only |
| `resourceContext` | `ResourceContext` | `string` (JSON) | java-only |
| `resourceParametersHelper` | `ResourceParametersHelper` | `string` (JSON) | java-only |
| `appInfoPath` | `String` | `string` | optional — YARN/Flink app info 文件路径 |

### TaskExecutorDispatchResponse

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `dispatchSuccess` | `boolean` | `bool` | required by rust-runner — 是否分发成功 |
| `message` | `String` | `string` | optional — 失败原因 |

### TaskExecutorKillRequest

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` | `Integer` | `int32` | required by rust-runner — 要 kill 的任务实例 ID |

### TaskExecutorKillResponse

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `success` | `boolean` | `bool` | required by rust-runner — 操作是否成功 |
| `message` | `String` | `string` | optional — 失败原因 |

### TaskExecutorPauseRequest

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` | `Integer` | `int32` | required by rust-runner — 要暂停的任务实例 ID |

### TaskExecutorPauseResponse

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `success` | `boolean` | `bool` | required by rust-runner — 操作是否成功 |
| `message` | `String` | `string` | optional — 失败原因 |

### TaskExecutorReassignMasterRequest

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` | `int` | `int32` | required by rust-runner — 任务实例 ID |
| `workflowHost` | `String` | `string` | required by rust-runner — 新 master 地址 (host:port) |

### TaskExecutorReassignMasterResponse

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `success` | `boolean` | `bool` | optional — 操作是否成功 |
| `message` | `String` | `string` | optional — 失败原因 |

### TaskExecutorLifecycleEventAck (ackPhysicalTaskExecutorLifecycleEvent)

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskExecutorId` | `int` | `int32` | required by rust-runner — 对应任务实例 ID |
| `taskExecutorLifecycleEventType` | `TaskExecutorLifecycleEventType` | `TaskLifecycleEventType` (proto enum) | required by rust-runner — ACK 的事件类型 |

---

## 2. ILogicTaskExecutorOperator

**Direction:** master → master (logic/embedded task execution on master)

**File:** `dolphinscheduler-extract/dolphinscheduler-extract-master/src/main/java/org/apache/dolphinscheduler/extract/master/ILogicTaskExecutorOperator.java`

**Methods:**

| Method | Request | Response |
|--------|---------|----------|
| `dispatchTask` | `TaskExecutorDispatchRequest` | `TaskExecutorDispatchResponse` |
| `killTask` | `TaskExecutorKillRequest` | `TaskExecutorKillResponse` |
| `pauseTask` | `TaskExecutorPauseRequest` | `TaskExecutorPauseResponse` |
| `ackTaskExecutorLifecycleEvent` | `ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck` | `void` |

Same DTOs as `IPhysicalTaskExecutorOperator` (no `reassignWorkflowInstanceHost`). This interface handles tasks that run in-process on master (switch/conditions/sub-workflow).

---

## 3. ITaskExecutorEventListener

**Direction:** worker → master (lifecycle event reporting)

**File:** `dolphinscheduler-extract/dolphinscheduler-extract-master/src/main/java/org/apache/dolphinscheduler/extract/master/ITaskExecutorEventListener.java`

**Methods:**

| Method | Event Class | Trigger |
|--------|-------------|---------|
| `onTaskExecutorDispatched` | `TaskExecutorDispatchedLifecycleEvent` | Worker received & accepted the task |
| `onTaskExecutorRunning` | `TaskExecutorStartedLifecycleEvent` | Task process/thread started |
| `onTaskExecutorRuntimeContextChanged` | `TaskExecutorRuntimeContextChangedLifecycleEvent` | PID / appIds updated |
| `onTaskExecutorSuccess` | `TaskExecutorSuccessLifecycleEvent` | Task exited successfully |
| `onTaskExecutorFailed` | `TaskExecutorFailedLifecycleEvent` | Task exited with failure |
| `onTaskExecutorKilled` | `TaskExecutorKilledLifecycleEvent` | Kill completed |
| `onTaskExecutorPaused` | `TaskExecutorPausedLifecycleEvent` | Pause completed |

All event classes extend `AbstractTaskExecutorLifecycleEvent` which contains:

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` | `int` | `int32` | required by rust-runner — 基类字段 |
| `eventCreateTime` | `long` | `int64` | optional — 事件创建时间戳(ms) |
| `type` | `TaskExecutorLifecycleEventType` | `string` (enum name) | java-only — 事件类型 |

### TaskExecutorDispatchedLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner |
| `taskInstanceHost` | `String` | `string` | required by rust-runner — worker 地址 |
| `latestReportTime` | `Long` | `int64` | java-only — 重传追踪时间 |

### TaskExecutorStartedLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner |
| `taskInstanceHost` | `String` | `string` | required by rust-runner — worker 地址 |
| `startTime` | `long` | `int64` | required by rust-runner — 启动时间戳(ms) |
| `logPath` | `String` | `string` | required by rust-runner — 日志文件路径 |
| `executePath` | `String` | `string` | optional — 工作目录 |
| `latestReportTime` | `Long` | `int64` | java-only |

### TaskExecutorRuntimeContextChangedLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | optional |
| `taskInstanceHost` | `String` | `string` | optional |
| `processId` | `int` | `int32` | optional — @Deprecated，OS PID |
| `appIds` | `String` | `string` | required by rust-runner — YARN/K8s app IDs |
| `latestReportTime` | `Long` | `int64` | java-only |

### TaskExecutorSuccessLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner |
| `taskInstanceHost` | `String` | `string` | optional |
| `endTime` | `long` | `int64` | required by rust-runner — 完成时间戳(ms) |
| `varPool` | `List<Property>` | `repeated Property` | required by rust-runner — 输出变量，传递给下游 |
| `latestReportTime` | `Long` | `int64` | java-only |

### TaskExecutorFailedLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner |
| `taskInstanceHost` | `String` | `string` | optional |
| `appIds` | `String` | `string` | optional — YARN/K8s app IDs |
| `endTime` | `long` | `int64` | required by rust-runner — 失败时间戳(ms) |
| `latestReportTime` | `Long` | `int64` | java-only |

### TaskExecutorKilledLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner |
| `taskInstanceHost` | `String` | `string` | optional |
| `endTime` | `long` | `int64` | required by rust-runner — kill 完成时间戳(ms) |
| `latestReportTime` | `Long` | `int64` | java-only |

### TaskExecutorPausedLifecycleEvent

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `taskInstanceId` (inherited) | `int` | `int32` | required by rust-runner |
| `workflowInstanceId` | `int` | `int32` | required by rust-runner |
| `taskInstanceHost` | `String` | `string` | optional |
| `endTime` | `long` | `int64` | required by rust-runner — pause 完成时间戳(ms) |
| `latestReportTime` | `Long` | `int64` | java-only |

---

## 4. ITaskInstanceController

**Direction:** worker → master (task group slot coordination)

**File:** `dolphinscheduler-extract/dolphinscheduler-extract-master/src/main/java/org/apache/dolphinscheduler/extract/master/ITaskInstanceController.java`

**Methods:**

| Method | Request | Response |
|--------|---------|----------|
| `notifyTaskGroupSlotAcquireSuccess` | `TaskGroupSlotAcquireSuccessNotifyRequest` | `TaskGroupSlotAcquireSuccessNotifyResponse` |

### TaskGroupSlotAcquireSuccessNotifyRequest

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `workflowInstanceId` | `Integer` | `int32` | java-only — 工作流实例 ID |
| `taskInstanceId` | `Integer` | `int32` | java-only — 任务实例 ID，获得了 task group slot |

### TaskGroupSlotAcquireSuccessNotifyResponse

| 字段名 | Java 类型 | proto 类型映射 | 说明 |
|--------|-----------|---------------|------|
| `success` | `boolean` | `bool` | java-only |
| `message` | `String` | `string` | java-only |

---

## 5. IWorkflowControlClient

**Direction:** api/service → master (workflow trigger & control)

**File:** `dolphinscheduler-extract/dolphinscheduler-extract-master/src/main/java/org/apache/dolphinscheduler/extract/master/IWorkflowControlClient.java`

**Methods:**

| Method | Request | Response |
|--------|---------|----------|
| `manualTriggerWorkflow` | `WorkflowManualTriggerRequest` | `WorkflowManualTriggerResponse` |
| `backfillTriggerWorkflow` | `WorkflowBackfillTriggerRequest` | `WorkflowBackfillTriggerResponse` |
| `scheduleTriggerWorkflow` | `WorkflowScheduleTriggerRequest` | `WorkflowScheduleTriggerResponse` |
| `repeatTriggerWorkflowInstance` | `WorkflowInstanceRepeatRunningRequest` | `WorkflowInstanceRepeatRunningResponse` |
| `triggerFromFailureTasks` | `WorkflowInstanceRecoverFailureTasksRequest` | `WorkflowInstanceRecoverFailureTasksResponse` |
| `triggerFromSuspendTasks` | `WorkflowInstanceRecoverSuspendTasksRequest` | `WorkflowInstanceRecoverSuspendTasksResponse` |
| `pauseWorkflowInstance` | `WorkflowInstancePauseRequest` | `WorkflowInstancePauseResponse` |
| `stopWorkflowInstance` | `WorkflowInstanceStopRequest` | `WorkflowInstanceStopResponse` |

Note: Workflow control DTOs are in `extract-master/transportor/workflow/` and are java-only for the rust-runner scope (rust-runner only executes tasks, not workflows).

---

## 6. ITaskExecutorQueryClient

**Direction:** master → worker (query running tasks)

**File:** `dolphinscheduler-extract/dolphinscheduler-extract-worker/src/main/java/org/apache/dolphinscheduler/extract/worker/ITaskExecutorQueryClient.java`

**Methods:**

| Method | Request | Response |
|--------|---------|----------|
| `queryTaskInstances` | `TaskExecutorQueryRequest` | `TaskExecutorQueryResponse` |

`TaskExecutorQueryRequest` has no fields (empty query = return all running tasks). Response fields are in `extract-worker/transportor/` and are java-only (used for master reconciliation, not needed by rust-runner).

---

## Summary: Fields Required by rust-runner

Fields marked `required by rust-runner` are those a Rust-based task executor needs to:
1. Execute the task (dispatch path): `taskInstanceId`, `taskType`, `taskParams`, `tenantCode`, `executePath`, `logPath`, `workflowInstanceHost`
2. Report lifecycle back (event path): `taskInstanceId`, `workflowInstanceId`, event-specific fields (`startTime`, `endTime`, `appIds`, `varPool`)
3. Accept control (kill/pause path): `taskInstanceId`
4. Receive master failover (reassign path): `taskInstanceId`, `workflowHost`
