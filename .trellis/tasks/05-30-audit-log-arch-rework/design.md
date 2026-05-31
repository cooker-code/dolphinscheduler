# 技术设计：审计日志架构治理

> 范围对应 `prd.md` 阶段一/二/三。阶段四仅在 `notes/future.md` 留档。

## 1. 边界与契约

### 1.1 改动模块

| 模块 | 改动类型 | 说明 |
|------|---------|------|
| `dolphinscheduler-dao` | schema + Mapper | 新增索引、新增 delete by 时间范围、JOIN 改 LEFT JOIN |
| `dolphinscheduler-api` | controller + service + aspect | 接口加 projectCode/权限、写入异步化 |
| `dolphinscheduler-common` | 配置项 + 权限点枚举 | 新增 `audit.*` 配置 namespace、`audit:view` 权限点 |
| `dolphinscheduler-tools` | 归档 CLI | 新增导出/清理工具 |

### 1.2 对外契约变化

| 接口 | 变化 | 兼容性 |
|------|-----|--------|
| `GET /projects/audit/audit-log-list` | 新增必填 `projectCode` 参数 | **破坏性**，需前端同步改 |
| `GET /projects/audit/audit-log-list` | `pageSize` 上限 1000，超过 400 | 破坏性，但默认值 10 不受影响 |
| `userName / modelName` 过滤 | 由 `LIKE '%x%'` 改为 `LIKE 'x%'` | 语义变化，需告知 |
| 配置 `audit.retention.days` | 新增，默认 180 | 兼容（默认行为为新增） |

## 2. 数据流

```
业务请求
   ↓
@OperatorLog 注解 + AOP 切面（不变）
   ↓
@AfterReturning 仍构造 AuditLog 对象
   ↓
[新] AuditEventBuffer.offer(auditLog)   ─────┐
   ↓ 立即返回                                 │
业务响应                                     │
                                             │
[新异步线程] AuditWriteWorker                ▼
   - 每 200ms 或满 500 条触发                批量
   - jdbcTemplate.batchUpdate INSERT  ◀─────┘
   - 失败重试 3 次 → metric + log
   - 队列满 → 降级 fallback 同步写 + 告警

[定时任务] AuditPurgeJob (Spring @Scheduled, cron=0 0 2 * * ?)
   - DELETE FROM t_ds_audit_log
       WHERE create_time < NOW() - INTERVAL ? DAY
       LIMIT 5000
   - 循环直到 affected_rows < 5000
   - 走 idx_audit_create_time，不锁全表
```

## 3. 关键决策

### 3.1 索引设计

```sql
-- A. 主索引：覆盖 ORDER BY 与时间范围过滤
CREATE INDEX idx_audit_log_create_time ON t_ds_audit_log (create_time);

-- B. JOIN 用户表的反向索引（user_id 已是 not null）
CREATE INDEX idx_audit_log_user_id ON t_ds_audit_log (user_id);

-- C. 多过滤条件复合索引（model_type + operation_type 通常一起出现）
CREATE INDEX idx_audit_log_model_op_time ON t_ds_audit_log (model_type, operation_type, create_time);

-- D. 清理 job 专用（与 A 重合，不重复创建）
```

**为什么不做 `(create_time DESC)` 显式 DESC**：MySQL 5.7 不支持降序索引；8.0 支持但兼容性差；正向索引 + ORDER BY DESC 由优化器反向扫描，效果相同。

**为什么不做 `model_name / user_name` 索引**：LIKE 改前缀后，`model_name LIKE 'x%'` 可走索引 C 的扩展或单列索引，但单列索引代价高、收益低；本期不加，由前缀匹配 + 复合索引覆盖大多数场景。

### 3.2 项目隔离

> ⚠️ **架构难点**：`t_ds_audit_log` 表当前**没有 project_code 字段**，无法直接过滤。

**两种方案对比：**

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **方案 A**：表加 `project_code` 字段 + 写入时填入 | 在 `BaseAuditOperator` / 各 OperatorImpl 提取项目 code 写入 | 查询直接过滤，性能最优 | schema 变更，需 backfill 历史数据；19 个 Operator 都要改 |
| **方案 B**：通过 `model_id` 反查 | 查询时按 model_type 反向 JOIN 业务表过滤 | 无需 schema 变更 | JOIN 复杂、性能差、跨 19 种 model 难统一 |

**决策**：选 **方案 A**。理由：
- 隔离是 P0 安全问题，长期看必须在表上落地
- backfill 可一次性脚本完成（执行时业务接受短暂"历史无项目归属"显示）
- Operator 改造由 BaseAuditOperator 收口，子类只覆盖 `getProjectCodeFromContext` 钩子，改动可控

**Schema 变更：**
```sql
ALTER TABLE t_ds_audit_log ADD COLUMN project_code BIGINT DEFAULT NULL COMMENT 'project code for tenant isolation';
CREATE INDEX idx_audit_log_project_time ON t_ds_audit_log (project_code, create_time);
```

