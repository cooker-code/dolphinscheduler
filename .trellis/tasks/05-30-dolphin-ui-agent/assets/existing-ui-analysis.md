# DolphinScheduler 现有 UI 功能分析

> 通过读取 `dolphinscheduler-ui/src/views/` 源码提取，API 未启动时页面均停在令牌管理（路由 guard 重定向），字段定义以源码为准。
> 探索日期：2026-05-30

---

## 一、顶部导航结构

| 菜单 | 路径 | 说明 |
|------|------|------|
| 首页 | `/home` | 任务实例状态统计 + 工作流实例状态统计 |
| 项目管理 | `/projects/list` | 项目列表，字段：项目名称、所属用户、工作流定义数、描述 |
| 资源中心 | `/resource/...` | 文件管理、UDF 函数 |
| 源中心 | `/datasource/list` | 数据源管理 |
| 监控中心 | `/monitor/...` | Master/Worker 监控 |
| 安全中心 | `/security/...` | 多子模块，见下 |

---

## 二、安全中心子模块字段

### 租户管理 (`/security/tenant-manage`)
创建/编辑弹窗字段：
- **租户编码** (key: `tenantCode`, type: input)
- **队列** (key: `queueId`, type: select)
- **描述** (key: `description`, type: textarea)

### Worker 分组管理 (`/security/worker-group-manage`)
创建/编辑弹窗字段：
- **分组名称** (key: `name`, type: input)
- **Worker 地址** (key: `addrList`, type: select/multiple) — 从注册的 Worker 节点中选择

### 告警组管理 (`/security/alarm-group-manage`)
创建/编辑弹窗字段：
- **告警组名称** (key: `groupName`, type: input)
- **告警插件实例** (key: `alertInstanceIds`, type: select/multiple)
- **告警组描述** (key: `description`, type: textarea)

### 告警插件实例管理 (`/security/alarm-instance-manage`)
创建/编辑弹窗字段：
- **告警实例名称** (key: `instanceName`, type: input)
- **选择插件** (key: `pluginDefineId`, type: select) — 支持 Email、DingTalk、WeChat、Slack、飞书等
- **[动态字段]** 根据选择的插件动态渲染配置项（每个插件有自己的 JSON schema）

### 环境管理 (`/security/environment-manage`)
- **环境名称** (key: `environmentName`, type: input)
- **环境配置** (key: `config`, type: textarea) — 环境变量 k=v 格式
- **Worker 分组** (key: `workerGroups`, type: select/multiple)
- **描述** (key: `description`, type: textarea)

---

## 三、工作流发布/运行相关字段（**SQL Studio 发布抽屉必须覆盖**）

### DAG 保存弹窗 (`dag-save-modal.tsx`)
用户在 DAG 编辑器点「保存」时填写：

| 字段 | key | type | 选项/说明 |
|------|-----|------|-----------|
| 工作流名称 | `name` | input | 必填 |
| 描述 | `description` | textarea | |
| 超时告警 | `timeoutFlag` | switch | 开启后显示超时时长 |
| 超时时长 | `timeout` | input-number | 单位：分钟 |
| 执行策略 | `executionType` | select | PARALLEL / SERIAL_WAIT / SERIAL_DISCARD / SERIAL_PRIORITY |
| 全局变量 | `globalParams` | dynamic-input | 字段：key, direct(IN/OUT), type(VARCHAR/INTEGER/LONG/FLOAT/DOUBLE/DATE/TIME/BOOLEAN/LIST/FILE), value |
| 直接上线 | `release` | checkbox | 保存后立即 ONLINE |
| 直接更新 | `sync` | checkbox | 更新已有定义 |

### 定时调度弹窗 (`timing-modal.tsx`)
用户设置 workflow 的 cron 调度时填写：

| 字段 | key | type | 选项/说明 |
|------|-----|------|-----------|
| 起止时间 | `startEndTime` | date-range-picker | |
| 定时表达式 | `crontab` | input + picker | 可视化 cron 编辑器 |
| 时区 | `timezoneId` | select | |
| 失败策略 | `failureStrategy` | radio | CONTINUE / END |
| 通知策略 | `warningType` | select | 不发送/成功发送/失败发送/全部发送 |
| 告警组 | `warningGroupId` | select | 条件显示 |
| 工作流优先级 | `workflowInstancePriority` | select | HIGHEST/HIGH/MEDIUM/LOW/LOWEST |
| Worker 分组 | `workerGroup` | select | |
| 租户 | `tenantCode` | select | |
| 环境名称 | `environmentCode` | select | |

### 手动运行弹窗 (`start-modal.tsx`)
用户手动触发一次工作流运行时填写：

