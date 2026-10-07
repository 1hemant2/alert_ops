# Current task plan

## Task: Fix CI container image build

Started: 2026-10-08
Status: Complete

### Goal and scope

Restore the full CI pipeline after Maven verification succeeds but the Docker
image build fails. Keep the pinned base image and install the current patched
Jammy OpenSSL packages without depending on an unavailable repository version.

Previous task: webhook event history navigation is complete in commits
`1b56267` and `be4c40b`. Spring Boot 4 test-import fixes are in `2e8f807`,
`c44ce71`, `63e8c7d`, and `6bc0f06`. CI run `37684605184` passed Maven but
failed during `Build commit image`; diagnostic logging in `c1f81ea` made the
successful rerun `37686152716` observable.

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

- [x] Update the Dockerfile package installation.
- [x] Run the local diff checks and push the fix.
- [x] Inspect the diagnostic Docker build result and apply the smallest fix.
- [x] Monitor CI through Maven, image build, and Trivy completion.
- [x] Correct the changelog with the final CI result.

### Verification and limitations

Local Docker is unavailable. GitHub Actions run `37686152716` passed Maven
verification, the container image build, and the Trivy scan; the run shows five
non-blocking deprecation warnings. Maven ran 238 tests with 0 failures/errors
and 17 environment-gated skips. Independent read-only verification was skipped
because no usable subagent mechanism was available.
