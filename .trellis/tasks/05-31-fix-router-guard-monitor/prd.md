# 修复前端路由守卫与监控页面节点不显示问题

## Background

本地开发环境 baseline 测试发现两个前端 bug：

1. **路由守卫缺失**：`router/index.ts` 的 `beforeEach` 不检查 sessionId，未登录用户可直接访问受保护页面，触发 API 401 后 err handler 跳回 `/login`，但 Pinia persist 初始化竞态导致页面状态丢失，造成循环。
2. **监控页面节点不显示**：`use-worker.ts` / `use-master.ts` 在 `onMounted` 只拉一次数据，没有轮询。如果页面首次挂载时节点尚未注册或 sessionId 丢失，数据为空后不会重新请求，显示"Worker/Master 节点不存在"。

## Requirements

### R1 — 路由守卫

- 访问非 `/login` 页面时，若 `userStore.sessionId` 为空，强制跳转 `/login`
- 已有 sessionId 访问 `/login` 时，跳转 `/home`
- 保留原有管理员权限校验逻辑（`ADMIN_USER` 判断）
- 不修改登录流程本身

### R2 — 监控页面数据刷新

- Worker 页（`use-worker.ts`）和 Master 页（`use-master.ts`）加入定时轮询，每 **10 秒**自动刷新节点数据
- 组件卸载时清除定时器，防止内存泄漏
- 不改变组件对外暴露的接口（`variables`、`getTableWorker` / `getTableMaster`）

## Acceptance Criteria

- [ ] 未登录时访问 `http://localhost:5173/home` 自动跳转到 `/login`
- [ ] 登录成功后访问 `http://localhost:5173/login` 自动跳转到 `/home`
- [ ] 已登录状态访问监控中心 Worker/Master 页面，10 秒内自动刷新并显示节点
- [ ] 切换离开监控页面后控制台无定时器残留报错
- [ ] 所有原有页面导航和权限逻辑不受影响

## Scope

- `dolphinscheduler-ui/src/router/index.ts`
- `dolphinscheduler-ui/src/views/monitor/servers/worker/use-worker.ts`
- `dolphinscheduler-ui/src/views/monitor/servers/master/use-master.ts`
