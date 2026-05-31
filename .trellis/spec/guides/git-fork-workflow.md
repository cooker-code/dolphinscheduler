# Git Fork 协作流程

> 本仓库已切换为 fork 模式：`origin` = 你的 fork (`cooker-code/dolphinscheduler`)，`upstream` = apache 官方。
>
> 三类内容、三类分支、各自的提交目的地都有明确归属。**不要把它们混在一起**。

---

## 一次性配置（已完成，仅作记录）

```bash
git remote set-url origin   https://github.com/cooker-code/dolphinscheduler.git
git remote add     upstream https://github.com/apache/dolphinscheduler.git
git branch --set-upstream-to=origin/dev dev
```

---

## 分支结构

```
本地 / fork（cooker-code）
├── dev          ← 永远 = apache/dev 镜像，只用来同步上游，不直接写代码
├── my-dev       ← 你的常驻个人分支，含个人配置 + 本地实验 + 临时改动
└── feat/<xxx>   ← 从 dev 切出，实现一个独立功能后给 apache 提 PR
```

| 分支 | 内容 | push 目的地 | 是否 PR 给 apache |
|------|------|------|------|
| `dev` | apache/dev 镜像 | `origin/dev`（保持与 upstream 同步） | 不直接 PR |
| `my-dev` | 个人配置（`.trellis`/`.claude`/`.codex`/`docker-compose-dev*.yml`/`scripts/`/`CLAUDE.md` 等）+ 本地化 yaml + 你的 UI 改动 | `origin/my-dev`（你的 fork 私有分支） | 永不 PR |
| `feat/xxx` | 一个干净的功能 / 一个 bug 修复，**只含与该功能相关的 commit** | `origin/feat/xxx` | PR 到 `apache:dev` |

---

## 内容分类（决定 commit 去哪条分支）

| 类别 | 例子 | 去向 |
|------|------|------|
| **个人开发环境配置** | `CLAUDE.md`、`AGENTS.md`、`.trellis/`、`scripts/`、`docker-compose-dev*.yml`、`start-*.sh`、`.codex/`、`.claude/` | `my-dev`，永不 PR |
| **本地实验/小修小补** | yaml 数据源端口、CPU/内存阈值、本机 dev URL | `my-dev`，永不 PR |
| **真正想贡献给社区的功能/修复** | 修 bug、加新特性 | 从 `dev` 切 `feat/xxx`，PR 到 apache |

---

## 日常工作流

### 1. 同步上游到 dev（建议每天开工时跑一次）

```bash
git checkout my-dev          # 先回到你的常驻分支（避免 stash 麻烦）
git stash -u                 # 如有未提交改动
./scripts/sync-upstream.sh   # 自动 fetch upstream → fast-forward dev → push origin/dev
```

`sync-upstream.sh` 脚本特性：
- 工作区脏会拒绝执行
- `origin` 不是你的 fork 会拒绝执行
- 仅接受 fast-forward 合并，避免 dev 与 upstream 分叉

### 2. 把上游变更带进 my-dev

`dev` 同步完后，你的 `my-dev` 还停在旧 base 上。两种合入方式：

```bash
git checkout my-dev
git merge dev                # 推荐：保留 my-dev 历史，产生一个 merge commit
# 或
git rebase dev               # 线性历史，但会改写 my-dev 的 commit hash
git push origin my-dev       # rebase 后必须 --force-with-lease
```

**推荐 `merge`**，因为 my-dev 是私有分支没人协作，merge commit 可读性更好；`rebase` 适合在 PR 前清理 `feat/xxx` 历史。

### 3. 日常开发：在 my-dev 上写代码、commit、push

```bash
git checkout my-dev
# ...编辑文件...
git add <files>
git commit -m "..."
git push origin my-dev
```

注意 commit 信息按"内容分类"加前缀：

- 个人配置：`chore(personal): ...`
- 本地化 yaml：`chore(local): ...`
- 真正功能/修复：`fix(ui): ...` / `feat(api): ...`（这种以后要 cherry-pick 出去）

