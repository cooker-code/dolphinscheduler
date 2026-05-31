# DolphinScheduler UI 功能全景清单

> 来源：`dolphinscheduler-ui/src/router/` + `src/views/`
> 版本：my-dev 分支，扫描日期 2026-05-31

---

## 一、认证 & 个人中心

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/login` | 登录页 | 账密登录、SSO登录、OAuth2登录、OIDC登录 | — | `src/views/login/index.tsx` |
| `/home` | 首页 | 查看任务实例/工作流实例/工作流定义状态统计 | — | `src/views/home/index.tsx` |
| `/profile` | 用户信息 | 查看/编辑个人信息（用户名、邮件、手机） | 已登录 | `src/views/profile/index.tsx` |
| `/password` | 修改密码 | 修改当前用户密码 | 已登录 | `src/views/password/index.tsx` |
| `/about` | 产品信息 | 查看版本、许可证信息 | — | `src/views/about/index.tsx` |
| `/ui-setting` | UI 设置 | 切换主题（亮/暗）、语言（中/英）、API 超时 | — | `src/views/ui-setting/index.tsx` |

---

## 二、项目管理

### 2.1 项目列表

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/projects/list` | 项目列表 | 查看/创建/编辑/删除项目、搜索项目 | — | `src/views/projects/list/index.tsx` |

### 2.2 项目内页面（需 projectCode）

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/projects/:projectCode` | 项目概览 | 查看任务/实例状态统计、工作流完成趋势图 | 项目 | `src/views/projects/overview/index.tsx` |
| `/projects/:projectCode/parameter` | 项目级参数 | 查看/创建/编辑/删除全局参数 | 项目 | `src/views/projects/parameter/index.tsx` |
| `/projects/:projectCode/preferences` | 项目偏好设置 | 配置任务超时告警、失败策略等 | 项目 | `src/views/projects/preference/index.tsx` |
| `/projects/:projectCode/workflow/relation` | 工作流关系图 | 查看工作流间依赖关系（DAG 图） | 项目 | `src/views/projects/workflow/relation/index.tsx` |

### 2.3 工作流定义

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/projects/:projectCode/workflow-definition` | 工作流定义列表 | 查看/搜索/创建/编辑/删除/复制/移动/导入/导出工作流、上线/下线、批量操作 | 项目 | `src/views/projects/workflow/definition/index.tsx` |
| `/projects/:projectCode/workflow/definitions/create` | 创建工作流 | DAG 画布拖拽建图、添加任务节点、配置依赖、设置全局参数 | 项目 | `src/views/projects/workflow/definition/create/index.tsx` |
| `/projects/:projectCode/workflow/definitions/:code` | 工作流定义详情 | 查看/编辑工作流 DAG、保存、上线/下线 | 项目、工作流 | `src/views/projects/workflow/definition/detail/index.tsx` |
| `/projects/:projectCode/workflow-definition/tree/:definitionCode` | 工作流定义树形图 | 查看工作流 DAG 的树形结构 | 项目、工作流 | `src/views/projects/workflow/definition/tree/index.tsx` |
| `/projects/:projectCode/workflow-definition/timing/:definitionCode` | 工作流定时管理 | 查看/创建/编辑/删除/上线/下线定时任务 | 项目、工作流 | `src/views/projects/workflow/definition/timing/index.tsx` |

### 2.4 工作流实例

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/projects/:projectCode/workflow/timings` | 工作流定时列表 | 查看全项目所有定时任务、搜索/上线/下线/删除 | 项目 | `src/views/projects/workflow/timing/index.tsx` |
| `/projects/:projectCode/workflow/instances` | 工作流实例列表 | 查看/搜索实例、停止/暂停/恢复/重跑/删除 | 项目 | `src/views/projects/workflow/instance/index.tsx` |
| `/projects/:projectCode/workflow/instances/:id` | 工作流实例详情 | 查看实例 DAG（含节点状态）、查看任务日志 | 项目、实例 | `src/views/projects/workflow/instance/detail/index.tsx` |
| `/projects/:projectCode/workflow/instances/:id/gantt` | 工作流实例甘特图 | 查看任务执行时间轴、并行度分析 | 项目、实例 | `src/views/projects/workflow/instance/gantt/index.tsx` |

### 2.5 任务实例

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/projects/:projectCode/task/instances` | 任务实例列表 | 查看/搜索/停止/重跑任务实例、查看日志 | 项目 | `src/views/projects/task/instance/index.tsx` |

### 2.6 支持的任务类型（38种）

DAG 画布中可添加的任务节点类型：

| 类型 | 说明 |
|------|------|
| SHELL | Shell 脚本 |
| PYTHON | Python 脚本 |
| SQL | SQL 执行（需配数据源） |
| JAVA | Java 程序 |
| SPARK | Spark 任务 |
| FLINK | Flink 任务（批） |
| FLINK_STREAM | Flink 任务（流） |
| MR | MapReduce 任务 |
| SUB_WORKFLOW | 子工作流 |
| DEPENDENT | 跨项目依赖 |
| CONDITIONS | 条件分支 |
| SWITCH | 多分支开关 |
| HTTP | HTTP 请求 |
| GRPC | gRPC 调用 |
| PROCEDURE | 存储过程 |
| DATAX | DataX 数据同步 |
| SQOOP | Sqoop 数据导入 |
| SEATUNNEL | SeaTunnel |
| CHUNJUN | ChunJun |
| HIVE_CLI | Hive CLI |
| ZEPPELIN | Zeppelin Notebook |
| JUPYTER | Jupyter Notebook |
| K8S | Kubernetes 任务 |
| KUBEFLOW | KubeFlow |
| MLFLOW | MLflow |
| OPENMLDB | OpenMLDB |
| DVC | DVC 数据版本控制 |
| SAGEMAKER | Amazon SageMaker |
| EMR | Amazon EMR |
| EMR_SERVERLESS | Amazon EMR Serverless |
| DMS | Amazon DMS |
| DATASYNC | Amazon DataSync |
| DATA_FACTORY | Azure Data Factory |
| LINKIS | Linkis |
| DINKY | Dinky |
| REMOTESHELL | 远程 Shell |
| ALIYUN_SERVERLESS_SPARK | 阿里云 Serverless Spark |

