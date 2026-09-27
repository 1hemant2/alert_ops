# AlertOps UI

Standalone React client for the AlertOps Spring Boot API. The production build is static and can be deployed independently from the backend.

## Run locally

Requirements: Node.js 20.19 or newer, or Node.js 22.12 or newer, and npm.

```bash
cp .env.example .env.local
npm ci
npm run dev
```

Vite proxies `/api` and `/actuator` to `http://localhost:8096` when `VITE_API_BASE_URL` is empty. Start the API and its dependencies with the instructions in the repository root README. The backend's `ALERTOPS_CORS_ALLOWED_ORIGINS` defaults to `http://localhost:5173` for local browser development.

## Build and serve

Set `VITE_API_BASE_URL` to the public API origin at build time. It is public browser configuration; never put credentials or signing keys in it.

```bash
npm ci
npm run typecheck
npm run build
npm run preview
```

Upload `dist/` to any static host or CDN configured to serve `index.html` for unknown client routes. Use the same UI origin in the backend's `ALERTOPS_CORS_ALLOWED_ORIGINS` value. Multiple exact origins may be comma separated. Include the scheme and port when required, for example `https://alerts.example.com`.

For a standalone container build:

```bash
docker build --build-arg VITE_API_BASE_URL=https://api.example.com -t alertops-ui .
docker run --rm -p 8080:8080 alertops-ui
```

The container serves only the static UI on port `8080`; it does not contain or start the Java API. Nginx falls back to `index.html` for React Router paths and exposes `/health` for a simple host check.

## Demo walkthrough

1. Register an account and sign in.
2. Create or select a team.
3. Add a task with the incident context.
4. Create a flow and add at least one node. The recipient email must belong to a registered user in that team.
5. Create an escalation from the task and flow, open its detail page, and start it.
6. Watch the escalation and saved node states update. The detail page refreshes while the escalation is `RUNNING`.

Notification delivery is simulated: the consumer currently writes the recipient and message to the backend application log. The UI labels this explicitly. Flow nodes can be moved with the up/down controls; each move sends the flow version and refetches the flow after the API accepts it.

## API configuration

The browser calls these Spring endpoints under `/api/v1`: auth, team selection, tasks, flows and nodes, escalation creation/start, and the team scoped execution-state read. Bearer tokens are kept in `sessionStorage`; selecting a team exchanges the login token for a team scoped token. The client clears cached data when the session or team changes.