### 4. 想给 apache 提 PR：从 dev 切干净分支

```bash
git checkout dev
git checkout -b feat/your-feature
# 用 cherry-pick 把 my-dev 上对应的 commit 摘过来（commit 越独立越好挑）
git cherry-pick <commit-sha-1> <commit-sha-2>
# 或者重新写一份干净的实现
git push -u origin feat/your-feature
gh pr create --repo apache/dolphinscheduler \
  --base dev \
  --head cooker-code:feat/your-feature \
  --title "..." --body "..."
```

**关键**：`feat/your-feature` 上**不能**有 `chore(personal)` / `chore(local)` 这种 commit，否则 PR 会污染。

提 PR 之后，my-dev 不需要做什么，等 PR 合并进 apache/dev 后下一次 `sync-upstream.sh` 就会自然把它带回来。

---

## 同步上游

### 自动方式（推荐）

```bash
./scripts/sync-upstream.sh        # dev
./scripts/sync-upstream.sh dev    # 同上
./scripts/sync-upstream.sh 3.4.2-release  # 同步指定 release 分支
```

### 手动方式（脚本不工作时的兜底）

```bash
git fetch upstream --prune --tags
git checkout dev
git merge --ff-only upstream/dev    # 失败说明 dev 已分叉
git push origin dev
```

### dev 已分叉怎么办

如果 `git merge --ff-only` 失败，说明你违规在 dev 上做了提交。修复：

```bash
# 假设违规 commit 是 a1b2c3d
git checkout -b feat/rescue-from-dev dev    # 把违规 commit 救出
git checkout dev
git reset --hard upstream/dev               # dev 强制贴回 upstream
git push origin dev --force-with-lease
```

---

## 危险动作清单

| 动作 | 风险 | 替代 |
|------|------|------|
| `git push upstream <anything>` | 直接推到 apache，没有写权限会报错；有写权限会污染上游 | 永远不要 push upstream |
| `git push --force origin dev` | 覆盖 fork 的 dev，和 apache 失去同步 | 改用 `--force-with-lease`，且只在 dev 已分叉时使用 |
| `git merge upstream/dev`（非 ff，到 dev） | dev 出现 merge commit，未来无法 fast-forward | `--ff-only`；分叉时按上面"已分叉"流程修 |
| 在 `dev` 上直接 commit 业务代码 | dev 与 upstream 分叉，sync 会失败 | 用 `my-dev` 或 `feat/xxx` |
| `feat/xxx` 上掺 `chore(personal)` commit | PR 包含个人配置，会被 reviewer 拒 | cherry-pick 时只挑业务相关 commit |
| 改 `application.yaml` 后 commit 到 `feat/xxx` | yaml 本地化（端口/阈值）不该 PR | 仅 commit 到 my-dev |

---

## 常见检查命令

```bash
# 看 fork 与 upstream 的差异
git rev-list --left-right --count origin/dev...upstream/dev
# 输出 "0	0" 表示完全同步

# 看 my-dev 与 dev 的差异（你在本地"叠加"了多少私有改动）
git rev-list --count dev..my-dev      # my-dev 多出多少 commit
git diff --stat dev..my-dev           # 文件级差异摘要

# 看 PR 状态
gh pr list --author '@me' --repo apache/dolphinscheduler
```

---

## 拉取 upstream 的 release 分支到 fork

fork 当前**只保留 `dev` 和 `my-dev`**，所有 release 分支都已删除。如果某个 release 分支需要本地工作：

```bash
./scripts/sync-upstream.sh 3.4.2-release
# 或手动
git fetch upstream --prune
git checkout -b 3.4.2-release upstream/3.4.2-release
git push -u origin 3.4.2-release
```

---

## 相关文件

- `scripts/sync-upstream.sh` — 一键同步脚本
- `~/.gitconfig` / repo `.git/config` — 确认 user.email 是个人邮箱（push 前必检）
