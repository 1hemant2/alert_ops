# Current task plan

## Task: Add agreed flow/node timing configuration

Started: 2026-10-06
Status: Complete

### Goal and scope

Implement task 4 in the [resolution-timeout plan](resolution-timeout-implementation-plan.md#4-add-agreed-flow-node-timing-configuration): add the flow-level resolution-timeout toggle and consistent per-node timing configuration, API validation, and flow-editor controls. Preserve current delivery behavior; runtime snapshot, acknowledgement pause, resolution, and timeout behavior remain out of scope.

### Acceptance criteria

- Disabled flows persist no node resolution timeout; enabled flows require a positive timeout on every node.
- Existing node duration remains the shared acknowledgement/delivery wait, with no separate send-delay setting.
- API updates are validated atomically/consistently and the editor exposes the agreed controls without partial configuration.

### Steps

- [x] Inspect the current flow/node model, persistence schema, APIs, editor, and tests.
- [x] Add the migration, model fields, validation, consistent update path, and editor controls.
- [x] Add focused tests for disabled/enabled validation, node updates, invalid input, and concurrent/partial update safety.
- [x] Run focused tests, normal builds, documentation checks, and review the final diff.

### Verification and limitations

- Verification: focused `FlowServiceTest`/`CreateFlowNodeUseCaseTest`, full `mvn test`, backend packaging, UI build, documentation target/anchor and whitespace checks, and focused diff review passed. Database-backed integration checks remain environment-gated; the suite reports 13 PostgreSQL, 1 Redis, and 3 RabbitMQ tests skipped. No usable independent read-only subagent mechanism was exposed in this environment, so that verifier was skipped.

### Resume note

After this task, continue with [runtime snapshot persistence](resolution-timeout-implementation-plan.md#5-persist-a-consistent-runtime-snapshot). Do not implement acknowledgement pause or resolution timeout behavior in this task.
