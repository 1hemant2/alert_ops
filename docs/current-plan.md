# Current task plan

## Task: Fix Spring Boot 4 integration-test imports

Started: 2026-10-08
Status: Complete

### Goal and scope

Restore clean CI test compilation after the Spring Boot 4 upgrade by using the
current auto-configuration package and class names in the two integration-test
configurations. Keep production behavior and dependency versions unchanged.

Previous task: webhook event history navigation is complete in commits
`1b56267` and `be4c40b`. The first compatibility fix is in `2e8f807` and
`c44ce71`; CI run `37684294945` found additional old Boot 3 imports.

### Decision-compliance note

- Use the auto-configuration classes supplied by the pinned Spring Boot 4.0.8
  dependencies; do not add compatibility dependencies or duplicate config.
- Change only the imports and exclusions in the two affected integration tests.

### Acceptance criteria

- Clean test sources compile in the Spring Boot 4.0.8 build.
- The integration-test configurations still exclude Redis, JDBC, JPA, and
  RabbitMQ startup where intended.
- Clean Maven verification passes in CI.
- The changelog records the compatibility fix.

### Steps

- [x] Update the Redis auto-configuration imports and exclusions.
- [x] Update the JDBC, JPA, AMQP, and entity-scan imports and exclusions.
- [x] Run clean test compilation, full package, and diff checks.
- [x] Update this plan and the existing changelog entry with verification.

### Verification and limitations

`./mvnw clean test-compile`, `./mvnw --batch-mode --no-transfer-progress clean
verify`, and `git diff --check` pass. The clean verification ran 238 tests with
0 failures/errors and 17 environment-gated skips. Independent read-only
verification was skipped because no usable subagent mechanism was available.
