# Current task plan

## Task: Label task details in escalation emails

Started: 2026-10-10
Status: Complete

### Goal and scope

Give the existing task description a clear `Task details` heading immediately
after the task metadata in both the plain-text and HTML email formats.

### Decision-compliance note

- Reuse the existing `taskDetails` field as the canonical source; do not add a
  second email or database field.
- Keep the current Markdown fallback, HTML escaping, compact dark styling, and
  recipient-scoped action links unchanged.

### Acceptance criteria

- [x] Plain-text email labels the description as `Task details` after `Task`.
- [x] HTML email renders the same content as a separate readable section.
- [x] Existing escaping, links, and email layout remain unchanged.

### Steps

- [x] Inspect the existing email structure and focused test.
- [x] Add the labeled details section and assertions.
- [x] Run the focused notification test, backend package, and diff checks.
- [x] Update the changelog and mark this plan complete.

### Intended verification

`NotificationTest`, the normal backend package with the repository's test
agent, and `git diff --check` pass. Visual SMTP-client inspection remains
outside local verification.
