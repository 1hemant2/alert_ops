# Current task plan

## Task: Document separated commit categories for agents

Started: 2026-10-06
Status: Complete

### Goal and scope

Update [AGENTS.md](../AGENTS.md) so future commits keep backend, frontend, test,
and documentation changes separate. When staging is mixed and a commit is
requested, the agent must ask for confirmation before committing so the staged
files can be split, then use the order backend, frontend, tests, documentation.
No product code behavior changes are in scope.

### Acceptance criteria

- The rule requires one commit category at a time and defines confirmation before splitting mixed staging and the commit order.
- The guidance preserves unrelated unstaged changes while separating commits.
- Existing product behavior remains unchanged.

### Steps

- [x] Inspect existing scope and version-control guidance.
- [x] Add the separate commit-category rule to `AGENTS.md`.
- [x] Record the documentation change in the changelog and review the diff.

### Verification and limitations

- Passed: document links/anchors, whitespace, plan length, and diff review.
- Product code and existing staged changes remain unchanged.
- No behavior tests or builds were needed for this documentation-only rule update.
- Outcome recorded in [the changelog](../CHANGELOG.md); this documentation change remains uncommitted until explicitly requested.
