# Design: SQL-first Job Editor

## 1. 整体架构（前后端分层图）

```
┌─────────────────────────────────────────────────────────────┐
│                     Browser (Vue 3 + TS)                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  SqlJobList  │  SqlEditor (MonacoEditor)             │    │
│  │  (文件列表)   │  DeployDrawer (依赖预览 + cron)        │    │
│  └──────────────┴──────────────────────────────────────┘    │
│    Route: /projects/:projectCode/sql-jobs                   │
└───────────────────┬─────────────────────────────────────────┘
                    │  HTTP/JSON  (axios + sessionId)
┌───────────────────▼─────────────────────────────────────────┐
│           dolphinscheduler-api  (Spring Boot / Jetty)       │
│  SqlJobController  @RequestMapping("projects/{pc}/sql-jobs")│
│  ├── POST   /                  createSqlJob                 │
│  ├── PUT    /{id}              saveSqlContent               │
│  ├── POST   /{id}/test         triggerTestRun               │
│  ├── GET    /{id}/parse        parseSql                     │
│  ├── GET    /{id}/dependency-preview  dependencyPreview     │
│  └── POST   /{id}/deploy       deployWorkflow               │
└──────────┬──────────────┬──────────────────────────────────-┘
           │              │
    ┌──────▼──────┐  ┌────▼──────────────────────────────────┐
    │SqlParserSvc │  │DependencyResolverService               │
    │(正则解析)    │  │(查 t_ds_task_definition.task_params)   │
    └──────┬──────┘  └────────────────┬──────────────────────┘
           │                          │
    ┌──────▼──────────────────────────▼──────────────────────┐
    │           DagCompilerService                            │
    │  构造 taskDefinitionJson + taskRelationJson + locations │
    └──────────────────────┬─────────────────────────────────┘
                           │
    ┌──────────────────────▼─────────────────────────────────┐
    │  WorkflowDefinitionService (现有)                       │
    │  createWorkflowDefinition / updateWorkflowDefinition    │
    │  onlineWorkflowDefinition                               │
    └────────────────────────────────────────────────────────┘
```

---

## 2. 后端新增组件

| 组件 | 职责 | 位置 | 依赖 |
|------|------|------|------|
| `SqlJobController` | 6 个 REST 端点，鉴权，DTO 转换 | `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/controller/SqlJobController.java` | `SqlJobService`, `@RequestAttribute SESSION_USER` |
| `SqlJobService` / `SqlJobServiceImpl` | 业务逻辑编排 | `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/SqlJobService.java` | `SqlParserService`, `DependencyResolverService`, `DagCompilerService`, `SqlJobMapper` |
| `SqlJobEntity` | 元数据持久化（id, projectCode, name, sql, workflowCode, createTime, updateTime） | `dolphinscheduler-dao/src/main/java/org/apache/dolphinscheduler/dao/entity/SqlJob.java` | MyBatis-Plus `@TableName("t_ds_sql_job")` |
| `SqlJobMapper` | CRUD mapper | `dolphinscheduler-dao/src/main/java/org/apache/dolphinscheduler/dao/mapper/SqlJobMapper.java` | `SqlJob` entity |
| `SqlParserService` | 静态 SQL 解析（目标表、来源表、CTE 展开） | `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/SqlParserService.java` | 正则（无外部依赖，JSQLParser 未在 pom.xml 中） |
| `DependencyResolverService` | source tables → 已有 DS 任务映射 | `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/DependencyResolverService.java` | `TaskDefinitionMapper` (查 `t_ds_task_definition`) |
| `DagCompilerService` | 从解析结果生成 WorkflowDefinition 入参，调 WorkflowDefinitionService | `dolphinscheduler-api/src/main/java/org/apache/dolphinscheduler/api/service/DagCompilerService.java` | `WorkflowDefinitionService`, `SqlParameters` |

### SqlParserService

**输入**：原始 SQL 字符串

**解析目标**：
- `INSERT INTO <table>` / `INSERT OVERWRITE <table>` → targetTable
- `FROM <table>` / `JOIN <table>` → sourceTables（含 CTE 别名展开：先收集 WITH 子句定义的别名，再从 FROM/JOIN 中排除）
- `${varName}` 动态变量 → unresolvedRefs

**输出 DTO**：
```java
public class SqlParseResult {
    String targetTable;           // null 若无 INSERT
    List<String> sourceTables;    // 去重，排除 CTE 别名和 targetTable
    List<String> unresolvedRefs;  // 含变量占位符的表名
}
```

