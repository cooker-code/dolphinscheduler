# DolphinScheduler UI Baseline Browser Report

Date: 2026-05-31 00:10 Asia/Shanghai

## Summary

Baseline result: **partially usable, with first-run development blockers**.

The local dependency containers were healthy, the API/Master/Worker services were reachable, and the Vite UI rendered all main route groups without blank screens. However, the browser UI session did not successfully bind authenticated API data into tables: project and datasource records existed and were returned by API calls, but the UI showed empty tables. PostgreSQL datasource creation through the API also failed with `illegal datasource type`, so datasource test data had to be inserted directly into Postgres.

## Environment

| Item | Observed |
|---|---|
| UI | `http://127.0.0.1:5173/`, HTTP 200 |
| API | `http://127.0.0.1:12345/dolphinscheduler/`, health UP |
| Master | `127.0.0.1:5679/actuator/health`, health UP |
| Worker | `127.0.0.1:1235/actuator/health`, health UP |
| Postgres | Docker healthy, `127.0.0.1:5433`, database `dolphinscheduler` |
| Zookeeper | Docker healthy, `127.0.0.1:2181` |
| Java | 17, while project docs recommend Java 8 |
| Node | 22.14.0, while UI docs recommend Node 16 |
| pnpm | 10.24.0, while UI docs recommend pnpm 7 |

## Data Created

| Data | Result |
|---|---|
| Project | Created through API: `codex_baseline_project_20260530`, code `174823919203488`, id `6` |
| PostgreSQL datasource | API create failed; inserted directly into DB: `codex_baseline_pg_20260530`, id `6`, type `POSTGRESQL` |
| Sequence repair | `t_ds_project_id_sequence` and `t_ds_datasource_id_sequence` were behind seeded max IDs and were advanced before data creation |

Created data was intentionally retained for follow-up inspection.

## Coverage

Desktop viewport route sweep covered 37 routes:

| Area | Routes Tested | Baseline Result |
|---|---:|---|
| Login/home/profile/password/about/UI setting | 6 | Rendered |
| Projects | 10 | Rendered; project detail routes used baseline project code |
| Resource center | 4 | Rendered |
| Datasource center | 1 | Rendered; table empty in UI despite API data |
| Monitor | 6 | Rendered |
| Security center | 10 | Admin-only routes redirected to token management |

Representative screenshots:

- [Login form](screenshots/login-form.png)
- [Projects list empty despite API data](screenshots/projects-list.png)
- [Datasource list empty despite API data](screenshots/datasource.png)
- [Workflow create page](screenshots/workflow-create.png)
- [Security admin route redirected to token page](screenshots/security-user-redirect.png)

## Findings

### P1: UI table data does not reflect authenticated API data

Affected pages:

- `/projects/list`
- `/datasource`

Observed:

- API login succeeds with `admin / dolphinscheduler123`.
- API project list returns the created baseline project and seeded projects.
- API datasource list returns the inserted baseline PG datasource and seeded datasources.
- Browser UI renders the pages but both project and datasource tables show `无数据` / total `0`.

Expected baseline:

- After login, project and datasource tables should show API-returned records.

Reproduction:

1. Open `http://127.0.0.1:5173/projects/list`.
2. Observe project table shows no data.
3. Query `GET /dolphinscheduler/projects?pageNo=1&pageSize=10` with a valid `sessionId`; API returns 6 projects.
4. Open `http://127.0.0.1:5173/datasource`.
5. Observe datasource table shows no data.
6. Query `GET /dolphinscheduler/datasources?pageNo=1&pageSize=10`; API returns 6 datasources.

Notes:

- The login page rendered and API login worked via curl.
- Browser automation filled the login form, but clicking/pressing Enter did not transition away from `/login`.
- This may be a UI session persistence/request-header issue or an automation interaction limitation; it needs a manual browser confirmation.

### P1: PostgreSQL datasource creation API rejects a supported datasource type

Observed:

- `POST /dolphinscheduler/datasources` with `type: "POSTGRESQL"` returned `code=10033`, message `create datasource error:illegal datasource type`.
- Frontend source modal lists `POSTGRESQL`.
- Backend `DbType.POSTGRESQL` exists.

Expected baseline:

- PostgreSQL datasource creation should be accepted or fail with a connection/validation error, not `illegal datasource type`.

Reproduction:

```bash
curl -X POST http://127.0.0.1:12345/dolphinscheduler/datasources \
  -H 'sessionId: <valid-session>' \
  -H 'Content-Type: application/json;charset=UTF-8' \
  --data '{"type":"POSTGRESQL","name":"codex_baseline_pg_20260530","host":"127.0.0.1","port":5433,"userName":"root","password":"root","database":"dolphinscheduler","other":{}}'
```

### P2: Fresh dev DB sequences are behind seeded table IDs

Observed:

- First project create failed with duplicate key on `t_ds_project_pkey`, `id=1`.
- `t_ds_project` max id was `5`, while `t_ds_project_id_sequence` last value was `1`.
- `t_ds_datasource` max id was `5`, while `t_ds_datasource_id_sequence` last value was `1`.

Expected baseline:

- A freshly initialized local database should allow immediate creation of records from the UI/API.

### P2: From-repo JVM startup is not reliable in this environment

Observed:

- `./mvnw -pl dolphinscheduler-api org.springframework.boot:spring-boot-maven-plugin:2.6.1:run ...` failed after dependency resolution attempts.
- Master and Worker startup commands failed similarly.
- Failure dependency: `org.apache.dolphinscheduler:dolphinscheduler-actuator-authentication:dev-SNAPSHOT`.
- Error: Apache snapshots SSL handshake terminated while resolving the `dev-SNAPSHOT` POM.

Expected baseline:

- With local project dependencies built/installed, service startup should not require remote snapshot resolution.

### P3: Security admin routes redirect to token management

Observed:

- Direct navigation to admin routes such as `/security/user-manage`, `/security/tenant-manage`, `/security/cluster-manage` landed on `/security/token-manage`.
- Token management itself rendered.

Expected baseline:

- Admin user should be able to access admin-only security pages, or the redirect should be documented and visible as permission behavior.

## Positive Baseline

- Vite started successfully with `npm run dev -- --host 127.0.0.1`.
- API, Master, Worker, Postgres, and Zookeeper were reachable.
- Main route groups rendered without blank screens.
- Workflow definition create page rendered the DAG/editor shell with visible toolbar entries.
- Datasource type selection modal rendered and included `POSTGRESQL`.
- Project create modal rendered.

## Suggested Follow-Up Tests

- Manual login in a visible browser and verify whether project/datasource tables populate.
- Re-run UI route sweep after explicitly clearing browser storage/cookies.
- Add a scripted smoke test that logs in, stores the session in the same way as the UI, and asserts project list is non-empty.
- Fix or document DB sequence initialization for seeded Postgres data.
- Start services from a clean checkout using the documented Java 8/Node 16 toolchain and compare startup behavior.
- Verify datasource plugin loading in API classpath, especially PostgreSQL.
