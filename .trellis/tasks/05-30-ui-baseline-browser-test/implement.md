# Implementation Plan

## Checklist

- [x] Confirm local service status: UI dev server, API endpoint, dependency containers if visible.
- [x] Start missing services only when safe and approved by normal tool permissions.
- [x] Open the UI in the in-app browser and test login with the documented default account.
- [x] Build the route coverage list from router files and runtime navigation.
- [x] Run desktop viewport browser pass across all reachable top-level pages.
- [x] Capture console errors and failed network requests.
- [x] Exercise safe primary controls and modals.
- [x] Create temporary `codex_baseline_` project/workflow/resource/security data where practical.
- [x] Create a PostgreSQL datasource using the local development PostgreSQL service.
- [x] Save screenshots for failures and representative pages.
- [x] Write `baseline-report.md` with coverage matrix, findings, setup notes, and follow-up tests.

## Validation

- Browser report includes explicit pass/fail/blocked status for every major route group.
- Any failure has a reproduction path.
- Report separates environment blockers from UI defects.
- `git status` shows only task/report artifacts changed by this task, no source code changes.

## Rollback

- Do not edit source code.
- List all temporary data created and either clean it up through the UI when practical or explicitly mark it retained for follow-up inspection.
- If a dev server is started by this task, leave the URL and process/session details in the report.
