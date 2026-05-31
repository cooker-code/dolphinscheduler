# Git Fork 协作流程

> 本仓库已切换为 fork 模式：`origin` = 你的 fork，`upstream` = apache 官方。
>
> 本地 `dev` 默认跟踪 `origin/dev`。所有日常 push/pull 走 fork，与官方同步走 `upstream`。

---

## 一次性配置（已完成）

```bash
git remote set-url origin   https://github.com/cooker-code/dolphinscheduler.git
git remote add     upstream https://github.com/apache/dolphinscheduler.git
git branch --set-upstream-to=origin/dev dev
```

确认：

```bash
git remote -v
# origin    https://github.com/cooker-code/dolphinscheduler.git (fetch+push)
# upstream  https://github.com/apache/dolphinscheduler.git      (fetch+push)
```

---

## 日常开发流程

### 1. 开始新工作前先同步上游

```bash
./scripts/sync-upstream.sh        # 同步 dev
# 或同步指定分支
./scripts/sync-upstream.sh 3.4.2-release
```

脚本逻辑：fetch upstream → fast-forward 合并 upstream/dev → push 到 origin/dev。
**只接受 fast-forward**，避免本地 dev 与上游产生分叉、污染未来 PR。

### 2. 基于最新 dev 切功能分支

```bash
git checkout -b feat/your-feature dev
# ...写代码、commit...
git push -u origin feat/your-feature
```

不要直接在 `dev` 上提交业务代码。`dev` 永远只是上游 dev 的镜像。

### 3. 提 PR 到 apache 官方

```bash
gh pr create --repo apache/dolphinscheduler \
  --base dev \
  --head cooker-code:feat/your-feature \
  --title "..." --body "..."
```

`gh` 会自动识别 fork 关系。head 一定要写 `cooker-code:feat/your-feature` 而不是 `feat/your-feature`，否则 gh 会以为分支在 apache 上。

---

## 同步 fork 与 upstream

### 自动方式（推荐）

```bash
./scripts/sync-upstream.sh        # dev
./scripts/sync-upstream.sh dev    # 同上
```

### 手动方式（脚本不工作时的兜底）

```bash
git fetch upstream --prune --tags
git checkout dev
git merge --ff-only upstream/dev    # 失败说明本地 dev 已分叉
git push origin dev
```

### 已分叉怎么办

如果 `git merge --ff-only` 失败，说明你在本地 `dev` 上做过提交（违反约定）。两种修复路径：

```bash
# 选项 A：保留本地提交，迁到新功能分支，然后硬重置 dev
git checkout -b feat/rescue-from-dev dev
git checkout dev
git reset --hard upstream/dev
git push origin dev --force-with-lease

# 选项 B：放弃本地提交（确认它们已经在别的分支或不需要了）
git checkout dev
git reset --hard upstream/dev
git push origin dev --force-with-lease
```

`--force-with-lease` 比 `--force` 安全：如果远程 origin/dev 在你不知情的时候被推过新内容，命令会失败而不是覆盖。

---

## 拉取 upstream 的 release 分支到 fork

fork 当前**只保留 `dev`**，所有 release 分支都已删除。如果某个 release 分支需要本地工作：

```bash
git fetch upstream --prune
git checkout -b 3.4.2-release upstream/3.4.2-release
git push -u origin 3.4.2-release    # 把这条分支也推到你的 fork
```

或直接调脚本：

```bash
./scripts/sync-upstream.sh 3.4.2-release
```

---

## 常见检查命令

```bash
# 看 fork 与 upstream 的差异
git rev-list --left-right --count origin/dev...upstream/dev
# 输出 "0	0" 表示完全同步

# 看本地 dev 是否在 origin 之上
git status -sb

# 看 PR 状态
gh pr list --author '@me' --repo apache/dolphinscheduler
```

---

## 危险动作清单

| 动作 | 风险 | 替代 |
|------|------|------|
| `git push upstream <anything>` | 直接推到 apache，没有写权限会报错；有写权限会污染上游 | 永远不要 push upstream |
| `git push --force origin dev` | 覆盖 fork 的 dev，丢失协作分支 | `--force-with-lease` |
| `git merge upstream/dev`（非 ff） | 本地 dev 出现 merge 提交，未来 PR 包含无关 commit | `--ff-only` |
| 直接在 `dev` 上写业务代码 | 后续无法 fast-forward 同步上游 | 切功能分支 |

---

## 相关文件

- `scripts/sync-upstream.sh` — 一键同步脚本
- `~/.gitconfig` — 全局 user.name / user.email（push 前确认是个人账号，不是公司账号）
