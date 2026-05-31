-- =====================================================================
-- DolphinScheduler 演示数据初始化脚本（PostgreSQL）
-- 包含：租户、用户、队列、项目、数据源、工作流定义、任务定义、
--       工作流实例、任务实例、告警、审计日志
-- =====================================================================

-- 清除旧的演示数据（保留 id=1 的 admin 用户和 id=-1 的 default 租户）
DELETE FROM t_ds_audit_log WHERE user_id != 1 OR id > 100;
DELETE FROM t_ds_alert WHERE id > 0;
DELETE FROM t_ds_task_instance WHERE id > 0;
DELETE FROM t_ds_workflow_instance WHERE id > 0;
DELETE FROM t_ds_workflow_task_relation WHERE id > 0;
DELETE FROM t_ds_workflow_task_relation_log WHERE id > 0;
DELETE FROM t_ds_task_definition_log WHERE id > 0;
DELETE FROM t_ds_task_definition WHERE id > 0;
DELETE FROM t_ds_workflow_definition_log WHERE id > 0;
DELETE FROM t_ds_workflow_definition WHERE id > 0;
DELETE FROM t_ds_schedules WHERE id > 0;
DELETE FROM t_ds_datasource WHERE id > 0;
DELETE FROM t_ds_relation_project_user WHERE id > 0;
DELETE FROM t_ds_project WHERE id > 0;
DELETE FROM t_ds_user WHERE id > 1;
DELETE FROM t_ds_tenant WHERE id != -1;
DELETE FROM t_ds_queue WHERE id > 1;

-- =====================================================================
-- 1. 队列
-- =====================================================================
INSERT INTO t_ds_queue (id, queue_name, queue, create_time, update_time) VALUES
(2, 'spark_queue',   'spark',   NOW(), NOW()),
(3, 'flink_queue',   'flink',   NOW(), NOW()),
(4, 'offline_queue', 'offline', NOW(), NOW());

-- =====================================================================
-- 2. 租户
-- =====================================================================
INSERT INTO t_ds_tenant (id, tenant_code, description, queue_id, create_time, update_time) VALUES
(1, 'ds_dev',     '开发团队租户',   2, NOW(), NOW()),
(2, 'ds_etl',     'ETL数据处理租户', 4, NOW(), NOW()),
(3, 'ds_analyst', '数据分析租户',   2, NOW(), NOW());

-- =====================================================================
-- 3. 用户（密码为 test123456 的 MD5：7c4a8d09ca3762af61e59520943dc26479d031a7）
-- =====================================================================
INSERT INTO t_ds_user (id, user_name, user_password, user_type, email, phone, tenant_id, state, create_time, update_time) VALUES
(2, 'developer1', '7c4a8d09ca3762af61e59520943dc26479d031a7', 1, 'dev1@example.com',      '13800001111', 1, 1, NOW(), NOW()),
(3, 'developer2', '7c4a8d09ca3762af61e59520943dc26479d031a7', 1, 'dev2@example.com',      '13800002222', 1, 1, NOW(), NOW()),
(4, 'etl_user',   '7c4a8d09ca3762af61e59520943dc26479d031a7', 1, 'etl@example.com',       '13800003333', 2, 1, NOW(), NOW()),
(5, 'analyst',    '7c4a8d09ca3762af61e59520943dc26479d031a7', 1, 'analyst@example.com',   '13800004444', 3, 1, NOW(), NOW());

-- =====================================================================
-- 4. 项目
-- project code 使用毫秒时间戳风格的 bigint
-- =====================================================================
INSERT INTO t_ds_project (id, name, code, description, user_id, flag, create_time, update_time) VALUES
(1, '电商数据处理',    1000000001, '电商平台订单、用户、商品数据的ETL处理', 1, 1, NOW() - INTERVAL '30 days', NOW()),
(2, '实时风控',        1000000002, '实时风控规则计算与特征工程',             2, 1, NOW() - INTERVAL '20 days', NOW()),
(3, '数仓建设',        1000000003, 'ODS/DWD/DWS层数据加工',                 1, 1, NOW() - INTERVAL '15 days', NOW()),
(4, '用户画像',        1000000004, '用户标签与画像计算',                     5, 1, NOW() - INTERVAL '10 days', NOW()),
(5, '报表自动化',      1000000005, '每日/每周/每月报表自动生成与投递',       4, 1, NOW() - INTERVAL '5 days',  NOW());

-- 项目成员关系
INSERT INTO t_ds_relation_project_user (id, project_id, user_id, perm, create_time, update_time) VALUES
(1, 1, 2, 7, NOW(), NOW()),
(2, 1, 4, 7, NOW(), NOW()),
(3, 2, 2, 7, NOW(), NOW()),
(4, 2, 3, 4, NOW(), NOW()),
(5, 3, 4, 7, NOW(), NOW()),
(6, 3, 5, 4, NOW(), NOW()),
(7, 4, 5, 7, NOW(), NOW()),
(8, 5, 4, 7, NOW(), NOW());

