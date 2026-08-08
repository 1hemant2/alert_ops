# Phase 3: Continuous Integration with GitHub Actions

## Objective

Turn the verified local build into a repeatable CI pipeline. Every relevant push and
pull request should prove that AlertOps compiles and passes its tests. Trusted branch
builds will then create an immutable Docker image and publish it to GitHub Container
Registry (GHCR) using the Git commit SHA.

Phase 3 automates artifact creation. It does not deploy directly to Kubernetes:

```text
Developer pushes commit to GitHub
        |
        v
GitHub Actions workflow
        |-- check out exact commit
        |-- install pinned Java toolchain
        |-- restore Maven dependency cache
        |-- run tests and package JAR
        |-- upload test/build evidence
        |-- build and scan container image
        `-- publish immutable image to GHCR on trusted branches

Later phase:
Argo CD reads Git desired state and deploys the selected image
```

CI answers, "Is this commit a valid release candidate?" GitOps deployment answers,
"Does the cluster match the version declared in Git?" Keeping those responsibilities
separate prevents a CI job from holding broad production-cluster credentials.

## Implementation decision

AlertOps already uses GitHub as its remote repository, so this phase uses:

- GitHub Actions for CI automation;
- GitHub-hosted runners initially;
- GitHub Actions artifacts for JARs and reports;
- GitHub Container Registry (`ghcr.io`) for Phase 3 images;
- the built-in `GITHUB_TOKEN` with explicitly limited permissions where possible.

There will be no parallel GitLab repository or `.gitlab-ci.yml` during implementation.
GitLab CI mapping and a small YAML translation exercise are deferred until the GitHub
workflow is complete. This keeps one source repository and one CI mental model active.

## CI is not the developer machine

A GitHub-hosted runner is a temporary clean machine allocated for a job. It does not
reuse the developer's local JDK, Maven cache, `.env`, Docker images, or kind cluster.
The workflow must declare everything required to reproduce the build.

```text
Local machine                          GitHub-hosted runner
------------                           --------------------
long-lived files                       temporary clean VM
developer credentials                  scoped workflow token/secrets
warm Maven cache                       cache restored only when configured
local Docker image                     image rebuilt from checked-out source
local .env                             unavailable and must remain uncommitted
```

A successful local build is necessary evidence, but CI proves that the repository can
build from a clean checkout without hidden machine-specific state.

## Core terminology

| Term | Meaning in this phase |
|---|---|
| Workflow | YAML automation definition under `.github/workflows/` |
| Event | Trigger such as a push, pull request, tag, or manual run |
| Job | Group of steps executed on one runner |
| Runner | Machine that executes a job |
| Step | One command or reusable action within a job |
| Action | Reusable workflow building block such as checkout or Java setup |
| Artifact | Output retained from a run, such as a JAR or test report |
| Cache | Reusable dependencies that improve speed but are not release outputs |
| Service container | Temporary dependency, such as Redis, available to a test job |
| Workflow secret | Sensitive value supplied to a job without committing it |
| Container registry | Remote store for versioned Docker/OCI images |

Artifacts and caches are not interchangeable:

```text
Maven cache -> speeds up later builds; safe to regenerate
JAR artifact -> evidence/output produced by this exact workflow run
Docker image -> deployable release artifact stored in a registry
```

## Image identity

The primary image tag will use the Git commit SHA:

```text
ghcr.io/<owner>/alert_ops:<commit-sha>
```

The commit SHA connects source, workflow evidence, image, and later Kubernetes
deployment. Human-friendly release tags can be added, but they should not replace an
immutable commit or digest reference for traceability.

## Security rules

- Never copy `.env` or `.env.k8s.local` into the workflow or image.
- Give `GITHUB_TOKEN` only the permissions required by each job.
- Do not publish images from untrusted pull-request code with write credentials.
- Pin third-party actions to reviewed versions and later to commit SHAs.
- Keep runtime credentials out of image-build arguments and image layers.
- Treat test reports and logs as potentially sensitive output.
- Build once and promote the same image rather than rebuilding per environment.

## Task sequence

1. Understand workflow, runner, job, step, event, cache, artifact, and registry.
2. Add a minimal workflow triggered by pushes and pull requests.
3. Run Maven tests and package the JAR on Java 17.
4. Cache Maven dependencies and upload useful test/build artifacts.
5. Run the Redis integration test with a temporary service container.
6. Build the Docker image in CI without publishing from pull requests.
7. Authenticate safely and publish commit-SHA images to GHCR from trusted branches.
8. Add image scanning and preserve useful security evidence.
9. Configure required checks/branch protection after the workflow is stable.
10. Cause and diagnose a controlled pipeline failure, then document recovery.
11. Document the completed workflow and only then map it to GitLab CI terminology.

Each task will be introduced separately. The first version will remain small; caching,
publishing, scanning, and policy will be added only after the Maven test job is proven.

## Completion evidence

Phase 3 is complete when:

- a pull request automatically runs the Maven quality gate;
- a failed test blocks the workflow;
- a successful run retains useful reports or a JAR artifact;
- the Docker image is built from the same tested commit;
- a trusted branch publishes an image tagged with its commit SHA to GHCR;
- secrets are not stored in Git or printed in logs;
- one controlled failure can be diagnosed from job logs;
- the process is documented well enough to explain in an interview.

## Official references

- [Building and testing Java with Maven](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
- [Publishing Docker images](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
