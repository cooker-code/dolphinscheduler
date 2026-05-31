# SQL-first 作业开发界面：用户直接写 SQL，自动生成 DAG 并上线

## 背景

当前 DolphinScheduler UI 以 DAG 画布为核心入口，用户必须手工拖拽节点、连线、配置依赖，对数据开发人员（SQL 为主）来说体验蹩脚。目标是提供一个 SQL-first 的作业工作台，让用户像写普通 SQL 文件一样开发、测试、上线，后台仍然生成标准 DS DAG，不丢失调度内核能力。

## Goal

用户在 UI 中直接编写 SQL，保存并测试后，点击"发布上线"，系统自动解析 SQL 的输出表/依赖表，生成 DolphinScheduler workflow 定义（taskDefinitionJson + taskRelationJson + locations），并将 workflow 置为 ONLINE 状态。用户全程无需手画 DAG。

## 用户故事

1. **开发者** 打开"SQL 作业"工作台，新建一个 SQL 文件，在编辑器中写 INSERT OVERWRITE / INSERT INTO SQL，点"保存"。
2. **开发者** 点"测试运行"，看到 SQL 在测试数据源上执行结果（行数、耗时、错误信息）。
3. **开发者** 点"发布上线"，弹出部署抽屉，看到：
   - 系统解析出的产出表、依赖表列表
   - 依赖表映射到的已有 DS 任务（如无法匹配，标注"未知来源"）
   - 当前 workflow 的旧依赖 vs 新解析依赖 diff
   - 选择"覆盖依赖"或"追加依赖"
   - 调度周期配置（cron）
4. **开发者** 确认后，系统创建/更新 DS workflow definition 并上线，跳转到实例监控页面。
5. **开发者** 可在工作台查看历史发布版本，查看对应 Git commit 和解析结果。

## Requirements

### 前端

- 新增"SQL 作业"菜单入口（不替换原 DAG 页面，并行存在）
- SQL 编辑器：复用现有 MonacoEditor，支持 HQL/SQL 语法高亮、格式化
- 文件树/列表：展示当前用户的 SQL 文件（对应 Git 个人分支上的文件）
- 测试运行面板：展示执行结果（行数、schema、错误）
- 部署抽屉：
  - 显示解析出的产出表、输入表
  - 依赖匹配列表（输入表 → DS 任务/工作流，含匹配状态：已匹配/未知来源）
  - 旧依赖 vs 新依赖 diff 视图
  - 选择依赖合并策略（覆盖 / 追加）
  - cron 表达式配置
  - 发布确认按钮
- 原 DAG 可作为"高级视图"，可在部署确认后提供"查看生成的 DAG"入口

### 后端

- 新增 `SqlJobController`，提供接口：
  - `POST /sql-jobs` 创建 SQL 作业元数据
  - `PUT /sql-jobs/{id}` 保存 SQL 内容（写入 Git 个人分支）
  - `POST /sql-jobs/{id}/test` 触发测试执行（沙箱 / 限行数）
  - `GET /sql-jobs/{id}/parse` 解析 SQL，返回产出表、输入表、参数
  - `GET /sql-jobs/{id}/dependency-preview` 返回依赖匹配结果 + 旧依赖 diff
  - `POST /sql-jobs/{id}/deploy` 执行发布：编译 DAG → 创建/更新 workflow → ONLINE
- 新增 `SqlParserService`：静态解析 SQL，识别：
  - INSERT OVERWRITE / INSERT INTO 目标表
  - FROM / JOIN 来源表（含 CTE 展开）
  - 分区字段、动态参数（无法解析时标记 unresolved）
- 新增 `DependencyResolverService`：
  - 通过"产出表 → DS 任务"索引，将输入表映射到已有任务
  - 索引由已有 workflow 的 SQL task 参数维护，周期刷新
- 新增 `DagCompilerService`：
  - 输入：SQL 内容 + 依赖列表 + 调度配置
  - 输出：DS 标准 `CreateWorkflowRequest`（taskDefinitionJson、taskRelationJson、locations）
  - 调用现有 `WorkflowDefinitionService.createOrUpdate` + `release`

### 约束

- 不破坏现有 DAG 页面和 API，新功能以并行模式上线
- 一个 SQL 文件对应一个 DS SQL task（单 task workflow）；多产出表场景 Phase 1 阻断发布，给出提示
- 动态拼表名（变量 `${tableName}`）解析结果标记 unresolved，要求用户在部署抽屉手动确认依赖
- 循环依赖在 deploy 接口侧检测，返回 4xx + 错误说明
- 测试运行限制返回行数（默认 ≤ 1000 行）

## Acceptance Criteria

- [ ] 用户可在"SQL 作业"入口新建、编辑、保存 SQL 文件，无需接触 DAG 页面
- [ ] 保存后可触发测试运行，页面显示执行结果或错误信息
- [ ] 点击"发布上线"弹出部署抽屉，展示产出表、输入表、依赖匹配列表
- [ ] 部署抽屉显示旧依赖 vs 新依赖 diff，支持覆盖/追加两种合并策略
- [ ] 确认发布后，DS 中自动创建/更新对应 workflow definition，状态为 ONLINE
- [ ] 已上线 workflow 可在原 DS 工作流管理页面查看（实例监控、补数、告警均可用）
- [ ] 单 SQL 写多产出表时，系统阻断发布并给出明确提示
- [ ] 循环依赖场景系统检测并拒绝发布，给出依赖路径说明
- [ ] 动态变量无法解析时，部署抽屉提示用户手动确认依赖

## 分阶段边界

**Phase 1（本任务）**：单 SQL → 单 task workflow，依赖解析 + 用户确认，SQL 保存到 Git 个人分支（依赖 Task: sql-git-version-management），发布上线。  
**Phase 2**（后续）：自动依赖索引、多文件 DAG 编排、Agent 辅助生成。  
**Phase 3**（后续）：Agent 生成 SQL、发布说明、回滚建议、血缘校验。

## 边缘案例（需在实现时覆盖测试）

- SQL 解析：单表、JOIN、CTE、分区、注释、大小写混合、多 INSERT
- DAG 编译：无依赖、单依赖、多依赖、依赖追加、依赖覆盖
- 发布流程：创建新 workflow、更新已有 workflow、上线失败回滚
- UI 流程：保存、测试、部署弹窗、依赖 diff、只读线上版本查看

## Notes

- 复杂任务，需补充 `design.md`（技术分层、接口契约、数据流）和 `implement.md`（执行清单）后才能 `task.py start`。
- 前端复用 MonacoEditor 和现有 SQL task 表单组件，减少重复实现。
- 后台 DagCompilerService 复用 `WorkflowDefinitionService` 现有 create/update/release 逻辑，不另起炉灶。
