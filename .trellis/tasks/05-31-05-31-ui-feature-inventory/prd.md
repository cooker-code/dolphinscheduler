# UI 功能全景清单与回测计划

## 背景

项目即将进行整体重构。重构前需要：
1. 梳理清楚当前 UI 提供的所有用户功能（路由粒度 + 操作粒度）
2. 梳理所有可复用 UI 组件
3. 输出结构化的回测 checklist，重构后按清单逐项验证

## 产出物

### 1. 功能清单（feature-inventory.md）

按导航模块分组，每条记录：
- 路由路径
- 功能名称（中文）
- 支持的操作（查看 / 创建 / 编辑 / 删除 / 执行 / 配置等）
- 依赖数据（需要哪些前置数据才能进入该页面）
- 入口组件文件路径

### 2. 可复用组件清单（components-inventory.md）

扫描 `dolphinscheduler-ui/src/components/` 及 views 中抽取的共用组件，每条记录：
- 组件名称与文件路径
- 用途描述
- 被哪些页面引用（主要引用方）
- 对外 Props / Events 摘要

### 3. 回测 Checklist（retest-checklist.md）

每个功能点一条 checklist 项：
```
- [ ] <功能名> — <验收标准（一句话，可操作）>
```
分组与功能清单一致，方便重构后逐模块回测。

## 范围

- 仅限 `dolphinscheduler-ui/` 前端代码
- 不含后端 API 接口清单
- 不含性能、兼容性测试

## Acceptance Criteria

- [ ] feature-inventory.md 覆盖 router 中所有已启用路由，无遗漏
- [ ] components-inventory.md 列出 src/components/ 下所有组件 + views 中被 ≥2 个页面引用的共用组件
- [ ] retest-checklist.md 每条有明确可操作的验收标准，不是模糊描述
