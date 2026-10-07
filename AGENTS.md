# Agent instructions for AlertOps

These rules apply to all work in this repository: backend, frontend, database, configuration, and documentation.

## Role

Act as a software engineer collaborating with the project owner. Read the relevant existing code and understand the requested behavior before making changes.

## Project goal

Build AlertOps into a production-ready alerting and escalation product for real users. Treat the backend, UI, security, reliability, and operations as parts of the same product. Use [the product launch checklist](docs/product-launch-readiness.md) for current release priorities.

## Changes

- Use clear, descriptive names for variables, methods, and classes.
- Name types and methods for the business responsibility or user-visible
  outcome first, not for the internal mechanism. Prefer names such as
  `EscalationTimeoutService` and `scheduleResolutionTimeout` that explain what
  the escalation is waiting for, rather than names such as
  `EscalationDeadlineTransitionService` or `registerDeadlineWakeUp` that focus
  on implementation details. If an infrastructure role must be named, put the
  domain subject first and keep the mechanism secondary; explain the mechanics
  in a comment or class description.
- Name methods with concise verb-object phrases, normally two to five words.
  Prefer precise domain terms such as `rescheduleTimeoutWakeUp` over vague
  names such as `rearm`, `handle`, `process`, or `doWork`; do not chain clauses
  until a method name reads like a sentence or paragraph. Split a method when
  one concise name cannot describe a single responsibility.
- Add a one-line `//` purpose comment immediately above every new or modified
  method, including private helpers, lifecycle callbacks, and test methods.
  Keep the comment behavior-focused and on one line; do not use a long block
  comment to compensate for an unclear method name.
- Prefer the smallest readable implementation that meets the request. Keep the
  normal path easy to follow from validation to state change; avoid speculative
  abstractions, wrappers, and defensive branches that do not protect a real
  boundary.
- Remove helpers that only forward arguments, rename a one-line expression, or
  duplicate a check. Keep a helper when it names a business rule, protects a
  security/concurrency invariant, handles a nullable boundary, or makes a
  repeated operation easier to review.
- Add files, abstractions, or dependencies only when they reduce real
  complexity or are required by the product behavior. Before handoff, review
  the diff for dead methods, duplicate logic, and code that can be made clear
  with a local variable or a focused method.
- When one operation supports materially different business modes, do not encode the mode with overloaded method signatures or boolean flags. Use one clear method with a specific enum/request object, or separate methods with distinct names when the operations truly differ. Keep the mode values finite and explicit, and add focused coverage for each mode.
- Treat decisions recorded as agreed behavior, core decisions, final storage decisions, canonical mappings, or acceptance rules in the linked product checklist and feature plans as binding implementation requirements. Before changing a model, schema, API, or lifecycle transition, read those sections and record the relevant canonical source of truth in the current plan.
- Do not add a second field, column, timer, or API property for a concept already represented by an agreed canonical field or timing boundary. Search the code and plans for domain synonyms first; if reuse is insufficient, document the reason and resolve the conflict before coding.
- When the user clarifies or corrects an agreed decision, update the current plan and relevant feature plan before changing code, remove or revise conflicting implementation and tests, and rerun the focused verification. Do not preserve an earlier implementation merely because it already exists.
- Review the final diff for unrelated changes and explain any important limitation.

## Domain values and enums

- Use an enum whenever a value has a finite, known set of valid domain options, such as lifecycle statuses, actions, roles, resolutions, notification states, and entity types. This keeps invalid values out of business logic and makes transitions explicit.
- Persist domain enums with `@Enumerated(EnumType.STRING)` or an explicit string mapping so database values remain readable and stable if enum declaration order changes. Add or update database constraints and focused tests when the allowed values change.
- At API boundaries, parse and validate incoming strings into the corresponding enum close to the boundary; do not pass arbitrary strings through service logic. Return a clear validation error for unknown values.
- Keep a `String` when the value is genuinely open-ended or owned by an external system, such as a user-provided name, email address, reason/message, IANA timezone, or an extensible provider value. Do not invent an enum for values that cannot be known and controlled by this project.
- Do not use broad constructs such as `Enum<?>` when the application owns the value set. Use the specific enum type; use strings only when the possible values are intentionally unknown.

## Scope and version control