**实现方式**：纯正则。JSQLParser **未在任何 pom.xml 中**（取证确认），引入成本高；ANTLR 同理未引入。Phase 1 用正则覆盖主流模式，复杂嵌套 subquery 标记 unresolved。

**需覆盖的正则模式**：
1. `INSERT\s+(INTO|OVERWRITE)\s+(TABLE\s+)?(\S+)`
2. WITH CTE 块：`WITH\s+(\w+)\s+AS\s*\(`  
3. FROM 子句：`FROM\s+(\w+[\w.]*)`
4. JOIN 子句：`(LEFT|RIGHT|INNER|FULL|CROSS)?\s*JOIN\s+(\w+[\w.]*)`
5. 动态变量：`\$\{[^}]+\}` → unresolved

### DependencyResolverService

**输入**：`List<String> sourceTables`, `long projectCode`

**查询逻辑**：
```sql
SELECT code, task_params
FROM t_ds_task_definition
WHERE project_code = #{projectCode}
  AND task_type = 'SQL'
  AND flag = 1
```
对每条记录：反序列化 `task_params`（JSON）为 `SqlParameters`，读取 `sql` 字段，用 `SqlParserService` 解析其 `targetTable`，构建 `Map<targetTable, taskCode>`。

**输出 DTO**：
```java
public class DependencyMatchResult {
    String table;
    Long matchedTaskCode;       // null if unresolved
    Long matchedWorkflowCode;   // null if unresolved
    String status;              // "MATCHED" | "UNRESOLVED"
}
```

### DagCompilerService

**输入**：`SqlJobDeployRequest`（含 sql、datasourceId、datasourceType、mergedDependencies、cron、mergeStrategy）

**构造 taskDefinitionJson**（单个 SQL task，映射到 `SqlParameters`）：
```java
SqlParameters params = new SqlParameters();
params.setType(datasourceType);       // e.g. "HIVE"
params.setDatasource(datasourceId);
params.setSql(sql);
params.setSqlType(1);                 // NON_QUERY
params.setLimit(1000);                // 测试运行限制复用
```

**构造 taskRelationJson**：单节点无边（`"preTaskCode": 0, "preTaskVersion": 0`），依赖通过 DS `DependentTask` 类型节点附加（Phase 1 简化：仅单 SQL task，依赖信息存到 description/globalParams 供 Phase 2 编排）。

**调用链**：
```
// 新建
WorkflowDefinitionService.createWorkflowDefinition(
    loginUser, projectCode, name, description,
    globalParams, locations, timeout,
    taskRelationJson, taskDefinitionJson, null, PARALLEL)

// 更新（先 offline 再 update 再 online）
WorkflowDefinitionService.updateWorkflowDefinition(...)
WorkflowDefinitionService.onlineWorkflowDefinition(loginUser, projectCode, workflowCode)
```

**关键约束**：`updateWorkflowDefinition` 要求 workflow 处于 OFFLINE 状态（源码 line 637 验证），deploy 流程需先 offline → update → online。

---

## 3. 前端新增组件

### 路由入口

复用 `@/layouts/content`（与 workflow-definition 同一 layout），在 `dolphinscheduler-ui/src/router/modules/projects.ts` 追加：

```typescript
{
  path: '/projects/:projectCode/sql-jobs',
  name: 'sql-job-list',
  component: components['projects-sql-jobs'],
  meta: { title: 'SQL 作业', activeMenu: 'projects', showSide: true, auth: [] }
},
{
  path: '/projects/:projectCode/sql-jobs/:id',
  name: 'sql-job-editor',
  component: components['projects-sql-jobs-editor'],
  meta: { title: 'SQL 编辑器', activeMenu: 'projects', activeSide: '/projects/:projectCode/sql-jobs', showSide: true, auth: [] }
}
```

视图文件放在：`dolphinscheduler-ui/src/views/projects/sql-jobs/`（`index.tsx` → list，`editor.tsx` → editor page）。

TSX 文件命名需与 `utils.mapping(modules)` 的 key 对应：`projects-sql-jobs` → `src/views/projects/sql-jobs/index.tsx`，`projects-sql-jobs-editor` → `src/views/projects/sql-jobs/editor.tsx`。

### SqlJobList 组件

文件：`src/views/projects/sql-jobs/index.tsx`

- 调用 `GET /projects/{projectCode}/sql-jobs` 获取列表
- NDataTable（Naive UI）展示 name, updateTime, workflowCode（已发布/未发布）
- 顶部"新建"按钮 → 调 `POST /projects/{projectCode}/sql-jobs` → 跳转编辑器

