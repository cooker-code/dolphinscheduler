# 代码质量规范 — 前端

## 禁止模式

| 禁止行为 | 原因 |
|---------|------|
| 使用 Node 18/20 | 现代 OpenSSL 会破坏 Vite/webpack 构建 |
| 使用 Options API 新增组件 | 项目统一 Composition API |
| 硬编码面向用户的字符串 | 必须走 i18n |
| 直接使用 axios（不走 service） | 绕过请求/响应拦截器 |
| 修改 DAG 编辑器不做充分测试 | 最复杂视图，最容易回归 |
| 生产构建用 gzip 文件调试加载问题 | 服务端可能优先提供 `.gz` 文件 |

## 构建命令

```bash
# 进入前端目录
cd dolphinscheduler-ui

# 安装依赖（首次或 package.json 变更后）
pnpm install

# 开发服务器（:5173，代理 /dolphinscheduler → 后端 12345）
pnpm run dev

# 生产构建（含 vue-tsc 类型检查）
pnpm run build:prod

# ESLint 修复
pnpm run lint

# Prettier 格式化
pnpm run prettier
```

## 后端 URL 配置

开发环境后端地址在 `.env.development` 中配置：
```
VITE_APP_DEV_WEB_URL=http://localhost:12345
```

## 测试

**前端模块内部没有单元测试**（无 `*.spec.ts` / `*.test.ts`）。  
端到端覆盖来自 `dolphinscheduler-e2e`（Selenium + Docker）。

功能验证需要：
1. 启动后端 API 服务（端口 12345）
2. 运行 `pnpm run dev`
3. 在浏览器手动验证

## 生产打包说明

- `pnpm run build:prod` 的产物在 `dist/` 目录
- `dolphinscheduler-dist` 模块会将 `dist/` 打入发行 tarball 的 `ui/` 目录
- 修改前端后，如果要验证完整打包流程，需要先跑 `pnpm run build:prod`

## 常见错误

| 错误 | 排查方向 |
|------|---------|
| 构建失败（OpenSSL 相关） | 切换到 Node 16.x |
| 请求 401/504 但 token 存在 | 检查 sessionId header 是否正确注入 |
| 文字不显示/显示 key | 检查 `en_US` 和 `zh_CN` locale 文件是否都有对应条目 |
| DAG 功能异常 | AntV X6 版本兼容性，检查 `vite.config.ts` 中的别名配置 |
| 页面卡住、清 cookie 也跳不到 /login | 大概率是 axios request interceptor 抛 JS 异常（无 `response.status` → 401 兜底失效），先看浏览器 console |

---

## UI 问题排查强制顺序

任何"页面表现不对 / 行为不符合预期"的问题，必须按以下顺序，**禁止跳过第 1 步直接读源码**。

### 1. 先看浏览器运行时错误（必做）

**首选：Chrome DevTools MCP**（`mcp__chrome-devtools__*`）— 可以直接读 console messages、network、performance、DOM。

```
# 取最近的 console 报错
mcp__chrome-devtools__list_console_messages
# 取最近的请求与响应
mcp__chrome-devtools__list_network_requests
```

**次选：opencli browser**（复用本机已登录的 Chrome 会话，无需新开浏览器）：

```bash
opencli browser --session work state          # DOM 快照（结构化文本）
opencli browser --session work get text body  # 页面文本
opencli browser --session work screenshot     # 视觉截图
```

**兜底：** 让用户去 DevTools → Console / Network 截图。

### 2. 再读代码

带着具体的运行时报错关键字（异常类、栈帧、URL）回到代码定位。**不要从静态类型推理"应该会怎样"**：本项目里 axios `config.headers` 在拦截器入口的实际形态、`pinia-plugin-persistedstate` 的持久化时机、Vite HMR 是否真的更新了某段代码，这些都是运行时事实，类型系统看不到。

### 3. 改之前再核对一次

**用户提供"修复后/期望状态"的截图或代码片段时**：

- 第一动作是 `git diff` 截图内容与当前工作区的差异
- 默认相信用户提供的"修复版"，而不是反向论证它错
- 改完必须在浏览器里实际触发一次完整路径（登录 → 进入相关页面 → 触发操作），不能仅靠 `tsc` / `lint` 通过就报告完成

### 4. 改完必须实测，不能用类型检查冒充功能验证

`vue-tsc` / `eslint` 通过 ≠ 功能正常。前端没有单元测试，所有功能正确性都依赖浏览器实测。报告"已修复"前必须在浏览器里跑一次。
