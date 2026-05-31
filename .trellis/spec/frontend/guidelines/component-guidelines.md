# 组件规范 — 前端

## 基本约定

- 使用 **Vue 3 Composition API**（`<script setup>`），禁止新增 Options API 组件
- 组件文件使用 PascalCase 命名，例如 `WorkflowList.vue`
- UI 组件库使用 **Naive UI v2.33.x**，禁止引入其他 UI 框架
- 页面级组件放在 `views/<feature>/`，跨页面可复用组件放在 `components/`
- 组件 props / emits 必须显式声明类型；跨层传递的复杂对象应在 `service/` 或 feature 内定义共享 TypeScript 类型，避免多个组件各自 `as any`
- 面向用户的 loading、empty、error、disabled、readonly 状态必须在组件层显式呈现，不得只依赖接口报错 toast

## DAG 编辑器（最高风险）

`views/projects/workflow/components/dag/` 是项目中最复杂的视图，基于 **AntV X6**。

**修改前必须**：
1. 阅读该目录下已有代码，理解 X6 的图模型
2. 使用 `gitnexus_impact` 分析改动影响范围
3. 在本地运行前端验证 DAG 功能正常

**边界**：
- X6 只负责原生 DolphinScheduler 工作流 DAG 编辑与展示
- SQL Studio 生成 DAG 时，应通过后端/API 发布为原生 workflow/task/timing 数据，不得在前端复制一套 DAG 调度模型
- SQL 血缘、字段级 lineage、依赖影响图优先走 FlowScope React island，不要强行复用 X6 重做血缘图

## SQL / Monaco 编辑器

- SQL、Shell、JSON、配置片段等可编辑代码区域优先复用 `src/components/monaco-editor`
- 禁止在正式页面中使用静态 `<pre>`、`contenteditable`、自绘行号模拟代码编辑器
- 编辑器必须支持受控值更新、只读模式、主题切换、高度自适应和表单禁用态
- SQL diff 可以使用 Monaco 只读模型或专用 diff 组件；不得只用字符串拼接渲染差异
- SQL lint、格式化、补全若依赖后端或 Agent，必须提供失败降级：编辑器仍可正常编辑和保存

## 国际化（i18n）

```typescript
// 使用 vue-i18n 的 useI18n
const { t } = useI18n()
t('project.workflow.name')

// 对应 locale 文件中必须同时存在：
// locales/en_US/xxx.ts: 'project.workflow.name': 'Workflow Name'
// locales/zh_CN/xxx.ts: 'project.workflow.name': '工作流名称'
```

**硬编码中文/英文字符串是禁止的。** 所有面向用户的文字必须走 i18n。

## 语言切换

语言首选项存储在 `language` cookie（通过 `js-cookie`），刷新页面后保持。

## 图表

- 仪表盘图表使用 **ECharts**
- 原工作流 DAG 使用 **AntV X6**
- SQL 血缘、依赖拓扑、字段级 lineage、SQL 影响范围图可通过 **FlowScope React island** 复用 `@pondpilot/flowscope-react` / `@xyflow/react`
- D3 仅用于已有页面或轻量自定义可视化；新增复杂关系图优先评估 FlowScope，而不是再手写 D3 图
- 禁止未经评审引入新的图表/关系图库

## FlowScope React Island 接入边界

FlowScope 是 React 生态组件，正式接入 DolphinScheduler Vue 主应用时必须隔离：

- React island 只能接收序列化 JSON props，例如 nodes、edges、columns、issues、layout、selection
- Vue 主应用负责路由、鉴权、数据请求、i18n、主题入口和错误边界
- React island 负责图形交互、布局、节点/边渲染、局部搜索、图导出
- React 内部状态不得直接写入 Pinia；需要回传时通过明确事件 contract，例如 `node-click`、`selection-change`、`export-request`
- React 依赖不得污染主应用全局样式；样式必须作用域隔离或有明确前缀
- FlowScope 版本升级前必须验证大图性能、暗/亮主题、节点点击、布局切换、浏览器缩放和导出能力
