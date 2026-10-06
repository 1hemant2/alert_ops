# Current task plan

## Task: Settle resolution lifecycle storage edge cases

Started: 2026-10-06
Status: Complete

### Goal and scope

Complete the prerequisite task in the [resolution-timeout plan](resolution-timeout-implementation-plan.md#implementation-tasks-one-at-a-time) before dependent implementation begins. Decide the final lifecycle/completion fields and active acknowledgement ownership rules; update the plan and launch checklist. No product-code changes are in scope.

### Acceptance criteria

- `RESOLVED` versus `COMPLETED` storage semantics are explicit and do not add an unnecessary reason field.
- Active acknowledgement ownership/deadline fields have clear set/clear rules, with historical facts delegated to audit events.
- The resolution-timeout plan and Requirement 3 readiness state agree that this prerequisite is complete and implementation remains pending.

### Steps

- [x] Inspect the current lifecycle model, migration constraints, and planned resolution behavior.
- [x] Decide and document final status, completion-reason, and acknowledgement-owner semantics.
- [x] Update the changelog and verify links, formatting, and unrelated diff changes.

### Verification and limitations

- Passed: documentation targets/anchors, `git diff --check`, 32-line plan-length check (within the 60-line limit), and focused diff review.
- Product tests/builds are not applicable because this task changes planning documentation only.
- Independent read-only verifier: skipped because no usable subagent mechanism is exposed in this environment, as required by `AGENTS.md`.

### Resume note

After this prerequisite, continue with the next pending implementation task, [unify execution-step statuses](resolution-timeout-implementation-plan.md#3-unify-execution-step-statuses), before the remaining resolution-timeout tasks.
