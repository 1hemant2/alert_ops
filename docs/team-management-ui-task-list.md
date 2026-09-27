# Team management UI task list

This extends the [initial UI plan](ui-implementation-plan.md). The first UI release covers registration, login, team creation and selection, tasks, flows, and escalations. The items below add member management and invitations only when their API contracts work. Keep the server authoritative for membership and permissions.

## Current contract inventory

| Capability | Current state | UI decision |
| --- | --- | --- |
| Register, log in, list/create/select teams | Implemented and present in the UI. `GET /api/v1/team` returns team names, IDs, and current membership roles. | Keep; role labels come from the API. |
| Member directory | `GET /api/v1/team/members` returns a selected-team member DTO list. Team-scoped requests verify current database membership and use the current database role. | Show the selected team's directory. |
| Invite a member | `POST /api/v1/team/invite` validates email, role, and expiry; owners can invite admins or users, admins can invite users only. It saves a random token, sends an SMTP invitation link, returns no token, and reports SMTP failure without leaving a pending invite. `ALERTOPS_UI_BASE_URL` sets the link origin. | Show the form to owners/admins. The server enforces permissions. Pending invitation reads and revocation are still missing. |
| Accept an invite | `GET /api/v1/team/join?token=...` is a read-only preview. Authenticated `POST /api/v1/team/join` checks invited email, status, and expiry, then creates membership and consumes the invite in one transaction. The UI keeps the token in tab session storage through sign-in or registration. | Use `/join?token=...`; retain server checks for every acceptance. |
| Roles and removal | `TEAM_OWNER`, `ADMIN`, `USER`, and `NONE` exist in an enum and permission registry. The role update and member delete routes return empty success responses; their DTOs use `Long` user IDs while users and members use UUIDs. | Do not expose role or remove controls yet. |
| Team settings | Team deletion is a placeholder; there is no team rename route. | Defer settings mutations until real contracts exist. |
| Other routes outside team management | Task read/update/delete routes exist, while the UI only lists and creates tasks. Account deletion exists but accepts an ID or email from the request. | Track separately after their authorization and response behavior is checked. |

The `role` and `permission` database entities are separate from the enum registry that currently decides `SELECT_TEAM` access. Define one source of truth for the first management release; do not build a general permission editor around unused tables.

## Ordered tickets

- [ ] **TM-01 — Enforce live team membership and role policy in the API.** Define who may view members, invite, revoke invites, change roles, remove members, and edit a team. A suggested starting policy is: all members may view the directory; owners and admins may invite; owners manage roles; admins may remove ordinary members; only an owner may edit team settings. Check membership against the database for every team-scoped request, including tasks, flows, and escalations, even when the JWT has a team ID; read the current role from the database for management actions. Return 401/403/404/409 as appropriate instead of converting access failures to 500. Add focused authorization tests, including a token issued before a member was removed.

- [ ] **TM-02 — Return team and member read DTOs.** Add a selected-team member list with `memberId`, `userId`, `name`, `email`, and `role`; exclude password hashes and internal entities. Include the current user's role in the team list or a selected-team detail response so the picker does not label an owner as “Member.” Fix UUID repository ID types and add a unique `(team_id, user_id)` constraint after handling existing duplicates. Acceptance: a member sees only the selected team's members; an outsider receives no roster data.

- [ ] **TM-03 — Show a read-only Members page.** Add `/app/:teamId/members`, a navigation link, member count, name/email/role rows, and recipient selection for flow nodes if it improves the form. Put requests in `ui/src/api/`, include `teamId` in query keys, and clear data on team switch/logout. Cover loading, empty, error, and retry states. Acceptance: changing teams never displays the previous team's roster.

- [ ] **TM-04 — Complete invite creation and delivery.** Validate and bind email, allowed role, and TTL; generate and persist a unique invite token and expiry; require invite permission; build a link to a configured UI origin; send a dedicated invitation email through SMTP. Do not log the token or return it in an ordinary success response. Report SMTP failure accurately, and define whether a failed send leaves a pending invite. Add tests for invalid input, unauthorized inviter, token generation, expiry, and mail failure. Acceptance: the invited address receives a working link, and the API does not claim delivery when sending fails.

- [ ] **TM-05 — Add invitation lifecycle reads and actions.** Add team-scoped pending-invite listing and revocation; add resend with a fresh token if needed. Return safe DTOs with email, role, status, created time, and expiry, without the raw token. Acceptance: owners/admins can see and cancel invitations for their team only; a revoked link cannot be accepted.

- [ ] **TM-06 — Make invite acceptance reliable.** Provide a read-only link preview and authenticated `POST` acceptance. Preserve the invite token across registration or login. Verify the signed-in email matches the invite, expiry and status are valid, then create membership and consume the invite atomically. Handle repeat clicks, wrong account, expired/revoked token, and duplicate membership with clear responses. Acceptance: both existing and newly registered users can join exactly once and then select the team.

- [ ] **TM-07 — Add Invite and Join UI.** On the Members page, show an invite form and pending invitation list only when the API permits management; the server must still enforce permission. Add `/join?token=...` with invite preview, login/register continuation, acceptance result, and team selection. Show errors for expired, revoked, and wrong-account links. Do not show a success message until the send or acceptance API succeeds.

- [ ] **TM-08 — Implement role changes and member removal.** Replace the placeholder routes with explicit, team-scoped operations and UUID identifiers. Enforce the policy from TM-01; protect the last owner, prohibit unauthorized self-promotion, and define owner transfer separately. Revalidate live membership so old team tokens lose access after removal. Acceptance: role changes appear in fresh team selection, removed users cannot access team data, and cross-team IDs cannot be modified.

- [ ] **TM-09 — Add role and removal controls to Members UI.** Show current roles from the member API. Enable actions only for eligible viewers and members, confirm removal, disable repeated submissions, display API errors, and refetch roster and current-team context after success. Handle the signed-in user's role or membership changing in another session. Acceptance: reload and team switch show the server's current role, never a locally invented one.

- [ ] **TM-10 — Add team settings after the contracts exist.** Implement rename first with owner authorization and a DTO response; update the picker and header from server data. Treat team deletion as a separate destructive operation with clear rules for active escalations, invitations, and members. Do not enable the current empty `DELETE /api/v1/team` handler in the UI.

- [ ] **TM-11 — End-to-end release check.** Exercise owner creates team → invites an existing user and a new user → each joins → owner changes a role → admin actions are limited → owner removes a member → old member token is denied. Cover two teams, expired/revoked links, reloads during login, SMTP failure, browser preflight, and direct `/join` reload on static hosting. Run focused backend tests plus UI typecheck/build when implementation changes land, and update the root/UI READMEs with the actual contracts.

## Separate follow-up work

- [ ] **UX-01 — Task read/edit/delete UI.** The API already has task detail, name/description update, and delete routes. First verify team ownership, edit/delete permission checks, no-op responses, and delete semantics; then add task detail and editing controls.
- [ ] **UX-02 — Account settings.** Do not expose the current `DELETE /api/v1/auth/user` route as account deletion. Make it self-service, protect related team ownership and data, and define the result before building a UI.
