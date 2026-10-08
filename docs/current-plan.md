# Current task plan

## Task: Make response-step menu icons self-explanatory

Started: 2026-10-09
Status: Complete

### Goal and scope

Replace ambiguous Unicode action glyphs in the response-step menu with visible,
consistent icons and clearer labels so users can immediately understand the
available duplicate, edit, and delete actions.

### Resume note

This follows the completed duplicate-step action recorded in the
[changelog](../CHANGELOG.md). Preserve the existing step mutations, menu
behavior, route structure, and dark-mode support.

### Decision-compliance note

- Keep the existing action labels and handlers as the behavioral source of
  truth; this task changes only affordance clarity.
- Use the existing inline SVG styling approach rather than adding an icon
  dependency or a second icon system.

### Acceptance criteria

- [x] Duplicate, edit, and delete actions each show a recognizable visible
  icon in light and dark themes.
- [x] The three-dot trigger has a clear native tooltip and accessible label.
- [x] Existing menu actions and keyboard accessibility remain unchanged.
- [x] UI build, lint, whitespace checks, and browser inspection pass.

### Steps

- [x] Replace the Unicode menu glyphs with inline SVG icons and themed styles.
- [x] Add clearer hover guidance to the action trigger and menu items.
- [x] Verify the open menu visually in Chrome and run focused checks.
- [x] Record the completed result in `CHANGELOG.md`.

### Intended verification

Completed the UI build, lint, `git diff --check`, and Chrome inspection of the
open response-step menu in the current dark theme. Lint retains the two
existing `FlowDetailPage.tsx` warnings.
