# AlertOps UI implementation plan

## Goal

Build a small, independently deployed web app that lets a reviewer run the real AlertOps workflow: sign in, choose a team, create a task, configure an ordered escalation flow, start an escalation, and inspect its progress. Keep the interface useful for operating the demo and for explaining the engineering behind it. Every displayed status must come from the API; explanatory diagrams may describe the architecture but must be labeled as explanations.

## Stack and deployment boundary

| Concern | Choice | Reason |
| --- | --- | --- |
| UI | React + TypeScript | Small component model and explicit API types. |
| Build | Vite, `react-ts` template | Produces static files; no UI server is required. |
| Styling | Tailwind CSS with `@tailwindcss/vite` | Fast, consistent styling with no runtime CSS library. |
| Navigation | React Router, declarative mode | Only nested pages and URL parameters are needed. |
| Server data | TanStack Query | Handles loading, refetching, cache invalidation, and polling without custom state machinery. |
| HTTP | Browser `fetch` in one typed API client | Avoid an extra HTTP dependency. |
| Forms | Controlled inputs and native validation | Forms are small; no form framework is needed. |
| Hosting | Any static host/CDN with an SPA fallback to `index.html` | UI and Spring Boot API can deploy independently. |

Use no global state library, large component kit, graph editor, or chart package for the first version. Make the flow a numbered vertical timeline using ordinary React/CSS. Follow the current official [Vite](https://react.dev/learn/build-a-react-app-from-scratch), [Tailwind](https://tailwindcss.com/docs/installation/using-vite), [React Router](https://reactrouter.com/start/declarative/installation), and [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview) setup guides; commit the lockfile.

Suggested commands, run from repository root:

```bash
npm create vite@latest ui -- --template react-ts
cd ui
npm install
npm install react-router @tanstack/react-query tailwindcss @tailwindcss/vite
```

Configure Tailwind as a Vite plugin and import it in the root CSS. Set `VITE_API_BASE_URL` at build time to the public API origin, such as `https://api.example.com`. This value is public configuration, never a secret. For local development, leave it empty so requests use `/api`, and use a Vite `/api` proxy to `http://localhost:8096`. For production, either allow the UI origin in backend CORS or route `/api` to the API at the edge. The API deployment remains separate. Deploy `ui/dist` to static hosting, with TLS and a rewrite of unknown UI paths to `index.html`. Do not put database, RabbitMQ, Redis, or JWT signing values in the UI build.

## What the repository supports today

| UI action | Backend contract | Notes for implementation |
| --- | --- | --- |
| Register | `POST /api/v1/auth/register` with `{name,email,password}` | The account starts unverified and the UI sends the user to `/verify-email` after the verification message is sent. |
| Log in | `POST /api/v1/auth/login` with `{email,password}` | Verified credentials receive the token at `data["jwt-token"]`; unverified accounts receive `EMAIL_NOT_VERIFIED` and use the verification screen. |
| List / create teams | `GET /api/v1/team`, `POST /api/v1/team` with `{teamName}` | List returns `{id,name}[]`; create returns `{teamId,teamName,userId,role}`. |
| Select team | `GET /api/v1/team/select?teamId=...` | Response is `{data:{token}}`; use this team token for team scoped requests. |
| List / create tasks | `GET /api/v1/task?page=0&size=20&sortBy=createdAt&sortDir=desc`, `POST /api/v1/task` with `{name,description}` | List is an array, not a Spring `Page`. Create returns a text message containing the ID; refetch the list. |
| List / create flows | `GET /api/v1/flow/all?page=0&size=20&sortBy=createdAt&sortDir=desc`, `POST /api/v1/flow` with `{flowName}` | Lists return arrays. Flow detail: `GET /api/v1/flow?flowId=...`. |
| Read / add flow nodes | `GET /api/v1/flow/node/all?flowId=...`, `POST /api/v1/flow/node` with `{flowId,nodeName,durationInMinutes,email}` | Recipient must be an existing user in the selected team. Render nodes ordered by `position`. |
| Reorder nodes | `PATCH /api/v1/flow/node/reorder` with `{nodeId,afterNodeId,version}` | `afterNodeId:null` means first. Refresh flow and nodes after success; on version conflict, refetch and show a retry message. Gate the control until backend validation is fixed. |
| List / read escalations | `GET /api/v1/escalation/all?page=0&size=20&sortBy=createdAt&sortDir=desc`, `GET /api/v1/escalation?escalationId=...` | Escalation includes `id,name,taskId,flowId,status,resolutionType,createdAt`. |
| Create / start escalation | `POST /api/v1/escalation/create` with `{escalationName,taskId,flowId}`; `POST /api/v1/escalation/start` with `{escalationId}` | Creation currently returns HTTP 400 despite persisting. Fix before enabling create. Start requires at least one flow node. |

The initial auth token has no team ID. Team selection issues a new JWT with team ID; this is a required step before task, flow, and escalation calls. Store only the active token in `sessionStorage` for this demo, hydrate it on reload, and clear it on logout and unauthorized responses. Keep the active team ID in the route. On team switch, request a new team token and clear TanStack Query's cache so data from the previous team cannot appear. Do not decode a token as the source of permissions; the API decides access.

## Backend gates to finish before calling the UI complete

1. **Correct the create response:** `EscalationController#createEsclation` saves and then returns HTTP 400. Return 201/200 with the saved escalation; add a controller test. The UI must treat non-2xx as failure and must not parse the 400 body as a success.
2. **Make registration safe:** `UserController#registerUser` returns the `User` entity, including its password hash. Return a DTO with only public fields. Never show the current response body in the UI.
3. **Enable browser access:** there is no CORS configuration in `SecurityConfig`. Add an allowlist for the separately deployed UI origin and allow `Authorization`, `Content-Type`, and the used methods, including `OPTIONS`. Keep the origin configurable. The local Vite proxy only solves local development.
4. **Expose execution evidence:** add `GET /api/v1/escalation/{id}/execution-states`, scoped by the selected team, ordered by node position. Return a small DTO: `nodeId`, `userEmail`, `executionState`, `notificationState`, `sendAttemptCount`, `createdAt`, `updatedAt`. This lets the detail page show the actual durable queue workflow. Return 404 when the escalation is outside the team. Do not expose internal entities directly.
5. **Secure flow node reads and writes:** `getNodesByFlowId`, node creation, and reorder currently look up a flow/node by ID without consistently checking the selected team's ownership. Enforce team membership on all three before exposing the controls in a public demo.
6. **Make reorder reliable before enabling it:** verify version increments, moving to first position, and reindexing. The current `newPosition.compareTo(BigInteger.valueOf(50)) < 50` condition is always true for a `compareTo` result. Add focused service tests for order and stale version handling.
7. **Email delivery:** `Notification#sendEmail` sends a styled HTML message rendered from Markdown, with a Markdown plain-text fallback, through Spring Mail when SMTP is configured. A missing SMTP configuration or send error returns failure so the saved node state follows the existing retry/failure path. The UI should explain that `SENT` means accepted by SMTP, not confirmed delivery. Configurable retry rules are future backend work; node creation has no retry settings today.

Member management now includes a selected-team member list, an SMTP-backed invitation form, and a `/join?token=...` flow with preview, sign-in or registration continuation, and acceptance. Invite links use `ALERTOPS_UI_BASE_URL`; set it to the deployed UI origin in production. Team deletion and member role routes remain placeholders and must stay unavailable in the UI.

The follow-on backend and UI tickets are in [Team management UI task list](team-management-ui-task-list.md).

## Page map and user journey

| Route | Page | Main content |
| --- | --- | --- |
| `/` | Public overview | What AlertOps does; concise architecture diagram; sign-in CTA; explain SMTP email requirements and the meaning of `SENT`. |
| `/register`, `/login` | Authentication | Simple forms with visible API errors; unverified users are sent to the verification screen after login. |
| `/verify-email?token=...` | Email verification | Confirm the one-time link with an explicit `POST`, request another link for the entered email, and continue to teams or an invitation. |
| `/teams` | Team picker | List teams, create team, select team. After create, select the new team automatically. |
| `/app/:teamId` | Team overview | Guided four-step demo: task → flow → nodes → escalation. Show counts only if API results are loaded. |
| `/app/:teamId/members` | Members | Team-scoped member directory and owner/admin invitation form. The server enforces invitation permission and SMTP delivery. |
| `/join?token=...` | Join team | Preview the invitation, sign in or register with the invited address, accept it, then select the team. |
| `/app/:teamId/tasks` | Tasks | Table and create form; task detail text used in notifications. |
| `/app/:teamId/flows` | Flows | List and create form. |
| `/app/:teamId/flows/:flowId` | Flow detail | Ordered node timeline with recipient and delay; add node; reorder only after gate 6. Include a short note that delays are scheduled through RabbitMQ. |
| `/app/:teamId/escalations` | Escalations | List, create form with existing task and flow selectors, status badges. |
| `/app/:teamId/escalations/:id` | Escalation detail | Task + flow links, status, start button for `IDLE`, and real node state timeline after gate 4. Poll while `RUNNING`; stop at terminal status. |

Use a persistent team switcher and navigation rail in app pages. On narrow screens, collapse navigation into a simple menu. Every list needs loading, empty, error, and retry states. Every mutation needs a pending state and a server error message. Confirm only destructive actions. Keep timestamps in the viewer's local timezone with a visible timezone label.

The overview should explain the architecture with a small static diagram: user configures ordered nodes → API persists escalation state → RabbitMQ delays work → consumer updates durable state → reconciler resumes running work after restart. Link each explanation to a real screen or to the repository source. Do not display fabricated live queue metrics, delivery receipts, or retry controls.

## Code layout

```text
ui/
  src/
    app/             router, AppShell, auth/team guards, QueryClient
    api/             client.ts, types.ts, auth.ts, teams.ts, tasks.ts, flows.ts, escalations.ts
    features/
      auth/           LoginPage, RegisterPage
      teams/          TeamPickerPage, TeamSwitcher
      tasks/          TasksPage, TaskForm
      flows/          FlowsPage, FlowDetailPage, NodeTimeline, NodeForm
      escalations/    EscalationsPage, EscalationDetailPage, EscalationForm
    components/      Button, Input, EmptyState, ErrorState, StatusBadge
    pages/           PublicOverviewPage, TeamOverviewPage
    styles.css
  .env.example       VITE_API_BASE_URL= (empty for local proxy)
  vite.config.ts
  package.json
  README.md
```

Keep API calls in `api/`, query hooks next to their feature, and components focused on presentation. Use one `request<T>` helper: prefix the base URL, attach `Authorization: Bearer <activeToken>`, JSON encode bodies, parse JSON or text by content type, and throw a typed error with the HTTP status and backend message. API functions should return normalized data to pages; put odd response shapes (the nested login token and text create-task response) in those functions. Define only fields consumed by the UI. For unknown fields, tolerate extras.

Query keys must include team ID, such as `['tasks', teamId, page]`. Invalidate the relevant list after create and refresh detail after start/reorder. For escalation detail, poll the escalation and execution states every 3–5 seconds only while status is `RUNNING`, and provide a manual Refresh button. Set no optimistic status transitions: show the server response. Handle `401` by clearing the token and returning to login; handle `403` with an access message; handle `404` as a missing resource; handle `5xx` as retryable. Some current controllers turn domain failures into `500`; display their safe message if available.

## Ordered implementation tickets

Each ticket is a reviewable commit or small PR. Finish its acceptance checks before taking the next one.

1. **Backend contract gates 1–3.** Fix escalation create status, registration response, and production CORS. Smoke test registration, login, team selection, and escalation create with real HTTP requests. This unblocks the separately hosted UI.
2. **Scaffold UI.** Create `ui/`, install the listed packages, add Tailwind, router, static hosting fallback instructions, `.env.example`, and a `typecheck` script (`tsc -b`). Verify `npm run typecheck` succeeds and `npm run build` produces `ui/dist`.
3. **API client and session.** Implement typed API modules, `request<T>`, token storage, unauthorized handling, team switching, and query cache reset. Verify login's nested token and team selection's nested token using actual responses.
4. **Auth and team pages.** Implement register, login, team list/create/select and route guards. Acceptance: refresh retains the active session; switching teams changes the token and never shows old team data.
5. **Tasks and flows.** Implement lists/create, flow detail, add-node form, ordered timeline. Acceptance: create a task and a two-node flow using registered team members; reload and see the same order.
6. **Backend execution read endpoint and ownership checks.** Add the scoped, ordered state DTO endpoint and fix node access; add focused backend tests. Enable the UI execution timeline only when the endpoint exists.
7. **Escalations.** Implement list/create/detail/start and status polling. Acceptance: select a task and configured flow, create escalation, start once, see `IDLE → RUNNING → COMPLETED` from API responses, and inspect the saved node states. Explain SMTP configuration and that `SENT` reflects SMTP acceptance.
8. **Reorder, if backend gate 6 passes.** Add accessible Move up/Move down buttons using `nodeId`, `afterNodeId`, and current flow `version`. Refetch after every move and show a clear stale-version retry state. Skip drag and drop.
9. **Release check.** Test the guided journey on desktop and mobile widths; run `typecheck` and `build`; test deployed UI against deployed API, including browser preflight, direct route reload, expired token, empty team, and a failed API request. Document the UI URL and `VITE_API_BASE_URL` in `ui/README.md`.

## Definition of done

- A new reviewer can complete the full demo from a fresh account without using curl or the database.
- The UI is served from its own static deployment and calls the API through a documented public origin or edge route.
- All live statuses and node progress are sourced from the API; email status wording reflects SMTP acceptance rather than inbox delivery.
- Tenant switching does not mix data, and the backend checks team ownership for every exposed flow operation.
- The repository contains build, configuration, and deployment instructions that another developer can follow without hidden steps.
