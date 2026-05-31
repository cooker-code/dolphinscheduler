# SQL Studio 规范 — 前端

## 目标边界

SQL Studio 是 DolphinScheduler UI 的增量工作台，不是新的调度内核。

- 用户可以用 SQL-first 方式创建、测试、解析、发布数据作业
- 发布结果必须落到 DolphinScheduler 原生 workflow / task / timing / alert / environment / tenant 等模型
- 原项目管理与 DAG 工作流能力必须继续可用，SQL Studio 不得破坏既有页面和 API 行为
- Agent 只能辅助生成、解释、修复、建议，不得绕过人工确认直接发布

## 技术选型

- 主页面：Vue 3 + TypeScript + Naive UI
- 状态：Pinia，按 feature 建 store，例如 `store/sql-workbench`
- 路由：Vue Router，新增路由放在 `src/router/modules/`
- 请求：只通过 `src/service/service.ts` 的 axios 实例与 `src/service/modules/*`
- 编辑器：复用 `src/components/monaco-editor`
- 表格、抽屉、表单、弹窗：优先使用 Naive UI
- 统计图：ECharts
- 原工作流 DAG：AntV X6
- SQL 血缘/依赖图：FlowScope React island / 微前端 / Web Component，输入输出走 JSON contract

## 推荐目录

```text
dolphinscheduler-ui/src/
├── views/sql-studio/              # SQL Studio 页面
│   ├── index.vue
│   ├── components/
│   │   ├── SqlJobTree.vue
│   │   ├── SqlEditorPane.vue
│   │   ├── ResultPanel.vue
│   │   ├── PublishDrawer.vue
│   │   └── LineageGraphIsland.vue
│   └── types.ts
├── store/sql-studio/              # SQL Studio 页面状态
├── service/modules/*              # 优先复用现有 DolphinScheduler 资源接口
└── router/modules/sql-studio.ts   # SQL Studio 路由
```

实际目录可跟随现有项目命名，但必须保持 feature 内聚：页面组件、局部类型、局部工具函数不要散落到全局目录。

`service/modules/sql-studio.ts` 不是默认必需项。只有当 SQL Studio 需要组合多个既有接口、并且这个组合逻辑无法自然归属到现有资源模块时，才允许新增 feature-level adapter；adapter 必须委托现有 `service/modules/*` wrapper，不得绕过既有接口重新拼同一批 URL。

## 路由约定

建议新增路由：

- `/sql-studio`
- `/sql-studio/:projectCode`
- `/sql-studio/:projectCode/:jobSlug`
- `/sql-studio/instances`
- `/sql-studio/timings`

路由守卫、权限、菜单展示必须复用现有布局与用户权限体系。不得在 SQL Studio 内部另写一套路由鉴权。

## API Contract

SQL Studio 是跨前后端功能，默认前提是 **现有 DolphinScheduler 后端接口已经可以覆盖主要能力**。前端实现前必须先确认 Swagger / OpenAPI 契约，并优先复用现有 `service/modules/*`。

本地契约入口：

- Swagger UI: `http://localhost:12345/dolphinscheduler/swagger-ui/index.html`
- Definition: `v1`
- OpenAPI JSON: `http://localhost:12345/dolphinscheduler/v3/api-docs/v1`

现有接口优先级：

- 工作流定义：复用 `workflow-definition` 接口创建、更新、查询、上线/下线、版本管理、任务列表、变量查看
- 任务定义：复用 `task-definition` 接口生成 task code、更新 task、上线/下线、版本管理
- 调度：复用 `schedules` 接口创建、更新、预览、上线、下线、列表查询
- 执行：复用 `executors` 接口启动工作流实例、执行任务、执行工作流
- 数据源：复用 `data-source` 接口查询数据源、连接测试、数据库列表、表列表、字段列表
- 租户：复用 `tenants` 接口查询租户列表和校验租户
- Worker Group：复用 `worker-groups` 与 project worker-group 接口查询全局/项目可用 worker group
- 环境：复用 `environment` 接口查询可用 environment
- 告警组：复用 `alert-group` 接口查询可用 warning group
- 血缘：复用 `lineages` 接口读取已有工作流/任务依赖；SQL 级字段血缘若现有接口不足，必须先标记为能力缺口

约束：

- 不得直接从静态原型推断 payload，必须以 Swagger / Controller / DTO 为准
- 不得因为页面叫 SQL Studio 就默认新增 `/sql-studio/*` 后端接口；先证明现有接口不能满足
- 前端 service wrapper 优先放在现有资源模块，例如 `workflow-definition`、`task-definition`、`schedules`、`executors`、`data-source`
- 只有新增后端能力时，才新增对应 service module；新增前必须在 spec / PRD 中说明现有接口缺口和替代方案
- request / response 类型必须集中定义，多个组件不得各自复制字段类型
- 发布链路应由现有接口组合：生成/保存 workflow definition、task definition、schedule，再 release/online；不得在前端创造第二套发布状态
- 运行/测试能力优先复用 `executors`，不得新增绕过 DS 权限和实例模型的执行入口
- 历史版本优先复用 workflow/task definition versions，不得单独维护与 DS 版本脱节的前端历史
- 发布结果必须能追溯生成或更新的 workflowCode、taskCode、schedule/timing id、version、operator、publishTime
- `lineage` 返回给 FlowScope 前必须在 Vue 层规范化为稳定 JSON contract，不能让 React island 直接理解 DolphinScheduler 后端原始 payload
- Swagger UI 在终端不可访问时，不代表接口不存在；优先检查 in-app browser 中的 Swagger 页面，再读 Controller

