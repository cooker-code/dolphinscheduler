# UI baseline browser test before changes

## Goal

Create a pre-change baseline report for the local Apache DolphinScheduler UI by exercising the application through a browser and recording the first-run development experience before any product/code changes are made.

The baseline should help future changes answer: "Did the local UI experience, key navigation, core pages, or obvious browser/API behavior regress compared with the untouched local setup?"

## Confirmed Facts

- Repository: Apache DolphinScheduler monorepo.
- UI module: `dolphinscheduler-ui`, Vue 3 + Vite + TypeScript + Naive UI.
- Local UI dev URL: `http://localhost:5173`.
- Local API URL expected by `.env.development`: `http://localhost:12345`, context `/dolphinscheduler`.
- Default login from `DEV_SETUP.md`: `admin / dolphinscheduler123`.
- Frontend route groups present in code:
  - Login, home, profile, password, about.
  - Projects: project list, overview, parameters, preferences, workflow relation, workflow definitions, timing, workflow instances, task instances, workflow detail/tree/gantt routes.
  - Resource center: file management, file create/edit/detail routes, task group option, task group queue.
  - Datasource center.
  - Monitor: master, worker, alert server, DB, statistics, audit log.
  - Security: tenant, user, alarm group, worker group, yarn queue, environment, cluster, token, alarm instance, K8S namespace.
  - UI setting.

## Requirements

- Do not modify production source code as part of this task.
- Verify the local development environment status before browser testing:
  - Dependency containers where applicable.
  - API reachability.
  - UI dev server reachability.
- Use browser-driven testing as the primary evidence source.
- Capture console errors, failed network requests, route load failures, visible rendering/layout problems, and blocked interactions.
- Cover all top-level navigation groups and the primary routed pages discovered from the frontend router.
- For project-scoped pages, create and use temporary baseline project data.
- For forms and mutating flows, create temporary baseline data with a `codex_baseline_` prefix where field constraints allow it.
- Datasource write-path testing must include a PostgreSQL datasource using the local development PostgreSQL service.
- Produce a baseline report in the task directory with:
  - Environment and service status.
  - Tested browser, viewport(s), locale, and user.
  - Route/function coverage matrix.
  - Findings grouped by severity.
  - Screenshots or file references for important failures.
  - Reproduction steps for every actionable failure.
  - Suggested follow-up tests and edge cases.

## Acceptance Criteria

- [ ] A task-local baseline report exists under `.trellis/tasks/05-30-ui-baseline-browser-test/`.
- [ ] The report states whether the UI, API, and required local dependencies were already running or had to be started.
- [ ] Login is tested and documented.
- [ ] Every major route group listed in Confirmed Facts is visited or explicitly marked blocked with a reason.
- [ ] At least one viewport representative of desktop local development is tested.
- [ ] Browser console errors and network failures are captured and summarized.
- [ ] Findings include severity, affected page/route, observed behavior, expected baseline behavior, and reproduction notes.
- [ ] The report clearly distinguishes environment/setup failures from UI product defects.
- [ ] No source-code changes are made.

## Out Of Scope

- Fixing bugs found during baseline testing.
- Exhaustive cross-browser certification.
- Full backend API contract testing independent of UI.
- Full permutation testing of every form field and every plugin type.
- Load/performance benchmarking beyond basic page responsiveness observations.

## Decisions

- Temporary local test data mutation is required.
- Test data should use a `codex_baseline_` prefix where possible.
- PostgreSQL datasource creation should use the local development PostgreSQL service.
