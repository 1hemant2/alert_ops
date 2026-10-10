# Changelog

Completed repository changes, newest first. Tracking begins on 2026-10-05;
earlier work has not been reconstructed. Planning and archive rules are in
[AGENTS.md](AGENTS.md#task-plans-and-documentation).

## 2026-10-10 — Label task details in escalation emails

- Add a dedicated `Task details` section after the task metadata so the
  description is clearly separated in both plain-text and HTML email formats.
- Verification: `NotificationTest`, the backend package, and `git diff --check`
  pass. Visual SMTP-client inspection remains pending.

## 2026-10-10 — Send the first escalation alert immediately

- Send the first response-step email immediately for every start mode, include
  the escalation name, ID, and response window, and keep later-step timing and
  retry behavior unchanged.
- Restyle the HTML email as a compact dark responsive card with wrapped long
  values while preserving the plain-text fallback and safe action links.
- Verification: focused unit tests, seven PostgreSQL scheduling tests, the
  backend package, and `git diff --check` pass. SMTP delivery and multi-client
  visual inspection remain pending.

## 2026-10-10 — Register timers after escalation starts

- Publish scheduling events normally and let a Spring transaction event listener
  register them after commit, with immediate fallback for transactionless
  callers. Read committed state in a separate read-only transaction so cached
  pre-start statuses cannot silently prevent timer registration.
- Verification: seven focused PostgreSQL cases cover all start modes and due
  publication, zero delay, rollback, fallback, and recovery; the backend package
  passes with a test agent, and `git diff --check` passes. The local app needs a
  restart; real broker/email verification remains pending. See the
  [timer handoff guidance](docs/in-memory-timer-implementation-plan.md#durable-scheduling-state).

## 2026-10-10 — Style escalation notification email

- Refresh the escalation email with the ReplyTrail navy, mint, and blue
  palette, a branded header, a structured task panel, and clearer actions while
  preserving the plain-text fallback and recipient-scoped links.
- Verification: focused mail test and `./mvnw -q package` pass with 240 tests,
  0 failures/errors, and 17 environment-gated skips; `git diff --check` passes.
  Visual email-client inspection was unavailable.

## 2026-10-10 — Fix home-page theme toggle contrast

- Give the light-mode Dark button in the public home-page header readable
  foreground and border contrast without changing the persisted theme behavior.
- Verification: UI build and `git diff --check` pass; lint retains the two
  existing `FlowDetailPage.tsx` warnings, and browser visual inspection was not
  available in this session.

## 2026-10-09 — Simplify response-route scanning

- Keep the route card focused on the step count, Add response step, and a
  compact horizontal card rail with a visible next-card cue; move rail controls
  out of the header.
- Add a page-header Resolution timeout shortcut with an ON/OFF status that
  opens and scrolls to the existing path settings disclosure.
- Verification: UI build, lint, and `git diff --check` pass; protected route
  browser inspection was unavailable because the local browser session was at
  the login screen. Lint retains the two existing `FlowDetailPage.tsx`
  warnings.

## 2026-10-09 — Refine response-route navigation

- Restore the established compact response-step card size and add a partial
  next-card cue with accessible previous/next controls for longer routes.
- Verification: UI build, lint, `git diff --check`, and Chrome inspection of
  compact cards, overflow cues, and rail navigation pass; lint retains the two
  existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Expand the response-route workspace

- Move Add response step into the route card and change the ordered steps to a
  spacious single-row rail that uses the available width and scrolls
  horizontally for longer paths.
- Verification: UI build, lint, `git diff --check`, and Chrome inspection of
  the route header and 11-card horizontal overflow pass; lint retains the two
  existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Keep active escalation activity live

- Poll the escalation detail activity timeline every three seconds alongside
  status and step progress while a run is `OPEN` or `ACKNOWLEDGED`, then stop
  polling for terminal and scheduled states.
- Verification: UI build, lint, `git diff --check`, and Chrome detail-page
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Clarify response-step action icons

- Replace ambiguous Unicode glyphs with visible duplicate, edit, and delete
  icons, and add native guidance to the three-dot step action trigger.
- Verification: UI build, lint, `git diff --check`, and Chrome dark-theme menu
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Add response-step duplication

- Add a Duplicate step action to each response-step menu; it copies the
  recipient, wait time, and enabled timeout, then places the new step after
  its source without requiring a trip back to the page header.
- Verification: UI build, lint, `git diff --check`, and Chrome menu and ordered
  duplicate inspection pass; lint retains the two existing
  `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Focus escalation paths on response steps

- Make the ordered response route the primary path-detail view, move resolution
  timeout into a separate collapsed Path settings disclosure, and remove the
  repeated timeout field from step cards and the step editor.
- Verification: UI build, lint, `git diff --check`, and Chrome route, settings,
  and step-editor inspection pass; lint retains the two existing
  `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Label collapsed sidebar icons on hover

- Add native hover titles and accessible names to sidebar navigation icons,
  workspace switching, the escalation engine, sign-out, and the sidebar
  toggle so the collapsed rail remains understandable.
- Verification: UI build, lint, `git diff --check`, and Chrome collapsed and
  expanded inspection pass; lint retains the two existing `FlowDetailPage.tsx`
  warnings.

## 2026-10-09 — Place the sidebar toggle inside the navigation rail

- Move the desktop expand/collapse control beside the ReplyTrail mark and keep
  it inside the collapsed rail beneath the mark, making the control feel owned
  by the navigation while preserving mobile drawer access.
- Verification: UI build, lint, `git diff --check`, and Chrome expanded,
  collapsed, reopened, and light/dark inspection pass; lint retains the two
  existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Restore dark-mode task detail contrast

- Add dark-theme colors for the saved task description, field labels, values,
  links, and metadata separators so task context remains readable against the
  dark card surface.
- Verification: UI build, lint, `git diff --check`, and Chrome light/dark task
  detail inspection pass; lint retains the two existing `FlowDetailPage.tsx`
  warnings.

## 2026-10-09 — Modernize the sidebar toggle

- Replace the text-heavy navigation control with a persistent icon-only
  panel-and-arrow toggle whose icon and accessible label follow the sidebar
  state; the same control expands and collapses navigation.
- Verification: UI build, lint, `git diff --check`, and Chrome expanded,
  collapsed, and reopened inspection pass; lint retains the two existing
  `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Improve collapsed sidebar identity spacing

- Add breathing room between the ReplyTrail mark and workspace avatar when the
  sidebar is collapsed, making the two identity cues easier to distinguish.
- Verification: UI build, lint, `git diff --check`, and Chrome expanded/collapsed
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Use one persistent sidebar toggle

- Remove the duplicate close button from the sidebar and make the top-bar
  toggle the single control for opening, collapsing, and closing navigation;
  keep it visible as the workspace scrolls.
- Verification: UI build, lint, `git diff --check`, and Chrome expanded,
  collapsed, reopened, and scrolled inspection pass; lint retains the two
  existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Polish team-selection theme spacing

- Give the team-selection theme toggle more breathing room and anchor the page
  to the viewport theme so dark mode no longer exposes a light strip at the top
  after switching.
- Verification: UI build, lint, `git diff --check`, and Chrome light/dark
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Make sidebar reopening discoverable

- Add visible Menu and Open menu labels to the workspace navigation toggle so
  the collapsed sidebar has an obvious reopen action; keep the expanded sidebar
  branding clean and retain the accessible close control.
- Verification: UI build, lint, `git diff --check`, and Chrome expanded,
  collapsed, and reopened inspection pass; lint retains the two existing
  `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Keep workspace navigation open and reachable

- Open the desktop sidebar by default on each page load while retaining the
  collapse/reopen control, and make the full navigation independently scrollable
  so the escalation engine and Sign out controls remain reachable.
- The existing landing and workspace copy already explains the product clearly
  on first view and remains unchanged. Verification: UI build, lint,
  `git diff --check`, and Chrome fresh-load, collapse/reopen, and sidebar-scroll
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Refine workspace navigation spacing

- Replace one-off sidebar margins with consistent navigation groups, link
  heights, section gaps, workspace spacing, and a separated bottom system area
  so the expanded and collapsed menu states share the same visual rhythm.
- Verification: UI build, lint, `git diff --check`, and Chrome expanded/collapsed
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Reorganize and dismiss the workspace navigation

- Group the signed-in menu into Monitor, Configure, and Team sections, then add
  desktop collapse behavior plus a closeable mobile navigation drawer with
  backdrop and reopen controls.
- Verification: UI build, lint, `git diff --check`, and Chrome interaction
  checks pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Add date filters to the Audit trail

- Add inclusive From date and To date filters to the frontend Audit trail,
  resolved against the user's local calendar, with clear dates and invalid-range
  feedback; the existing backend audit contract remains unchanged.
- Verification: UI build, lint, `git diff --check`, and Chrome interaction
  checks pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Standardize natural user-facing copy

- Add repository guidance to avoid comma-separated slogan fragments in UI,
  emails, notifications, templates, and user-facing documentation; replace the
  Audit page heading with “Review every handoff in one timeline.”
- Verification: targeted phrase search, UI build, lint, and `git diff --check`
  pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Add a frontend-only audit trail page

- Add a signed-in Audit trail page that aggregates all available escalation
  history through the existing APIs, with escalation links, actor/time/state
  context, search, event filtering, refresh, empty/error/loading states, and
  light/dark responsive styling; no backend changes were required.
- Verification: UI build, lint, `git diff --check`, and Chrome inspection pass;
  lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Refine the automatic recovery badge

- Turn the reliability card's recovery label into a compact icon badge that
  stays aligned with the heading on desktop and stacks cleanly on mobile.
- Verification: UI build, lint, and whitespace checks pass; lint retains the
  two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Modernize overview workflow cards

- Add meaningful task, path, response, saved-state, handoff, and recovery icons
  to the setup/reliability cards, with accent rails, connected step styling,
  badges, softer surfaces, and clearer hover hierarchy.
- Verification: UI build, lint, and whitespace checks pass; lint retains the
  two existing `FlowDetailPage.tsx` warnings. Live workspace visual inspection
  was limited because the browser had no authenticated session.

## 2026-10-09 — Add clickable workspace breadcrumbs

- Add route-aware breadcrumbs to the signed-in shell so collection and detail
  pages expose clickable parent levels such as Team → Tasks → Task details.
- Verification: UI build, lint, and whitespace checks pass; live route-click
  inspection was limited because the browser had no authenticated workspace.

## 2026-10-09 — Add icons to overview metrics

- Replace placeholder metric letters/arrows with the existing task, flow,
  escalation, and overview SVG icons while preserving clickable destinations.
- Verification: UI build, lint, and whitespace checks pass; lint retains the
  two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Make overview metrics clickable

- Turn the four overview metric cards into semantic links to Tasks, Escalation
  paths, and Escalations, with full-card hover, focus, and arrow affordances.
- Verification: UI build, lint, and whitespace checks pass; live click testing
  was unavailable because the browser had no authenticated workspace session.

## 2026-10-09 — Unify the response tagline

- Replace the comma-based “Your response, in order.” overview title with the
  natural existing ReplyTrail tagline, “Keep every response on track.”
- Verification: UI build, lint, phrase search, and whitespace checks pass; the
  old phrase is absent from the product UI.

## 2026-10-09 — Improve email footer wording

- Replace the awkward comma-based footer slogan in invitation and escalation
  notification emails with the existing natural ReplyTrail tagline,
  “Keep every response on track.”
- Verification: backend package, wording search, and whitespace checks pass.

## 2026-10-09 — Add password visibility controls

- Add a reusable eye-icon show/hide control to sign-in and registration
  password fields, keeping passwords masked by default with accessible state
  labels.
- Verification: UI build, lint, whitespace checks, and Chrome interaction
  checks pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Silence development SQL statement output

- Disable Hibernate SQL rendering and formatting in the local `dev` profile so
  debugger terminals keep normal application logs without printing every query.
- Verification: `mvn -DskipTests package`, property inspection, and whitespace
  checks pass; production SQL logging settings remain unchanged.

## 2026-10-09 — Clarify the escalation overview description

- Replace the technical-sounding overview description with clearer copy that
  emphasizes reliable ownership from the first alert to the final handoff.
- Verification: UI build, lint, and whitespace checks pass; lint retains the
  two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Soften the light UI palette

- Replace stark white light-mode canvases, cards, controls, public sections,
  product previews, and dialogs with muted blue-gray/off-white surfaces while
  preserving text contrast and semantic status colors.
- Verification: UI build, lint, whitespace checks, and Chrome light/dark theme
  inspection pass; lint retains the two existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Turn the home page into an interactive product tour

- Replace static use-case tiles with thirteen clickable workflow stories that
  open accessible details for the scenario, owner handoff, timing, and finish
  condition; add a responsive signed-in workspace preview of an active run.
- Add theme-aware product-window, card, dialog, and mobile styling with
  reduced-motion-safe interactions, keeping the existing registration and sign-in
  paths unchanged.
- Verification: UI build and whitespace checks pass; Chrome inspection confirmed
  the preview, all cards, dialog content, and close behavior. Lint retains the
  two pre-existing `FlowDetailPage.tsx` warnings.

## 2026-10-09 — Load local env from the VS Code Java launch profile

- Configure the `AlertOpsApplication` debug profile to load the ignored root
  `.env` and send Spring logs to the integrated terminal.
- Verification: launch JSONC parsing and whitespace checks pass; the existing
  backend remains healthy on port 8096 with the same local env. The VS Code GUI
  launch itself remains a user-side F5 action.

## 2026-10-09 — Prepare the local full-stack environment

- Reconcile the ignored root `.env` with Docker Compose's PostgreSQL, RabbitMQ,
  Redis, UI URL, and application defaults; add ignored `ui/.env` with the local
  Vite proxy configuration and add the missing email-verification example key.
- Install Docker CLI/Compose and native PostgreSQL 16/RabbitMQ dependencies;
  start the native services and initialize the local `alert_ops` database using
  the existing ignored credentials without exposing secret values.
- Verification: Compose config, backend compile, UI build, Vite proxy, backend
  liveness, Flyway migrations, and native service checks pass. Docker/Colima VM
  image downloads stalled, so the app was verified with native services instead.

## 2026-10-09 — Improve light-mode contrast and home-page engagement

- Strengthen light-mode text, border, control, card, and secondary-surface
  contrast for easier reading across the UI.
- Add workflow-benefit cues, a ready-to-run workflow count, richer public-section
  surfaces, hover feedback, and subtle reduced-motion-safe status animation.
- Verification: UI build, lint, diff checks, desktop/mobile Chrome inspection,
  and light/dark theme checks pass; lint retains two pre-existing warnings.

## 2026-10-08 — Add persisted dark mode to the UI

- Add a local-storage-backed light/dark theme with pre-paint restoration and
  accessible toggles across public, auth, workspace, team, invitation, and
  email-action surfaces.
- Add dark styling for shared navigation, forms, cards, notices, statuses, path
  builders, and public use-case sections without changing product behavior.
- Verification: UI build, lint, diff checks, public switching/reload, and sign-in
  screen inspection pass; lint retains two pre-existing FlowDetailPage warnings.

## 2026-10-08 — Bring ReplyTrail use cases to the home page

- Add a scannable public landing-page section for all thirteen README use cases,
  with plain-language descriptions, responsive cards, a hero anchor link, and
  the existing SVG favicon linked for a clean browser preview.
- Verification: UI build, `git diff --check`, and Chrome desktop/400px responsive
  inspection pass; the hero anchor and existing signup links work. UI lint retains
  two pre-existing FlowDetailPage warnings; live email and deployed journeys were
  not tested.

## 2026-10-08 — Explain ReplyTrail and its use cases

- Rewrite the [README](README.md) introduction for new visitors with thirteen use
  cases in plain language and definitions of the core concepts. Simplify setup
  and ten personal reminders, keeping timing and acknowledgement behavior clear;
  remove the extended customer onboarding walkthrough.
- Document current scheduling, resolution, manual actions, and history; correct
  the first-use walkthrough and Spring Boot version while retaining setup/API examples.
- Verification: UI build, Maven verification outside the sandbox (240 tests,
  17 environment-gated skips), link/example checks, and whitespace checks pass. UI lint retains two
  existing warnings; live email, Docker startup, and deployed journeys were not tested.

## 2026-10-08 — Remove mandatory secondary review instructions

- Remove the repository-level requirement and maintained-plan references that
  caused an extra review process to run for every task.
- Preserve historical changelog evidence while keeping current instructions
  focused on direct local verification.
- Verification: targeted instruction search and `git diff --check` pass; no
  product build was needed for this documentation-only change.

## 2026-10-08 — Implement the ReplyTrail public rebrand

- Rename visible product surfaces, metadata, current documentation, CI display
  labels, verification/invitation mail, and escalation notifications to ReplyTrail.
- Keep `com.alertops`, `alertops.*`, `ALERTOPS_*`, webhook headers, session keys,
  queues, caches, and deployment identifiers unchanged for compatibility; domain,
  sender, and deployed rollout work remain pending.
- Verification: UI build and 14 focused branding/email tests pass; full Maven clean
  verification passes (240 tests, 17 environment-gated skips); lint retains two
  existing FlowDetailPage warnings. Independent read-only review: **Achieved**;
  residual references, compatibility paths, and 132 documentation links/anchors
  were checked. Browser/deployed, domain, sender, and real-delivery checks remain pending.

## 2026-10-08 — Plan the product rebrand

- Add a [phased product rebranding plan](docs/product-rebranding-plan.md) covering
  identity selection, UI and emails, compatibility, verification, rollout, and rollback.
- Link the pending initiative from launch readiness; final name/domain selection
  and implementation remain open. This task changes documentation only.
- Verification: document links, diff checks, UI build, and full Maven verification
  pass (238 tests, 17 environment-gated skips); lint retains two existing warnings.
  Independent read-only review: Achieved; runtime/deployed checks await implementation.

## 2026-10-08 — Fix CI container image build

- Remove brittle exact Jammy OpenSSL package pins while continuing to install
  the current patched `libssl3` and `openssl` packages from the pinned base.
- Add plain Docker build output and a failure-log artifact to make future image
  failures diagnosable.
- Verification: GitHub Actions run `37686152716` passed Maven verification,
  container image build, and Trivy scanning; local Docker was unavailable.

## 2026-10-08 — Fix Spring Boot 4 integration-test imports

- Update integration-test Redis, JDBC, JPA, AMQP, and entity-scan imports to
  the Spring Boot 4 packages and class names, restoring clean CI compilation.
- Verification: clean test compilation and `./mvnw --batch-mode
  --no-transfer-progress clean verify` pass with 238 tests, 0 failures/errors,
  and 17 environment-gated skips; `git diff --check` passes.

## 2026-10-08 — Complete webhook event history UI

- Add expandable webhook event history and link each event to its specific task
  detail page while preserving the existing run and payload links.
- Verification: UI typecheck and production build pass; lint passes with two
  pre-existing flow-editor warnings, and `git diff --check` passes. PostgreSQL,
  Redis, browser, and deployed verification remain pending.

## 2026-10-08 — Complete task and run context UI

- Add team-scoped task detail/editing, metadata links, and saved task context on
  escalation details using the run snapshot rather than mutable task values.
- Verification: UI typecheck and production build pass; lint passes with two
  pre-existing flow-editor warnings. Browser/deployed verification and older
  webhook event navigation remain pending.

## 2026-10-08 — Snapshot task metadata in executions and emails

- Persist priority, category, and reference URL in each execution-step snapshot
  at run start and include the saved values in escalation email content without
  changing later runs when the task is edited.
- Add migration and focused coverage; `./mvnw -q package` passes with 260 tests,
  0 failures/errors, and 17 environment-gated skips. PostgreSQL migration and
  deployed email verification remain pending; task/webhook UI gaps remain open.

## 2026-10-08 — Fix backend image vulnerabilities

- Upgrade the backend to Spring Boot `4.0.8`/Spring Framework `7.0.9`, apply fixed
  Jackson, RabbitMQ, and Tomcat versions, refresh pinned container bases, and update
  the Hibernate 7 and MVC test compatibility imports.
- Verification: backend Maven dependencies report no HIGH or CRITICAL findings,
  `./mvnw -q package` passes with 260 tests and 17 environment-gated skips, and
  `git diff --check` passes. Final Docker image build/scan remains for CI because
  Docker is unavailable locally; the UI has a separate `source-map-js` finding.

## 2026-10-08 — Refresh launch checklist reassessment date

- Update the product launch checklist header to reflect the latest readiness review date without changing any implementation or production-verification status.
- Verification: documentation diff review and `git diff --check` pass; no runtime tests were needed and the change remains uncommitted.

## 2026-10-08 — Harden webhook task validation and ingress limits

- Align manual and webhook task limits, validate manual priority and description updates, inject the application clock for webhook event timing, and reject oversized declared webhook bodies before JSON parsing.
- Add focused coverage for webhook metadata, required/optional validation, same-team flow selection, secret state, replay/conflict, failure paths, size/rate limits, and manual task limits.
- Verification: focused tests and `./mvnw -q package` pass (260 tests, 0 failures/errors, 17 environment-gated skips); PostgreSQL/Redis multi-instance and deployed verification remain pending, and changes are intentionally uncommitted.

## 2026-10-08 — Complete incident lifecycle test coverage

- Add focused authorization and lifecycle-boundary tests for anonymous access, foreign-team starts, early scheduled starts, and preserving scheduler wake-ups when a start fails; retain the existing race, idempotency, acknowledgement, resolution, and timeout coverage.
- Mark incident lifecycle implementation complete locally while keeping PostgreSQL and deployed verification open.
- Verification: lifecycle-focused tests, full Maven package, and diff checks pass; PostgreSQL/deployed integration and independent read-only verification remain unavailable. Changes are intentionally uncommitted.

## 2026-10-08 — Add escalation activity timeline UI

- Add a read-only escalation activity section backed by the paginated history API, with plain milestone wording, actor/time/details, retry grouping, expandable attempts, empty/error/loading states, refresh, and load-more behavior.
- Keep current saved step progress separate and invalidate activity history after lifecycle actions; token-like and raw metadata remain excluded from the UI.
- Verification: UI build, UI lint, full Maven package, and diff checks pass; lint has two pre-existing flow-page warnings, browser/deployed verification remains pending, and no independent read-only verifier was available. Changes are intentionally uncommitted.

## 2026-10-08 — Add escalation activity history API

- Add a read-only, same-team paginated escalation history endpoint with stable ordering, explicit user/system actors, structured safe details, and removal of token-like metadata.
- Verification: focused history tests, full Maven package (232 tests, 0 failures/errors, 17 environment-gated skips), and diff checks pass; PostgreSQL/deployed verification remains pending. Changes are intentionally uncommitted.

## 2026-10-08 — Extend escalation audit event coverage

- Add creation, notification acceptance/failure/retry, and final exhaustion events with safe step, recipient, attempt, deadline, and system/actor context; preserve existing acknowledgement, manual escalation, resolution, timeout, scheduling, start, cancellation, and start-failure events.
- Verification: focused lifecycle/audit tests, full Maven package, and diff checks pass; infrastructure-gated and deployed verification remain pending. Changes are intentionally uncommitted.

## 2026-10-08 — Complete escalation and email UI

- Add team and recipient resolution controls, saved resolution-deadline display, lifecycle-aware acknowledgement messaging, and readable paused/sent/failed/skipped step explanations.
- Improve Escalate now stale, expired, and unavailable-action messaging and expose the existing resolution API through the UI.
- Verification: UI production build, focused acknowledgement/resolution tests, and backend package build pass; PostgreSQL/deployed verification remains pending.

## 2026-10-08 — Simplify manual Escalate now code

- Remove four redundant helper methods and rename the remaining internal helpers to explicit business names while preserving authorization, locking, deadline, token, audit, and idempotency behavior.
- Update `AGENTS.md` to prefer the smallest readable implementation and to remove wrappers or abstractions that do not protect a real boundary.
- Verification: focused manual-action/security tests, backend package build, and `git diff --check` pass; PostgreSQL/deployed verification remains pending.

## 2026-10-08 — Clarify Escalate now transition naming

- Rename the private manual-action helper from `applyLocked` to `applyEscalateNowTransition` so its business responsibility is explicit while preserving behavior.
- Verification: focused manual-action tests, backend package build, and `git diff --check` pass.

## 2026-10-08 — Implement Escalate now

- Add authenticated same-team and recipient-token Escalate now actions for OPEN and ACKNOWLEDGED runs, with expected-step validation, immediate durable scheduling, resolution-wait cancellation, explicit token capability, and actor/source/target audit details.
- Add signed-in and email preview/confirmation UI plus an Escalate now email action for non-final notifications; stale, terminal, expired, and repeated actions remain scoped and idempotent.
- Verification: focused manual-action, acknowledgement, notification, consumer, and security tests; full Maven suite (224 tests, 0 failures/errors, 17 environment-gated skips); backend package; UI build; and diff checks pass. PostgreSQL/RabbitMQ/Redis and deployed verification remain pending; no independent read-only verifier was available.

## 2026-10-07 — Make naming guidance business-focused

- Update `AGENTS.md` to prioritize business responsibilities and user-visible outcomes over implementation mechanisms when naming classes and methods, with concise timeout examples.
- Verification: naming-guidance review, `git diff --check`, plan-length check, and backend compile pass; no runtime behavior changed, so the full test suite was not rerun.

## 2026-10-07 — Use clear timeout terminology for escalation waits

- Replace internal `deadline` component names with `EscalationTimeoutService`, `EscalationTimeoutScheduler`, and related timeout messages; explain that acknowledgement timeout exhausts an open escalation while resolution timeout resumes the next step or exhausts the run.
- Preserve the canonical persisted `resolutionDeadline` field and `dueAt` boundary; only internal names, comments, and audit detail labels changed.
- Verification: focused timeout, acknowledgement, and resolution tests; full Maven suite (190 tests, 0 failures/errors, 17 environment-gated skips); backend package; UI build; compile; and diff/name checks pass. PostgreSQL/deployed verification remains pending, and no independent read-only verifier was available.

## 2026-10-07 — Clarify previous deadline component naming

- Rename the durable deadline transition component to `EscalationDeadlineTransitionService` and the in-memory timer owner to `EscalationDeadlineWakeUpScheduler`; clarify their class documentation and dependency names without changing lifecycle behavior.
- Verification: focused deadline, acknowledgement, and resolution tests; full Maven suite (190 tests, 0 failures/errors, 17 environment-gated skips); backend package; UI build; and diff/name checks pass. PostgreSQL/deployed verification remains pending, and no independent read-only verifier was available.

## 2026-10-07 — Clarify method names and comments in deadline handling

- Add concise explicit naming and one-line method-comment rules to `AGENTS.md`.
- Rename deadline recovery, wake-up, delivery, and scheduling methods to state their purpose; replace `rearm` terminology with `rescheduleDeadlineWakeUp` and update related tests/docs without changing lifecycle behavior.
- Verification: focused tests, full Maven suite (190 tests, 0 failures/errors, 17 environment-gated skips), backend package, UI build, and diff/document checks pass. PostgreSQL/deployed verification remains pending.

## 2026-10-07 — Implement deadline expiry and recovery

- Persist shared acknowledgement boundaries after successful sends, recover final acknowledgement/resolution deadlines after restart, and safely expire or resume/exhaust runs under the database lock.
- Enforce acknowledgement deadlines on POST and record automatic acknowledgement/resolution expiry audit events without adding parallel timing fields.
- Verification: full Maven tests (190 tests, 0 failures/errors, 17 environment-gated skips), backend package, UI build, focused expiry/recovery tests, and documentation checks pass. PostgreSQL/deployed concurrency, rollback, and restart verification remains pending.

## 2026-10-07 — Implement explicit resolution

- Add locked team-member and recipient-token resolution from `ACKNOWLEDGED` to `RESOLVED`, with actor/time persistence, active-owner clearing, unsent-step skipping, audit history, and post-commit wake-up cancellation.
- Enforce the saved resolution deadline and exact current recipient/step ownership; use the saved deadline rather than token TTL for action eligibility and require exact accepted token-hash/step/source audit evidence for idempotent recipient replays.
- Verification: focused resolution/security tests, full Maven tests (177 tests, 0 failures/errors), backend package, UI build, and documentation checks pass. Independent read-only review was **Inconclusive** only for PostgreSQL/deployed migration, rollback, and concurrency behavior because PostgreSQL/RabbitMQ/Redis integration checks remain environment-gated.

## 2026-10-07 — Implement acknowledgement pause

- Add the enabled `ACKNOWLEDGED` lifecycle transition, exact current-step validation, saved resolution owner/deadline, next-step pause, and post-commit wake-up cancellation while preserving disabled terminal acknowledgement behavior.
- Keep successful final sends open for acknowledgement and record the acknowledgement audit event; `dueAt` remains the shared acknowledgement/delivery boundary with no redundant acknowledgement-deadline field.
- Verification: focused tests, full Maven tests, backend package, UI build, and documentation checks pass. PostgreSQL/RabbitMQ/Redis integration checks remain environment-gated; independent read-only verifier verdict: **Achieved**.

## 2026-10-06 — Clarify agreed-decision implementation guardrails

- Require implementation plans to identify binding agreed decisions and canonical sources of truth before adding fields, columns, timers, or API properties.
- Require conflicting earlier implementation and tests to be removed or revised when the user clarifies a decision, with focused re-verification before handoff.
- Verification: documentation whitespace, plan-length, anchor, and diff checks pass.

## 2026-10-06 — Persist runtime resolution snapshots

- Add V13 durable per-step resolution snapshots, run-level acknowledgement ownership/resolution deadlines, and exact execution-step binding for acknowledgement tokens; reuse `dueAt` as the canonical shared acknowledgement/delivery deadline.
- Propagate the flow/node snapshot through immediate and scheduled starts, with focused coverage for enabled/disabled timing, failed writes, same-recipient steps, and mismatched tokens.
- Follow-up correction removes the redundant per-step acknowledgement-deadline column so the shared wait has one durable time source.
- Verification: focused tests, full `mvn test`, backend packaging, UI build, documentation checks, and diff review pass. PostgreSQL/RabbitMQ/Redis integration remains environment-gated and skipped; independent read-only verifier verdict: **Achieved**. PostgreSQL rollback/migration execution remained unavailable.

## 2026-10-06 — Add flow/node resolution timing configuration

- Add the flow-level resolution-timeout toggle, nullable positive per-node timeout storage, version-checked atomic timing updates, and matching create/edit validation.
- Add flow-editor controls for enabling the timeout and setting each node's resolution window while retaining the existing shared wait duration.
- Update the PostgreSQL scheduling fixtures to pass the unified execution-step enum, preserving full-suite compilation after the preceding status migration.
- Verification: focused flow tests, full `mvn test`, backend packaging, UI build, documentation checks, and diff review pass. PostgreSQL/Redis/RabbitMQ integration tests remain environment-gated and skipped; independent read-only verification was unavailable.

## 2026-10-06 — Unify execution-step statuses

- Replace the independent execution/notification status strings with the persisted `FlowExecutionStepStatus` enum, migrate legacy rows with V11, rebuild publication recovery queries, and reject unsupported database values.
- Update delivery/retry/duplicate-message paths, disabled acknowledgement skip handling, execution-state API/UI mappings, and focused tests so each step has one authoritative status.
- Verification: focused tests, full `mvn test`, backend packaging, and `npm run build` pass. PostgreSQL integration remains skipped by its environment gate (13 skipped tests); independent read-only verification was inconclusive because two dispatched verifier attempts did not return reports and were shut down.

## 2026-10-06 — Settle resolution lifecycle storage semantics

- Decide that `RESOLVED` is self-describing, while `COMPLETED` requires `ACKNOWLEDGED` or `EXHAUSTED`; clear active acknowledgement ownership when the run leaves that state and retain history in audit events.
- Mark the resolution-timeout requirements prerequisite complete in the [launch checklist](docs/product-launch-readiness.md#3-resolution-timeout-after-acknowledgement); implementation remains pending, with execution-step status unification selected as the next task.
- Verification: focused documentation diff review, link/anchor, whitespace, and plan-length checks passed. Product tests/builds were not applicable; independent subagent verification was skipped because no usable subagent mechanism was exposed.

## 2026-10-06 — Require independent subagent completion verification

- Update [AGENTS.md](AGENTS.md#verification-and-handoff) to require a read-only verification subagent for each repository task, with acceptance-criteria evidence, an explicit verdict, and documented limitations before handoff.
- Require the primary agent to reconcile the verifier's report and record when the environment cannot provide a subagent; no product behavior changed.
- Verification: local documentation links/anchors, whitespace, plan length, and diff review passed. An independent verifier initially returned `Not achieved` because the task plan and changelog still said verification was pending; that report was reconciled, and a final verifier returned `Achieved`. A later audit was `Inconclusive` only because its child context could not recursively verify the parent-run subagent mechanism; the parent had successfully used that mechanism, and recursive verification is not required. Documentation linters and product tests/builds were not applicable or unavailable.

## 2026-10-06 — Document Spring test-context dependency checks

- Add [AGENTS.md](AGENTS.md#verification-and-handoff) guidance to update `@WebMvcTest`/slice-test mocks and direct constructor tests whenever Spring constructor dependencies change.
- Require focused context verification and diagnosis of the first underlying `UnsatisfiedDependencyException`; no product behavior changed.
- Verification: whitespace, plan length, and diff review pass. Changes remain uncommitted.

## 2026-10-06 — Restore security test context wiring

- Add the missing `EscalationStartScheduler` Mockito bean to the MVC security test after the controller gained that constructor dependency.
- Anonymous escalation, schedule, reschedule, and cancel requests continue to verify `401` responses; production wiring is unchanged.
- Verification: focused `EscalationSecurityTest` and backend packaging pass. Changes remain uncommitted.

## 2026-10-06 — Require separate commits by change category

- Update [AGENTS.md](AGENTS.md#scope-and-version-control) to keep backend, frontend, test, and documentation changes in separate commits, requiring confirmation before committing to split mixed staging and using a fixed backend-to-documentation order.
- Preserve unrelated unstaged changes while separating commits; no product behavior changed.
- Verification: documentation links/anchors, whitespace, plan length, and diff review pass. Changes remain uncommitted.

## 2026-10-06 — Require explicit mode APIs in agent guidance

- Update [AGENTS.md](AGENTS.md#changes) to prohibit overloaded methods and boolean mode flags for materially different business behavior; require explicit enums/request objects or clearly named operations with focused mode tests.
- This documents the `startFlowExecution` refactoring practice for future work. No product behavior or existing staged changes were changed.
- Verification: documentation links/anchors, whitespace, and current-plan length checks pass; no behavior tests or builds were needed.

## 2026-10-06 — Simplify flow execution start modes

- Replace the overloaded/boolean `startFlowExecution` API with one explicit `FlowExecutionStartMode` enum covering idle, due scheduled, and early scheduled starts.
- Update all callers and focused tests without changing claims, transaction boundaries, actor auditing, first-step delays, or timer behavior.
- Verification: focused Maven tests, backend packaging, UI build, and diff/whitespace checks pass. The implementation is recorded in the preceding implementation commit.

## 2026-10-06 — Implement Start now for scheduled escalations

- Allow same-team users to start a scheduled escalation early through the existing start API, using a conditional `SCHEDULED → OPEN` claim, normal first-step delay, actor audit, and post-commit timer cancellation.
- Add scheduled-run UI wording and focused use-case, service, controller, and scheduling tests. Automatic due-time starts remain due-gated; PostgreSQL/deployed verification remains pending.
- Verification: focused Maven tests, backend packaging, UI build, and diff/whitespace checks pass. Resolution, Escalate now, webhook gaps, and activity timeline remain separate tasks.

## 2026-10-06 — Correct webhook launch progress from implementation evidence

- Audit existing webhook/task backend, UI, migrations, and email code; replace stale unchecked tracking in [Feature 2](docs/product-launch-readiness.md#feature-2-create-and-start-an-escalation-through-a-webhook) with completed implementation pieces and explicit remaining metadata, event-history, validation, and verification gaps.
- Mark the existing webhook plan partially implemented, not release-ready. No product code was changed or repaired in this audit.
- Verification: the existing mocked-SMTP Notification test, backend packaging, UI build, and document checks pass. Webhook-specific tests are absent; PostgreSQL webhook behavior and deployed end-to-end checks were not run.

## 2026-10-06 — Defer Send test escalation until after launch

- Move Send test escalation to the [feature backlog](docs/feature-backlog.md#send-test-escalation) and remove it from initial-launch requirements. Revisit after real users start using AlertOps; detailed test behavior remains undecided.
- Verification: documentation links/anchors and whitespace checks only. No product code, lifecycle states, or other release requirements changed.

## 2026-10-06 — Clarify the shared acknowledgement and delivery wait

- Correct the [lifecycle plan](docs/resolution-timeout-implementation-plan.md#agreed-uniform-node-waits-and-final-step-outcome) and Requirement 3: existing delivery delay is the acknowledgement wait, not another duration to stack or compare. Resolution duration starts at acknowledgement when enabled; unresolved expiry advances immediately.
- Remove the reopened timing question and add a shared-wait verification example. No new setting or state is planned; implementation and deployed verification remain pending.
- Verification: documentation links/anchors and whitespace checks only; product code is unchanged and no behavior or deployed tests ran.

## 2026-10-06 — Settle email Escalate now eligibility

- Record current-recipient acknowledgement/resolution windows for [email Escalate now](docs/resolution-timeout-implementation-plan.md#escalate-now-from-email); links stay viewable after expiry or advancement, but cannot advance another step through repeated confirmation.
- Update Requirements 3/4 and planned boundary, race, restart, and duplicate-click checks. Carry forward the closed resolution-access decision using existing same-team rules, with no new roles or states.
- Verification: documentation links/anchors and whitespace checks only; implementation and deployed verification remain pending. No product code or commits changed.

## 2026-10-06 — Record viewable links with deadline-gated response actions

- Record [acknowledgement/resolution action rules](docs/resolution-timeout-implementation-plan.md#agreed-viewable-links-and-action-deadlines): keep links viewable, reject new late/obsolete actions, and show saved outcomes for previously accepted actions without restarting timers.
- Update Requirement 3 and acceptance cases for independent deadlines, stale links, delayed timers, and restart. Correct planning wording to retain the already-agreed two node response durations without another setting; resolution permissions and email Escalate now eligibility remain open.
- Verification: documentation links/anchors and whitespace checks; product implementation remains pending, with no behavior or deployed tests run.

## 2026-10-05 — Plan uniform node waits and final-step exhaustion

- Record [uniform node rules](docs/resolution-timeout-implementation-plan.md#agreed-uniform-node-waits-and-final-step-outcome): the last recipient gets the same acknowledgement/resolution waits; expired waits with no next step exhaust the run, without resending.
- Update Requirement 3 and implementation/recovery acceptance cases. Acknowledgement-window configuration remains open because the current node duration is a pre-send delay; no special final-node timeout is planned.
- Verification: documentation checks only; no product code or implementation-readiness claims changed, and no behavior or deployed tests ran.

## 2026-10-05 — Plan readable incident activity history

- Add the agreed user/automatic event history, important milestones, and expandable
  failure/retry summaries to the [shared lifecycle plan](docs/resolution-timeout-implementation-plan.md#agreed-incident-activity-timeline).
- Mark Requirement 5 agreed and plan audit coverage, a team-scoped history API, and
  detail-page presentation; implementation and deployed verification remain pending.
- Verification: documentation checks only; product code is unchanged and no behavior
  or deployed tests ran. Existing passing backend/UI builds are unchanged.

## 2026-10-05 — Plan early scheduled starts and manual escalation from UI or email

- Extend the [shared lifecycle plan](docs/resolution-timeout-implementation-plan.md)
  with Start now for scheduled runs and Escalate now for waiting/paused next steps,
  including email-token preview/POST confirmation, recipient audit, stale-link checks,
  transactions, timer cleanup, races, and focused implementation tasks.
- Mark Requirement 4 agreed while keeping implementation pending; the activity
  timeline and remaining resolution edge cases still need discussion.
- Verification: documentation links/anchors and whitespace checks passed. Product code
  is unchanged; prior passing backend/UI builds were reused, and no behavior tests ran.

## 2026-10-05 — Plan resolution timeout after acknowledgement

- Record the agreed flow toggle, per-node timeouts, runtime snapshots, single
  step-status enum, and immediate next-step delivery when resolution expires in
  the [feature plan](docs/resolution-timeout-implementation-plan.md).
- Link Requirement 3 to ordered implementation tasks and identify the remaining
  product decisions; implementation and release verification remain pending.
- Verification: local document-link targets, whitespace checks, backend packaging,
  and UI build passed. No behavior tests or deployed checks ran for this documentation task.

## 2026-10-05 — Require concise task plans and maintain a changelog

- Require a plan before each implementation and reuse one current task plan to
  limit document growth.
- Define when to reuse feature plans, maintain lasting documentation, and archive
  completed plans and older changelog entries.
- Verification: document links and whitespace checks, backend packaging, and UI
  build passed. Tests and deployed checks were not run for this documentation-only change.