-- =====================================================================
-- 5. 数据源
-- type: 0=MySQL 1=PostgreSQL 2=HIVE 3=Spark 7=ClickHouse
-- =====================================================================
INSERT INTO t_ds_datasource (id, name, note, type, user_id, connection_params, create_time, update_time) VALUES
(1, 'MySQL-生产库',      'MySQL生产数据库',         0, 1,
 '{"address":"jdbc:mysql://mysql-prod:3306","database":"prod_db","jdbcUrl":"jdbc:mysql://mysql-prod:3306/prod_db","user":"ds_user","password":"******","driverClassName":"com.mysql.cj.jdbc.Driver","validationQuery":"select 1"}',
 NOW() - INTERVAL '25 days', NOW()),
(2, 'PostgreSQL-DW',    'PostgreSQL数仓',           1, 1,
 '{"address":"jdbc:postgresql://pg-dw:5432","database":"dw","jdbcUrl":"jdbc:postgresql://pg-dw:5432/dw","user":"dw_user","password":"******","driverClassName":"org.postgresql.Driver","validationQuery":"select 1"}',
 NOW() - INTERVAL '20 days', NOW()),
(3, 'Hive-离线仓',       'Hive离线数仓',             2, 4,
 '{"address":"jdbc:hive2://hive-server:10000","database":"ods","jdbcUrl":"jdbc:hive2://hive-server:10000/ods","user":"hive","password":"","driverClassName":"org.apache.hive.jdbc.HiveDriver","validationQuery":"select 1"}',
 NOW() - INTERVAL '18 days', NOW()),
(4, 'ClickHouse-分析',   'ClickHouse分析数据库',     7, 5,
 '{"address":"jdbc:clickhouse://ck-server:8123","database":"analytics","jdbcUrl":"jdbc:clickhouse://ck-server:8123/analytics","user":"ck_user","password":"******","driverClassName":"ru.yandex.clickhouse.ClickHouseDriver","validationQuery":"select 1"}',
 NOW() - INTERVAL '12 days', NOW()),
(5, 'MySQL-用户中心',    'MySQL用户中心数据库',       0, 2,
 '{"address":"jdbc:mysql://mysql-uc:3306","database":"user_center","jdbcUrl":"jdbc:mysql://mysql-uc:3306/user_center","user":"uc_user","password":"******","driverClassName":"com.mysql.cj.jdbc.Driver","validationQuery":"select 1"}',
 NOW() - INTERVAL '8 days', NOW());

-- =====================================================================
-- 6. 工作流定义
-- release_state: 0=下线 1=上线; flag: 1=可用
-- =====================================================================
INSERT INTO t_ds_workflow_definition (id, code, name, version, description, project_code, release_state, user_id, global_params, flag, timeout, execution_type, create_time, update_time) VALUES
-- 项目1: 电商数据处理
(1,  2000000001, '订单数据同步',        1, '将MySQL订单数据同步到数仓',           1000000001, 1, 1, '[]', 1, 0, 0, NOW() - INTERVAL '28 days', NOW()),
(2,  2000000002, '用户行为分析',        1, '分析用户行为数据',                    1000000001, 1, 2, '[]', 1, 0, 0, NOW() - INTERVAL '25 days', NOW()),
(3,  2000000003, '商品库存更新',        1, '每日商品库存数据更新',                1000000001, 1, 1, '[]', 1, 0, 0, NOW() - INTERVAL '22 days', NOW()),
-- 项目2: 实时风控
(4,  2000000004, '风控特征计算',        1, '计算实时风控特征指标',                1000000002, 1, 2, '[]', 1, 0, 0, NOW() - INTERVAL '18 days', NOW()),
(5,  2000000005, '黑名单更新',          1, '更新欺诈用户黑名单',                  1000000002, 0, 3, '[]', 1, 0, 0, NOW() - INTERVAL '15 days', NOW()),
-- 项目3: 数仓建设
(6,  2000000006, 'ODS层数据加载',       1, 'ODS层原始数据加载',                   1000000003, 1, 4, '[]', 1, 0, 0, NOW() - INTERVAL '13 days', NOW()),
(7,  2000000007, 'DWD层数据清洗',       1, 'DWD层数据清洗与标准化',               1000000003, 1, 4, '[]', 1, 0, 0, NOW() - INTERVAL '12 days', NOW()),
(8,  2000000008, 'DWS层指标聚合',       1, 'DWS层业务指标聚合计算',               1000000003, 1, 4, '[]', 1, 0, 0, NOW() - INTERVAL '10 days', NOW()),
-- 项目4: 用户画像
(9,  2000000009, '用户标签计算',        1, '计算用户各类标签',                    1000000004, 1, 5, '[]', 1, 0, 0, NOW() - INTERVAL '8 days',  NOW()),
(10, 2000000010, '画像报告生成',        1, '生成用户画像分析报告',                1000000004, 1, 5, '[]', 1, 0, 0, NOW() - INTERVAL '6 days',  NOW()),
-- 项目5: 报表自动化
(11, 2000000011, '日报自动生成',        1, '每天生成销售日报',                    1000000005, 1, 4, '[]', 1, 0, 0, NOW() - INTERVAL '4 days',  NOW()),
(12, 2000000012, '周报数据汇总',        1, '每周数据汇总报表',                    1000000005, 1, 4, '[]', 1, 0, 0, NOW() - INTERVAL '3 days',  NOW());

