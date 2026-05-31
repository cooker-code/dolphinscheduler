# 修复前端登录请求头注入异常

## Goal

修复 DolphinScheduler UI 登录页点击“登录”后没有有效响应的问题。用户在浏览器中反馈：`http://localhost:5173/login` 页面填写默认账号密码后点击“登录”，页面无跳转、无明显反馈，需要查看 DevTools 错误信息并修复。

## Requirements

- 登录页初始化请求和登录请求不能因为请求头注入逻辑抛出前端运行时异常。
- `src/service/service.ts` 中的 axios 请求拦截器仍需为所有请求注入 `sessionId` header。
- 如果存在 `language` cookie，请求拦截器仍需注入 `language` header。
- 修复范围应限定在前端请求封装，不改登录表单、路由守卫、后端接口或无关 UI。
- 修复需要兼容当前前端运行时里 `config.headers` 可能不是带 `.set()` 方法对象的情况。

## Acceptance Criteria

- [x] 刷新 `http://localhost:5173/login` 后，控制台不再新增 `TypeError: config.headers.set is not a function`。
- [x] 点击“登录”时，登录请求不会被请求拦截器的 headers 写入逻辑中断。
- [x] `sessionId` 和 `language` header 的注入语义保持不变。
- [x] `pnpm exec eslint src/service/service.ts` 通过。
- [x] `pnpm exec prettier --check src/service/service.ts` 通过。

## Notes

- 复现环境：Codex in-app browser，当前 URL 为 `http://localhost:5173/login`。
- 原始错误：`TypeError: config.headers.set is not a function`，堆栈指向 `dolphinscheduler-ui/src/service/service.ts` 的请求拦截器。
- 触发链路：登录页 mounted 阶段会调用 `ssoLoginUrl()`、`getOauth2Provider()`、`getOidcProviders()`；点击登录后会调用 `login()`。这些请求都经过同一个 axios wrapper，因此拦截器抛错会让页面表现为“点击后没有反应”。
- 已尝试但放弃的方案：使用 `AxiosHeaders.from(config.headers)` 统一包装 headers。该方案在当前 Vite 浏览器运行时中表现为 `AxiosHeaders` 导出不可用，出现 `Cannot read properties of undefined (reading 'from')`，因此改为普通对象合并写法。
- 实际改动：`dolphinscheduler-ui/src/service/service.ts` 中将 `config.headers.set(...)` 改为对象合并，保留 `sessionId` 与可选 `language`。
- 验证记录：`pnpm exec eslint src/service/service.ts` 通过；`pnpm exec prettier --check src/service/service.ts` 通过；浏览器刷新后未再新增 headers 注入相关错误。
- 全量 `pnpm exec vue-tsc --noEmit` 在当前工作区失败，主要是既有 TSX/JSX 类型问题；当前 Node 为 `v22.14.0`，也不符合项目规范要求的 Node 16。该失败未指向本次修改的 `service.ts`。
- GitNexus：对 `Const:dolphinscheduler-ui/src/service/service.ts:service` 的 impact 结果为 LOW；`detect-changes` 当前工作区包含大量既有未提交变更，因此只作为低风险参考。
