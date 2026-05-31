# 前端开发规范 — DolphinScheduler UI

> 约束来源：`dolphinscheduler-ui/CLAUDE.md`。

---

## 规范索引

| 规范文件 | 说明 |
|---------|------|
| [目录结构](./directory-structure.md) | src 目录组织、各目录职责 |
| [组件规范](./component-guidelines.md) | Vue 3 组件约定、Naive UI 使用规则 |
| [Hook 规范](./hook-guidelines.md) | 数据请求、自定义 composable |
| [请求拦截器规范](./request-interceptor.md) | axios 实例、headers 写法、401 兜底链路 |
| [SQL Studio 规范](./sql-studio-guidelines.md) | SQL-first 工作台、发布映射、Agent/FlowScope 边界 |
| [代码质量](./quality-guidelines.md) | 禁止模式、构建命令 |

---

## 技术栈

- **Vue 3**（Composition API）+ **TypeScript**
- **Vite**（开发服务器 + 生产构建，含 gzip 预压缩）
- **Pinia**（状态管理）
- **Vue Router**（5 个顶层路由组）
- **axios**（单一封装，入口 `src/service/service.ts`）
- **Naive UI** v2.33.x（UI 组件库）
- **AntV X6**（DAG 编辑器）
- **ECharts + D3**（图表）
- **vue-i18n**（国际化，支持 `en_US` / `zh_CN`）

## 正式版技术选型边界

- **主应用保持 Vue 3 + Naive UI**：DolphinScheduler UI 已有路由、权限、主题、i18n、请求拦截、Pinia store 都建立在 Vue 技术栈上。新增数据系统页面不得把主应用整体迁移到 React。
- **SQL 编辑使用 Monaco Editor**：`dolphinscheduler-ui` 已内置 `src/components/monaco-editor` 与 `monaco-editor` 依赖。SQL Studio、SQL diff、只读脚本预览优先复用该组件，而不是用静态 `<pre>` / `<div>` 模拟编辑器。
- **原 DAG 编辑继续使用 AntV X6**：`views/projects/workflow/components/dag/` 是现有工作流 DAG 编辑器。SQL Studio 不得重写或替换原 DAG 内核；需要生成工作流时，应发布为 DolphinScheduler 原生 workflow/task/timing 数据。
- **统计图表使用 ECharts**：首页、监控、趋势、聚合统计类图表继续使用 ECharts。
- **SQL 血缘/依赖图可复用 FlowScope React island**：FlowScope 的可视化包基于 React 19、`@xyflow/react`、`dagre`、`elkjs`。正式接入时应作为独立 React island / 微前端 / Web Component 嵌入 Vue 页面，输入输出使用稳定 JSON contract，不得把 React 状态模型泄漏到 Vue 主应用。
- **Agent 是辅助能力，不是发布主链路**：AI 生成 SQL、解释 SQL、修复 lint、依赖建议必须可审计、可回滚、可人工确认。发布上线不得依赖 Agent 成功执行。

## 工具链（版本不可随意升级）

- **Node 16.x**（Node 18/20 会因 OpenSSL 问题导致构建失败）
- **pnpm 7.x**

---

## 开发前检查清单

- [ ] Node 版本为 16.x（`node -v` 确认）
- [ ] 已在 `dolphinscheduler-ui/` 目录下执行 `pnpm install`
- [ ] 修改 DAG 编辑器（`views/projects/workflow/components/dag/`）前已阅读该目录 README

---

## 质量检查清单

- [ ] ESLint 通过：`pnpm run lint`
- [ ] Prettier 格式通过：`pnpm run prettier`
- [ ] TypeScript 类型检查通过：`pnpm run build:prod`（含 `vue-tsc`）
- [ ] i18n：新增文案已同时添加到 `en_US` 和 `zh_CN` 两份 locale 文件
- [ ] 新增 API 请求已在 `src/service/` 对应文件中添加类型声明
- [ ] SQL Studio / 数据系统页面已阅读 [SQL Studio 规范](./sql-studio-guidelines.md)
- [ ] 新增图形展示已明确使用边界：X6（工作流 DAG）、ECharts（统计图）、FlowScope React island（SQL 血缘/依赖图）
