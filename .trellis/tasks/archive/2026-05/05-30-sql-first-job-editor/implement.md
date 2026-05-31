# Implement: SQL-first Job Editor

有序执行清单，分三个阶段。每步注明验收方式。

---

## Phase 1.1 — 后端基础（DAO + 解析服务）

### 1. DDL：新建 `t_ds_sql_job` 表

在三个 schema 文件中追加建表语句：

- `dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_mysql.sql`
- `dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_postgresql.sql`
- `dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_h2.sql`

```sql
CREATE TABLE IF NOT EXISTS t_ds_sql_job (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_code  BIGINT       NOT NULL,
  user_id       INT          NOT NULL,
  name          VARCHAR(255) NOT NULL,
  sql_content   MEDIUMTEXT,
  datasource_id INT,
  datasource_type VARCHAR(64),
  workflow_code BIGINT,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_name (project_code, name)
);
```
(PostgreSQL/H2 版本去掉 `AUTO_INCREMENT` 改 `SERIAL` / `IDENTITY`)

**验证**：H2 跑现有 DAO 集成测试不报错：
```bash
./mvnw verify -B -pl "dolphinscheduler-dao" -Dspotless.skip=true -DskipUT=false -Danalyze.skip=true
```

---

### 2. `SqlJob` 实体

**文件**：`dolphinscheduler-dao/src/main/java/org/apache/dolphinscheduler/dao/entity/SqlJob.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("t_ds_sql_job")
public class SqlJob {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long projectCode;
    private Integer userId;
    private String name;
    @TableField("sql_content")
    private String sqlContent;
    private Integer datasourceId;
    private String datasourceType;
    private Long workflowCode;
    private Date createTime;
    private Date updateTime;
}
```

---

### 3. `SqlJobMapper`

**文件**：`dolphinscheduler-dao/src/main/java/org/apache/dolphinscheduler/dao/mapper/SqlJobMapper.java`

```java
@Mapper
public interface SqlJobMapper extends BaseMapper<SqlJob> {
    List<SqlJob> selectByProjectCode(@Param("projectCode") long projectCode);
}
```

XML：`dolphinscheduler-dao/src/main/resources/org/apache/dolphinscheduler/dao/mapper/SqlJobMapper.xml`
```xml
<select id="selectByProjectCode" resultType="SqlJob">
  SELECT * FROM t_ds_sql_job
  WHERE project_code = #{projectCode}
  ORDER BY update_time DESC
</select>
```

**验证**：
```bash
./mvnw verify -B -pl "dolphinscheduler-dao" -Dspotless.skip=true -DskipUT=false \
  -Dtest=SqlJobMapperTest -Danalyze.skip=true
```

---

### 4. `SqlParserService`

**文件**：`dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/SqlParserService.java`

核心逻辑（纯正则，无外部依赖）：

```java
@Service
public class SqlParserService {

    // 匹配 WITH cte_name AS (
    private static final Pattern CTE_PATTERN =
        Pattern.compile("\\bWITH\\b.*?\\b(\\w+)\\s+AS\\s*\\(", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // INSERT INTO/OVERWRITE [TABLE] tableName
    private static final Pattern INSERT_PATTERN =
        Pattern.compile("\\bINSERT\\s+(?:INTO|OVERWRITE)\\s+(?:TABLE\\s+)?(\\S+)", Pattern.CASE_INSENSITIVE);
    // FROM tableName (不含子查询括号)
    private static final Pattern FROM_PATTERN =
        Pattern.compile("\\bFROM\\s+(\\w[\\w.]*)", Pattern.CASE_INSENSITIVE);
    // JOIN tableName
    private static final Pattern JOIN_PATTERN =
        Pattern.compile("\\bJOIN\\s+(\\w[\\w.]*)", Pattern.CASE_INSENSITIVE);
    // 动态变量
    private static final Pattern VAR_PATTERN =
        Pattern.compile("\\$\\{[^}]+\\}");

    public SqlParseResult parse(String sql) { ... }
}
```

必须覆盖的 6 种 SQL 模式（单元测试用例驱动）：
1. `INSERT INTO t1 SELECT ... FROM t2` → target=t1, sources=[t2]
2. `INSERT OVERWRITE TABLE t1 SELECT ... FROM t2 JOIN t3` → target=t1, sources=[t2,t3]
3. CTE：`WITH cte AS (SELECT ... FROM t2) INSERT INTO t1 SELECT ... FROM cte` → sources=[t2]（cte 被排除）
4. 多层 JOIN：`FROM t2 LEFT JOIN t3 INNER JOIN t4` → sources=[t2,t3,t4]
5. 动态变量：`FROM ${tableName}` → unresolvedRefs=["${tableName}"]
6. 注释 + 大小写混合：`-- comment\ninsert Into T1 select * from T2` → target=t1(lower), sources=[t2(lower)]