| 字段 | key | type | 选项/说明 |
|------|-----|------|-----------|
| 失败策略 | `failureStrategy` | radio | CONTINUE / END |
| 节点执行策略 | `taskDependType` | radio | TASK_POST(后续节点) / TASK_PRE(前向节点) / TASK_ONLY(当前节点) |
| 通知策略 | `warningType` | select | 不发送/成功发送/失败发送/全部发送 |
| 告警组 | `warningGroupId` | select | |
| 工作流优先级 | `workflowInstancePriority` | select | HIGHEST/HIGH/MEDIUM/LOW/LOWEST |
| Worker 分组 | `workerGroup` | select | |
| 租户 | `tenantCode` | select | |
| 环境名称 | `environmentCode` | select | |
| 补数 | `execType` | checkbox | 开启后显示补数配置 |
| 补数模式 | `complementDependentMode` | radio | OFF_MODE / ALL_DEPENDENT |
| 运行模式 | `runMode` | radio | RUN_MODE_SERIAL / RUN_MODE_PARALLEL |
| 并行度 | `expectedParallelismNumber` | input-number | 并行时可配置 |
| 执行顺序 | `executionOrder` | radio | DESC_ORDER / ASC_ORDER |
| 调度时间 | `scheduleTime` | date-range/textarea | 补数使用 |
| 启动参数 | `startParamsList` | dynamic-input | prop / direct / type / value |
| 空跑 | `dryRun` | switch | 不实际执行 |

---

## 四、数据源管理 (`/datasource/list`)

创建数据源字段（以 MySQL 为例）：
- **数据源类型** (type: select) — MySQL / PostgreSQL / Hive / Spark / ClickHouse / Oracle / SQL Server / DB2 / Presto / Redshift / Athena / Trino 等
- **数据源名称** (name: input)
- **描述** (description: textarea)
- **IP/主机名** (host: input)
- **端口** (port: input-number)
- **用户名** (userName: input)
- **密码** (password: input)
- **数据库名** (database: input)
- **连接参数** (other: textarea) — jdbc 附加参数

---

## 五、SQL Studio 发布抽屉字段对应关系

基于以上分析，发布抽屉需要覆盖的 DS 原生字段（已在 demo 中实现或需补充）：

| SQL Studio 字段 | 对应 DS 字段 | 来源弹窗 | 状态 |
|----------------|-------------|---------|------|
| Worker 组 | `workerGroup` | timing-modal / start-modal | ✅ demo 已有 |
| 租户 | `tenantCode` | timing-modal / start-modal | ✅ demo 已有 |
| 告警组 | `warningGroupId` | timing-modal | ✅ demo 已有 |
| 告警策略 | `warningType` | timing-modal | ✅ demo 已有（选项需对应：不发送/成功/失败/全部） |
| 超时阈值 | `timeout` + `timeoutFlag` | dag-save-modal | ✅ demo 已有 |
| Cron 表达式 | `crontab` | timing-modal | ✅ demo 已有 |
| 失败策略 | `failureStrategy` | timing-modal | ✅ demo 已有（结束=END/继续=CONTINUE）|
| 数据源 | `datasource` | SQL task config | ✅ demo 已有 |
| **工作流优先级** | `workflowInstancePriority` | timing-modal | ❌ demo 缺失 |
| **环境名称** | `environmentCode` | timing-modal | ❌ demo 缺失 |
| **执行策略** | `executionType` | dag-save-modal | ❌ demo 缺失 |
| **全局变量** | `globalParams` | dag-save-modal | ⚠️ SQL 中 ${dt} 等变量需映射到此 |

---

## 六、待补充到 Demo 的字段

下次迭代需在发布抽屉「调度配置」区域补充：
1. **工作流优先级** — select: HIGHEST / HIGH / MEDIUM / LOW / LOWEST（默认 MEDIUM）
2. **环境名称** — select: 来自环境管理，影响 Worker 执行环境变量
3. **执行策略** — select: PARALLEL（默认）/ SERIAL_WAIT / SERIAL_DISCARD / SERIAL_PRIORITY
4. **全局变量自动映射** — SQL 中解析出的 `${dt}` 等变量，自动填入 globalParams 列表，type 默认 VARCHAR，direct 默认 IN

---

## 七、现有 UI 的痛点（SQL-first 改造动机）

1. **入口不友好**：创建工作流必须进入项目 → 工作流定义 → 创建 → DAG 编辑器，5 步以上
2. **DAG 编辑器对 SQL 任务冗余**：单 SQL 任务仍需在画布上拖拽节点、连线
3. **SQL 内容不可见**：SQL 文本藏在 task 参数里，列表页看不到内容
4. **无版本对比**：只有工作流版本号，无 SQL 内容的 diff 视图
5. **依赖配置全手动**：依赖其他工作流需手工添加 dependent task，无自动推导
6. **调度和运行参数分离**：定时配置在一个弹窗，手动运行在另一个弹窗，字段重复
7. **无 SQL 测试入口**：无法在保存前验证 SQL 正确性

---

*分析来源：源码读取 + OpenCLI 浏览器截图（API 未启动，界面停留在令牌管理）*