- Work on one agreed task at a time. Do not bundle a later checklist item, opportunistic refactor, or unrelated cleanup into the current change.
- Do not create commits unless the user explicitly asks. Preserve the user's staged and unstaged changes, and when a commit is requested include only the scope they specified.
- Keep backend, frontend, test, and documentation changes in separate commits; never commit all categories together. If the staging area mixes categories, ask for confirmation before committing so the staged files can be split, then commit them in this order: backend, frontend, tests, documentation. Preserve unrelated unstaged changes while separating the commits.
- Never reset, discard, or rewrite user changes without explicit approval. Inspect the working tree and staging area before modifying overlapping files.

## Task plans and documentation

- Before implementing any task, including fixes and documentation changes, write a concise plan in [the current task plan](docs/current-plan.md). Include the task name, start date, status, goal and scope, acceptance criteria, implementation steps, and intended verification. Small tasks need only a few bullets. Planning does not require a separate approval unless the user requests it or scope needs clarification.
- Before implementation, add a short decision-compliance note for any task governed by an existing feature plan: list the relevant agreed decisions, canonical fields/sources of truth, and explicitly excluded alternatives. Use that note during diff review so implementation does not silently introduce a parallel representation.
- Maintain one current task plan. Update its checkboxes, decisions, blockers, and verification results as work progresses. Resume an unfinished plan rather than overwriting it; replace a completed plan's contents when the next agreed task begins. If the user explicitly switches tasks, preserve a short resume note and link from the new plan to the existing feature plan or backlog item before replacing it.
- Keep the current plan roughly one screen long (aim for at most 60 lines). Summarize findings and link to relevant code or documentation instead of copying requirements, test logs, conversation history, or earlier plan revisions.
- Use [the product launch checklist](docs/product-launch-readiness.md) as the source of truth for release priorities and readiness. The current plan describes only the task being worked on; link to the relevant checklist item or existing feature plan instead of duplicating it. Update readiness only when supported by verification evidence.
- Reuse existing feature plans for work spanning multiple tasks. Create a dedicated plan only when a substantial initiative needs shared requirements, design decisions, or sequencing that cannot fit in the current plan. Give it a clear status and link it from the relevant checklist item; do not create a separate plan, implementation report, and completion summary for every task.
- At completion, record the outcome in the changelog and mark the current plan complete with verification results and any remaining limitations. Move lasting API, configuration, architecture, or operational guidance into the relevant maintained document so it remains available when the current plan is replaced. Git history preserves earlier tracked plan revisions; do not create commits solely to archive plans.
- When a dedicated feature plan is complete, retain useful guidance in maintained docs and move the historical plan to `docs/archive/plans/` if it is no longer an active reference. Fix incoming and relative links when moving it. Archive only documents relevant to the agreed task; do not reorganize unrelated existing docs.

## Changelog

- Maintain [CHANGELOG.md](CHANGELOG.md) from now on. Add one concise entry for each completed task that changes repository files, including documentation and configuration. Do not invent historical entries or record planned work as completed.
- Use a dated task heading (`YYYY-MM-DD — descriptive task title`), newest first. In one to three bullets, explain what changed and why, summarize verification and material limitations, and link to relevant maintained documentation where useful. Edit the same entry for follow-up corrections within the same task; avoid entries for individual edits or test reruns.
- Keep implementation detail, raw logs, and task checklists out of the changelog. The changelog records completed outcomes; the current plan records progress; maintained product and operations docs describe current behavior.
- Keep the current calendar year's entries in the root changelog. When the year changes, move older entries unchanged into `docs/archive/changelog/YYYY.md` and retain year links in the root file. If a year's entries become difficult to scan (roughly 200 lines), archive its older completed months in `docs/archive/changelog/YYYY-MM.md` with clear links. Update relative links when archiving and preserve all history.

## Time and scheduling

- Persist real-world moments as `Instant`/UTC and inject `Clock` into business services instead of calling `Instant.now()` directly; this keeps comparisons deterministic and tests controllable.
- Accept a local date/time plus an IANA timezone at the boundary, resolve it to one UTC `Instant`, and retain the submitted timezone when the product needs to display or reschedule the value.
- Treat in-memory timers as wake-up handles only. Timer callbacks must reload current state from PostgreSQL and tolerate cancellation, replacement, restart recovery, and duplicate callbacks.
- Add concise comments around calendar/timezone conversion, retry timing, and concurrency code when the reason is not obvious from the syntax. The one-line comment rule applies to every new or modified method; unchanged routine getters and setters do not need retroactive comments.

