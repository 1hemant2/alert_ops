# AlertOps cloud deployment learning path

This guide takes AlertOps from a local Spring Boot application to a GitOps-managed
Kubernetes workload. OCI is the implementation target; AWS equivalents are included
so the same design can be explained in interviews.

## Target architecture

```text
Developer -> GitLab -> CI pipeline -> container registry
                   \-> manifests repository -> Argo CD
                                                |
Internet -> load balancer -> Kubernetes Service -> AlertOps pods
                                                   |-- PostgreSQL
                                                   |-- RabbitMQ
                                                   `-- Redis

OCI: VCN + OKE/Compute + OCIR + Object Storage
AWS: VPC + EKS/EC2     + ECR  + S3
```

PostgreSQL, RabbitMQ, and Redis can run inside Kubernetes for learning. For a
production design, prefer managed services where the chosen cloud provides them.
Stateful systems require backups, persistent volumes, disruption planning, and
different scaling rules from the stateless AlertOps application.

## Course roadmap

| Phase | Build | Main concepts | Evidence of completion |
|---|---|---|---|
| 0 | Deployment readiness | config, secrets, health, tests | clean build and production checklist |
| 1 | Local containers | images, networks, volumes, Compose | healthy local stack after restart |
| 2 | Kubernetes locally | Pod, Deployment, Service, ConfigMap, Secret, PVC | stack runs in kind/minikube |
| 3 | CI with GitLab | stages, cache, artifacts, image tags, scanning | commit produces tested image |
| 4 | OCI foundation | compartments, IAM, VCN, subnets, gateways, security lists | private network and cluster exist |
| 5 | Cloud deployment | registry, ingress, DNS, TLS, storage | public HTTPS health endpoint |
| 6 | Argo CD GitOps | desired state, sync, drift, rollback | Git change deploys automatically |
| 7 | Reliability | probes, resources, HPA, PDB, backups, observability | failure and restore drills pass |
| 8 | Advanced delivery | Helm/Kustomize, promotion, canary, policy | controlled multi-environment release |

Do the phases in order. In particular, do not begin by copying YAML into a cloud
cluster: first prove the same image and configuration locally.

Completed foundation: [Phase 0 - production readiness](phase-0-readiness.md)

Current lesson: [Phase 1 - Docker and Compose foundations](phase-1-docker-foundations.md)

## Phase 0 audit (current repository)

The application builds on Java 17 and has a multi-stage, non-root Docker image plus a
Compose stack for PostgreSQL, RabbitMQ, and authenticated Redis. Spring Boot Actuator
is present. The Maven build now runs the first JWT unit tests; broader service and
integration coverage is still required. Phase 1 migrated one-time intent state from
process memory to authenticated Redis with TTL and atomic consumption.

Before treating the image as production-ready, fix these items:

- Move the JWT signing secret out of Java source and rotate the exposed value.
- Stop tracking `.env`; assume every committed value has been exposed and rotate it.
- Add actual unit/integration tests. A green build with zero tests is not a quality gate.
- Use a production profile with schema migrations (Flyway/Liquibase), not
  `spring.jpa.hibernate.ddl-auto=update`.
- Expose only health endpoints publicly; protect detailed Actuator endpoints.
- Replace development database and RabbitMQ credentials.
- Add readiness, liveness, and startup health groups.
- Decide how PostgreSQL, RabbitMQ, and Redis data are backed up and restored.
- Verify Redis-backed intent sharing and atomic consumption before multiple app pods. (Completed in Phase 1.)
- Pin container versions and add image/dependency scanning in CI.

## Configuration ownership

Use this rule throughout the project:

| Kind | Example | Storage |
|---|---|---|
| Non-secret config | port, log level, queue name | Git, ConfigMap |
| Secret | JWT key, database password | secret manager / Kubernetes Secret source |
| Build output | JAR, test report | GitLab artifact |
| Deployable image | immutable image tagged with commit SHA | OCIR (OCI) / ECR (AWS) |
| Application data | relational records | PostgreSQL with backups |
| Unstructured backup/export | database dump, report | OCI Object Storage / AWS S3 |

Never commit real credentials, base64-encoded or otherwise. Kubernetes Secret values
are only encoded, not encrypted by default.

## OCI to AWS interview map

| Purpose | OCI | AWS | Interview explanation |
|---|---|---|---|
| Isolation/billing boundary | Compartment | Account/OU (approximate) | OCI compartments are IAM and resource organization boundaries |
| Virtual network | VCN | VPC | CIDR, routing, subnets, and network controls |
| Virtual machine | Compute Instance | EC2 | host for self-managed workloads or cluster nodes |
| Managed Kubernetes | OKE | EKS | managed control plane with worker nodes |
| Container registry | OCIR | ECR | stores immutable application images |
| Object storage | Object Storage | S3 | backups and unstructured objects, not a mounted app database |
| Network firewall | NSG/Security List | Security Group/NACL | stateful workload rules vs subnet rules |
| Public entry point | OCI Load Balancer | ALB/NLB | routes traffic to Kubernetes ingress/services |
| Identity | IAM policies/dynamic groups | IAM policies/roles | least-privilege human and workload access |
| Keys/secrets | Vault | KMS/Secrets Manager | encryption keys and runtime secrets |

The mappings are conceptual, not exact one-to-one product equivalences.

## Recommended repository layout

Later phases will grow toward:

```text
.
|-- Dockerfile
|-- docker-compose.yml
|-- .gitlab-ci.yml
|-- docs/deployment/
|-- k8s/
|   |-- base/
|   `-- overlays/dev/
`-- src/
```

For serious GitOps, use a separate environment repository so CI can update an image
tag without granting the application repository direct cluster access. Argo CD pulls
desired state from Git; CI should not run `kubectl apply` against production.

## Learning journal template

For every phase, record:

1. What problem the technology solves.
2. The command/configuration used and why.
3. One failure you caused deliberately.
4. How you diagnosed it using logs, events, metrics, or health endpoints.
5. How the design maps from OCI to AWS.
6. The rollback and data-recovery procedure.

This turns the deployment into concrete interview stories rather than a list of tools.