### SqlEditor 组件

文件：`src/views/projects/sql-jobs/editor.tsx`

复用 `@/components/monaco-editor`（import 路径已确认）：

```typescript
import MonacoEditor from '@/components/monaco-editor'
// 使用：
<MonacoEditor
  value={sqlRef.value}
  onUpdate:value={(v) => (sqlRef.value = v)}
  options={{ language: 'sql', readOnly: false }}
  height="500px"
/>
```

顶部操作栏：保存、测试运行（展开结果面板）、发布上线（打开 DeployDrawer）。

### DeployDrawer 组件

文件：`src/views/projects/sql-jobs/deploy-drawer.tsx`

打开时调用 `GET /projects/{pc}/sql-jobs/{id}/dependency-preview`，展示：
1. 产出表（targetTable）
2. 输入表列表（sourceTables）+ 每行匹配状态（MATCHED/UNRESOLVED）
3. 旧依赖 vs 新依赖 diff（旧依赖从 dependencyPreview 响应中读取）
4. 依赖合并策略 Radio（OVERWRITE / APPEND）
5. Cron 表达式输入框（NInput）
6. 确认发布按钮 → 调 `POST /projects/{pc}/sql-jobs/{id}/deploy`

---

## 4. 接口契约（6 个端点）

所有端点挂在 `projects/{projectCode}/sql-jobs` 下。

### POST `/projects/{projectCode}/sql-jobs`
```json
// Request
{ "name": "dwd_order_daily" }

// Response 201
{
  "id": 42,
  "projectCode": 1234567890,
  "name": "dwd_order_daily",
  "sql": "",
  "workflowCode": null,
  "createTime": "2026-05-31T10:00:00Z"
}
```

### PUT `/projects/{projectCode}/sql-jobs/{id}`
```json
// Request
{
  "sql": "INSERT OVERWRITE dwd_order SELECT ... FROM ods_order",
  "datasourceId": 5,
  "datasourceType": "HIVE"
}

// Response 200
{ "id": 42, "updateTime": "2026-05-31T10:01:00Z" }
```

### POST `/projects/{projectCode}/sql-jobs/{id}/test`
```json
// Request
{ "limitRows": 100 }

// Response 200
{
  "status": "SUCCESS",
  "rowCount": 87,
  "schema": ["col1", "col2"],
  "rows": [["v1", "v2"]],
  "durationMs": 3210,
  "error": null
}
```

### GET `/projects/{projectCode}/sql-jobs/{id}/parse`
```json
// Response 200
{
  "targetTable": "dwd_order",
  "sourceTables": ["ods_order", "dim_product"],
  "unresolvedRefs": []
}
```

### GET `/projects/{projectCode}/sql-jobs/{id}/dependency-preview`
```json
// Response 200
{
  "targetTable": "dwd_order",
  "dependencies": [
    {
      "table": "ods_order",
      "matchedTaskCode": 98765432,
      "matchedWorkflowCode": 11111111,
      "status": "MATCHED"
    },
    {
      "table": "dim_product",
      "matchedTaskCode": null,
      "matchedWorkflowCode": null,
      "status": "UNRESOLVED"
    }
  ],
  "oldDependencies": [
    { "table": "ods_order_v1", "matchedTaskCode": 88888888, "status": "MATCHED" }
  ],
  "hasUnresolved": true,
  "hasMultipleTargets": false
}
```

### POST `/projects/{projectCode}/sql-jobs/{id}/deploy`
```json
// Request
{
  "mergeStrategy": "OVERWRITE",
  "confirmedDependencies": [
    { "table": "ods_order", "taskCode": 98765432 },
    { "table": "dim_product", "taskCode": null }
  ],
  "cron": "0 30 1 * * ? *"
}

// Response 200
{
  "workflowCode": 22222222,
  "workflowName": "sql_job_dwd_order_daily",
  "releaseState": "ONLINE",
  "instanceUrl": "/projects/1234567890/workflow/instances"
}

// Response 4xx（循环依赖）
{
  "code": 10100,
  "msg": "Circular dependency detected: dwd_order -> ods_order -> dwd_order",
  "data": null
}
```

---

## 5. 数据流

### 保存流程
```
前端 PUT /sql-jobs/{id}
  → SqlJobController.saveSqlContent(id, request)
  → SqlJobService.save(id, sql, datasourceId, type)
    → SqlJobMapper.updateById(sqlJob)   // 更新 t_ds_sql_job
  ← 200 { id, updateTime }
```