## Verification and handoff

- Add or update a focused test for each meaningful behavior or failure path, especially races, retries, restart recovery, idempotency, and null repository results.
- When a Spring component's constructor dependencies change, update every affected test context before handoff. Provide new dependencies with `@MockitoBean` or explicit test configuration in `@WebMvcTest` and other slice tests, update direct constructor tests, and run the focused slice test. If a context failure threshold appears, inspect the first underlying `UnsatisfiedDependencyException` rather than treating the threshold as the root cause.
- Run the narrowest relevant tests plus the normal project build before handoff. Clearly distinguish passed local checks from integration or deployed checks that were skipped because required services or environment variables were unavailable.
- Before handoff for every repository task, run an independent, read-only verification subagent using the original request, acceptance criteria, changed-file list, and intended verification commands as its inputs. Ask it to inspect the current worktree and relevant code, tests, and documentation; run the narrowest relevant checks it can run; and return an evidence-based verdict of `Achieved`, `Not achieved`, or `Inconclusive`, with each acceptance criterion mapped to evidence, command results, gaps, regressions, and recommended follow-up.
- Keep the verifier independent: do not seed it with the desired conclusion, and do not allow it to modify files, stage changes, commit, or otherwise change repository state. The primary agent remains responsible for the result, must reconcile any disagreement or `Inconclusive` finding before claiming completion, and must record the verifier verdict, evidence, and material limitations in the current plan and final handoff. If the environment has no usable subagent mechanism, explicitly record that verification was skipped and why; do not present the task as independently verified.

## Transactions

- Do not add `@Transactional` automatically. First identify the atomicity requirement and the actual database operations in the method.
- A service method that only performs validation reads followed by one repository `save` usually does not need its own transaction; Spring Data already executes the write transactionally. Add a service transaction only when multiple writes, related state changes, or after-commit work must succeed or fail together.
- Check whether the caller already provides the transaction boundary. Remember that a call between methods on the same Spring bean bypasses the transactional proxy, so annotating only the inner method may not have the intended effect.
- Do not treat `@Transactional` as a substitute for database constraints, optimistic/pessimistic locking, or conditional updates. It does not by itself prevent validation races.
- When a transaction is justified, keep it short and document the invariant it protects. Verify rollback and concurrency behavior with a focused test.

## Null safety

- Always write null-safe code explicitly. Do not rely on a static analyzer to infer safety from a long compound condition.
- Guard nullable repository results, request fields, entity fields, and external values before dereferencing them; prefer clear early returns or exceptions over deeply nested expressions.
- Assign nullable values to a local variable, validate the local variable, and only then pass it to another method or repository query.
- Use `Optional` deliberately at repository boundaries, but do not call `.get()` without handling the empty case. Keep null checks close to the point where a value is introduced.
- Respect API nullability contracts: do not add branches for values returned by APIs annotated or documented as non-null, because those branches are dead code; handle the API's documented failure or rejection mechanism instead.
- Add a focused test for each meaningful null path, especially missing database rows and nullable identifiers.

## In-memory state and sources of truth

- Do not create two in-memory sources of truth for the same data. Choose one authoritative owner and let other components keep only references, derived values, or short-lived coordination state.
- Keep PostgreSQL as the source of truth for durable entities, statuses, timestamps, and workflow state. Reload those values when a background callback, retry, or request needs current state instead of trusting a stale in-memory copy.
- Keep in-memory registries, caches, and runtime entries limited to data they genuinely own, such as a `ScheduledFuture` needed to cancel or replace a timer. Do not copy database-owned fields into those structures without a documented consistency strategy.
- Avoid storing the same identifier or state in multiple in-memory locations. Use the canonical map key, object, or repository record and pass references when needed instead of duplicating fields.
- Any cache or in-memory copy must have a documented purpose, ownership, invalidation/refresh policy, and focused tests for stale, replacement, cancellation, retry, or concurrent-update behavior as applicable.
