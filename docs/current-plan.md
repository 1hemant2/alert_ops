# Current task plan

## Task: Fix Spring Boot 4 Redis test imports

Started: 2026-10-08
Status: Complete

### Goal and scope

Restore CI test compilation after the Spring Boot 4 upgrade by using the
current Redis auto-configuration package and class names in integration-test
configuration. Keep production behavior and dependency versions unchanged.

Previous task: webhook event history navigation is complete in commits
`1b56267` and `be4c40b`.

### Decision-compliance note

- Use the auto-configuration classes supplied by the pinned Spring Boot 4.0.8
  dependency; do not add compatibility dependencies or duplicate Redis config.
- Change only the two test configurations that explicitly exclude Redis
  auto-configuration.

### Acceptance criteria

- Test sources compile in the Spring Boot 4.0.8 build.
- The affected integration-test configurations still exclude Redis startup.
- Focused test compilation and the normal Maven package pass.
- The changelog records the compatibility fix.

### Steps

- [x] Update the two Redis auto-configuration imports and exclusions.
- [x] Run focused compilation/tests, full package, and diff checks.
- [x] Update this plan and the changelog with verification results.

### Verification and limitations

`./mvnw -q -DskipTests test-compile`, `./mvnw -q package`, and
`git diff --check` pass. Environment-gated PostgreSQL/RabbitMQ integration
tests may remain skipped when their services are unavailable. Independent
read-only verification was skipped because no usable subagent mechanism was
available.
