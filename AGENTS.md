<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **dolphinscheduler** (25095 symbols, 71271 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/dolphinscheduler/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/dolphinscheduler/context` | Codebase overview, check index freshness |
| `gitnexus://repo/dolphinscheduler/clusters` | All functional areas |
| `gitnexus://repo/dolphinscheduler/processes` | All execution flows |
| `gitnexus://repo/dolphinscheduler/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## CLI

- Re-index: `npx gitnexus analyze`
- Check freshness: `npx gitnexus status`
- Generate docs: `npx gitnexus wiki`

<!-- gitnexus:end -->

---

# Apache DolphinScheduler — Agent Project Index

Apache DolphinScheduler is a distributed, visual DAG workflow-scheduling platform. This is the monorepo: backend servers (master / worker / api / alert), a Vue 3 frontend, plugin families for tasks / datasources / storage / alerting / scheduling, and the release tooling.

Module-specific details live in each module's `CLAUDE.md`; use those files as the source of truth and do not duplicate module contents here.

---

## Tech Stack (project-wide)

- **Java 1.8** (do not assume 11+ APIs; `dolphinscheduler-api-test` is the only Java 11 island).
- **Spring Boot 2.6.1** across servers, **Jetty** (Tomcat is excluded transitively).
- **MyBatis-Plus** for ORM; **HikariCP** for the metadata DB pool, **Druid** inside user-facing datasource plugins.
- **Quartz** for cron scheduling (via `scheduler-plugin`).
- **Netty / gRPC** for inter-server RPC (see `extract-base`).
- **Vue 3 + Vite + TypeScript + Naive UI** for the frontend.
- **Maven** multi-module reactor (26 modules in root `pom.xml` + 2 test modules).
- **Zookeeper 3.8** by default for the registry (Etcd and JDBC also supported).

---

## Runnable Services

A production deployment runs **four independent services** (plus an external registry and metadata DB). A fifth entry point, `StandaloneServer`, embeds all four in one JVM for development.

| Service | Module | Main class | Default ports |
|---------|--------|------------|---------------|
| **API** | [`dolphinscheduler-api`](dolphinscheduler-api/CLAUDE.md) | `org.apache.dolphinscheduler.api.ApiApplicationServer` | `12345` (HTTP / UI + REST) |
| **Master** | [`dolphinscheduler-master`](dolphinscheduler-master/CLAUDE.md) | `org.apache.dolphinscheduler.server.master.MasterServer` | `5679` (RPC) |
| **Worker** | [`dolphinscheduler-worker`](dolphinscheduler-worker/CLAUDE.md) | `org.apache.dolphinscheduler.server.worker.WorkerServer` | `1235` (RPC) |
| **Alert** | [`dolphinscheduler-alert`](dolphinscheduler-alert/CLAUDE.md) | `org.apache.dolphinscheduler.alert.AlertServer` | `50053` (HTTP), `50052` (RPC) |
| Standalone (dev only) | [`dolphinscheduler-standalone-server`](dolphinscheduler-standalone-server/CLAUDE.md) | `org.apache.dolphinscheduler.StandaloneServer` | `12345` + `50052` |

Every service is a `@SpringBootApplication` on Jetty and implements `IStoppable`. Scale Master / Worker / Alert horizontally; coordination happens via the registry (Zookeeper by default). API is stateless and also scales horizontally behind a load balancer.

Ports are overridable via `server.port` / service-specific keys in each service's `application.yaml`.

---

## Build & Run

```bash
# Full build (release profile; produces dist tarball)
./mvnw clean install -Prelease

# Zookeeper 3.4 legacy
./mvnw clean install -Prelease -Dzk-3.4

# Skip UI build (faster iteration on backend only)
./mvnw -pl '!dolphinscheduler-ui' clean install

# Build one module (+ its required siblings)
./mvnw -pl dolphinscheduler-master -am clean install

# Skip tests and spotless (fastest local build)
mvn clean install -DskipTests -Dspotless.skip=true

# Format (must pass before commit)
./mvnw spotless:apply
```

Binary artifact: `dolphinscheduler-dist/target/apache-dolphinscheduler-*-bin.tar.gz`.

---

## Test

```bash
# Unit tests for one module
./mvnw -pl dolphinscheduler-master test

# Run specific test class / method
mvn test -Dtest=WorkflowInstanceMapperTest -pl dolphinscheduler-dao
mvn test -Dtest=WorkflowInstanceMapperTest#testQueryByWorkflowDefinitionCode -pl dolphinscheduler-dao

# API integration tests (separate reactor, requires Docker)
mvn -pl dolphinscheduler-api-test/dolphinscheduler-api-test-case test

# E2E browser tests (Selenium + Docker)
mvn -pl dolphinscheduler-e2e/dolphinscheduler-e2e-case test

# Apple Silicon: add -Dm1_chip=true to the Docker-driven suites
```

---

## Architecture Overview

A **user** hits the UI, which calls the API server. The API server writes to the **metadata DB** and, for runtime operations (start / kill / pause workflow), talks to the **master** over RPC. The master consumes `t_ds_command` rows, runs the workflow state machine, and dispatches tasks to **workers**. Workers execute task plugins (shell, SQL, Spark, ...) and stream lifecycle events back to master. Failures and SLA breaches flow to the **alert server**, which fans out through alert plugins. **Registry** (Zookeeper / Etcd / JDBC) provides service discovery, leader election, and distributed locks. **Storage plugins** back the resource center and distributed-task artifacts. **Quartz** (via scheduler plugin) fires scheduled workflows, which become new `Command` rows.

### Workflow Execution Flow

1. User creates workflow via API/UI → stored in `t_ds_workflow_definition`
2. User triggers workflow → Command inserted into `t_ds_command`
3. CommandEngine scans command → creates WorkflowInstance
4. WorkflowEngine parses DAG → splits into TaskInstances
5. TaskDispatcher sends tasks to Worker based on task group config
6. Worker executes task using appropriate TaskPlugin
7. Worker reports status → Master updates state
8. Master monitors completion → handles downstream tasks

### Master-Worker Communication

- Master dispatches tasks via Netty RPC to Worker
- Worker reports status via RPC callbacks
- Registry (ZooKeeper/JDBC) tracks node health via heartbeats
- Failover mechanism reassigns tasks if Worker crashes

---

## Module Index

Click into a module's `CLAUDE.md` for details.

### Core Execution

- [`dolphinscheduler-master`](dolphinscheduler-master/CLAUDE.md) — workflow orchestration engine; consumes `Command`s, runs the DAG state machine, dispatches to workers.
- [`dolphinscheduler-worker`](dolphinscheduler-worker/CLAUDE.md) — runs physical tasks dispatched from master; hosts task plugins.
- [`dolphinscheduler-task-executor`](dolphinscheduler-task-executor/CLAUDE.md) — reusable task-lifecycle framework embedded by the worker.
- [`dolphinscheduler-alert`](dolphinscheduler-alert/CLAUDE.md) — alert server + channel plugins (email, Feishu, DingTalk, ...).

### API Layer

- [`dolphinscheduler-api`](dolphinscheduler-api/CLAUDE.md) — REST API server (entry point for UI, Python SDK, external clients).
- [`dolphinscheduler-api-test`](dolphinscheduler-api-test/CLAUDE.md) — integration tests against the REST API (Docker Compose + Testcontainers).
- [`dolphinscheduler-authentication`](dolphinscheduler-authentication/CLAUDE.md) — Actuator-endpoint auth + AWS credential helpers (NOT the main login path).

### Shared Libraries

- [`dolphinscheduler-common`](dolphinscheduler-common/CLAUDE.md) — foundation utilities (everything depends on this).
- [`dolphinscheduler-dao`](dolphinscheduler-dao/CLAUDE.md) — MyBatis DAO layer + SQL migration scripts.
- [`dolphinscheduler-service`](dolphinscheduler-service/CLAUDE.md) — business logic between DAO and the servers.
- [`dolphinscheduler-spi`](dolphinscheduler-spi/CLAUDE.md) — Service-Provider Interface root (every plugin depends on this).
- [`dolphinscheduler-extract`](dolphinscheduler-extract/CLAUDE.md) — RPC interface contracts between servers.
- [`dolphinscheduler-eventbus`](dolphinscheduler-eventbus/CLAUDE.md) — in-process event-bus abstractions.
- [`dolphinscheduler-registry`](dolphinscheduler-registry/CLAUDE.md) — pluggable registry (Zookeeper / Etcd / JDBC).
- [`dolphinscheduler-meter`](dolphinscheduler-meter/CLAUDE.md) — metrics (Prometheus) + server load-protection primitives.

### Plugin Families

- [`dolphinscheduler-task-plugin`](dolphinscheduler-task-plugin/CLAUDE.md) — task-type plugins (shell, SQL, Spark, Flink, K8s, EMR, ...). 33 concrete plugins.
- [`dolphinscheduler-datasource-plugin`](dolphinscheduler-datasource-plugin/CLAUDE.md) — user-facing datasource plugins (MySQL, Hive, Trino, Snowflake, ...). 28 concrete plugins.
- [`dolphinscheduler-storage-plugin`](dolphinscheduler-storage-plugin/CLAUDE.md) — resource storage (S3, HDFS, OSS, GCS, ABS, OBS, COS).
- [`dolphinscheduler-scheduler-plugin`](dolphinscheduler-scheduler-plugin/CLAUDE.md) — cron scheduler (Quartz today).
- [`dolphinscheduler-dao-plugin`](dolphinscheduler-dao-plugin/CLAUDE.md) — metadata-DB dialect support (MySQL / PostgreSQL / H2).

### Build, Ops, Tools

- [`dolphinscheduler-bom`](dolphinscheduler-bom/CLAUDE.md) — Maven BOM; central dependency version pinning.
- [`dolphinscheduler-dist`](dolphinscheduler-dist/CLAUDE.md) — assembles the release tarball + Docker images.
- [`dolphinscheduler-standalone-server`](dolphinscheduler-standalone-server/CLAUDE.md) — all-in-one JVM with H2 (dev / smoke tests).
- [`dolphinscheduler-tools`](dolphinscheduler-tools/CLAUDE.md) — CLIs for schema upgrade + resource / lineage migration.
- [`dolphinscheduler-microbench`](dolphinscheduler-microbench/CLAUDE.md) — JMH micro-benchmarks.
- [`dolphinscheduler-yarn-aop`](dolphinscheduler-yarn-aop/CLAUDE.md) — AspectJ weaver capturing YARN ApplicationIds.

### Frontend & E2E

- [`dolphinscheduler-ui`](dolphinscheduler-ui/CLAUDE.md) — Vue 3 frontend.
- [`dolphinscheduler-e2e`](dolphinscheduler-e2e/CLAUDE.md) — Selenium browser tests.

---

## Where Things Live (Quick Lookup)

| Looking for... | Start here |
|----------------|------------|
| A REST endpoint | `dolphinscheduler-api/src/main/java/.../api/controller/` |
| Workflow execution logic | `dolphinscheduler-master/src/main/java/.../server/master/engine/` |
| Task execution logic | `dolphinscheduler-worker` + the specific `task-plugin/<type>` |
| How "X" is stored | `dolphinscheduler-dao/src/main/java/.../dao/entity/` |
| SQL schema / upgrade | `dolphinscheduler-dao/src/main/resources/sql/` |
| RPC contract between servers | `dolphinscheduler-extract/dolphinscheduler-extract-<role>` |
| UI page source | `dolphinscheduler-ui/src/views/<feature>/` |
| API call in the UI | `dolphinscheduler-ui/src/service/modules/<resource>.ts` |
| Version of a dependency | `dolphinscheduler-bom/pom.xml` |

---

## Database Schema

Core tables follow naming convention `t_ds_*`:

| Table | Purpose |
|-------|---------|
| `t_ds_workflow_definition` | Workflow definitions (DAG structure) |
| `t_ds_workflow_instance` | Workflow execution instances |
| `t_ds_task_definition` | Task definitions |
| `t_ds_task_instance` | Task execution instances |
| `t_ds_command` | Command queue for workflow triggers |
| `t_ds_user`, `t_ds_project`, `t_ds_datasource` | Metadata |

---

## Project-Wide Conventions

- **Formatting**: Run `./mvnw spotless:apply` before every commit/push. CI runs `./mvnw spotless:check` and will fail PRs that are not formatted.
- **Commit style**: `[Type-ISSUE_ID][Scope] Subject`, e.g. `[Fix-18168][Worker] ...`. All types except `Chore` require an issue ID. See [commit-message.md](docs/docs/en/contribute/join/commit-message.md).
- **Branching**: `dev` is the main integration branch (not `main`/`master`).
- **PRs must link a GitHub issue** and keep their scope tight: one module / one concern.
- **Do not break wire / DB compatibility** silently. Changes to `extract-*` RPC interfaces, `dao` entities, enum values, and `spi.DbType` ripple to deployed clusters mid-upgrade.
- **Only one registry / storage / DB dialect is active at runtime**. Code paths that check "which one" belong inside the plugin SPI, not sprinkled through services.

---

## Plugin Development

When adding new task types:
1. Create module under `dolphinscheduler-task-plugin/`
2. Implement `TaskChannel` and `TaskChannelFactory`
3. Add `@AutoService(TaskChannelFactory.class)` annotation
4. Register in parent `pom.xml`

---

## Common Pitfalls

- **Port conflicts**: Check ports 5678, 1234, 12345, 2181, 5432 are free.
- **Registry connection**: Ensure ZooKeeper/JDBC registry is accessible before starting Master/Worker.
- **Database initialization**: Tables must exist before first launch.
- **Module scope**: Changes to DAO/Service layers require rebuilding Master/Worker/API.

---

## External References

- Release docs: https://dolphinscheduler.apache.org/en-us/docs
- GitHub issues: https://github.com/apache/dolphinscheduler/issues
- Python SDK: https://dolphinscheduler.apache.org/python/main/index.html
- Contribution guide: [`docs/docs/en/contribute/join/contribute.md`](docs/docs/en/contribute/join/contribute.md)
- API Docs (local): http://localhost:12345/dolphinscheduler/doc.html
- Default credentials: admin / dolphinscheduler123

---

<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

---

# Agent 协作快速指南

> 详细版见仓库根 `DEV_SETUP.md`。本节仅作为 agent 进项目的入口提示。

## 进项目必读顺序

1. **本文件**（AGENTS.md）—— GitNexus + Trellis 全局规则
2. **`DEV_SETUP.md`** —— 端口、启动、Swagger、agent 协作硬规则
3. **当前模块 `CLAUDE.md`**（如 `dolphinscheduler-api/CLAUDE.md`）—— 模块 gotchas
4. **`.trellis/tasks/<current>/prd.md` + `design.md` + `implement.md`** —— 当前任务上下文

## 编码硬规则（MUST）

- 改任何 symbol 前必须 `gitnexus_impact`，HIGH/CRITICAL 必须告知用户
- commit 前必须 `gitnexus_detect_changes` 核对影响面
- 新 controller 必须有 `@Tag/@Operation/@Parameter/@Schema`（前端代码生成依赖）
- 新审计场景必须有 `@OperatorLog`
- 数据库改动必须有升级脚本（`dolphinscheduler-dao/src/main/resources/sql/upgrade/`）
- 测试用 `./mvnw verify`，**不要**用 `mvn test`（jacoco 会报错）
- Postgres 端口 **5433**，不是 5432

## 不要做

- ❌ 跳过 `gitnexus_impact` 直接编辑
- ❌ 在 master/dev 分支直接 commit（除非用户明确要求）
- ❌ 用 `--no-verify` 绕过 hook
- ❌ 不读现有任务就建新任务
- ❌ 在用户没明确授权时 push / 创建 PR

## 标准工作流

`prd → design → implement → task.py start → trellis-implement → trellis-check → commit → PR`

完整说明见 `DEV_SETUP.md` §8。
