# Current task plan

## Task: Document explicit mode APIs for agents

Started: 2026-10-06
Status: Complete

### Goal and scope

Update [AGENTS.md](../AGENTS.md) so future implementations use explicit named
mode values instead of overloaded methods or boolean flags for materially
different business behavior. This documents the refactoring practice just used;
no product code behavior changes are in scope.

### Acceptance criteria

- The rule covers overloaded behavior methods, boolean mode flags, enums/request objects, and focused mode tests.
- The guidance is concise and consistent with the existing enum and transaction rules.
- Existing staged/unstaged product changes remain untouched.

### Steps

- [x] Inspect existing API and enum guidance.
- [x] Add the explicit mode API rule to `AGENTS.md`.
- [x] Record the documentation change in the changelog and review the diff.

### Verification and limitations

- Passed: document links/anchors, whitespace, plan length, and diff review.
- Product code and existing staged changes remain unchanged.
- No behavior tests or builds were needed for this documentation-only rule update.
- Outcome recorded in [the changelog](../CHANGELOG.md); the implementation and documentation changes are now committed separately.