**验证**：
```bash
./mvnw verify -B -pl "dolphinscheduler-api" -Dspotless.skip=true -DskipUT=false \
  -Dtest=SqlParserServiceTest -Danalyze.skip=true
```

---

### 5. `DependencyResolverService`

**文件**：`dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/DependencyResolverService.java`

```java
@Service
public class DependencyResolverService {

    @Autowired private TaskDefinitionMapper taskDefinitionMapper;
    @Autowired private SqlParserService sqlParserService;

    // 构建 projectCode 下所有 SQL task 的 targetTable → taskCode 索引
    public Map<String, Long> buildTargetTableIndex(long projectCode) { ... }

    // 将 sourceTables 映射到已有任务
    public List<DependencyMatchResult> resolve(List<String> sourceTables, long projectCode) { ... }

    // 循环依赖检测（DFS）
    public Optional<String> detectCycles(List<ConfirmedDependency> deps, String selfTable) { ... }
}
```

查询语句（通过 `TaskDefinitionMapper.xml` 或直接用 MyBatis-Plus）：
```sql
SELECT code, task_params
FROM t_ds_task_definition
WHERE project_code = #{projectCode} AND task_type = 'SQL' AND flag = 1
```

**验证**（单元测试，mock TaskDefinitionMapper）：
```bash
./mvnw verify -B -pl "dolphinscheduler-api" -Dspotless.skip=true -DskipUT=false \
  -Dtest=DependencyResolverServiceTest -Danalyze.skip=true
```

---

## Phase 1.2 — 后端 API（Controller + DagCompilerService）

### 6. `DagCompilerService`

**文件**：`dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/DagCompilerService.java`

入参 `SqlJobDeployRequest`：
```java
public class SqlJobDeployRequest {
    private String sql;
    private Integer datasourceId;
    private String datasourceType;
    private String mergeStrategy;            // OVERWRITE | APPEND
    private List<ConfirmedDependency> confirmedDependencies;
    private String cron;
}
```

输出：调用 `WorkflowDefinitionService` 所需的参数对象（封装为内部 record `CompileResult`）：
```java
public record CompileResult(
    String taskDefinitionJson,
    String taskRelationJson,
    String locations
) {}
```

`taskDefinitionJson` 结构（JSON array，单元素，对应 `SqlParameters`）：
```json
[{
  "code": <generated>,
  "name": "sql_task_<jobName>",
  "taskType": "SQL",
  "taskParams": {
    "type": "<datasourceType>",
    "datasource": <datasourceId>,
    "sql": "<sql>",
    "sqlType": 1,
    "limit": 1000,
    "preStatements": [],
    "postStatements": []
  },
  "flag": "YES",
  "taskPriority": "MEDIUM",
  "workerGroup": "default",
  "failRetryTimes": 0,
  "timeout": 0,
  "timeoutFlag": "CLOSE"
}]
```

`taskRelationJson`（单节点无前驱）：
```json
[{ "preTaskCode": 0, "preTaskVersion": 0, "postTaskCode": <taskCode>, "postTaskVersion": 1, "conditionType": "AND", "conditionParams": {} }]
```

`locations`（单节点默认坐标）：
```json
[{ "taskCode": <taskCode>, "x": 100, "y": 100 }]
```

单元测试覆盖：
- 无依赖场景（空 confirmedDependencies）
- 单依赖场景
- 循环依赖由 DependencyResolverService.detectCycles 提前拦截，此处不重复测试

**验证**：
```bash
./mvnw verify -B -pl "dolphinscheduler-api" -Dspotless.skip=true -DskipUT=false \
  -Dtest=DagCompilerServiceTest -Danalyze.skip=true
```

---

### 7. `SqlJobService` / `SqlJobServiceImpl`

**文件**：
- `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/SqlJobService.java`
- `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/impl/SqlJobServiceImpl.java`

方法：
```java
SqlJob create(User loginUser, long projectCode, String name);
SqlJob save(long id, String sql, int datasourceId, String datasourceType);
TestRunResult testRun(long id, int limitRows);
SqlParseResult parseSql(long id);
DependencyPreviewResult dependencyPreview(long id);
DeployResult deploy(User loginUser, long projectCode, long id, SqlJobDeployRequest request);
```

