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

## Completed workflow

The implemented workflow is [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).
It runs for pushes to `deployment` and `master`, pull requests targeting `master`,
and manual dispatches.

```text
pull request to master                    push to deployment or master
-----------------------                   ----------------------------
Maven verification                        Maven verification
        |                                          |
        v                                          v
container build + Trivy scan               container build + Trivy scan
GHCR login and push: skipped               GHCR login and push: allowed
```

The two jobs run on separate temporary runners. `actions/checkout` is therefore
required in each job: it puts the triggering commit's source files on that job's
otherwise empty runner. `needs: test` makes `container-build` wait for a successful
Maven verification job; a Maven failure skips the container job.

### Pipeline outputs

| Output | Location | Retention | Purpose |
|---|---|---:|---|
| Fat JAR | GitHub Actions artifact | 7 days | Build evidence for the exact commit |
| Surefire reports | GitHub Actions artifact | 7 days | Test-failure diagnosis |
| `trivy-results.txt` | GitHub Actions artifact | 30 days | Container vulnerability evidence |
| Container image | GHCR | Package retention policy | Deployable release artifact |

An artifact is downloaded as a ZIP because GitHub Actions packages uploaded files
for transport. GHCR is separate from Actions artifacts: it stores OCI/Docker images,
not build reports.

### Image name, tag, and digest

The workflow builds and pushes an image named:

```text
ghcr.io/1hemant2/alert_ops:<git-commit-sha>
```

`<git-commit-sha>` is the readable source-version tag. `docker push` uploads the
image and GHCR returns a content digest such as `sha256:...`; that digest identifies
the exact immutable image content. The tag is used for source traceability, while a
digest is the strongest deployment pin:

```text
ghcr.io/1hemant2/alert_ops@sha256:<image-content-digest>
```

The local image disappears when the GitHub-hosted runner is destroyed. The pushed
GHCR image remains available for a later Kubernetes cluster to pull.

## Container security gate

After Docker builds the final image, Trivy scans the image before registry login and
push. Scanning after the build is necessary because the final image contains the JRE,
operating-system packages, application JAR, and all runtime Java dependencies.

```text
build image -> scan HIGH and CRITICAL findings -> upload report -> push only if clean
```

The scan uses a reviewed SHA-pinned Trivy Action. It writes a readable
`trivy-results.txt` file and `actions/upload-artifact` uploads it with `if: always()`.
That condition means the report is retained even when the scan fails; it does not run
another scan. The later GHCR steps use their normal implicit `success()` condition,
so they are skipped after a scan failure.

`exit-code: "1"` turns HIGH or CRITICAL findings into a release gate. The initial
audit found 31 findings. They were remediated by:

- upgrading the Spring Boot parent from `3.5.4` to `3.5.16`, which updates its managed
  dependency set together;
- temporarily overriding Spring Boot's managed Netty version to `4.1.136.Final` and
  PostgreSQL JDBC version to `42.7.12` until a future Spring Boot release manages
  those patched versions.

Netty is transitive rather than an AlertOps direct dependency:

```text
spring-boot-starter-data-redis -> lettuce-core -> Netty
```

Do not add an individual Netty module directly just to patch a CVE. The `netty.version`
override updates the Netty BOM consistently. The PostgreSQL driver is a direct runtime
dependency, but its original version was also supplied by Spring Boot dependency
management. Remove the temporary overrides once a Spring Boot update supplies the
same or newer fixed versions.

## Protected master branch

The GitHub repository has an active ruleset targeting `master`:

- pull requests are required before merging;
- branches must be up to date before merging;
- `Maven verification` and `Container image build` are required checks from GitHub
  Actions;
- force pushes and deletion are blocked.

The branch rule requires passing checks; it does not decide which workflow steps run.
The `if: github.event_name != 'pull_request'` condition on GHCR login and image push
is what prevents a pull-request build from publishing a container image.

## Controlled failure and recovery drill

An unmerged `ci-failure-drill` pull request added a temporary JUnit assertion of
`assertTrue(false)`. The Maven job reported the exact failing class, method, and
assertion message. Because `container-build` has `needs: test`, its image build was
skipped. The required checks prevented the PR from merging into `master`.

Recovery consisted of removing only the temporary test with `git rm`, committing,
and pushing the same branch. The PR then became green. It was closed without merging
and its remote and local branches were deleted.

Use this diagnostic order for a real CI incident:

```text
failed workflow -> failed job -> failed step -> first concrete error -> source/config fix
```

## GitHub Actions to GitLab CI mapping

The implementation remains GitHub Actions; this mapping is for interviews and for a
future deliberate GitLab migration.

| GitHub Actions | GitLab CI | Meaning |
|---|---|---|
| `.github/workflows/ci.yml` | `.gitlab-ci.yml` | Pipeline definition |
| `on: push` / `pull_request` | `rules:` / `only` / `except` | Pipeline trigger conditions |
| workflow job | GitLab job | Unit of work on a runner |
| `runs-on: ubuntu-24.04` | runner tags / executor selection | Runner environment |
| `needs: test` | `needs: [test]` | Dependency and ordering between jobs |
| `run:` | `script:` | Shell commands executed by a job |
| `uses:` | `image:`, `before_script`, included templates | Reusable implementation; no exact one-to-one equivalent |
| `services: redis` | `services:` | Temporary test dependency |
| `actions/upload-artifact` | `artifacts:` | Retained job output |
| `actions/setup-java` Maven cache | `cache:` | Reusable dependency cache |
| `GITHUB_TOKEN` | `CI_JOB_TOKEN` / protected CI variables | Scoped job authentication |
| GHCR | GitLab Container Registry | Remote OCI image registry |
| GitHub ruleset | protected branches + merge-request approval/pipeline rules | Merge policy |

In an interview: “The concepts are portable. I used GitHub Actions to test, build,
scan, and publish an immutable image; GitLab CI expresses the same lifecycle through
stages/jobs, `rules`, `services`, artifacts, caches, and the GitLab registry. CI still
creates evidence and release artifacts; GitOps later deploys the version declared in
Git.”

## Official references

- [Building and testing Java with Maven](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
- [Publishing Docker images](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [GitHub repository rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)
- [Trivy GitHub Action](https://github.com/aquasecurity/trivy-action)
