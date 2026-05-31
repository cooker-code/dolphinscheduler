# Design

## Boundary

This task is a browser baseline, not a feature implementation. It will not modify application source code. It may create task-local report artifacts and screenshots under `.trellis/tasks/05-30-ui-baseline-browser-test/`.

## Test Strategy

The baseline uses the frontend router as the coverage source of truth. Routes are grouped by user-facing areas:

- Authentication and shell: login, home, profile, password, about, UI setting.
- Projects: list, overview, parameters, preferences, workflow relation, workflow definitions, timings, workflow instances, task instances, workflow detail routes where reachable.
- Resource center: file management, file editor routes, task group option/queue.
- Datasource center.
- Monitor: master, worker, alert server, DB, statistics, audit log.
- Security: tenant, user, alarm group, worker group, yarn queue, environment, cluster, token, alarm instance, K8S namespace.

Each page is evaluated with a consistent checklist:

- Route loads without a blank screen or redirect loop.
- Main content and navigation render.
- Console errors are collected.
- Failed network requests are collected.
- Primary table/search/create controls are visible where expected.
- For modal-heavy pages, the primary create/detail modal opens when safe.

## Evidence

Evidence should be saved in the task directory:

- `baseline-report.md`: main report.
- `screenshots/`: screenshots for failures or representative route groups.
- Optional `browser-log.json`: structured browser/network observations if automation produces one.

## Data Handling

Temporary mutation is required. Test records should use the `codex_baseline_` prefix where field constraints allow it. The report must list created data and whether it was cleaned up or intentionally retained for follow-up inspection.

PostgreSQL datasource testing should use the local development PostgreSQL service from `DEV_SETUP.md`:

- Host: `127.0.0.1`
- Port: `5433`
- Database: `dolphinscheduler`
- User: `root`
- Password: `root`

## Risk Notes

- A fresh local environment may lack backend services or initialized metadata. Those are baseline findings, not UI regressions.
- Some project detail routes require an existing project/workflow/instance; create baseline project/workflow data where practical and document remaining blockers.
- Plugin-specific forms may require external systems; validate form rendering and required-field behavior rather than external connectivity unless credentials already exist locally.
