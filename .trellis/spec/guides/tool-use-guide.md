# Tool Use Guide

> **Purpose**: 防止"工具选错"导致代码被反向破坏。每条规则都来自实际踩坑。

---

## 编辑文件：选对工具的优先级

| 场景 | 推荐 | 禁止 |
|------|------|------|
| 修改已存在文件 | `Edit`（精确替换） | `sed -i` / `perl -pe` 处理结构敏感文件 |
| 修改单行简单文本（路径、URL、版本号） | `Edit` 优先；也可 `sed -i` | — |
| 创建新文件 / 完整重写 | `Write` | `cat <<EOF` |
| 跨多行的格式调整（yaml / json / xml / 多行字符串） | **必须** `Read` + `Edit` | `perl -pe` / `sed -i` 跨行替换 |
| 大量重复替换（同一字符串） | `Edit` 加 `replace_all: true` | `find -exec sed` |

### 为什么禁止 `perl -pe` / `sed -i` 处理多行结构

`-pe` 一次处理一行，正则**跨不到下一行**。但 yaml 这种语法里：

```yaml
profiles:
  active: postgresql
banner:
  charset: UTF-8
```

如果你想把 `active: mysql` 改回 `active: postgresql`，写
`s/active: mysql/active: postgresql/` 看似没问题；但如果脑补成
`s/active:\s*mysql\s*$/active: postgresql/` 多打一个 `\s*`，**或正则末尾少了 `$` 时**，
能匹配到的范围就跨过换行，吃掉下一行的开头：

实际事故（2026-05-31 在本仓库发生）：

```
原：active: postgresql\n  banner:\n    charset: UTF-8
误：active: postgresql  banner:    charset: UTF-8   ← 两次跨行替换叠加，yaml 直接坏掉
```

**根因不是正则写错，而是工具选错。** `Read` + `Edit` 看到的是完整文本，能精确知道边界；
`perl -pe` 工作在"行流"上，看不到结构。

### 正确做法

```python
# 1. Read 文件相关段落
Read(file_path, offset=33, limit=15)

# 2. Edit 精确替换（old_string 必须包含足够上下文确保唯一）
Edit(file_path,
     old_string='  profiles:\n    active: mysql\n  banner:',
     new_string='  profiles:\n    active: postgresql\n  banner:')
```

---

## 跨多行匹配的兜底方案

如果一定要用脚本（比如批量处理几十个文件），用 Python 而不是 perl/sed：

```python
import pathlib, re
for f in pathlib.Path('.').rglob('application.yaml'):
    text = f.read_text()
    text = re.sub(r'active:\s*mysql', 'active: postgresql', text)
    f.write_text(text)
```

Python 的 `re.sub` 默认整个文本一起处理，行边界由你显式控制。

---

## Bash 命令：哪些场景用哪个

| 任务 | 推荐 | 注意 |
|------|------|------|
| 看文件内容 | `Read` | 不要用 `cat` / `head` / `tail` |
| 列目录 | `Bash: ls -la <path>` | 加 `-la` 看权限和隐藏文件 |
| 找文件 | `Bash: find . -name '...'` | 不要 `find /`，会扫整个磁盘 |
| 找代码 | `Bash: grep -rn '...' <path>` | 大仓库优先指定子目录 |
| 删除文件 | `rm` | **删之前用 ls 确认路径**，特别是带通配符时 |
| 批量重命名 | `git mv` | 不要 `mv`，不会更新 git 索引 |

---

## Git 命令：高风险动作清单

参见 [`git-fork-workflow.md`](./git-fork-workflow.md) "危险动作清单"。补充几条：

| 动作 | 风险 | 替代 |
|------|------|------|
| `git checkout HEAD -- <file>` | 丢失工作区改动（**已发生过：把 codex 修复的 service.ts 还原回错误版本**） | 改之前先看 `git diff HEAD -- <file>`；用户已经给出"修复后版本"时不要反向回退 |
| `git restore --staged --worktree <path>` | 同时清掉暂存和工作区，**不可恢复** | 想保留工作区改动用 `git restore --staged <path>`（不带 `--worktree`） |
| `git clean -fd` | 删掉所有 untracked 文件 | 改用 `git clean -nd` 先 dry-run 看清楚 |

---

## 修改前必做的"看一眼"

| 即将做的事 | 做之前必须运行 |
|------|------|
| 改文件 | `Read` 该段落、`git diff HEAD -- <file>` 看现状 |
| 删文件 | `ls <path>` 确认；如果是 `git rm`，先 `git status -s` 看会影响什么 |
| commit | `git status -s` + `git diff --cached` |
| push | `git log <branch> -5 --oneline` 确认 commit 是你预期的 |
| force push | **暂停**：`git rev-list --left-right --count <branch>...origin/<branch>` 看你要覆盖什么 |

---

## 用户给出"修复后状态"时的纪律

当用户提供截图、代码片段、或明确告诉你"应该是这样"时：

1. **第一动作是 `git diff` 截图内容与当前工作区**
2. 默认相信用户给的版本，不要反向论证它错
3. 如果你认为用户给的版本有问题，**先问清楚再动手**，不要自作主张回退
4. 实际事故：用户截图里红色是"原错误版本"、绿色是"codex 修复版本"，但被反向理解成"codex 改坏了"，导致 `git checkout HEAD --` 把修复弄丢

**总结**：用户提供的不是"待你审查的代码"，是"已经验证过的事实"。审查是分内事，反向回退是越权。