**针对全局型操作**（如 User / Tenant / WorkerGroup CRUD，无项目归属）：
- `project_code = NULL`
- 查询时仅 admin 可见 `project_code IS NULL` 的记录（在 SQL 层加 `(project_code = ? OR (? AND project_code IS NULL))`）

### 3.3 权限点

新增枚举值 `AUDIT_LOG_VIEW`，加到 `RequiredPermission` 体系；admin 默认拥有，普通用户需通过项目角色继承。

### 3.4 异步队列实现选型

| 方案 | 评估 |
|------|------|
| 自实现 `LinkedBlockingQueue` + 单线程消费 | ✅ 选用：零依赖、可控、足够 |
| Disruptor | 性能过剩，引入复杂度 |
| Kafka / RabbitMQ | 引入外部依赖，超出本期范围 |

**配置：**
```yaml
audit:
  async:
    enabled: true
    queue-capacity: 10000
    batch-size: 500
    flush-interval-ms: 200
    fallback-on-full: sync   # sync | drop
  retention:
    days: 180
    purge-cron: "0 0 2 * * ?"
    purge-batch-size: 5000
```

### 3.5 LEFT JOIN 改造

```xml
<!-- 改造后 AuditLogMapper.xml 片段 -->
select log.*, COALESCE(u.user_name, '[deleted-user]') as user_name
from t_ds_audit_log log
left join t_ds_user u on log.user_id = u.id
where log.project_code = #{projectCode}
  <if test="startDate != null">and log.create_time &gt;= #{startDate}</if>
  <if test="endDate != null">and log.create_time &lt;= #{endDate}</if>
  ...
order by log.create_time desc
```

**`userName` 过滤**：因 LEFT JOIN 导致 `u.user_name` 可能为 null，过滤时需 `(u.user_name LIKE ? OR (u.user_name IS NULL AND ? = '[deleted-user]'))`。

## 4. 兼容性 & 回滚

### 4.1 升级路径

1. 执行 schema 变更脚本（在线 DDL，加索引 + 加列）
2. 部署新版本 API（异步队列默认开启）
3. 一次性脚本 backfill `project_code`（按 `model_type + model_id` 反查业务表）
4. 启用清理 job（首次执行前 DBA 确认归档完成）

### 4.2 回滚

- 索引：可随时 DROP，无业务影响
- 异步写入：配置 `audit.async.enabled=false` 立即回到同步路径
- LEFT JOIN：Mapper 改回 INNER JOIN（向后兼容）
- 项目隔离：暂时性放开权限校验（仅紧急回滚使用）
- 清理 job：`audit.retention.days=0` 关闭

### 4.3 灰度策略

- 异步写入按节点百分比灰度（通过配置中心动态开关）
- 清理 job 先在测试环境跑 30 天，确认无误后上线

## 5. 性能预期

| 场景 | 当前 | 阶段一后 | 阶段二后 | 阶段三后 |
|------|------|----------|----------|----------|
| 首页查询（无过滤，1000万行） | 5-10s | < 200ms | < 200ms | < 200ms |
| 时间范围 + model_type 过滤 | 3-5s | < 100ms | < 100ms | < 100ms |
| 业务接口 P99（含审计写入） | +10-50ms | +10-50ms | +10-50ms | < +1ms |
| 表大小（12 个月） | 30-60GB | 30-60GB | < 5GB | < 5GB |

## 6. 监控指标

| Metric | 类型 | 说明 |
|--------|------|------|
| `audit.write.success` | Counter | 成功写入条数 |
| `audit.write.failed` | Counter | 写入失败条数（重试后仍失败） |
| `audit.queue.size` | Gauge | 当前队列长度 |
| `audit.queue.full` | Counter | 队列满次数 |
| `audit.batch.duration` | Histogram | 单次批量写入耗时 |
| `audit.purge.deleted` | Counter | 清理 job 删除条数 |
| `audit.purge.duration` | Histogram | 清理 job 单次耗时 |

## 7. 测试策略

- **单测**：AuditWriteWorker 批量、降级、重试逻辑（Mockito）
- **集成**：AuditLogMapper 索引使用（`EXPLAIN` 断言）
- **性能**：1000 万条 mock 数据下分页查询基线
- **e2e**：DB 故障时业务接口仍正常响应（Testcontainers + 故意切断 DB 连接）
- **合规**：删除用户后审计仍可见验证（场景测试）

## 8. 待澄清问题（在 task.py start 前需要回答）

1. 线上当前 `t_ds_audit_log` 实际行数？决定索引方案是 INPLACE 还是切库迁移
2. 是否需要满足金融/SOX 类长留存要求？决定 `retention.days` 默认值
3. master/worker 内部行为审计是否需在本期带上？目前规划放后期
