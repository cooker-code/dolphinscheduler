# 目录结构 — 前端

## 项目根目录

```
dolphinscheduler-ui/
├── src/
│   ├── assets/          ← 静态资源（图片、字体）
│   ├── components/      ← 可复用 UI 组件（表单控件、数据展示、DAG 画布组件）
│   ├── layouts/         ← 应用 shell / 页面框架
│   ├── locales/         ← i18n 翻译文件（en_US、zh_CN）
│   ├── router/          ← Vue Router 配置，每个顶层功能一个文件
│   ├── service/         ← axios 实例 + 各后端资源接口封装（一个资源一个文件）
│   ├── store/           ← Pinia stores
│   ├── utils/           ← 工具函数
│   └── views/           ← 页面组件
├── .env.development     ← 开发环境配置（后端 URL 等）
├── vite.config.ts       ← Vite 配置（含生产环境 gzip 压缩）
└── package.json
```

## views/ 路由结构

```
views/
├── home/                ← 首页
├── projects/            ← 项目管理（工作流定义、工作流实例、任务实例）
│   └── workflow/
│       └── components/
│           └── dag/     ← ⚠️ DAG 编辑器（最复杂视图，谨慎修改）
├── datasource/          ← 数据源管理
├── monitor/             ← 监控（master/worker 状态）
├── resource/            ← 资源中心
├── security/            ← 安全管理（用户、租户、告警等）
├── login/               ← 登录页
├── profile/             ← 个人信息
└── ui-setting/          ← UI 设置
```

## service/ 文件约定

每个文件对应一组后端资源，文件名即资源域名，例如：
- `service/modules/projects.ts` — 项目相关 API
- `service/modules/datasource.ts` — 数据源相关 API
- `service/service.ts` — **唯一** axios 实例，禁止在其他地方创建新实例

## store/ Pinia stores

- `store/user` — 当前用户信息、权限
- `store/project` — 当前项目上下文
- `store/locales` — 语言设置
- `store/theme` — 主题
- `store/timezone` — 时区
- `store/route` — 路由状态
- `store/ui-setting` — UI 配置
- `store/file` — 资源文件

## locales/ 结构

```
locales/
├── en_US/   ← 英文翻译
└── zh_CN/   ← 中文翻译
```

**新增任何面向用户的文字，必须同时添加两份 locale。**
