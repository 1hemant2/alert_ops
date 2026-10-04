# Agent instructions for AlertOps

These rules apply to all work in this repository: backend, frontend, database, configuration, and documentation.

## Role

Act as a software engineer collaborating with the project owner. Read the relevant existing code and understand the requested behavior before making changes.

## Project goal

Build AlertOps into a production-ready alerting and escalation product for real users. Treat the backend, UI, security, reliability, and operations as parts of the same product. Use [the product launch checklist](docs/product-launch-readiness.md) for current release priorities.

## Changes

- Use clear, descriptive names for variables, methods, and classes.
- Choose the simplest solution that meets the request. Keep the change focused and reuse existing code where practical.
- Add files, abstractions, or dependencies only when they are needed and can be justified.
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
- Never reset, discard, or rewrite user changes without explicit approval. Inspect the working tree and staging area before modifying overlapping files.

## Time and scheduling

- Persist real-world moments as `Instant`/UTC and inject `Clock` into business services instead of calling `Instant.now()` directly; this keeps comparisons deterministic and tests controllable.
- Accept a local date/time plus an IANA timezone at the boundary, resolve it to one UTC `Instant`, and retain the submitted timezone when the product needs to display or reschedule the value.
- Treat in-memory timers as wake-up handles only. Timer callbacks must reload current state from PostgreSQL and tolerate cancellation, replacement, restart recovery, and duplicate callbacks.
- Add concise comments around calendar/timezone conversion, retry timing, and concurrency code when the reason is not obvious from the syntax. Do not comment routine getters, setters, or self-explanatory code.

## Verification and handoff

- Add or update a focused test for each meaningful behavior or failure path, especially races, retries, restart recovery, idempotency, and null repository results.
- Run the narrowest relevant tests plus the normal project build before handoff. Clearly distinguish passed local checks from integration or deployed checks that were skipped because required services or environment variables were unavailable.

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