-- =====================================================================
-- 7. 任务定义（每个工作流若干任务）
-- task_type: SHELL / SQL / SPARK / PYTHON / HTTP
-- flag: 1=可用; task_priority: 2=MEDIUM
-- =====================================================================
INSERT INTO t_ds_task_definition (id, code, name, version, description, project_code, user_id, task_type, task_execute_type, task_params, flag, task_priority, worker_group, environment_code, fail_retry_times, fail_retry_interval, timeout_flag, timeout_notify_strategy, timeout, delay_time, cpu_quota, memory_max, create_time, update_time) VALUES
-- 工作流1: 订单数据同步 (project_code=1000000001)
(1,  3000000001, '抽取MySQL订单',    1, '', 1000000001, 1, 'SQL',   0, '{"type":"SQL","datasource":1,"sql":"SELECT * FROM orders WHERE dt=''${dt}''","sqlType":0}',     1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '28 days', NOW()),
(2,  3000000002, '数据清洗转换',     1, '', 1000000001, 1, 'SHELL', 0, '{"rawScript":"#!/bin/bash\necho \"clean data\"\npython3 /opt/scripts/clean_orders.py --dt=${dt}"}',              1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '28 days', NOW()),
(3,  3000000003, '加载到数仓',       1, '', 1000000001, 1, 'SQL',   0, '{"type":"SQL","datasource":2,"sql":"INSERT INTO dw.orders SELECT * FROM stage.orders","sqlType":0}',1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '28 days', NOW()),
-- 工作流2: 用户行为分析
(4,  3000000004, '加载点击流数据',   1, '', 1000000001, 2, 'SPARK', 0, '{"mainClass":"com.example.ClickStreamLoader","programType":"SCALA","deployMode":"cluster"}',               1, 2, 'default', -1, 1, 5, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '25 days', NOW()),
(5,  3000000005, '用户路径分析',     1, '', 1000000001, 2, 'PYTHON',0, '{"rawScript":"import pandas as pd\ndf=pd.read_parquet(''/data/click'')\nprint(df.describe())"}',           1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '25 days', NOW()),
-- 工作流3: 商品库存更新
(6,  3000000006, '读取库存快照',     1, '', 1000000001, 1, 'SQL',   0, '{"type":"SQL","datasource":1,"sql":"SELECT sku_id,qty FROM inventory","sqlType":0}',                       1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '22 days', NOW()),
(7,  3000000007, '更新库存数仓',     1, '', 1000000001, 1, 'SQL',   0, '{"type":"SQL","datasource":2,"sql":"MERGE INTO dw.inventory USING stage.inventory ON ...","sqlType":0}',   1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '22 days', NOW()),
-- 工作流4: 风控特征计算
(8,  3000000008, '特征提取',         1, '', 1000000002, 2, 'SPARK', 0, '{"mainClass":"com.example.FeatureExtractor","programType":"JAVA","deployMode":"client"}',                  1, 2, 'default', -1, 1, 5, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '18 days', NOW()),
(9,  3000000009, '风险评分',         1, '', 1000000002, 2, 'PYTHON',0, '{"rawScript":"import sklearn\nmodel=load_model(''/models/risk'')\nprint(model.predict(features))"}',     1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '18 days', NOW()),
-- 工作流6: ODS层数据加载
(10, 3000000010, 'ODS用户表加载',    1, '', 1000000003, 4, 'SQL',   0, '{"type":"SQL","datasource":3,"sql":"LOAD DATA INPATH ''/data/ods/user'' INTO TABLE ods.user","sqlType":0}', 1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '13 days', NOW()),
(11, 3000000011, 'ODS订单表加载',    1, '', 1000000003, 4, 'SQL',   0, '{"type":"SQL","datasource":3,"sql":"LOAD DATA INPATH ''/data/ods/order'' INTO TABLE ods.order","sqlType":0}',1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '13 days', NOW()),
(12, 3000000012, 'ODS商品表加载',    1, '', 1000000003, 4, 'SQL',   0, '{"type":"SQL","datasource":3,"sql":"LOAD DATA INPATH ''/data/ods/item'' INTO TABLE ods.item","sqlType":0}', 1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '13 days', NOW()),
-- 工作流7: DWD层数据清洗
(13, 3000000013, 'DWD用户数据清洗',  1, '', 1000000003, 4, 'SQL',   0, '{"type":"SQL","datasource":3,"sql":"INSERT INTO dwd.user SELECT * FROM ods.user WHERE is_valid=1","sqlType":0}', 1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '12 days', NOW()),
(14, 3000000014, 'DWD订单数据清洗',  1, '', 1000000003, 4, 'SQL',   0, '{"type":"SQL","datasource":3,"sql":"INSERT INTO dwd.order SELECT * FROM ods.order WHERE status!=''invalid''","sqlType":0}',1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '12 days', NOW()),
-- 工作流9: 用户标签计算
(15, 3000000015, '活跃度标签',       1, '', 1000000004, 5, 'PYTHON',0, '{"rawScript":"python3 /opt/scripts/active_tag.py --dt=${dt}"}',                                           1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '8 days',  NOW()),
(16, 3000000016, '消费力标签',       1, '', 1000000004, 5, 'PYTHON',0, '{"rawScript":"python3 /opt/scripts/consume_tag.py --dt=${dt}"}',                                          1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '8 days',  NOW()),
-- 工作流11: 日报
(17, 3000000017, '汇总销售数据',     1, '', 1000000005, 4, 'SQL',   0, '{"type":"SQL","datasource":2,"sql":"SELECT dt,SUM(amount) FROM dws.sales GROUP BY dt","sqlType":1}',     1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '4 days',  NOW()),
(18, 3000000018, '发送邮件报告',     1, '', 1000000005, 4, 'HTTP',  0, '{"url":"http://mail-service/api/send","httpMethod":"POST","body":"{\"to\":\"team@company.com\"}"}',       1, 2, 'default', -1, 0, 1, 0, 0, 0, 0, -1, -1, NOW() - INTERVAL '4 days',  NOW());

