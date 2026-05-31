# DolphinScheduler UI 可复用组件清单

> 来源：`dolphinscheduler-ui/src/components/` + `src/views/` 中跨模块共用组件
> 版本：my-dev 分支，扫描日期 2026-05-31

---

## 一、全局通用组件（`src/components/`）

### 1. Modal — 对话框

- **路径**：`src/components/modal/index.tsx`
- **用途**：封装 Naive UI NModal，提供统一的确认/取消按钮和标题样式
- **引用页面数**：62 个页面（几乎所有增删改操作弹窗）
- **Props**：
  | Prop | 类型 | 说明 |
  |------|------|------|
  | `show` | Boolean | 是否显示 |
  | `title` | String | 标题（必填） |
  | `cancelText` | String | 取消按钮文字 |
  | `cancelShow` | Boolean | 是否显示取消按钮 |
  | `confirmShow` | Boolean | 是否显示确认按钮 |
  | `confirmText` | String | 确认按钮文字 |
- **Emits**：`confirm`、`cancel`、`maskClick`

---

### 2. Form — 动态表单

- **路径**：`src/components/form/index.tsx`
- **用途**：根据 JSON Schema 动态渲染表单，支持多种字段类型（input/select/switch/checkbox/radio/monaco-editor/tree-select/custom-parameters/multi-input 等）
- **引用页面数**：373 处（全系统最广泛使用的组件）
- **Props**：
  | Prop | 类型 | 说明 |
  |------|------|------|
  | `meta` | Object | 表单 Schema（必填） |
  | `layout` | String | 布局方向 |
  | `loading` | Boolean | 加载态 |
- **支持字段类型**：`input`、`input-number`、`select`、`switch`、`checkbox`、`radio`、`tree-select`、`monaco-editor`、`custom-parameters`、`multi-input`、`multi-condition`

---

### 3. Card — 内容卡片

- **路径**：`src/components/card/index.tsx`
- **用途**：页面内容区统一容器，带标题栏和内容区
- **引用页面数**：48 个页面（监控/安全/资源等几乎所有列表页）
- **Props**：
  | Prop | 类型 | 说明 |
  |------|------|------|
  | `title` | String | 卡片标题 |
  | `headerStyle` | Object | 标题栏样式 |
  | `headerExtraStyle` | Object | 标题右侧额外区域样式 |
  | `contentStyle` | Object | 内容区样式 |

---

### 4. Chart — 图表套件

- **路径**：`src/components/chart/index.ts`（通用 ECharts 初始化），子组件在 `modules/`
- **用途**：ECharts 图表封装，支持主题切换和国际化
- **引用页面数**：20 个页面（首页统计、项目概览、监控中心）
- **子组件**：
  | 子组件 | 图表类型 |
  |--------|----------|
  | `modules/Bar.tsx` | 柱状图 |
  | `modules/Gauge.tsx` | 仪表盘 |
  | `modules/Pie.tsx` | 饼图 |
  | `modules/Tree.tsx` | 树形图 |
- **特性**：自动响应主题切换（亮/暗）、窗口 resize 自适应、国际化重渲染

---

### 5. InputSearch — 搜索框

- **路径**：`src/components/input-search/index.tsx`
- **用途**：带清除按钮的搜索输入框，统一所有列表页搜索交互
- **引用页面数**：16 个页面
- **Props**：`placeholder`（必填）
- **Emits**：`search`（输入内容）、`clear`（清除内容）

---

### 6. Crontab — Cron 表达式编辑器

- **路径**：`src/components/crontab/index.tsx`
- **用途**：可视化 Cron 表达式编辑器（秒/分/时/日/月/周分Tab）
- **引用页面数**：6 个（工作流定时相关页面）
- **Props**：`value`（Cron 字符串）
- **Emits**：`update:value`（更新后的 Cron 字符串）

---

### 7. MonacoEditor — 代码编辑器

- **路径**：`src/components/monaco-editor/index.tsx`
- **用途**：嵌入式代码编辑器，支持语法高亮、只读模式
- **引用页面数**：3 个（文件编辑、SQL 任务）
- **Props**：
  | Prop | 类型 | 说明 |
  |------|------|------|
  | `value` | String | 当前内容 |
  | `defaultValue` | String | 默认内容 |
  | `options` | Object | Monaco 配置项 |
  | `readOnly` | Boolean | 是否只读 |
  | `language` | String | 语言类型 |
  | `height` | String | 编辑器高度 |
- **Emits**：`change`、`blur`、`focus`

---

### 8. LogModal — 日志查看弹窗