### 测试运行流程
```
前端 POST /sql-jobs/{id}/test
  → SqlJobController.triggerTestRun(id, limitRows)
  → SqlJobService.testRun(id, limitRows)
    → 读取 SqlJob.sql + datasourceId
    → 构造 SqlParameters(limit=limitRows, sqlType=0 QUERY)
    → 通过 DataSourceClientProvider 直接执行（复用 SqlTask 内逻辑）
    → 截断结果到 limitRows 行
  ← 200 { status, rowCount, schema, rows, durationMs, error }
```

### 发布上线流程
```
前端 POST /sql-jobs/{id}/deploy
  → SqlJobController.deployWorkflow(id, request)
  → SqlJobService.deploy(id, request)
    → SqlJobMapper.findById(id)
    → SqlParserService.parse(sql) → SqlParseResult
    → 验证 targetTable 唯一（多产出表 → 抛 400）
    → DependencyResolverService.detectCycles(confirmedDeps) → 循环依赖 → 抛 400
    → DagCompilerService.compile(sql, confirmedDeps, cron)
      → 构造 taskDefinitionJson（SqlParameters JSON）
      → 构造 taskRelationJson（单节点）
      → 构造 locations（单节点坐标）
    → if sqlJob.workflowCode == null:
        WorkflowDefinitionService.createWorkflowDefinition(...)
      else:
        WorkflowDefinitionService.updateWorkflowDefinition(...)
    → WorkflowDefinitionService.onlineWorkflowDefinition(...)
    → SqlJobMapper.updateById(workflowCode, updateTime)
  ← 200 { workflowCode, releaseState, instanceUrl }
```

---

## 6. 约束与边界

### 不破坏现有 DAG 页面
- `SqlJobController` 挂在独立 path `sql-jobs`，与 `workflow-definition` 完全隔离
- 不修改 `WorkflowDefinitionController`、`WorkflowDefinitionService` 接口
- 调用 WorkflowDefinitionService 时使用现有 `createWorkflowDefinition` / `updateWorkflowDefinition` / `onlineWorkflowDefinition` 签名，不新增方法

### 循环依赖检测
- 位置：`DependencyResolverService.detectCycles(List<DependencyMatchResult>)`
- 算法：以 `confirmedDependencies` 构建有向图，DFS 检测环，找到则返回环路径字符串
- 触发时机：`deploy` 接口调用，校验失败抛 `ServiceException(4xx)`

### 测试运行行数限制
- 实现：`SqlParameters.setLimit(limitRows)`，`SqlParameters.setSqlType(0)` (QUERY 模式)
- 现有 `SqlTask` 执行时会读取 `limit` 字段做结果截断（`SqlParameters.limit` 字段已有，line 98）
- 测试运行走独立临时执行路径，不提交 DS 实例，直连数据源

### 多产出表阻断
- `SqlParserService` 若检测到多个 `INSERT` 语句对应不同 targetTable，`SqlParseResult.hasMultipleTargets = true`
- `deploy` 接口：`if hasMultipleTargets → throw 400 "Multiple target tables are not supported in Phase 1"`

### JSQLParser 缺失
- **关键决策**：`dolphinscheduler-task-sql/pom.xml` 和根 `pom.xml` 均无 JSQLParser 或 ANTLR 依赖
- Phase 1 采用纯正则实现 SqlParserService，复杂嵌套 subquery 标记 unresolved，足够覆盖 INSERT/FROM/JOIN/CTE 主流场景
- Phase 2 可评估引入 JSQLParser（`com.github.jsqlparser:jsqlparser:4.x`）到 `dolphinscheduler-api/pom.xml`

### updateWorkflowDefinition 强制 OFFLINE 限制（取证确认）
- `WorkflowDefinitionServiceImpl.java:637`：更新已有 workflow 前必须先下线
- deploy 流程对已有 workflow：`offlineWorkflowDefinition` → `updateWorkflowDefinition` → `onlineWorkflowDefinition`（三步串行）
- 存在短暂下线窗口（秒级），实现时在 deploy 注释中说明，Phase 2 考虑原子替换方案

### 前端路由文件命名约定（取证确认）
- `routes.ts` 通过 `import.meta.glob('/src/views/**/**.tsx')` 自动映射，key 由路径生成
- 视图文件必须放在 `src/views/projects/sql-jobs/` 下，命名为 `index.tsx`（列表）和 `editor.tsx`（编辑器）
- 命名不符会导致 `components['projects-sql-jobs']` 为 undefined → 路由白屏，实现时严格遵守
