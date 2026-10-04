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
