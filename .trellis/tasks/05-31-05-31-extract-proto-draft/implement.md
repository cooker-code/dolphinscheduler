# Extract 协议文档化 + proto 草稿 — 执行计划

## 前置条件

- [ ] `protoc` 已安装（`protoc --version`）

## 执行步骤

### Step 1：定位 Extract 接口

```bash
# 找 worker↔master 核心接口
find dolphinscheduler-extract -name "*.java" | grep -v test | grep -v "impl" | head -30
# 找 @RpcService 注解（接口定义处）
grep -r "@RpcService" dolphinscheduler-extract --include="*.java" -l
# 找 @RpcMethod 注解（方法定义处）
grep -r "@RpcMethod" dolphinscheduler-extract --include="*.java" | head -20
```

目标：找到 `PhysicalTaskExecutorOperator`、`TaskExecutorQueryClient`、`WorkerRpcClient`、`TaskInstanceOperator` 四个接口文件。

---

### Step 2：读取接口方法 + JSON 字段

对每个接口：
1. 读取 Java interface 文件，提取方法签名和参数类型
2. 找对应的 Request/Response Java 类（Jackson 序列化对象）
3. 列出所有 `@JsonProperty` 或字段名（作为 proto 字段来源）

```bash
# 找具体 DTO 类
grep -r "StandardRpcRequest\|StandardRpcResponse" dolphinscheduler-extract --include="*.java" -l
# 找 worker 向 master 上报任务状态的类
grep -r "TaskExecuteResultCommand\|TaskExecutionStatus" dolphinscheduler-common/src --include="*.java" -l | head -5
```

---

### Step 3：写 `notes/extract-interfaces.md`

格式：

```markdown
## 接口：PhysicalTaskExecutorOperator

方向：Master → Worker
注解：@RpcService(clazz = PhysicalTaskExecutorOperator.class)

### executeTask(TaskExecuteRequestCommand) → void

Request 字段（来自 TaskExecuteRequestCommand.java）：
| 字段名 | Java 类型 | 必填 |
|--------|-----------|------|
| taskDefinitionCode | long | Y |
| ...

Response：void（无返回值）
```

---

### Step 4：写 `notes/worker-master.proto`

基于 Step 3 的字段清单，对每个接口写 message + service：

```protobuf
syntax = "proto3";
package ds.extract.v1;

// master → worker：分发任务
service WorkerTaskExecutor {
  rpc ExecuteTask (ExecuteTaskRequest) returns (ExecuteTaskResponse);
  rpc KillTask    (KillTaskRequest)    returns (KillTaskResponse);
}

message ExecuteTaskRequest {
  // required by rust-runner
  int64  task_instance_id   = 1;
  string task_type          = 2;
  string script             = 3;
  // ...（来自 TaskExecuteRequestCommand 字段）
}
```

字段 tag 规则：与 JSON key 名称保持语义一致（Rust 侧可用 serde_json 对齐）。

---

### Step 5：验证 proto 可编译

```bash
protoc --proto_path=notes notes/worker-master.proto
```

确保无语法错误。

---

### Step 6：标注最小字段集

在 proto 文件中为每个字段加注释：
- `// required by rust-runner`：P0 集成时 Rust 侧必须填写
- `// java-only`：当前 Rust runner 不需要，预留给未来
- `// optional`：可选，默认值即可

---

## 完成标准（与 PRD 一致）

```
notes/extract-interfaces.md 覆盖 4 个接口 ✅
notes/worker-master.proto 通过 protoc 编译 ✅
proto 字段与 Java JSON 字段一一对应 ✅
最小字段集已注释标注 ✅
```
