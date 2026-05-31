# 实施计划：SQL 内容 Git 管理

> 与 prd.md / design.md 配套。Phase 1 是本任务交付范围；Phase 2/3 仅记录入口，不在本期实施。

## Phase 0 — 前置依赖

- [ ] 确认 `sql-first-job-editor` 已就绪或可同步推进（本任务是其后端依赖）
- [ ] 与运维确认 `${repo-path}` 路径、备份策略
- [ ] 与产品确认 AI provider（OpenAI / 内网网关 / 关闭）
- [ ] 集群路由策略落地（多 API 节点 → 单写节点）

## Phase 1.1 — 基础设施

- [ ] **1.1.1 引入 JGit 依赖**
  - `dolphinscheduler-bom/pom.xml` 添加 `org.eclipse.jgit:org.eclipse.jgit:6.x`
  - 新建 `dolphinscheduler-service/.../sqljob/` 包
- [ ] **1.1.2 配置项**
  - `application.yaml`：`sql-job.git.*` + `ai.sql-assistant.*` block
  - `Constants` 中加路径常量
- [ ] **1.1.3 数据库 schema**
  - 新建 `t_ds_sql_job` 表（id / project_code / job_slug / personal_branch / current_master_commit / owner / created_at / updated_at）
  - mysql/postgres/h2 三方言 + 升级脚本
- [ ] **1.1.4 仓库 bootstrap**
  - `SqlJobRepoBootstrap`：API 启动后检查/初始化 bare repo + master 初始 commit
  - 单元测试：覆盖"已存在"/"不存在"/"已存在但无 master"三种状态

## Phase 1.2 — Git 操作服务

- [ ] **1.2.1 SqlJobLockManager**
  - per-job ReentrantLock + LRU 驱逐
  - 单测：并发 save 同一 job 串行、不同 job 并行
- [ ] **1.2.2 SqlJobGitService**
  - `save(jobKey, content, user)` — DirCache 写 + commit
  - `commits(jobKey, branch, limit=50)` — 读 RevWalk
  - `diff(jobKey, base, head)` — unified diff
  - `deploy(jobKey, force)` — squash merge to master + 写 publish log
  - `revert(jobKey, toCommit)` — Git revert
  - `branchStatus(jobKey)` — ahead/behind master
- [ ] **1.2.3 PublishLogWriter**
  - `.publish-log/<job>-<ts>.json` 写入 + 路径 sanitize
- [ ] **1.2.4 单元测试**
  - JGit 内存仓库（`InMemoryRepository`）做基线测试
  - 覆盖：save/deploy/revert 全链路、落后 master 提示、并发锁

## Phase 1.3 — REST 接口

- [ ] **1.3.1 SqlJobController**（dolphinscheduler-api）
  - 6 个端点（PRD 列举）
  - `@OperatorLog` 标注 SQL_JOB_SAVE / SQL_JOB_DEPLOY / SQL_JOB_REVERT / SQL_JOB_AI_DRAFT
  - 入参严格校验（job-slug 正则、pageSize 上限）
- [ ] **1.3.2 权限点**
  - 新增 `SQL_JOB_VIEW / SQL_JOB_EDIT / SQL_JOB_DEPLOY` 三个权限点
  - deploy 只允许项目 OWNER 角色
- [ ] **1.3.3 DTO**
  - `SqlJobSaveRequest / DiffResponse / CommitDto / DeployResponse / AgentDraftRequest / AgentDraftResponse`
- [ ] **1.3.4 集成测试**
  - 端到端：create → save → diff → deploy → revert
  - 越权测试：A 用户的 deploy 改 B 项目，403
  - 落后 master：force=false 报 409，force=true 通过

## Phase 1.4 — AI Agent 集成

- [ ] **1.4.1 SqlAiAssistantService 抽象**
  - `draft(intent, context)` 接口
  - 三个实现：`OpenAiSqlAssistant` / `LocalNoopAssistant`（默认） / `BedrockAssistant`（可选）
- [ ] **1.4.2 限流**
  - 基于 Bucket4j 或 Redisson（DS 已有缓存层）的每用户/租户配额
- [ ] **1.4.3 失败降级**
  - 超时/错误返回 200 + `draft=null` + error 字段，不抛异常
- [ ] **1.4.4 调用审计**
  - 走 `@OperatorLog`，记录 token 用量到 `latency` 字段（重命名为 `extra` 或新增字段）

## Phase 1.5 — 与 sql-first-job-editor / 工作流定义联动

- [ ] **1.5.1 deploy 后联动**
  - SqlJobGitService.deploy 成功后，调用 ProcessDefinitionService.update（注入新 commit hash 到 task params）
- [ ] **1.5.2 工作流执行时读取 SQL**
  - 任务参数中存 `sql_job_commit_hash`
  - master/worker 执行任务时通过 SqlJobGitService 拿对应 commit 的 SQL 内容
  - **注意**：worker 通过 RPC 调 API 取 SQL，避免 worker 直接访问 Git 仓库

## Phase 1.6 — 监控、文档、上线

- [ ] **1.6.1 metrics 接入**（design.md §8 列出的所有指标）
- [ ] **1.6.2 文档**
  - `docs/zh/feature/sql-job-git.md`：用法、配置、回滚操作、敏感信息警告
- [ ] **1.6.3 灰度配置**
  - `sql-job.git.enabled` feature flag 默认 false
- [ ] **1.6.4 验收用例对齐 PRD Acceptance Criteria 全过**

## Phase 1 验证 gate

- [ ] 所有 PRD acceptance criteria 跑通
- [ ] 1000 commit 的仓库下，commits/diff/save 接口 P95 < 500ms
- [ ] 并发 50 个 save 同一作业，串行执行无死锁
- [ ] AI provider down 时，save/deploy 不受影响
- [ ] feature flag off 时，旧路径完全不变

## Rollback 节点

| 阶段 | 回滚方法 |
|------|---------|
| Phase 1.1-1.2 | 不发布即可，无业务影响 |
| Phase 1.3 | controller 不暴露端点 |
| Phase 1.4 | `ai.sql-assistant.enabled=false` |
| Phase 1.5 | feature flag off，工作流恢复读取 task params 中的 SQL（兼容旧字段） |
| Phase 1.6 | feature flag off，t_ds_sql_job 数据保留不删除 |

## Phase 2/3 仅记录入口（不在本期）

- Phase 2：远程 Git（GitLab/GitHub）+ PR/MR 审批 + 多人协作冲突
- Phase 3：AI 自动生成发布说明 / 回滚建议 / 影响面分析

## 依赖外部任务

- `sql-first-job-editor`：本任务是其后端，需保持接口契约同步
- `audit-log-arch-rework`：本任务的所有审计动作走 `@OperatorLog`，依赖审计治理后的稳定写入
