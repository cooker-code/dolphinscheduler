# 审计日志架构治理：性能、隔离、生命周期

## 背景

线上反馈：随着 SQL 执行类操作累积，`t_ds_audit_log` 表查询越来越慢，部分时段已出现页面打不开。
经过对 `dolphinscheduler-api` / `dolphinscheduler-dao` 的全量代码审计，发现该子系统存在跨多个维度的架构问题，远不止"加索引"能解决。

## 现状问题清单（带证据）

### P0 — Critical

1. **表结构只有 PRIMARY KEY(id)，无任何辅助索引**
   - 证据：`dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_mysql.sql` 第 1206-1218 行
   - 影响：所有按 `create_time / model_type / operation_type / user_id` 过滤都全表扫描；`ORDER BY create_time DESC` 必触发外排序

2. **查询接口无项目/租户隔离、无权限点**
   - 证据：`AuditLogController#queryAuditLogListPaging` 无 `projectCode` 参数，无 `@RequiredPermission`，仅有 SESSION_USER 校验
   - 影响：任意登录用户可查全系统审计（含他人项目、管理员操作），违反最小权限原则

3. **INNER JOIN t_ds_user 导致已删除用户的历史审计不可见**
   - 证据：`AuditLogMapper.xml` `join t_ds_user u on log.user_id = u.id`
   - 影响：违反审计日志"不可变历史"语义，删除用户后其全部历史操作消失

4. **完全无数据生命周期管理**
   - 证据：`grep -r "purge|clean|retention|archive"` 命中 0；`AuditLogMapper` 无 delete 方法；升级脚本无任何归档处理
   - 影响：无界增长，按现状 12 个月后表达 30-60GB，备份/恢复/查询全面恶化

5. **同步阻塞写入审计**
   - 证据：`OperatorLogAspect.afterReturning` → `BaseAuditOperator` → `AuditServiceImpl#addAudit` → `auditLogMapper.insert`，整条链路在主请求线程执行
   - 影响：业务接口 P99 被审计写入抬高；高并发下 DB 写入压力直接传导到 API

### P1 — High

6. **modelName==null 时静默跳过审计**（`AuditServiceImpl#addAudit` 第 52-54 行）
   - 删除场景下 `getObjectNameFromIdentity` 在事务提交后查询，常拿到空串/null → 审计悄无声息丢失

7. **VARCHAR(100) 字段截断**（`description / detail / model_name`）
   - 复杂参数变更（如 SQL、JSON）超过 100 字直接被截

8. **缺少 before/after 变更对比**
   - 表无 `old_value / new_value`，仅有非结构化的 `detail`，无法支持回滚追溯

9. **缺少请求追踪信息**
   - 无 `request_ip / user_agent / trace_id / session_id`，安全溯源困难

10. **写入失败仅 log.error 吞掉**，无 metrics、无告警、无 DLQ

### P2 — Medium

11. **审计仅覆盖 API 层**：master/worker 内部调度自动行为（自动重试、failover、超时杀任务）完全无审计
12. **`latency` 字段语义模糊**：业务+审计耗时混在一起，排查困难
13. **`pageSize` 无上限**，可被构造 `pageSize=99999999` 拉爆 JVM
14. **LIKE '%xxx%' 双侧模糊**，无法用任何索引

## 目标（按优先级）

本治理任务采用**分阶段交付**，避免一次性大改造带来的回归风险：

### 阶段一（必交付，P0）— 紧急止血
- **A1** 加索引：`(create_time DESC)`、`(user_id)`、`(model_type, operation_type)`、必要的覆盖索引
- **A2** 接口加权限点 + 项目隔离：禁止跨项目越权查询
- **A3** `INNER JOIN` 改 `LEFT JOIN`，应用层显示 `[deleted-user]`
- **A4** `pageSize` 上限 1000、`pageNo` 上限 10000、LIKE 改前缀匹配（接受语义变化）

