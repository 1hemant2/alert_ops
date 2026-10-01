# AlertOps UI

React and TypeScript client for the AlertOps Spring Boot API. It runs with Vite locally or as static files deployed separately from the backend. See the [root README](../README.md) for backend setup, SMTP configuration, and a webhook API example.

## Run locally

Use Node.js 22 and npm. Start the backend and its dependencies first, then run:

```bash
npm ci
npm run dev
```

Open `http://localhost:5173`. When `VITE_API_BASE_URL` is empty or unset, Vite proxies `/api` and `/actuator` to `http://localhost:8096`. The backend's default allowed browser origin is `http://localhost:5173`.

## Product walkthrough

1. Register, verify the address from the email, and log in.
2. Create or select a team. Owners and admins can invite members from **Members**; admins can invite users only. An invitee signs in with the verified address that received the invitation.
3. Create a task with a name, description, and source. Priority, category, and reference URL are optional.
4. Create a response path with steps assigned to team members. Add, edit, or delete steps, and drag the grip to change their order. Arrow keys also work when the grip has focus.
5. Create a run from the task and path, open its details, and start it. Follow the saved step states while it runs.
6. Open the emailed acknowledgement link as the recipient and confirm on the page to stop later steps.

For automatic creation, an owner or admin can create a webhook on **Webhooks**, choose a default response path, and copy the secret shown on creation. Incoming events create a task and run; the page shows saved events with their task and run links. The same page can rotate the secret or disable the webhook.

The UI keeps login and team-scoped tokens in `sessionStorage`. It clears client data when the session or selected team changes.

## Build and deploy

Set `VITE_API_BASE_URL` to the public API origin at build time for a separately hosted UI. It is browser-visible configuration, so never put secrets in it. Set the backend's `ALERTOPS_CORS_ALLOWED_ORIGINS` to the UI origin and `ALERTOPS_UI_BASE_URL` to the same origin so emailed links open the UI.

```bash
npm ci
npm run build
npm run preview
```

Deploy `dist/` to a static host that serves `index.html` for unknown client routes. The optional container builds the UI with Node.js 22 and serves it with Nginx on port 8080:

```bash
docker build --build-arg VITE_API_BASE_URL=https://api.example.com -t alertops-ui .
docker run --rm -p 8080:8080 alertops-ui
```

The UI container does not start the Java API. Its Nginx configuration exposes `/health` and supports client-side routes. `npm run typecheck` and `npm run lint` are available for local checks.
