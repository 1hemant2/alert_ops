# Current task plan

## Task: Move response steps reliably in both directions

Started: 2026-10-10
Status: Complete

### Goal and scope

Make dropping onto a numbered step choose that position, and allow direct
position selection. Refresh displayed numbers from the saved server order.

### Decision-compliance note

- Reuse PATCH /api/v1/flow/node/reorder with nodeId, afterNodeId, and version.
- The backend owns durable positions and renumbers them in increments of 1000.
  Display positions remain one-based indexes of the ordered server response.
- Preserve the responsive grid and scrolling changes already in the worktree;
  no schema or timing changes are included.

### Acceptance criteria

- [x] First/middle steps move later, including to the last position.
- [x] Later steps move earlier, including to the first position.
- [x] Dragging, keyboard arrows, and position selection share placement rules.
- [x] No-op and invalid moves do not submit writes; saved order updates labels.

### Steps and verification

- [x] Inspect frontend placement and backend reorder/renumbering behavior.
- [x] Add a shared position-to-predecessor rule and wire all reorder controls.
- [x] Cover forward/backward/boundary moves with focused tests.
- [x] Run ordering tests, backend reorder coverage, UI build/lint, and diff checks.
- [x] Record completion and browser verification limits in the changelog.

### Verification results

Four UI ordering tests and two backend reorder/version tests pass. UI build
and diff checks pass; lint retains the two existing flow-page warnings.
Backend production code is unchanged. Browser drag/drop and position-selector
interaction checks remain pending because browser access is unavailable.
