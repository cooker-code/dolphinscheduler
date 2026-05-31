# Hook / Composable 规范 — 前端

## 数据请求规范

所有 HTTP 请求必须通过 `src/service/service.ts` 中的 axios 实例：

```typescript
// ✅ 正确：通过 service 模块调用
import { queryProjectList } from '@/service/modules/projects'

// ❌ 禁止：在组件内直接使用 axios
import axios from 'axios'
axios.get('/dolphinscheduler/projects')
```

## 响应拦截约定

axios 响应拦截器会自动处理：
- 解包 `{ code, msg, data }` 结构
- `code != 0` 时抛出错误
- `401 / 504` 时跳转到 `/login`

**调用方不需要再次检查 code 字段**，直接使用返回的 `data` 即可。

## 请求头注入

请求拦截器自动注入：
- `sessionId` header（来自 Pinia user store）
- `language` cookie

**不要在业务代码中手动设置这两个字段。**

## 注意：无 OpenAPI 自动生成

前端没有根据后端 OpenAPI 自动生成 SDK。  
后端控制器签名变更后，前端 `service/` 目录中的 TypeScript 包装不会自动更新，**需要人工同步**。  
这是已知的漂移风险：后端改了参数名/类型，前端会在运行时出现 4xx/5xx，不会有编译期报错。

## Swagger / OpenAPI 契约约束

本地 API 服务启动后，前端接口契约以 Swagger UI 为第一核对来源：

- Swagger UI: `http://localhost:12345/dolphinscheduler/swagger-ui/index.html`
- 当前主定义：`v1`
- OpenAPI JSON: `http://localhost:12345/dolphinscheduler/v3/api-docs/v1`

新增或修改 `src/service/modules/*` 前必须核对：

- request path 是否包含 `/dolphinscheduler` context path 之后的真实接口路径
- HTTP method 是否与 Swagger 一致
- 参数位置：path、query、form、body 不得混用
- request body 字段名、枚举值、默认值是否与 Swagger / DTO 一致
- response 的 `data` shape 是否与 Swagger / Controller 返回一致
- 是否需要登录态、`sessionId` header、项目权限或管理员权限

约束：

- 不得根据静态原型、页面字段名或浏览器 mock 数据猜 API payload
- 不得在组件中临时拼 URL；所有接口必须落在 `src/service/modules/*`
- 不得为同一个后端接口创建多份 wrapper；若已有 wrapper，扩展类型或参数即可
- 前端 request / response 类型必须显式定义，禁止在多个调用点重复 `as any`
- 如果 Swagger UI 在终端不可访问，但 in-app browser 可以打开，不要误判 API 不存在；以浏览器页面和后端 Controller 交叉确认
- 如果 Swagger UI 不可用，退回读取 `dolphinscheduler-api/src/main/java/.../controller/` 的 Controller 方法签名、DTO 和 `@Operation` / `@Parameter` 注解，不允许凭原型实现接口

后端接口变更时，同步检查对应前端文件：

1. `src/service/modules/<resource>.ts`
2. 调用该接口的 composable / store / view
3. 相关 TypeScript 类型
4. 错误态、空态、权限态展示