- **路径**：`src/components/log-modal/index.tsx`
- **用途**：任务实例/工作流实例的日志查看弹窗，支持实时刷新和下载
- **引用页面数**：3 个（任务实例、工作流实例详情）
- **Props**：
  | Prop | 类型 | 说明 |
  |------|------|------|
  | `showModalRef` | Boolean | 显示状态 |
  | `logRef` | String | 日志内容 |
  | `logLoadingRef` | Boolean | 加载中 |
  | `row` | Object | 当前任务行数据 |
  | `showDownloadLog` | Boolean | 是否显示下载按钮 |
- **Emits**：`confirmModal`、`refreshLogs`、`downloadLogs`

---

### 9. ButtonLink — 链接按钮

- **路径**：`src/components/button-link/index.tsx`
- **用途**：带路由跳转的按钮，避免硬编码 `<a>` 标签
- **引用页面数**：7 个
- **Props**：`disabled`、`type`、`iconPlacement`

---

### 10. Result — 结果展示

- **路径**：`src/components/result/index.tsx`
- **用途**：空状态/错误状态的统一展示页（403、404、空数据等）
- **引用页面数**：19 个
- **Props**：`title`、`description`、`size`、`status`（success/warning/error/info）、`contentStyle`

---

## 二、项目内跨模块共用组件（`src/views/projects/`）

### 11. DAG 画布（`projects/workflow/components/dag/`）

- **路径**：`src/views/projects/workflow/components/dag/index.tsx`
- **用途**：工作流 DAG 编辑器核心，基于 AntV X6，支持节点拖拽、连线、右键菜单、自动布局
- **引用页面**：工作流定义创建页、工作流定义详情页、工作流实例详情页（只读）
- **子组件**：
  | 子组件 | 职责 |
  |--------|------|
  | `dag-canvas.tsx` | X6 画布容器，渲染节点/边 |
  | `dag-sidebar.tsx` | 左侧任务类型面板（拖拽源） |
  | `dag-toolbar.tsx` | 工具栏（保存/上线/全屏/缩放/自动布局等） |
  | `dag-context-menu.tsx` | 右键菜单（编辑/删除/复制节点） |
  | `dag-save-modal.tsx` | 保存弹窗（工作流名称/描述/全局参数） |
  | `dag-startup-param.tsx` | 运行参数面板（手动触发时填写参数） |
  | `dag-auto-layout-modal.tsx` | 自动布局配置弹窗 |
  | `dag-node-status.tsx` | 节点状态标记（实例视图） |

### 12. Dynamic DAG（`projects/workflow/components/dynamic-dag/`）

- **路径**：`src/views/projects/workflow/components/dynamic-dag/index.tsx`
- **用途**：动态工作流的 DAG 编辑器（支持参数化节点数量）
- **引用页面**：带有动态分支的工作流创建/编辑

### 13. 任务节点配置面板（`projects/task/components/node/`）

- **路径**：`src/views/projects/task/components/node/index.tsx`
- **用途**：任务节点编辑抽屉（右侧滑出），根据任务类型动态渲染配置字段
- **引用页面**：工作流定义创建/详情页（点击 DAG 节点时触发）
- **子目录**：
  - `fields/`：各字段的 composable（共76个，对应各类配置项）
  - `tasks/`：特殊任务类型的额外逻辑（mlflow/flink-stream/java 等）

### 14. 依赖关系弹窗（`projects/components/dependencies/`）

- **路径**：`src/views/projects/components/dependencies/dependencies-modal.tsx`
- **用途**：DEPENDENT 任务类型的跨项目依赖配置弹窗
- **引用页面**：工作流定义列表、工作流定时管理

---

## 三、资源中心共用组件（`src/views/resource/components/`）

### 15. 资源选择组件（`resource/components/resource/`）

- **路径**：`src/views/resource/components/resource/index.tsx`
- **用途**：任务节点中引用资源文件的选择器弹窗（文件树 + 搜索）
- **引用页面**：Shell/Python/Java 等任务节点配置中引用脚本文件

---

## 四、SQL Workbench 组件（`src/views/sql-workbench/`，开发中）

> 注：以下组件已编写但尚未注册路由，未对用户开放。

| 子目录 | 职责 |
|--------|------|
| `components/database-tree/` | 数据库/表/字段树形浏览器 |
| `components/sql-editor-tabs/` | 多 Tab SQL 编辑器 |
| `components/script-panel/` | 脚本内容面板（代码编辑区） |
| `components/result-panel/` | SQL 执行结果展示区 |
| `components/toolbar/` | 工具栏（运行/格式化/保存等操作） |