-- =====================================================================
-- 8. 工作流实例（历史运行记录）
-- state: 1=运行中 5=成功 6=失败 9=停止
-- command_type: 0=启动工作流 6=定时触发
-- =====================================================================
INSERT INTO t_ds_workflow_instance (id, name, workflow_definition_code, workflow_definition_version, project_code, state, recovery, start_time, end_time, run_times, host, command_type, command_param, task_depend_type, max_try_times, failure_strategy, warning_type, warning_group_id, schedule_time, command_start_time, global_params, flag, update_time, is_sub_workflow, executor_id, executor_name) VALUES
-- 订单数据同步 成功运行记录
(1,  '订单数据同步-20260528',   2000000001, 1, 1000000001, 5, 0, NOW() - INTERVAL '2 days' - INTERVAL '1 hour',  NOW() - INTERVAL '2 days',                          1, 'worker-1:1234', 6, '{"scheduleTime":"2026-05-28 01:00:00"}', 0, 1, 0, 0, 0, NOW() - INTERVAL '2 days' - INTERVAL '1 hour',  NOW() - INTERVAL '2 days' - INTERVAL '1 hour',  '[]', 1, NOW() - INTERVAL '2 days',              0, 1, 'admin'),
(2,  '订单数据同步-20260529',   2000000001, 1, 1000000001, 5, 0, NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',  NOW() - INTERVAL '1 day',                           1, 'worker-1:1234', 6, '{"scheduleTime":"2026-05-29 01:00:00"}', 0, 1, 0, 0, 0, NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',  NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',  '[]', 1, NOW() - INTERVAL '1 day',               0, 1, 'admin'),
(3,  '订单数据同步-20260530',   2000000001, 1, 1000000001, 5, 0, NOW() - INTERVAL '1 hour',                       NOW() - INTERVAL '10 minutes',                    1, 'worker-1:1234', 6, '{"scheduleTime":"2026-05-30 01:00:00"}', 0, 1, 0, 0, 0, NOW() - INTERVAL '1 hour',                       NOW() - INTERVAL '1 hour',                       '[]', 1, NOW() - INTERVAL '10 minutes',          0, 1, 'admin'),
-- 用户行为分析
(4,  '用户行为分析-20260528',   2000000002, 1, 1000000001, 5, 0, NOW() - INTERVAL '2 days' - INTERVAL '2 hours', NOW() - INTERVAL '2 days' - INTERVAL '30 minutes', 1, 'worker-2:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '2 days' - INTERVAL '2 hours', NOW() - INTERVAL '2 days' - INTERVAL '2 hours', '[]', 1, NOW() - INTERVAL '2 days' - INTERVAL '30 minutes', 0, 2, 'developer1'),
(5,  '用户行为分析-20260529',   2000000002, 1, 1000000001, 6, 0, NOW() - INTERVAL '1 day'  - INTERVAL '2 hours', NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',     1, 'worker-2:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '1 day'  - INTERVAL '2 hours', NOW() - INTERVAL '1 day'  - INTERVAL '2 hours', '[]', 1, NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',      0, 2, 'developer1'),
(6,  '用户行为分析-20260530',   2000000002, 1, 1000000001, 1, 0, NOW() - INTERVAL '30 minutes',                  NULL,                                              1, 'worker-2:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '30 minutes',                  NOW() - INTERVAL '30 minutes',                  '[]', 1, NOW(),                                              0, 2, 'developer1'),
-- 商品库存更新
(7,  '商品库存更新-20260528',   2000000003, 1, 1000000001, 5, 0, NOW() - INTERVAL '2 days' - INTERVAL '3 hours', NOW() - INTERVAL '2 days' - INTERVAL '2 hours',    1, 'worker-1:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '2 days' - INTERVAL '3 hours', NOW() - INTERVAL '2 days' - INTERVAL '3 hours', '[]', 1, NOW() - INTERVAL '2 days' - INTERVAL '2 hours',    0, 1, 'admin'),
(8,  '商品库存更新-20260529',   2000000003, 1, 1000000001, 5, 0, NOW() - INTERVAL '1 day'  - INTERVAL '3 hours', NOW() - INTERVAL '1 day'  - INTERVAL '2 hours',    1, 'worker-1:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '1 day'  - INTERVAL '3 hours', NOW() - INTERVAL '1 day'  - INTERVAL '3 hours', '[]', 1, NOW() - INTERVAL '1 day'  - INTERVAL '2 hours',    0, 1, 'admin'),
-- ODS层数据加载
(9,  'ODS层数据加载-20260528',  2000000006, 1, 1000000003, 5, 0, NOW() - INTERVAL '2 days' - INTERVAL '4 hours', NOW() - INTERVAL '2 days' - INTERVAL '3 hours',    1, 'worker-3:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '2 days' - INTERVAL '4 hours', NOW() - INTERVAL '2 days' - INTERVAL '4 hours', '[]', 1, NOW() - INTERVAL '2 days' - INTERVAL '3 hours',    0, 4, 'etl_user'),
(10, 'ODS层数据加载-20260529',  2000000006, 1, 1000000003, 5, 0, NOW() - INTERVAL '1 day'  - INTERVAL '4 hours', NOW() - INTERVAL '1 day'  - INTERVAL '3 hours',    1, 'worker-3:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '1 day'  - INTERVAL '4 hours', NOW() - INTERVAL '1 day'  - INTERVAL '4 hours', '[]', 1, NOW() - INTERVAL '1 day'  - INTERVAL '3 hours',    0, 4, 'etl_user'),
(11, 'ODS层数据加载-20260530',  2000000006, 1, 1000000003, 5, 0, NOW() - INTERVAL '2 hours',                      NOW() - INTERVAL '1 hour',                        1, 'worker-3:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '2 hours',                      NOW() - INTERVAL '2 hours',                      '[]', 1, NOW() - INTERVAL '1 hour',               0, 4, 'etl_user'),
-- DWD层数据清洗
(12, 'DWD层数据清洗-20260530',  2000000007, 1, 1000000003, 5, 0, NOW() - INTERVAL '50 minutes',                   NOW() - INTERVAL '20 minutes',                    1, 'worker-3:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '50 minutes',                   NOW() - INTERVAL '50 minutes',                   '[]', 1, NOW() - INTERVAL '20 minutes',          0, 4, 'etl_user'),
-- 风控特征计算
(13, '风控特征计算-20260529',   2000000004, 1, 1000000002, 6, 0, NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',  NOW() - INTERVAL '1 day'  - INTERVAL '30 minutes', 1, 'worker-2:1234', 0, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',  NOW() - INTERVAL '1 day'  - INTERVAL '1 hour',  '[]', 1, NOW() - INTERVAL '1 day'  - INTERVAL '30 minutes', 0, 2, 'developer1'),
(14, '风控特征计算-20260530',   2000000004, 1, 1000000002, 1, 0, NOW() - INTERVAL '15 minutes',                   NULL,                                              1, 'worker-2:1234', 0, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '15 minutes',                   NOW() - INTERVAL '15 minutes',                   '[]', 1, NOW(),                                              0, 2, 'developer1'),
-- 日报自动生成
(15, '日报自动生成-20260529',   2000000011, 1, 1000000005, 5, 0, NOW() - INTERVAL '1 day'  - INTERVAL '6 hours', NOW() - INTERVAL '1 day'  - INTERVAL '5 hours',    1, 'worker-1:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '1 day'  - INTERVAL '6 hours', NOW() - INTERVAL '1 day'  - INTERVAL '6 hours', '[]', 1, NOW() - INTERVAL '1 day'  - INTERVAL '5 hours',    0, 4, 'etl_user'),
(16, '日报自动生成-20260530',   2000000011, 1, 1000000005, 5, 0, NOW() - INTERVAL '6 hours',                      NOW() - INTERVAL '5 hours',                       1, 'worker-1:1234', 6, '{}',                                    0, 1, 0, 0, 0, NOW() - INTERVAL '6 hours',                      NOW() - INTERVAL '6 hours',                      '[]', 1, NOW() - INTERVAL '5 hours',               0, 4, 'etl_user'),
-- 用户标签计算
(17, '用户标签计算-20260530',   2000000009, 1, 1000000004, 9, 0, NOW() - INTERVAL '3 hours',                      NOW() - INTERVAL '2 hours' - INTERVAL '40 minutes',1, 'worker-2:1234', 0, '{}',                                   0, 1, 0, 0, 0, NOW() - INTERVAL '3 hours',                      NOW() - INTERVAL '3 hours',                      '[]', 1, NOW() - INTERVAL '2 hours' - INTERVAL '40 minutes',0, 5, 'analyst');

-- =====================================================================
-- 9. 任务实例（对应工作流实例中的任务执行记录）
-- state: 1=运行中 5=成功 6=失败 9=停止
-- =====================================================================
INSERT INTO t_ds_task_instance (id, name, task_type, task_execute_type, task_code, task_definition_version, workflow_instance_id, workflow_instance_name, project_code, state, submit_time, start_time, end_time, host, retry_times, flag, task_instance_priority, worker_group, environment_code, executor_id, executor_name, delay_time, dry_run, cpu_quota, memory_max) VALUES
-- 工作流实例3 (订单数据同步-20260530): 3个任务 全部成功
(1,  '抽取MySQL订单',  'SQL',    0, 3000000001, 1, 3,  '订单数据同步-20260530',   1000000001, 5, NOW()-INTERVAL'1 hour',         NOW()-INTERVAL'1 hour',                       NOW()-INTERVAL'50 minutes',             'worker-1:1234', 0, 1, 2, 'default', -1, 1, 'admin',      0, 0, -1, -1),
(2,  '数据清洗转换',   'SHELL',  0, 3000000002, 1, 3,  '订单数据同步-20260530',   1000000001, 5, NOW()-INTERVAL'50 minutes',     NOW()-INTERVAL'50 minutes',                   NOW()-INTERVAL'30 minutes',             'worker-1:1234', 0, 1, 2, 'default', -1, 1, 'admin',      0, 0, -1, -1),
(3,  '加载到数仓',     'SQL',    0, 3000000003, 1, 3,  '订单数据同步-20260530',   1000000001, 5, NOW()-INTERVAL'30 minutes',     NOW()-INTERVAL'30 minutes',                   NOW()-INTERVAL'10 minutes',             'worker-1:1234', 0, 1, 2, 'default', -1, 1, 'admin',      0, 0, -1, -1),
-- 工作流实例5 (用户行为分析-20260529): 失败
(4,  '加载点击流数据', 'SPARK',  0, 3000000004, 1, 5,  '用户行为分析-20260529',   1000000001, 5, NOW()-INTERVAL'1 day'-INTERVAL'2 hours', NOW()-INTERVAL'1 day'-INTERVAL'2 hours',  NOW()-INTERVAL'1 day'-INTERVAL'1 hour 30 minutes','worker-2:1234',0,1,2,'default',-1,2,'developer1',0,0,-1,-1),
(5,  '用户路径分析',   'PYTHON', 0, 3000000005, 1, 5,  '用户行为分析-20260529',   1000000001, 6, NOW()-INTERVAL'1 day'-INTERVAL'1 hour 30 minutes',NOW()-INTERVAL'1 day'-INTERVAL'1 hour 30 minutes',NOW()-INTERVAL'1 day'-INTERVAL'1 hour','worker-2:1234',0,1,2,'default',-1,2,'developer1',0,0,-1,-1),
-- 工作流实例6 (用户行为分析-20260530): 运行中
(6,  '加载点击流数据', 'SPARK',  0, 3000000004, 1, 6,  '用户行为分析-20260530',   1000000001, 5, NOW()-INTERVAL'30 minutes',     NOW()-INTERVAL'30 minutes',                   NOW()-INTERVAL'10 minutes',             'worker-2:1234', 0, 1, 2, 'default', -1, 2, 'developer1', 0, 0, -1, -1),
(7,  '用户路径分析',   'PYTHON', 0, 3000000005, 1, 6,  '用户行为分析-20260530',   1000000001, 1, NOW()-INTERVAL'10 minutes',     NOW()-INTERVAL'10 minutes',                   NULL,                                   'worker-2:1234', 0, 1, 2, 'default', -1, 2, 'developer1', 0, 0, -1, -1),
-- 工作流实例11 (ODS层数据加载-20260530): 成功
(8,  'ODS用户表加载',  'SQL',    0, 3000000010, 1, 11, 'ODS层数据加载-20260530',  1000000003, 5, NOW()-INTERVAL'2 hours',        NOW()-INTERVAL'2 hours',                      NOW()-INTERVAL'1 hour 40 minutes',      'worker-3:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1),
(9,  'ODS订单表加载',  'SQL',    0, 3000000011, 1, 11, 'ODS层数据加载-20260530',  1000000003, 5, NOW()-INTERVAL'2 hours',        NOW()-INTERVAL'1 hour 40 minutes',            NOW()-INTERVAL'1 hour 20 minutes',      'worker-3:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1),
(10, 'ODS商品表加载',  'SQL',    0, 3000000012, 1, 11, 'ODS层数据加载-20260530',  1000000003, 5, NOW()-INTERVAL'2 hours',        NOW()-INTERVAL'1 hour 20 minutes',            NOW()-INTERVAL'1 hour',                 'worker-3:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1),
-- 工作流实例12 (DWD层数据清洗-20260530): 成功
(11, 'DWD用户数据清洗','SQL',    0, 3000000013, 1, 12, 'DWD层数据清洗-20260530',  1000000003, 5, NOW()-INTERVAL'50 minutes',     NOW()-INTERVAL'50 minutes',                   NOW()-INTERVAL'35 minutes',             'worker-3:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1),
(12, 'DWD订单数据清洗','SQL',    0, 3000000014, 1, 12, 'DWD层数据清洗-20260530',  1000000003, 5, NOW()-INTERVAL'35 minutes',     NOW()-INTERVAL'35 minutes',                   NOW()-INTERVAL'20 minutes',             'worker-3:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1),
-- 工作流实例13 (风控特征计算-20260529): 失败
(13, '特征提取',       'SPARK',  0, 3000000008, 1, 13, '风控特征计算-20260529',   1000000002, 6, NOW()-INTERVAL'1 day'-INTERVAL'1 hour',NOW()-INTERVAL'1 day'-INTERVAL'1 hour',NOW()-INTERVAL'1 day'-INTERVAL'30 minutes','worker-2:1234',0,1,2,'default',-1,2,'developer1',0,0,-1,-1),
-- 工作流实例14 (风控特征计算-20260530): 运行中
(14, '特征提取',       'SPARK',  0, 3000000008, 1, 14, '风控特征计算-20260530',   1000000002, 1, NOW()-INTERVAL'15 minutes',     NOW()-INTERVAL'15 minutes',                   NULL,                                   'worker-2:1234', 0, 1, 2, 'default', -1, 2, 'developer1', 0, 0, -1, -1),
-- 工作流实例16 (日报自动生成-20260530): 成功
(15, '汇总销售数据',   'SQL',    0, 3000000017, 1, 16, '日报自动生成-20260530',   1000000005, 5, NOW()-INTERVAL'6 hours',        NOW()-INTERVAL'6 hours',                      NOW()-INTERVAL'5 hours 30 minutes',     'worker-1:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1),
(16, '发送邮件报告',   'HTTP',   0, 3000000018, 1, 16, '日报自动生成-20260530',   1000000005, 5, NOW()-INTERVAL'5 hours 30 minutes',NOW()-INTERVAL'5 hours 30 minutes',         NOW()-INTERVAL'5 hours',                'worker-1:1234', 0, 1, 2, 'default', -1, 4, 'etl_user',   0, 0, -1, -1);

-- =====================================================================
-- 10. 定时调度（schedules）
-- release_state: 1=上线; failure_strategy: 0=结束; warning_type: 0=无
-- =====================================================================
INSERT INTO t_ds_schedules (id, workflow_definition_code, start_time, end_time, timezone_id, crontab, failure_strategy, user_id, release_state, warning_type, warning_group_id, worker_group, environment_code, create_time, update_time) VALUES
(1, 2000000001, NOW() - INTERVAL '30 days', '2099-12-31 23:59:59', 'Asia/Shanghai', '0 0 1 * * ? *',  0, 1, 1, 0, 0, 'default', -1, NOW() - INTERVAL '28 days', NOW()),
(2, 2000000002, NOW() - INTERVAL '25 days', '2099-12-31 23:59:59', 'Asia/Shanghai', '0 0 2 * * ? *',  0, 2, 1, 0, 0, 'default', -1, NOW() - INTERVAL '25 days', NOW()),
(3, 2000000003, NOW() - INTERVAL '22 days', '2099-12-31 23:59:59', 'Asia/Shanghai', '0 0 3 * * ? *',  0, 1, 1, 0, 0, 'default', -1, NOW() - INTERVAL '22 days', NOW()),
(4, 2000000006, NOW() - INTERVAL '13 days', '2099-12-31 23:59:59', 'Asia/Shanghai', '0 0 0 * * ? *',  0, 4, 1, 0, 0, 'default', -1, NOW() - INTERVAL '13 days', NOW()),
(5, 2000000007, NOW() - INTERVAL '12 days', '2099-12-31 23:59:59', 'Asia/Shanghai', '0 30 0 * * ? *', 0, 4, 1, 0, 0, 'default', -1, NOW() - INTERVAL '12 days', NOW()),
(6, 2000000011, NOW() - INTERVAL '4 days',  '2099-12-31 23:59:59', 'Asia/Shanghai', '0 0 8 * * ? *',  0, 4, 1, 0, 0, 'default', -1, NOW() - INTERVAL '4 days',  NOW());

-- =====================================================================
-- 11. 告警记录
-- alert_status: 0=等待 1=成功 2=失败; warning_type: 1=成功 2=失败 3=全部
-- =====================================================================
INSERT INTO t_ds_alert (id, title, sign, content, alert_status, warning_type, log, alertgroup_id, create_time, update_time, project_code, workflow_definition_code, workflow_instance_id, alert_type) VALUES
(1, '用户行为分析 执行失败', 'abc123', '[{"type":"WORKFLOW_FAILURE","content":"工作流 用户行为分析-20260529 执行失败，任务 用户路径分析 出现异常"}]', 1, 2, '发送成功', 1, NOW()-INTERVAL'1 day'-INTERVAL'1 hour', NOW()-INTERVAL'1 day'-INTERVAL'1 hour', 1000000001, 2000000002, 5, 1),
(2, '风控特征计算 执行失败', 'def456', '[{"type":"WORKFLOW_FAILURE","content":"工作流 风控特征计算-20260529 执行失败，SPARK任务特征提取超时"}]',          1, 2, '发送成功', 1, NOW()-INTERVAL'1 day'-INTERVAL'30 minutes', NOW()-INTERVAL'1 day'-INTERVAL'30 minutes', 1000000002, 2000000004, 13, 1),
(3, '订单数据同步 执行成功', 'ghi789', '[{"type":"WORKFLOW_SUCCESS","content":"工作流 订单数据同步-20260530 执行成功，耗时50分钟"}]',                     1, 1, '发送成功', 1, NOW()-INTERVAL'10 minutes', NOW()-INTERVAL'10 minutes', 1000000001, 2000000001, 3, 0);

-- =====================================================================
-- 12. 审计日志
-- =====================================================================
INSERT INTO t_ds_audit_log (id, user_id, model_id, model_name, model_type, operation_type, description, latency, detail, create_time) VALUES
(1,  1, 1000000001, '电商数据处理', 'PROJECT',             'CREATE',  '创建项目',         120,  NULL, NOW()-INTERVAL'30 days'),
(2,  1, 1000000002, '实时风控',     'PROJECT',             'CREATE',  '创建项目',         98,   NULL, NOW()-INTERVAL'20 days'),
(3,  1, 1000000003, '数仓建设',     'PROJECT',             'CREATE',  '创建项目',         105,  NULL, NOW()-INTERVAL'15 days'),
(4,  2, 2000000001, '订单数据同步', 'WORKFLOW_DEFINITION', 'CREATE',  '创建工作流定义',   230,  NULL, NOW()-INTERVAL'28 days'),
(5,  2, 2000000001, '订单数据同步', 'WORKFLOW_DEFINITION', 'ONLINE',  '上线工作流定义',   88,   NULL, NOW()-INTERVAL'27 days'),
(6,  2, 2000000002, '用户行为分析', 'WORKFLOW_DEFINITION', 'CREATE',  '创建工作流定义',   310,  NULL, NOW()-INTERVAL'25 days'),
(7,  4, 2000000006, 'ODS层数据加载','WORKFLOW_DEFINITION', 'CREATE',  '创建工作流定义',   275,  NULL, NOW()-INTERVAL'13 days'),
(8,  1, 1,          'MySQL-生产库', 'DATASOURCE',          'CREATE',  '创建数据源',       145,  NULL, NOW()-INTERVAL'25 days'),
(9,  1, 2,          'PostgreSQL-DW','DATASOURCE',          'CREATE',  '创建数据源',       132,  NULL, NOW()-INTERVAL'20 days'),
(10, 2, 3,          'Hive-离线仓',  'DATASOURCE',          'CREATE',  '创建数据源',       118,  NULL, NOW()-INTERVAL'18 days'),
(11, 1, 3,          '订单数据同步-20260530','WORKFLOW_INSTANCE','TRIGGER','手动触发工作流', 76,  NULL, NOW()-INTERVAL'1 hour'),
(12, 2, 5,          '用户行为分析-20260529','WORKFLOW_INSTANCE','TRIGGER','定时触发工作流', 54,  NULL, NOW()-INTERVAL'1 day'-INTERVAL'2 hours');

-- =====================================================================
-- 验证数据
-- =====================================================================
SELECT
  (SELECT COUNT(*) FROM t_ds_user)               AS users,
  (SELECT COUNT(*) FROM t_ds_tenant)             AS tenants,
  (SELECT COUNT(*) FROM t_ds_project)            AS projects,
  (SELECT COUNT(*) FROM t_ds_datasource)         AS datasources,
  (SELECT COUNT(*) FROM t_ds_workflow_definition)AS workflow_defs,
  (SELECT COUNT(*) FROM t_ds_task_definition)    AS task_defs,
  (SELECT COUNT(*) FROM t_ds_workflow_instance)  AS wf_instances,
  (SELECT COUNT(*) FROM t_ds_task_instance)      AS task_instances,
  (SELECT COUNT(*) FROM t_ds_schedules)          AS schedules,
  (SELECT COUNT(*) FROM t_ds_alert)              AS alerts,
  (SELECT COUNT(*) FROM t_ds_audit_log)          AS audit_logs;