---

## 三、资源中心

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/resource/file-manage` | 文件管理 | 查看/上传/下载/重命名/删除文件、创建目录、支持子目录浏览 | — | `src/views/resource/file/index.tsx` |
| `/resource/file/create` | 文件创建 | 在线创建文本文件（支持代码编辑器） | — | `src/views/resource/file/create/index.tsx` |
| `/resource/file/edit` | 文件编辑 | 在线编辑文本文件 | 文件 | `src/views/resource/file/edit/index.tsx` |
| `/resource/file/list` | 文件详情 | 查看文件内容（只读） | 文件 | `src/views/resource/file/edit/index.tsx` |
| `/resource/task-group-option` | 任务组配置 | 查看/创建/编辑/删除/启停任务组 | — | `src/views/resource/task-group/option/index.tsx` |
| `/resource/task-group-queue` | 任务组队列 | 查看当前任务组排队情况、强制启动/停止排队任务 | 任务组 | `src/views/resource/task-group/queue/index.tsx` |

---

## 四、数据源中心

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/datasource` | 数据源列表 | 查看/搜索/创建/编辑/删除/测试连接数据源 | — | `src/views/datasource/list/index.tsx` |

**支持的数据源类型（26种）：**
MYSQL、POSTGRESQL、HIVE、KYUUBI、SPARK、CLICKHOUSE、ORACLE、SQLSERVER、VERTICA、PRESTO、REDSHIFT、ATHENA、TRINO、AZURESQL、STARROCKS、DAMENG、OCEANBASE、SNOWFLAKE、SSH、DATABEND、HANA、ZEPPELIN、DORIS、SAGEMAKER、ALIYUN_SERVERLESS_SPARK、DOLPHINDB

---

## 五、监控中心

| 路由 | 功能名称 | 操作 | 依赖数据 | 组件路径 |
|------|----------|------|----------|----------|
| `/monitor/master` | Master 节点监控 | 查看 Master 节点列表、CPU/内存/负载指标，每10秒自动刷新 | — | `src/views/monitor/servers/master/index.tsx` |
| `/monitor/worker` | Worker 节点监控 | 查看 Worker 节点列表、CPU/内存/负载指标，每10秒自动刷新 | — | `src/views/monitor/servers/worker/index.tsx` |
| `/monitor/alert_server` | Alert Server 监控 | 查看 Alert Server 节点状态 | — | `src/views/monitor/servers/alert_server/index.tsx` |
| `/monitor/db` | 数据库监控 | 查看数据库连接池、执行队列等状态 | — | `src/views/monitor/servers/db/index.tsx` |
| `/monitor/statistics` | 统计管理 | 查看任务/命令/错误队列数量统计 | — | `src/views/monitor/statistics/statistics/index.tsx` |
| `/monitor/audit-log` | 审计日志 | 查看/搜索系统操作审计日志（按用户/操作类型/时间过滤） | — | `src/views/monitor/statistics/audit-log/index.tsx` |

---

## 六、安全中心

| 路由 | 功能名称 | 操作 | 权限 | 组件路径 |
|------|----------|------|------|----------|
| `/security/tenant-manage` | 租户管理 | 查看/创建/编辑/删除租户 | 管理员 | `src/views/security/tenant-manage/index.tsx` |
| `/security/user-manage` | 用户管理 | 查看/创建/编辑/删除用户、授权（项目/资源/数据源） | 管理员 | `src/views/security/user-manage/index.tsx` |
| `/security/alarm-group-manage` | 告警组管理 | 查看/创建/编辑/删除告警组、关联告警实例 | 管理员 | `src/views/security/alarm-group-manage/index.tsx` |
| `/security/alarm-instance-manage` | 告警实例管理 | 查看/创建/编辑/删除告警插件实例（Dingtalk/Wechat/Email等） | 管理员 | `src/views/security/alarm-instance-manage/index.tsx` |
| `/security/worker-group-manage` | Worker 分组管理 | 查看/创建/编辑/删除 Worker 分组 | 管理员 | `src/views/security/worker-group-manage/index.tsx` |
| `/security/yarn-queue-manage` | Yarn 队列管理 | 查看/创建/编辑/删除 Yarn 队列 | 管理员 | `src/views/security/yarn-queue-manage/index.tsx` |
| `/security/environment-manage` | 环境管理 | 查看/创建/编辑/删除执行环境（含 Worker 分组绑定） | 管理员 | `src/views/security/environment-manage/index.tsx` |
| `/security/cluster-manage` | 集群管理 | 查看/创建/编辑/删除集群配置 | 管理员 | `src/views/security/cluster-manage/index.tsx` |
| `/security/k8s-namespace-manage` | K8S 命名空间管理 | 查看/创建/编辑/删除 K8S 命名空间 | 管理员 | `src/views/security/k8s-namespace-manage/index.tsx` |
| `/security/token-manage` | 令牌管理 | 查看/创建/删除 API Token（所有用户可访问） | 所有用户 | `src/views/security/token-manage/index.tsx` |

---

## 七、未注册路由的模块

| 目录 | 说明 |
|------|------|
| `src/views/sql-workbench/` | SQL 工作台（开发中，无路由注册，不对用户开放） |
