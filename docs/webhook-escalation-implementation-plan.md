# Webhook task creation plan

Status: Implemented locally — deployed verification remains pending.
Source audit: 2026-10-06. Track inspected progress and remaining release checks in
[Feature 2 of the launch checklist](product-launch-readiness.md#feature-2-create-and-start-an-escalation-through-a-webhook).
This plan describes the target behavior, not proof that every item is implemented.

A team chooses a default response path for a webhook. When an external system sends a valid event, AlertOps creates one task, starts one run, and saves the complete JSON event for later viewing. A request can optionally choose a different path from the same team.

## Task fields

Tasks work for alerts, onboarding questions, and other requests. Add these fields to the existing task create/edit API and UI:

| Field | Rule |
| --- | --- |
| `name` (shown as Title) | Required |
| `description` | Required for webhook-created tasks |
| `source` | Required for webhook-created tasks; manual tasks default to “Manual” and can change it |
| `priority` | Optional user-defined label, such as `P1`, `VIP`, or `urgent` |
| `category` | Optional short text, such as “Onboarding” |
| `referenceUrl` | Optional HTTP(S) link useful to the responder |

Keep the current task description length limit. Validate lengths for the new text fields. Existing tasks get `source = "Manual"`; leave their optional fields empty. Show these fields on task and run screens. At run start, snapshot the task title, source, optional metadata, and description into each execution step so later task edits do not change a saved run. Every escalation email shows the task title, source, and available task metadata, including a short, safe source label in its subject. Use wording that works for more than incidents.

## Webhook request

```text
POST /api/v1/webhooks/{webhookId}/events
X-AlertOps-Webhook-Secret: <secret>
Content-Type: application/json
```

Example:

```json
{
  "eventId": "event-123",
  "taskName": "New hire cannot complete account setup",
  "description": "The setup form reports an access error",
  "source": "HR request form",
  "priority": "NORMAL",
  "category": "Onboarding",
  "referenceUrl": "https://example.com/requests/123",
  "requestId": "123",
  "department": "Finance"
}
```

The body must be a JSON object. `eventId`, `taskName`, `description`, and `source` must be nonblank strings; `taskName` becomes `Task.name`. Reject missing or invalid values with `400` and create nothing, including when an optional task field is supplied with an invalid value. Accept other JSON fields without mapping them to the task; save the whole object in the webhook event. Limit the request size before storing it.

The caller may include a `flowId` to choose a response path. If it is absent, use the webhook's default path. If it is present, verify that the path belongs to the webhook's team before creating anything. The caller cannot choose a team. Do not accept an `escalationId` as a path choice: `escalationId` identifies the new run, while `flowId` identifies the reusable path. Return the chosen `flowId`, created `taskId`, and generated `escalationId` in the response.

## Records to save

- **Task:** Copy the required and supplied optional task fields from the request.
- **Webhook configuration:** Save its team, default response path, name, secret hash, and enabled state. Show the raw secret only when it is created or rotated.
- **Webhook event:** Save `webhookId`, `eventId` from the request body, `receivedAt`, the complete payload as JSON, `taskId`, and `escalationId`. Extra fields stay in the payload for later viewing; they do not need separate columns now.

Create the task, run, and event in one database transaction. If any part fails, save none of them. Keep a unique constraint on `(webhookId, eventId)`: the same ID and same payload returns the original task and run, even if the webhook's default path has since changed; the same ID with different payload returns `409`. Handle concurrent retries using that constraint. Invalid or disabled webhooks cannot create tasks. Cap request size and rate to prevent abuse.

## UI

- Let team owners/admins create a webhook with a default response path, copy its one-time secret, and later rotate or disable it. Show team flow IDs so a sender can choose a different path when needed.
- Show received events with their time and a link to the specific task detail page they created. Keep the latest three events visible by default, with an explicit control to reveal older events. The event detail shows the complete saved JSON payload and a link to the run. Only members of that team can view it.
- Show task source and optional metadata on the task detail, run, and email surfaces. Team members can open the webhook event when they need the original request.

## Implementation order

1. Add task fields to the database, task API, task UI, run display, and email.
2. Add webhook configuration and event tables, plus team-admin configuration APIs and UI.
3. Add the authenticated webhook event route. Reuse the existing run-start logic without inventing a signed-in user for webhook requests.
4. Add the event list and detail UI with links to its task and run.

## Before marking this complete

- A valid request creates exactly one task and run and saves the complete JSON event with their IDs.
- Missing `taskName`, `description`, `source`, or event ID creates nothing.
- Omitting `flowId` uses the default path; a same-team `flowId` overrides it; a foreign or unknown flow creates nothing.
- Replaying an event ID never creates another run; a changed payload with the same ID is rejected.
- Invalid secrets, disabled webhooks, oversized requests, and foreign-team access are rejected.
- The task and run appear in the UI, the event links to them, and the email includes the saved task title, source, optional metadata, and description. Email acknowledgement still stops later steps.