### 阶段二（必交付，P0）— 数据生命周期
- **B1** 新增配置项 `audit.retention.days`（默认 180），新增清理 job
- **B2** 提供归档导出工具（手动触发，导出到对象存储），保留合规需要的长尾数据

### 阶段三（应交付，P1）— 写入路径异步化
- **C1** 引入内存有界队列 + 异步消费线程批量 INSERT
- **C2** 队列满时降级为同步写 + 告警，避免数据静默丢失
- **C3** 写入失败 metrics + 告警接入

### 阶段四（暂不在本次范围）— 表结构演进、master/worker 审计、外部审计存储
- 列入 `notes/future.md`，后续单独立项

## 验收标准

### 阶段一
- [ ] `t_ds_audit_log` 上至少存在 3 个有效索引；`EXPLAIN` 验证：按 `create_time + model_type` 查询走索引、无 filesort
- [ ] 1000 万级 mock 数据下，首页查询 < 200ms（无过滤）、< 500ms（带 model_type/时间范围过滤）
- [ ] 审计查询接口必须传 `projectCode`，且经 `projectService.checkProjectAndAuth` 校验
- [ ] 审计查询接口受 `@RequiredPermission` 控制（新增 `audit:view` 权限点）
- [ ] 用户被删除后，其历史审计仍可查询，user_name 显示 `[deleted-user]`
- [ ] `pageSize > 1000` 直接 400 拒绝

### 阶段二
- [ ] 提供 `application.yaml` 配置 `audit.retention.days`，可关闭（值为 0 或负数 = 不清理）
- [ ] 清理 job 通过 Spring `@Scheduled` 实现，默认每天凌晨 02:00 执行
- [ ] 清理 job 单批 ≤ 5000 行，全程 `WHERE create_time < ?` 走索引，不锁全表
- [ ] 清理 job 输出 metrics：`audit.purge.deleted_count`、`audit.purge.duration_ms`
- [ ] 提供归档导出 CLI（`dolphinscheduler-tools` 或 `dolphinscheduler-api` 子命令），导出为 CSV/JSON 到本地或 S3

### 阶段三
- [ ] 同步写入路径改为投递到本地有界队列（默认 capacity=10000）
- [ ] 异步线程每 200ms 或每 500 条触发批量 INSERT
- [ ] 队列满时：降级同步写 + 上报 `audit.queue.full` 告警 metric
- [ ] 异步写入失败：重试 3 次 + 上报 `audit.write.failed` metric，超过阈值触发告警
- [ ] 增加 e2e 测试：业务接口在审计 DB 故障时仍能正常返回（仅 metric 报警，不影响主请求）

## 非目标

- 本任务**不重构** AuditType 枚举与 Operator 实现类的耦合（19 个 impl）
- 本任务**不引入**外部审计存储（ClickHouse/ES/Loki）
- 本任务**不补全** master/worker 内部自动操作的审计（视为后续独立 task）
- 本任务**不改** AuditLog 表的字段结构（VARCHAR 长度、新增 IP/trace_id 等留给后续）

## 风险与权衡

- **加索引会锁表**：百万级以下表，MySQL 5.7+ 用 `ALGORITHM=INPLACE, LOCK=NONE` 在线 DDL；千万级以上需走灰度库 + 主从切换或低峰时段
- **LIKE 改前缀匹配是语义破坏**：会失去"模糊搜索包含字符串"的能力，需在 PRD 明确告知前端/产品；保留必要时回退方案
- **异步队列丢数据风险**：JVM 异常退出时队列内未持久化数据丢失；本次接受该风险（审计非强一致），后续再考虑 WAL
- **清理策略一刀切**：直接按时间删除可能违反金融/合规留存要求；通过 `audit.retention.days=0` 提供关闭开关，并提供归档工具兜底

## 参考

- 七维风险评估全文：见本任务 `notes/risk-assessment.md`（待落档）
- 上游 issue/讨论：暂无（属于内部治理项）
