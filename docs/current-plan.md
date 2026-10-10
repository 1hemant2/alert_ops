# Current task plan

## Task: Separate Kubernetes materials from the first-release deployment

Started: 2026-10-11
Status: Complete — archive stored alongside the application; EC2 deployment remains pending

### Goal and scope

Move Kubernetes manifests and the historical deployment learning guides into a
separate local Git repository under `/Users/hemant/pers`. Keep the application, Docker Compose,
Dockerfiles, and GitHub Actions in ReplyTrail. Update deployment links and the
[release checklist](product-launch-readiness.md#release-check).

### Decision-compliance note

- The owner selected one EC2 machine with Docker Compose for version one.
- Preserve the backend, PostgreSQL, RabbitMQ, and Redis dependencies.
- Preserve moved files byte-for-byte before removing them from this working tree.
- This cleanup does not implement or deploy the production Compose configuration.
- Recurrence implementation is complete locally; its remaining verification is
  tracked in the [recurrence plan](recurring-escalation-implementation-plan.md).

### Acceptance criteria and steps

- [x] Inspect the working tree, deployment files, CI, and incoming links.
- [x] Create a separate local repository and preserve the deployment materials.
- [x] Remove the moved materials here and document the EC2/Compose direction.
- [x] Verify archive contents, remaining links, packaging, and the final diff.
- [x] Record the outcome and archive location in the changelog.
- [x] Move the archive to `/Users/hemant/pers/replytrail-kubernetes` and update its location.

### Verification results

All 18 archived files match their original tracked versions byte-for-byte.
Updated-document links resolve, backend packaging with tests skipped passes, and
`git diff --check` passes. Application code, CI, and Compose are unchanged.
The archive repository is `/Users/hemant/pers/replytrail-kubernetes`; it has no
remote or commits and is preserved alongside the application. Source files
remain recoverable from Git history. No EC2 or deployed verification was performed.
