# Current task plan

## Task: Document Spring test-context dependency guidance

Started: 2026-10-06
Status: Complete

### Goal and scope

Update [AGENTS.md](../AGENTS.md) so future constructor-dependency changes keep
Spring MVC slice tests and direct constructor tests wired. Document the required
mock/configuration step and how to find the underlying cause of context failures.
No product behavior changes are in scope.

### Acceptance criteria

- The guidance covers `@WebMvcTest`, `@MockitoBean`, direct constructor tests, and context-failure diagnosis.
- The guidance is concise and consistent with the existing focused-test rule.
- Existing test and documentation changes remain untouched and uncommitted.

### Steps

- [x] Inspect the existing verification guidance and recent failure.
- [x] Add the Spring test-context dependency rule to `AGENTS.md`.
- [x] Record the guidance change in the changelog and review the diff.

### Verification and limitations

- Passed: whitespace, plan length, and diff review.
- The related `EscalationSecurityTest` fix was already verified with the focused test and backend packaging; this task only documents the prevention rule.
- Outcome recorded in [the changelog](../CHANGELOG.md); changes remain uncommitted.