`deploy` 内部逻辑：
1. 读取 SqlJob，验证 sql 非空
2. `SqlParserService.parse(sql)` → 检查 `hasMultipleTargets` → 400
3. `DependencyResolverService.detectCycles(confirmedDeps, targetTable)` → 400（含路径说明）
4. `DagCompilerService.compile(...)` → CompileResult
5. `if sqlJob.workflowCode == null` → `createWorkflowDefinition(...)` else → `offline → update → online`
6. `onlineWorkflowDefinition(loginUser, projectCode, workflowCode)`
7. 更新 `SqlJob.workflowCode`

---

### 8. `SqlJobController`

**文件**：`dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/controller/SqlJobController.java`

```java
@Tag(name = "SQL_JOB_TAG")
@RestController
@RequestMapping("projects/{projectCode}/sql-jobs")
@Slf4j
public class SqlJobController extends BaseController {
    @Autowired private SqlJobService sqlJobService;

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public Result<SqlJob> createSqlJob(@RequestAttribute(SESSION_USER) User loginUser,
        @PathVariable long projectCode,
        @RequestParam String name) { ... }

    @PutMapping("/{id}")
    public Result<SqlJob> saveSqlContent(@RequestAttribute(SESSION_USER) User loginUser,
        @PathVariable long projectCode, @PathVariable long id,
        @RequestBody SaveSqlRequest request) { ... }

    @PostMapping("/{id}/test")
    public Result<TestRunResult> testRun(@RequestAttribute(SESSION_USER) User loginUser,
        @PathVariable long projectCode, @PathVariable long id,
        @RequestParam(defaultValue = "1000") int limitRows) { ... }

    @GetMapping("/{id}/parse")
    public Result<SqlParseResult> parseSql(@PathVariable long projectCode, @PathVariable long id) { ... }

    @GetMapping("/{id}/dependency-preview")
    public Result<DependencyPreviewResult> dependencyPreview(
        @PathVariable long projectCode, @PathVariable long id) { ... }

    @PostMapping("/{id}/deploy")
    public Result<DeployResult> deploy(@RequestAttribute(SESSION_USER) User loginUser,
        @PathVariable long projectCode, @PathVariable long id,
        @RequestBody SqlJobDeployRequest request) { ... }
}
```

**curl 端点测试**（API 服务启动后）：
```bash
# 创建
curl -s -X POST "http://localhost:12345/dolphinscheduler/projects/1/sql-jobs?name=test_job" \
  -H "sessionId: <your-session>"

# 保存
curl -s -X PUT "http://localhost:12345/dolphinscheduler/projects/1/sql-jobs/1" \
  -H "Content-Type: application/json" -H "sessionId: <your-session>" \
  -d '{"sql":"INSERT INTO t1 SELECT * FROM t2","datasourceId":1,"datasourceType":"HIVE"}'

# 解析
curl -s "http://localhost:12345/dolphinscheduler/projects/1/sql-jobs/1/parse" \
  -H "sessionId: <your-session>"
```

**验证（单元测试）**：
```bash
./mvnw verify -B -pl "dolphinscheduler-api" -Dspotless.skip=true -DskipUT=false \
  -Dtest=SqlJobControllerTest -Danalyze.skip=true
```

---

## Phase 1.3 — 前端

### 9. 路由注册

**文件**：`dolphinscheduler-ui/src/router/modules/projects.ts`

在 `children` 数组末尾追加两条路由（见 design.md §3）。

**验证**：`pnpm run dev`，访问 `/projects/1/sql-jobs` 不 404，页面渲染。

---

### 10. Service 层（axios 调用封装）

**文件**：`dolphinscheduler-ui/src/service/modules/sql-jobs/index.ts`

```typescript
import { axios } from '@/service/service'

export function createSqlJob(projectCode: number, name: string) {
  return axios({ url: `/projects/${projectCode}/sql-jobs`, method: 'post', params: { name } })
}
export function saveSqlJob(projectCode: number, id: number, data: SaveSqlRequest) {
  return axios({ url: `/projects/${projectCode}/sql-jobs/${id}`, method: 'put', data })
}
export function testRunSqlJob(projectCode: number, id: number, limitRows = 1000) {
  return axios({ url: `/projects/${projectCode}/sql-jobs/${id}/test`, method: 'post', params: { limitRows } })
}
export function parseSqlJob(projectCode: number, id: number) {
  return axios({ url: `/projects/${projectCode}/sql-jobs/${id}/parse`, method: 'get' })
}
export function getDependencyPreview(projectCode: number, id: number) {
  return axios({ url: `/projects/${projectCode}/sql-jobs/${id}/dependency-preview`, method: 'get' })
}
export function deploySqlJob(projectCode: number, id: number, data: DeployRequest) {
  return axios({ url: `/projects/${projectCode}/sql-jobs/${id}/deploy`, method: 'post', data })
}
```

