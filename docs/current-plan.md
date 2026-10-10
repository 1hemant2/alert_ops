# Current task plan

## Task: Fix home-page theme toggle contrast

Started: 2026-10-10
Status: Complete

### Goal and scope

Make the home-page Dark mode button readable when the application is in light
mode. Keep the public header dark and change only the toggle's light-mode
foreground and border contrast.

### Resume note

This follows the completed UI theme-control work recorded in the
[changelog](../CHANGELOG.md). Preserve the persisted theme behavior, public
header layout, and dark-mode appearance.

### Decision-compliance note

- Keep the existing theme state and toggle behavior as the source of truth.
- Scope the correction to the public home-page header in light mode; do not
  change global light or dark surfaces.

### Acceptance criteria

- [x] The home-page Dark mode button has readable text, icon, border, and
  background contrast in light mode.
- [x] Dark mode and the existing public-header layout remain unchanged.
- [x] UI build, lint, and whitespace checks pass.

### Steps

- [x] Add the scoped light-mode public-header override.
- [x] Run UI checks and inspect the final diff.
- [x] Record the completed result in `CHANGELOG.md`.

### Intended verification

UI build and `git diff --check` pass. Lint passes with the two existing
`FlowDetailPage.tsx` warnings. Browser visual inspection was not available in
this session; the selector is scoped to the home-page public header.
