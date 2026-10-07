# Current task plan

## Task: Complete task and run context UI

Started: 2026-10-08
Status: Complete

### Goal and scope

Complete the next launch-checklist item by adding task detail/editing and
showing all saved task metadata in task and escalation views. Reuse existing
task APIs and execution-step snapshots. Keep webhook event history and
deployment verification out of scope.

Previous backend task: task metadata snapshotting and email display is complete
in commits `f2d4f7a`, `83c4627`, and `9577f23`.

### Decision-compliance note

- Task detail/editing uses the existing team-scoped task API; do not add a
  duplicate UI-only task store or a new backend endpoint without evidence.
- Active and historical escalation views use `FlowExecutionState` snapshot
  fields, not the mutable current task, for run context.
- Keep optional priority, category, and reference URL nullable and display
  clear fallback text when older rows do not contain them.

### Acceptance criteria

- Team members can open a task from the task library, view all fields, edit
  name, description, source, priority, category, and reference URL, and see
  saved success/error states.
- The task list exposes useful metadata and links to the task detail view.
- Escalation detail displays saved run metadata from execution snapshots,
  including after the task is edited later.
- UI typecheck, lint, production build, focused tests if available, and diff
  checks pass; launch documentation records the updated status.

### Steps

- [x] Add task detail route, edit form, and task-list metadata/linking.
- [x] Render saved task metadata in the escalation context card.
- [x] Run UI verification and update launch documentation/changelog.

### Verification and limitations

`npm run typecheck` and `npm run build` pass. `npm run lint` passes with two
pre-existing warnings in `FlowDetailPage.tsx`; the new task UI adds no lint
warnings. No focused UI test script exists. Browser/deployed verification
remains pending, and the separate webhook-history UI task is not included.
Independent read-only verification was skipped because no usable subagent
mechanism was available.