允许新增后端 API 的情况：

- 现有接口无法保存 SQL 源码草稿或 SQL 作业元数据，并且不能安全挂接到 workflow/task definition 版本模型
- 现有接口无法提供 SQL AST / 表级血缘 / 字段级血缘解析结果
- 现有接口无法表达 Agent 建议、AI diff、审计记录等辅助信息
- 现有接口组合会破坏 DS 权限、版本、审计、发布语义

新增 API 必须先补 Swagger 注解、Controller/DTO、前端 TypeScript 类型和错误态设计。

## 发布字段映射

发布抽屉必须显式覆盖 DolphinScheduler 运行所需字段。前端类型、表单字段、API payload、后端实体必须一一对应。

必填或需明确默认值的字段：

- projectCode
- workflowCode / workflowName
- taskCode / taskName / taskType
- datasource
- sql / rawScript
- tenantCode
- workerGroup
- environmentCode
- taskPriority / workflowInstancePriority
- executionType
- failureStrategy
- warningGroupId
- warningType
- timeoutFlag / timeout
- crontab / timezone
- globalParams
- dependency mapping
- publish message / owner / version

约束：

- 默认值必须来自后端接口或现有 DS 常量，不得在组件内随意硬编码
- 枚举字段必须用共享类型表达，禁止散落字符串判断
- 表单保存、测试运行、发布上线应使用同一份规范化 payload，避免三个入口各自拼字段
- SQL 作业发布后必须能追溯生成的 workflowCode、taskCode、timing id 和发布版本

## SQL 编辑与测试

- SQL 编辑区必须使用 Monaco Editor
- 测试运行不得修改正式 workflow/task
- 测试结果需要区分成功、失败、超时、取消、权限不足、数据源不可用
- 解析结果需要展示输入表、输出表、字段、全局参数、调度依赖、未知依赖
- 解析失败时必须保留可编辑 SQL 和错误详情，不能清空用户内容
- 历史版本 diff 必须基于版本/commit id，不得只按当前文本临时比较

## 依赖图与 FlowScope

FlowScope 只负责图形体验，不负责 DolphinScheduler 业务权限和发布逻辑。

Vue 到 FlowScope 的输入 contract 至少包含：

- nodes：表、字段、任务、数据源、未知依赖
- edges：读、写、字段映射、调度依赖
- layout：方向、层级、过滤条件
- selection：当前选中节点/边
- issues：未知表、歧义字段、缺失 datasource、循环依赖等诊断

FlowScope 到 Vue 的事件 contract 至少包含：

- node-click
- edge-click
- selection-change
- reveal-source
- export-request

所有 contract 需要定义 TypeScript 类型，并放在 SQL Studio feature 的稳定类型文件中。多个消费者读取同一字段时，必须通过共享 normalizer / type guard。

## Agent 边界

Agent 功能必须满足：

- 可关闭：没有 Agent 服务时，SQL Studio 仍可编辑、测试、发布
- 可审计：展示 Agent 输入、输出、模型/服务标识、生成时间、操作者
- 可回滚：Agent 改写 SQL 前必须能查看 diff
- 可确认：发布上线前必须由用户确认最终 SQL 和调度配置
- 可降级：Agent 超时、失败、限流时只影响辅助能力，不影响核心表单

禁止：

- Agent 直接调用发布上线接口
- Agent 静默修改 datasource、tenant、workerGroup、environment、warningGroup 等运行配置
- 前端只保存 Agent 生成后的结果而丢失原始 SQL

## 状态管理

- Pinia store 保存跨组件共享状态：当前项目、当前作业、打开的脚本、测试任务、解析结果、发布草稿
- 组件本地状态保存纯 UI 细节：展开/折叠、tab、hover、局部输入
- 服务端返回的原始 payload 必须先规范化，再进入 store
- store 中不得保存不可序列化的 Monaco editor、React root、DOM node、X6 graph 实例

## 质量门禁

每次实现或修改 SQL Studio 功能，至少验证：

- `pnpm run lint`
- `pnpm run build:prod`
- 新增文案已同步 `en_US` / `zh_CN`
- 测试运行、解析失败、发布失败、权限不足、空状态都有可见反馈
- SQL 内容刷新、路由切换、主题切换不会丢失未保存编辑
- FlowScope island 在大图、暗/亮主题、节点点击、布局切换下可用

## 设计约束

- 正式页面不得照搬静态原型的 inline style / inline event handler
- 不使用静态 HTML 模拟表单、编辑器、表格、抽屉
- 页面应继承 DolphinScheduler 现有导航、主题、间距、字号和组件密度
- 深色主题可支持，但默认风格必须与现有 DS UI 一致
