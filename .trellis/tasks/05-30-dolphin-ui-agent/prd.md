# DolphinScheduler UI Agent化建设

## 目标

将 DolphinScheduler 前端从「手画 DAG」模式升级为「SQL-first + Agent 辅助」的现代数据开发工作台，同时保留原有 DAG 调度内核的全部能力。

## 子任务

| 子任务 | 说明 |
|--------|------|
| `05-30-sql-first-job-editor` | SQL-first 作业开发界面：写 SQL → 自动生成 DAG 并上线 |
| `05-30-sql-git-version-management` | SQL 内容 Git 管理：master 线上分支 + 个人开发分支 + AI 辅助 |

## 设计产物

- `assets/sql-studio-demo.html` — SQL Studio 交互 Demo 页面（可在浏览器直接打开）

## 现有系统探索任务（需 OpenCLI 完成）

- [ ] 启动 Docker 依赖服务 + DS API + UI Dev Server
- [ ] 使用 OpenCLI Browser 访问 `localhost:5173`，截图并记录现有功能点
- [ ] 梳理现有 workflow/task 创建流程，对应到新 UI 的哪些步骤
- [ ] 整理 Worker Group、租户、告警组、数据源管理页面，作为新 UI 发布抽屉的配置来源
- [ ] 将探索结果补充到各子任务 `prd.md` 和 `design.md`

## 环境

- Docker 依赖服务：`docker-compose -f docker-compose-dev.yml up -d`
- API：`localhost:12345`，UI Dev Server：`localhost:5173`
- 默认账号：admin / dolphinscheduler123

## Acceptance Criteria

- [ ] SQL Studio Demo 存放在 `assets/sql-studio-demo.html`，可在浏览器打开
- [ ] 现有 DS UI 功能点已梳理并记录到 `assets/existing-ui-analysis.md`
- [ ] 两个子任务 PRD 均包含「与现有 DS 字段的对应关系」章节
- [ ] 子任务 `sql-first-job-editor` 完成 Phase 1 实现并通过验收
- [ ] 子任务 `sql-git-version-management` 完成 Phase 1 实现并通过验收

## Notes

- 父任务不直接实现代码，负责整体方向、探索分析和集成验收
- 子任务独立可验收，完成后合并到父任务进行集成验收
- 现有 DS UI 探索需先启动项目，使用 OpenCLI 浏览器模拟用户操作并截图
