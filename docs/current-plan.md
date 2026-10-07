# Current task plan

## Task: Fix CI container image build

Started: 2026-10-08
Status: In progress

### Goal and scope

Restore the full CI pipeline after Maven verification succeeds but the Docker
image build fails. Keep the pinned base image and install the current patched
Jammy OpenSSL packages without depending on an unavailable repository version.

Previous task: webhook event history navigation is complete in commits
`1b56267` and `be4c40b`. Spring Boot 4 test-import fixes are in `2e8f807`,
`c44ce71`, `63e8c7d`, and `6bc0f06`. CI run `37684605184` passed Maven but
failed during `Build commit image`.

### Decision-compliance note

- Keep the existing pinned container base; change only the package installation
  that currently prevents the image from building.
- Install named patched packages from the current Jammy repository and retain
  the image vulnerability scan as the release gate.

### Acceptance criteria

- The Docker image builds successfully in CI.
- Trivy reports no unfixed HIGH or CRITICAL vulnerabilities.
- Maven verification remains green and the complete CI workflow passes.
- The changelog records the compatibility fix.

### Steps

- [ ] Update the Dockerfile package installation.
- [ ] Run the local diff checks and push the fix.
- [ ] Monitor CI through Maven, image build, and Trivy completion.
- [ ] Update the changelog with the final CI result.

### Verification and limitations

The prior clean Maven verification ran 238 tests with 0 failures/errors and
17 environment-gated skips. Docker is unavailable locally, so image build and
Trivy verification must be confirmed by GitHub Actions. Independent read-only
verification will be skipped if no usable subagent mechanism is available.
