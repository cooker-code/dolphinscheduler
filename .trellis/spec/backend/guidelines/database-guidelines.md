# 数据库规范 — 后端

## ORM：MyBatis-Plus

**调用层级规则**：

```
master / service / api
    ↓ 调用
dao.repository          ← 正确入口
    ↓ 内部调用
dao.mapper              ← 禁止从 master/service/api 直接调用
```

从 `master`、`service`、`api` 直接调用 `XxxMapper` 是历史遗留做法，**禁止新增此类调用**。

## 三种数据库方言

MySQL、PostgreSQL、H2（测试用）三种方言均需支持。

Schema 初始化文件：
```
dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_mysql.sql
dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_postgresql.sql
dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_h2.sql
```

版本升级 DDL：`src/main/resources/sql/upgrade/<version>/`

**Mapper XML 必须方言中立**，或使用 `dolphinscheduler-dao-plugin` 里的方言抽象。  
新增方言分支前先 grep `<if test="databaseType == ...">` 了解现状，避免继续扩大该模式。

## 命名约定

- 表名前缀：`t_ds_`
- 字段映射：通过 `@TableField` 注解或 MyBatis-Plus camelCase ↔ snake_case 默认规则
- **实体字段改名是破坏性变更**：必须同时改 DB 列名和 Java 字段名，只改一侧会导致运行时映射错误

## 连接池

- 元数据 DB 使用 **HikariCP**，禁止切换为 Druid
- Druid 仅用于 `datasource-plugin` 内部，管理用户自定义数据源连接

## 事务

所有 service 层写操作方法**必须**加注解：
```java
@Transactional(rollbackFor = Exception.class)
```
缺少此注解的写方法几乎都是 bug。

## 枚举序列化

`dolphinscheduler-common` 中的工作流/任务状态枚举通过 MyBatis 类型处理器序列化进数据库。

**改名前必须全局 grep 枚举值** — 静默改名会损坏历史数据。

## Schema 文件打包

- `*.sql` 文件被排除在 jar 之外（减小体积）
- `dolphinscheduler-tools` 负责为升级 CLI 重新打包 SQL
- 禁止在应用启动时执行升级逻辑

## 常见错误

| 错误 | 说明 |
|------|------|
| 从 master/service 直接调 Mapper | 绕过 repository 抽象，禁止 |
| Mapper XML 写方言特定 SQL | 未验证 PostgreSQL/MySQL/H2 兼容性 |
| 只改枚举名未改 DB 序列化 | 历史数据静默损坏 |
| 写方法缺 `@Transactional` | service 层漏事务 |
| 把 Druid 加入元数据 DB 配置 | 核心连接池必须用 HikariCP |
