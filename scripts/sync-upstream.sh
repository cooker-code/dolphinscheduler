#!/usr/bin/env bash
# 把 apache/dolphinscheduler 上游的 dev 同步到本地 + 你的 fork (origin)
#
# 前置：
#   origin   -> https://github.com/cooker-code/dolphinscheduler.git  (你的 fork)
#   upstream -> https://github.com/apache/dolphinscheduler.git
#
# 用法：
#   ./scripts/sync-upstream.sh           # 默认 sync dev 分支
#   ./scripts/sync-upstream.sh 3.4.2-release  # sync 指定分支（本地 + fork 都会建好）
#
# 行为：
#   1. 校验工作区干净（避免覆盖未提交修改）
#   2. fetch upstream
#   3. 切到目标分支（不存在就建一个跟踪 origin 的）
#   4. 用 fast-forward 把 upstream/<branch> 合到本地（拒绝 merge，避免污染 PR）
#   5. push 到 origin（你的 fork）

set -euo pipefail

BRANCH="${1:-dev}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $1"; }
info() { echo -e "${YELLOW}[→]${NC} $1"; }
fail() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

# --- 前置检查 ---
git rev-parse --git-dir >/dev/null 2>&1 || fail "不在 git 仓库里"

git remote get-url upstream >/dev/null 2>&1 || \
  fail "缺少 upstream remote。请运行：git remote add upstream https://github.com/apache/dolphinscheduler.git"

ORIGIN_URL=$(git remote get-url origin)
case "$ORIGIN_URL" in
  *cooker-code/dolphinscheduler*) ;;
  *) fail "origin 不是你的 fork (当前: $ORIGIN_URL)，拒绝 push" ;;
esac

if ! git diff-index --quiet HEAD --; then
  fail "工作区有未提交改动，请先 commit 或 stash"
fi

# --- 同步 ---
info "fetch upstream..."
git fetch upstream --prune --tags

info "fetch origin..."
git fetch origin --prune

# 本地分支不存在则建一个跟踪 origin
if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
    info "本地无 $BRANCH，从 origin/$BRANCH 创建..."
    git checkout -b "$BRANCH" "origin/$BRANCH"
  else
    info "本地与 origin 都无 $BRANCH，从 upstream/$BRANCH 创建..."
    git checkout -b "$BRANCH" "upstream/$BRANCH"
  fi
else
  git checkout "$BRANCH"
fi

# --- fast-forward 合并 ---
info "fast-forward 合并 upstream/$BRANCH..."
if ! git merge --ff-only "upstream/$BRANCH"; then
  fail "无法 fast-forward。本地分支与 upstream 已分叉，请手动检查（git log --oneline --graph $BRANCH upstream/$BRANCH）"
fi

# --- push 到 fork ---
info "push 到 origin/$BRANCH..."
git push origin "$BRANCH"

log "同步完成：local $BRANCH = origin/$BRANCH = upstream/$BRANCH = $(git rev-parse --short HEAD)"
