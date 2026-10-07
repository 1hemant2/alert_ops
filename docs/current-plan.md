# Current task plan

## Task: Fix production image vulnerabilities

Started: 2026-10-08
Status: Complete

### Goal and scope

Remove the critical Spring MVC dependency finding that fails the Trivy image
gate and refresh the pinned runtime image while preserving application
behavior. Keep the separate UI-only dependency finding out of scope.

### Decision-compliance note

- The Spring MVC vulnerability is fixed by the Spring Framework 7.0.9 line;
  use the compatible Spring Boot 4 maintenance release rather than mixing
  Spring Framework 7 into the Boot 3 dependency graph.
- Keep Java 17 compatibility and existing Jakarta APIs; update only the
  starter names or application code required by the Boot 4 migration.
- Keep Trivy HIGH/CRITICAL findings as the release gate; do not suppress the
  finding or add an ignore rule.

### Acceptance criteria

- Maven resolves Spring Framework 7.0.9 or newer and the application builds.
- Existing focused tests and the full Maven package pass.
- The Dockerfile uses refreshed pinned base images and retains the fixed
  OpenSSL package version.
- Trivy reports no unfixed HIGH or CRITICAL backend image vulnerabilities when
  the image can be built locally or by CI.
- The changelog records the completed security fix and limitations.

### Steps

- [x] Upgrade the Spring Boot dependency graph and resolve migration issues.
- [x] Refresh pinned container bases and apply current transitive security fixes.
- [x] Run focused tests, the full package, and documentation checks.

### Verification and limitations

The backend dependency scan reports zero HIGH or CRITICAL findings, and
`./mvnw -q package` passes with 260 tests, 0 failures/errors, and 17
environment-gated skips. The pinned runtime base still contains an older
`libssl3`, but the Dockerfile installs the fixed `3.0.2-0ubuntu1.30` package.
Docker is unavailable locally, so the final image build/scan remains a CI check.
The UI `source-map-js` finding is outside this backend image task. Independent
read-only verification was skipped because no usable subagent mechanism was
available.
