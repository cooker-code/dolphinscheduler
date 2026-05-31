# 请求拦截器规范 — `dolphinscheduler-ui/src/service/service.ts`

> 仅有的 axios 实例。所有请求/响应拦截、鉴权、错误兜底都集中在这里，禁止绕过。

---

## 强制规则

1. 业务代码一律 `import { axios } from '@/service/service'` 或调 `service/modules/*` 下的封装；不得直接 `import axios from 'axios'`。
2. 修改 request interceptor 时，必须在浏览器中实际触发一次登录 + 一次受保护接口验证，不能仅靠类型检查放行。
3. `userStore.sessionId` 持久化在 **localStorage**（pinia-plugin-persistedstate），与浏览器 cookie 无关。前端"是否登录"的判定来自 store，不是 cookie。

---

## request interceptor 的正确写法

axios 升级到 `^1.15.0` 之后，曾经写过的 `config.headers.set('sessionId', …)` 在本项目运行时会抛 `TypeError: config.headers.set is not a function`，原因是经过项目内的 `transformRequest` / `paramsSerializer` / `baseRequestConfig` 合并后，进入拦截器的 `config.headers` 不一定是 `AxiosHeaders` 实例，可能已退化为 plain object。仅依赖 TypeScript 类型推断不能反映这一运行时事实。

**唯一允许的写法**（对象解构赋值，对两种形态都安全）：

```ts
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const language = cookies.get('language')
  config.headers = {
    ...config.headers,
    sessionId: userStore.getSessionId,
    ...(language ? { language } : {})
  } as InternalAxiosRequestConfig['headers']

  return config
}, err)
```

**禁止的写法**：

```ts
// ❌ 运行时可能抛 TypeError，导致请求在前端死掉
config.headers.set('sessionId', userStore.getSessionId)
if (language) config.headers.set('language', language)
```

---

## 与 401 联动的故障链

错误的 request interceptor 不会变成 4xx/5xx 响应，而是在 axios 内部抛 JS 异常。带来的连锁反应：

1. `err(error)` 收到的是 `TypeError`，没有 `response.status`
2. `axiosError.response?.status === 401` 判定不成立
3. 不会 `setSessionId('')`、不会 `router.push('/login')`
4. 表现：页面卡住、控制台一堆 axios 报错、永远跳不到登录页

排查口径：当用户报告"清了 cookie 还是停在内页 / 登录页跳不过去"，先看浏览器控制台是不是有 axios 抛的 `TypeError`，再判断是不是又退回到 `.set()` 写法。

---

## 与后端 session 表的协同

后端清库（`t_ds_session` 被清空）后，前端如果还能从 localStorage 拿到 sessionId，会被 router 守卫放行进入内页 → 第一次受保护接口请求收到 401 → 走 axios response interceptor 的 401 分支 → `setSessionId('')` → `router.push('/login')`。

这条链路依赖 **request interceptor 不抛异常**。所以 request interceptor 的健壮性是整个未登录回退机制的前提。

---

## 紧急恢复

清浏览器侧残留登录态：

```js
localStorage.clear(); location.href = '/login'
```

---

## 相关文件

- `src/service/service.ts` — 唯一 axios 实例
- `src/store/user/user.ts` — `sessionId` 持久化（`persist: true`）
- `src/router/index.ts` — `beforeEach` 守卫，根据 `userStore.sessionId` 判定登录态
- `src/service/modules/login/index.ts`、`src/service/modules/logout/index.ts` — 登录/登出请求封装