---

### 11. SqlJobList 组件

**文件**：`dolphinscheduler-ui/src/views/projects/sql-jobs/index.tsx`

```tsx
import { defineComponent, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDataTable, NSpace } from 'naive-ui'
import { createSqlJob } from '@/service/modules/sql-jobs'

export default defineComponent({
  name: 'SqlJobList',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const projectCode = Number(route.params.projectCode)
    const data = ref([])

    onMounted(() => { /* fetch list */ })

    const handleCreate = async () => {
      const name = `sql_job_${Date.now()}`
      const res = await createSqlJob(projectCode, name)
      router.push({ name: 'sql-job-editor', params: { projectCode, id: res.id } })
    }

    return () => (
      <NSpace vertical>
        <NButton onClick={handleCreate}>新建 SQL 作业</NButton>
        <NDataTable columns={[...]} data={data.value} />
      </NSpace>
    )
  }
})
```

**验证**：页面显示列表，点"新建"跳转编辑器。

---

### 12. SqlEditor 页面

**文件**：`dolphinscheduler-ui/src/views/projects/sql-jobs/editor.tsx`

结构：
- 顶部工具栏（保存 / 测试运行 / 发布上线按钮）
- MonacoEditor（language: 'sql', height: '500px'）
- 底部可折叠的 TestRunPanel（展示 rowCount, schema, rows, error）
- DeployDrawer 组件（v-if 展开）

```tsx
import MonacoEditor from '@/components/monaco-editor'
// ...
<MonacoEditor
  value={sqlRef.value}
  onUpdate:value={(v: string) => { sqlRef.value = v }}
  options={{ language: 'sql' }}
  height="500px"
/>
```

**验证**：编辑器渲染，输入 SQL，点"保存"后 Network 面板显示 PUT 请求 200。

---

### 13. DeployDrawer 组件

**文件**：`dolphinscheduler-ui/src/views/projects/sql-jobs/deploy-drawer.tsx`

Props：`{ projectCode, jobId, visible, onClose }`

打开时（watch visible）调用 `getDependencyPreview`，展示：
- `NTag` 显示 targetTable
- `NDataTable` 展示 dependencies（table / status / matchedTaskCode）
- 旧依赖 diff：用 `NTag` 区分新增（绿）/ 移除（红）/ 不变（灰）
- `NRadioGroup` 合并策略（OVERWRITE / APPEND）
- `NInput` cron 表达式
- `NButton` 确认发布

确认时调 `deploySqlJob(projectCode, jobId, payload)`，成功后 `router.push` 到 workflow instance 列表。

**验证**：点"发布上线"按钮打开抽屉，依赖预览数据渲染，点确认后 Network 面板显示 POST /deploy 请求 200。

---

## 整体集成验证

```bash
# 后端单元测试（全 api 模块）
./mvnw verify -B -pl "dolphinscheduler-bom,dolphinscheduler-api" \
  -am -DskipTests=true -Dspotless.skip=true -DskipUT=true -Djacoco.skip=true -Danalyze.skip=true
./mvnw verify -B -pl "dolphinscheduler-api" \
  -Dmaven.test.skip=false -Dspotless.skip=true -DskipUT=false -Danalyze.skip=true \
  -Dtest="SqlParserServiceTest,DependencyResolverServiceTest,DagCompilerServiceTest,SqlJobControllerTest"

# 前端构建
cd dolphinscheduler-ui && pnpm run build:prod
```

---

## 关键决策记录

1. **JSQLParser 未引入**：Phase 1 全用正则。若 Phase 2 需要精确解析子查询，在 `dolphinscheduler-api/pom.xml` 添加 `com.github.jsqlparser:jsqlparser:4.7`（Apache License 2.0，无冲突）。

2. **deploy 需先 offline**：`WorkflowDefinitionServiceImpl.updateWorkflowDefinition`（line 637）强制 OFFLINE 校验，deploy 时若已有 workflowCode 且为 ONLINE，必须先调 `offlineWorkflowDefinition` 再 update 再 online，三步必须在同一事务或接受中间态。

3. **t_ds_sql_job 独立表**：不复用 t_ds_task_definition，避免污染已有任务管理流程；workflowCode 字段作为与 DS 核心层的桥接外键。
