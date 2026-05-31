# 代码质量规范 — 后端

## 绝对禁止（NEVER DO）

| 禁止行为 | 原因 |
|---------|------|
| 未运行 `gitnexus_impact` 就修改函数/类/方法 | 无法评估影响范围 |
| 忽略 HIGH/CRITICAL 风险告警 | 可能破坏直接调用方 |
| 用 find-and-replace 重命名符号 | 应使用 `gitnexus_rename`，理解调用图 |
| 未运行 `gitnexus_detect_changes()` 就提交 | 无法确认改动范围符合预期 |
| 在 master engine 加 ad-hoc 状态转换 | 破坏状态机一致性 |
| 向 `t_ds_command` 以外的方式触发工作流 | 绕过命令驱动架构 |

## 禁止模式

### 跨层直调 Mapper
```java
// ❌ 禁止
@Autowired
private WorkflowInstanceMapper workflowInstanceMapper; // 在 service/master 中

// ✅ 正确
@Autowired
private WorkflowInstanceRepository workflowInstanceRepository;
```

### 在处理器中 sleep
```java
// ❌ 禁止
Thread.sleep(1000);

// ✅ 正确
eventBus.publishDelayed(new RetryEvent(), Duration.ofSeconds(1));
```

### 引入 Tomcat
```xml
<!-- ❌ 禁止：Tomcat 已被传递性排除 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
</dependency>
```

### 在 common 引入 DS 内部依赖
`common` 是底层库，不得依赖任何其他 DS 模块。

## 必须遵守

### Swagger 注解（API 控制器必填）
```java
@Operation(summary = "创建项目")
@Parameter(name = "projectName", description = "项目名称")
```
缺少 Swagger 注解会导致前端团队消费的文档不完整。

### 新服务器/长生命周期组件实现 IStoppable
```java
public class MyServer implements IStoppable {
    @Override
    public void stop(String cause) { ... }
}
```

### 参数扩展顺序（不可变更）
`CuringParamsServiceImpl` 中的参数优先级：
```
项目参数 → 工作流参数 → 任务参数 → 内置参数
```
改变此顺序是对所有任务可见的契约变更。

## 构建命令

```bash
# 完整构建（含 release 产物）
./mvnw clean install -Prelease

# 跳过 UI 构建（后端快速迭代）
./mvnw -pl '!dolphinscheduler-ui' clean install

# 构建单个模块（含必要依赖）
./mvnw -pl dolphinscheduler-master -am clean install

# 最快本地构建
mvn clean install -DskipTests -Dspotless.skip=true

# 格式化（提交前必须通过）
./mvnw spotless:apply
```

## 测试命令

```bash
# 单模块单元测试
./mvnw -pl dolphinscheduler-master -am clean test

# 指定测试类
mvn test -Dtest=WorkflowInstanceMapperTest -pl dolphinscheduler-dao

# Master 集成测试（无需 Docker，使用内存 H2）
./mvnw -pl dolphinscheduler-master -am clean test \
    -Dtest=WorkerGroupDispatcherTest \
    -Dsurefire.failIfNoSpecifiedTests=false

# API 集成测试（需要 Docker）
mvn -pl dolphinscheduler-api-test/dolphinscheduler-api-test-case test

# Apple Silicon 额外参数
# 对 api-test / e2e 加 -Dm1_chip=true
```

## API 模块特殊测试命令

```bash
# 一次性安装依赖到本地 m2（跑测试前必须先执行）
export MAVEN_OPTS="-Xmx4g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=1024m"
./mvnw install -B \
  -pl "dolphinscheduler-bom,dolphinscheduler-api" \
  -am -DskipTests=true -Dspotless.skip=true -DskipUT=true \
  -Djacoco.skip=true -Danalyze.skip=true

# 运行测试（用 verify，不是 test）
./mvnw verify -B -pl "dolphinscheduler-api" \
  -Dmaven.test.skip=false -Dspotless.skip=true -DskipUT=false -Danalyze.skip=true \
  -Dsurefire.printSummary=true -Dsurefire.useFile=false
```

> **注意**：API 模块必须用 `verify`（不是 `test`），否则 JaCoCo 会留下已插桩的 class 文件导致下次构建失败。

## 常见错误

| 错误 | 现象 | 修复 |
|------|------|------|
| `Cannot process instrumented class` | 构建失败 | 加 `-Djacoco.skip=true` 或清理 `target/` |
| `NoClassDefFoundError: oshi/SystemInfo` | 测试运行时崩溃 | 重新执行一次性安装步骤（含 `dolphinscheduler-bom`）|
| `class file contains wrong class` | 本地 `.m2` 中有损坏的 `dev-SNAPSHOT` jar | 删除 `~/.m2/repository/org/apache/dolphinscheduler/` 对应目录重装 |
