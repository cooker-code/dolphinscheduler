# SQL 内容 Git 管理：master 为线上分支，个人分支为开发测试，AI 辅助

## 背景

SQL-first 作业工作台（见 Task: sql-first-job-editor）需要一套 SQL 内容存储方案。DolphinScheduler 现有方案是把 SQL 文本直接塞进任务参数 JSON，无版本历史、无 diff、无协作。引入 Git 仓库管理 SQL 源码，master 分支代表线上版本，个人分支（`user/<name>` 或 `feature/<name>`）代表开发/测试版本，AI Agent 可辅助生成 SQL、解释变更、生成发布说明。

## Goal

为 SQL 作业提供 Git 驱动的版本管理后端：保存 SQL 写入个人分支 commit，发布上线时合并到 master，并生成可审计的发布记录。AI 可参与 SQL 生成、变更说明、diff 解读，但最终上线仍走可审计流程（保存 → 测试 → 发布 → 合并 master）。

## 用户故事

1. **开发者** 新建 SQL 作业时，系统自动在 Git 仓库创建 `user/<name>/<job-slug>` 分支，后续保存操作写入该分支 commit。
2. **开发者** 在工作台看到当前文件的修改历史（commit list），可 diff 任意两个版本。
3. **开发者** 点"发布上线"时，系统将个人分支的 SQL 文件合并到 master 分支（fast-forward 或 squash merge），生成发布记录。
4. **开发者** 在部署抽屉看到"当前分支 vs master 的 SQL diff"，确认后发布。
5. **AI Agent** 可在编辑器内调用 Agent 生成 SQL 草稿（写入个人分支暂存区），开发者审查后手动保存为正式 commit。
6. **管理员** 可查看 master 分支上所有 SQL 文件的最新版本（即当前线上版本）。

## Requirements

### Git 仓库结构

```
sql-jobs/                          ← 仓库根目录（可与 DS 代码仓分离）
  <project-code>/
    <job-slug>.hql                 ← SQL 源码
    <job-slug>.yaml                ← 元数据（调度周期、负责人、描述、数据源）
  .publish-log/
    <job-slug>-<timestamp>.json    ← 发布记录（commit、分支、发布人、解析依赖、生成 DAG JSON）
```

- `master` 分支：线上版本，只允许通过发布流程写入，禁止直接 push
- `user/<name>` 分支：个人开发分支，保存时自动 commit，允许覆写
- 分支命名规则：`user/{username}/{job-slug}`，一个作业一个分支

### 后端接口

- `GET /sql-jobs/{id}/branches` 列出当前作业的分支列表
- `GET /sql-jobs/{id}/commits` 列出当前分支的 commit 历史（最近 50 条）
- `GET /sql-jobs/{id}/diff?base=master&head=user/xxx` 返回 SQL diff（unified diff 格式）
- `POST /sql-jobs/{id}/save` 将当前编辑内容写入个人分支，生成 commit（含 AI 生成标记）
- `POST /sql-jobs/{id}/deploy` 发布后执行 squash merge 到 master，写发布记录
- `POST /sql-jobs/{id}/agent-draft` 调用 AI Agent 生成 SQL 草稿，返回建议内容（不直接 commit）

### Git 操作服务

新增 `SqlJobGitService`，封装：
- 初始化作业分支（仓库不存在时 init，分支不存在时 checkout -b）
- 写文件并 commit（author 为当前登录用户）
- squash merge 到 master（`--no-ff --squash`，commit message 含发布人、job-slug、时间）
- 获取 commit log、diff
- 检测分支是否落后 master（发布前检查，落后时提示 rebase）
- 写发布记录 JSON 到 `.publish-log/`

### AI Agent 集成

- `agent-draft` 接口接收：job-slug、目标表名（可选）、表 schema（可选）、历史 SQL（可选）
- 调用 LLM（通过 DS 现有 LLM 配置或新增 AI 配置项）生成 SQL 草稿
- 返回草稿内容 + 生成说明（使用了哪些表、推断的业务逻辑）
- 草稿不直接写入 Git，由用户审查后手动保存
- AI 生成的 commit message 标注 `[AI-assisted]`

### 约束

- Git 操作在服务端执行，前端只通过 API 交互，不直接访问 Git
- master 分支保护：deploy 接口是唯一合并入口，其他写操作报 403
- 个人分支允许 force push（覆盖草稿），但发布记录不可覆盖
- 仓库路径可配置（`application.yaml` 新增 `sql-job.git.repo-path`）
- 首次使用自动初始化仓库（bare repo 或本地 repo，Phase 1 用本地）

## Acceptance Criteria

- [ ] 新建 SQL 作业时，系统自动创建个人 Git 分支 `user/{username}/{job-slug}`
- [ ] 保存 SQL 时，内容以 commit 形式写入个人分支，commit message 含用户名和时间戳
- [ ] 工作台可展示当前分支 commit 历史（最近 50 条），点击可查看对应版本 SQL
- [ ] 部署抽屉展示"个人分支 vs master"的 SQL diff（unified diff 或并排 diff）
- [ ] 发布成功后，SQL 以 squash merge 合并到 master，生成发布记录文件
- [ ] 分支落后 master 时，部署前给出提示（"你的分支落后 master X 个 commit，建议先 rebase"）
- [ ] `agent-draft` 接口返回 AI 生成的 SQL 草稿和生成说明，不自动 commit
- [ ] AI 辅助生成后用户手动保存，commit message 标注 `[AI-assisted]`
- [ ] master 分支不允许非发布流程的直接写入（API 层拒绝，返回 403）
- [ ] 发布记录 JSON 包含：commit hash、分支名、发布人、发布时间、解析依赖快照、生成 DAG JSON 摘要

## 与 sql-first-job-editor 的依赖关系

本任务是 `sql-first-job-editor` 的存储后端。`sql-first-job-editor` 的保存、部署流程依赖本任务提供的 `SqlJobGitService` 和相关 API。建议本任务的 Git 服务层先于前端联调完成。

## 分阶段边界

**Phase 1（本任务）**：本地 Git 仓库（JGit 或 ProcessBuilder 调用 git CLI），单机存储，master 保护，发布记录，AI 草稿生成（文本返回，不自动 commit）。  
**Phase 2**（后续）：对接远程 Git 仓库（GitLab/GitHub），支持 PR/MR 审批流程，多人协作冲突处理。  
**Phase 3**（后续）：AI 自动生成发布说明、回滚建议、影响面分析，写入 commit message 或 PR description。

## 边缘案例（需在实现时覆盖测试）

- 首次创建作业时仓库不存在：自动 init
- 个人分支落后 master：提示 rebase，不强制阻断（Phase 1 仅警告）
- 同一张产出表被多个作业引用：允许，但 deploy 时在发布记录中标注冲突警告
- master 被手动推送污染：deploy 接口检测非预期 commit，记录警告日志
- AI draft 生成超时：返回 timeout 错误，不影响 SQL 保存和发布流程
- commit 内容含敏感信息（密码、token）：Phase 1 不做自动扫描，文档说明需手动检查

## Notes

- 复杂任务，需补充 `design.md`（Git 操作封装、JGit vs CLI 选型、仓库路径配置）和 `implement.md` 后才能 `task.py start`。
- Phase 1 推荐用 JGit（纯 Java，无外部依赖）或 `ProcessBuilder` 调用本地 git，避免引入重量级依赖。
- AI 集成部分可复用 DS 现有 LLM 配置，或单独新增 `ai.sql-assistant` 配置项。
- 发布记录 JSON 是未来 Phase 2 对接远程 Git 的桥接数据，设计时预留字段。
