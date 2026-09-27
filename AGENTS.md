# Agent instructions for AlertOps

These instructions apply to work in this repository. For UI work, read [docs/ui-implementation-plan.md](docs/ui-implementation-plan.md) before editing. Treat that plan as the implementation checklist and this file as the working rules. Check the current Java controllers and DTOs before relying on an API contract, since the backend can change.

## Project goal

Build a small UI that demonstrates the real AlertOps workflow and its engineering: team scoped access, ordered flow nodes, delayed queue execution, durable state, and restart recovery. A reviewer should be able to register, choose a team, create a task and flow, start an escalation, and inspect its real state without using curl or the database.

The UI must build and deploy independently from Spring Boot. Put it in `ui/`; do not bundle it into the Java application or require a UI server at runtime.

## Implementation order

Follow the numbered tickets and acceptance checks in the UI plan. In particular:

1. Resolve the backend contract gates that block a working browser demo: successful escalation creation status, safe registration response, and cross origin access for the deployed UI.
2. Scaffold the React app and a single typed API client; implement auth and team selection before team scoped pages.
3. Implement tasks, flows, ordered nodes, then escalations and their saved execution states.
4. Enable node reorder only after the backend's ownership, version, and ordering behavior is verified.
5. Run the end to end demo journey and document independent UI deployment.

If a gate is still open, leave its UI control unavailable with an accurate explanation. Do not work around a broken API by treating an error response as success or by inventing local status.

## Stack and code rules

- Use React, TypeScript, Vite, Tailwind CSS, React Router, TanStack Query, and native `fetch` as specified in the plan. Add another dependency only when it removes substantial code or complexity.
- Keep API calls and response normalization in `ui/src/api/`. Keep query hooks near their feature and presentation components free of backend response quirks.
- Use small files with descriptive names. Prefer a numbered CSS timeline for flow nodes over a graph editor. Use controlled inputs and native form validation for the small forms.
- Make all server data team scoped in query keys. Clear cached team data when switching teams or logging out.
- Store the active demo JWT in `sessionStorage`; attach it as a bearer token. Treat the server as the authority for access and status. Never put secrets in `VITE_` variables or committed frontend files.
- Handle loading, empty, error, and retry states. Disable repeated mutation submissions. Show backend errors in plain language without exposing sensitive response bodies.
- Do not add UI for placeholder team deletion, member role management, invite delivery, or retry settings until working backend contracts exist.
- Label notifications as simulated while `Notification#sendEmail` only logs output. Do not fabricate delivery receipts, queue metrics, node progress, or recovery events.

## Backend changes

Keep backend edits narrow and tied to the UI plan. Add or update focused tests when changing response contracts, authorization, execution state reads, or node ordering. The proposed execution state endpoint must verify the selected team and return a DTO, ordered by node position. Check authorization on flow node reads, creation, and reorder before exposing these controls.

## Verification before finishing

- Run the relevant backend tests for any Java changes.
- Run UI type checking and production build; verify `ui/dist` is produced.
- With the API running, exercise registration → login → team selection → task → flow with nodes → escalation create/start → execution detail. Confirm data survives reload and team switching never shows another team's data.
- Verify the deployed UI can reach the independently deployed API, browser preflight succeeds, and direct UI route reloads reach `index.html`.
- Report what was changed, which checks ran, and any unmet backend gate. Do not describe an untested path as verified.
