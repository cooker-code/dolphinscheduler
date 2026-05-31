# 实施计划：审计日志架构治理

> 与 `prd.md`、`design.md` 配套使用。检查项按顺序执行，每个 phase 结束有验证 gate。

## Phase 1 — 紧急止血（索引 + 隔离 + LEFT JOIN）

### 准备

- [ ] 通过 DBA 拿到生产 `t_ds_audit_log` 当前行数 / 表大小（决定 DDL 方式）
- [ ] 在测试环境构造 1000 万行 mock 数据（脚本：`tools/mock-audit-data.sh`）

### 实施

- [ ] **1.1 schema 变更脚本** `dolphinscheduler-dao/src/main/resources/sql/upgrade/<version>_ddl/`
  - 新增 `project_code` 列
  - 新增三个索引（`create_time` / `user_id` / `model_type+operation_type+create_time` / `project_code+create_time`）
  - 三种方言（mysql / postgresql / h2）都要写
- [ ] **1.2 backfill 脚本** `dolphinscheduler-tools` 子命令 `audit-backfill-project-code`
  - 按 model_type 分批更新（workflow、task、schedule 走 t_ds_workflow_definition 等业务表反查）
  - 输出进度日志，断点续跑
- [ ] **1.3 写入路径填充 project_code**
  - `BaseAuditOperator` 新增 `protected Long getProjectCodeFromContext(...)` 抽象方法
  - 19 个 OperatorImpl 各自实现（项目无关的返回 null）
- [ ] **1.4 Mapper INNER JOIN → LEFT JOIN**
  - `AuditLogMapper.xml` 改 LEFT JOIN + `COALESCE(user_name, '[deleted-user]')`
  - `userName` 过滤兼容 NULL 用户
- [ ] **1.5 Controller / Service 加 projectCode 参数与权限**
  - `AuditLogController#queryAuditLogListPaging` 新增 `@RequestParam Long projectCode`
  - 调用 `projectService.checkProjectAndAuth(loginUser, projectCode, ...)`
  - 新增 `AUDIT_LOG_VIEW` 权限点
- [ ] **1.6 pageSize 上限 + LIKE 前缀化**
  - `BaseController#checkPageParams` 增加上限校验（pageSize ≤ 1000，pageNo ≤ 10000）
  - Mapper 中 `LIKE concat('%', X, '%')` → `LIKE concat(X, '%')`
- [ ] **1.7 前端联调**：`dolphinscheduler-ui` 审计页传 `projectCode`，过滤说明改为"前缀匹配"

### Phase 1 验证 gate

- [ ] `EXPLAIN SELECT ... ORDER BY create_time DESC LIMIT 10` 显示 type=index、Extra 不含 filesort
- [ ] 1000 万 mock 数据下，查询 P95 < 500ms
- [ ] 越权测试：用户 A 用 B 项目的 `projectCode` 查询，返回 403
- [ ] 删除测试用户后，其历史审计仍可查到，user_name 显示 `[deleted-user]`
- [ ] `pageSize=99999` 返回 400 错误
- [ ] `mvn spotless:apply && mvn test -pl dolphinscheduler-api,dolphinscheduler-dao` 全过

## Phase 2 — 数据生命周期（清理 + 归档）

### 实施

- [ ] **2.1 配置项** `application.yaml` 新增 `audit.retention.*` 块（含 enabled / days / cron / batch-size）
- [ ] **2.2 AuditLogMapper 新增** `int deleteByCreateTimeBefore(Date threshold, int limit)` 方法
- [ ] **2.3 AuditPurgeJob**（在 dolphinscheduler-api 或 dolphinscheduler-service 模块）
  - `@Scheduled(cron = "${audit.retention.purge-cron}")`
  - 循环 `deleteByCreateTimeBefore` 直到 affected_rows < batch-size
  - 输出 metrics
- [ ] **2.4 归档导出 CLI** `dolphinscheduler-tools` 新增子命令 `audit-export`
  - 入参：`--start-date / --end-date / --output-path / --format=csv|json`
  - 流式分页拉取，避免 OOM
  - 支持本地 / S3（复用现有 `dolphinscheduler-storage-plugin`）
- [ ] **2.5 文档**：在 `dolphinscheduler-tools/README.md` 增加使用示例

### Phase 2 验证 gate

- [ ] 测试环境 mock 1000 万条数据（含 200 天前数据），跑 purge job 能在 30 分钟内清理完
- [ ] 清理过程中并发查询审计接口，无明显卡顿（验证 batch + 索引避免锁表）
- [ ] `audit.retention.days=0` 时清理 job 跳过执行
- [ ] 归档导出 100 万条到 S3，文件可用，无字段缺失

## Phase 3 — 写入异步化

### 实施

- [ ] **3.1 配置项** `audit.async.*`
- [ ] **3.2 AuditEventBuffer**（基于 `LinkedBlockingQueue`）
- [ ] **3.3 AuditWriteWorker** 单线程消费，批量 INSERT（用 MyBatis batch 或 jdbcTemplate）
- [ ] **3.4 队列满降级**
  - `fallback-on-full=sync`：直接同步写
  - `fallback-on-full=drop`：丢弃 + 告警
- [ ] **3.5 改造 AuditServiceImpl#addAudit**
  - 默认走 buffer.offer
  - `audit.async.enabled=false` 时回退到同步 insert（保留回滚能力）
- [ ] **3.6 优雅停机**：`@PreDestroy` flush 队列，最多等待 30s
- [ ] **3.7 metrics 接入**（Micrometer）

### Phase 3 验证 gate

- [ ] 集成测试：故意把 DB 切断 60s，业务接口仍正常返回，metric `audit.write.failed` 上升
- [ ] 压测：100 RPS 业务请求，开启异步前 P99 vs 开启后 P99 下降 ≥ 80%
- [ ] 优雅停机测试：发 SIGTERM，验证队列内数据全部写入 DB
- [ ] `audit.async.enabled=false` 配置切回，行为等同改造前

## Phase 4 — 收尾

- [ ] 把 7 维风险评估全文落档到 `notes/risk-assessment.md`
- [ ] 后续工作（master/worker 审计、表结构演进、外部审计存储）登记到 `notes/future.md`
- [ ] 编写 PR 描述（含每个 Phase 的回滚方法）
- [ ] `mvn clean install -DskipTests=false` 全量构建过
- [ ] code-review skill 跑一遍 + spotbugs 通过

## Rollback 节点

| 阶段 | 回滚方法 |
|------|---------|
| Phase 1 | DROP INDEX + Mapper 改回 INNER JOIN + 后端去掉 projectCode 校验 |
| Phase 2 | 配置 `audit.retention.days=0`；归档工具不影响主链路 |
| Phase 3 | 配置 `audit.async.enabled=false`，立刻回到同步路径 |

## 不在本任务实现

- AuditType 枚举/Operator 解耦
- master/worker 内部行为审计
- before/after 字段、IP/trace_id 字段
- 接入 ClickHouse/ES/Loki 等外部审计存储
