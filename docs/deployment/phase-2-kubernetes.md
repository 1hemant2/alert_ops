# Phase 2: Local Kubernetes

## Objective

Run the verified AlertOps container stack on a local Kubernetes cluster and understand
how Kubernetes continuously reconciles declared state. The local cluster uses kind
(Kubernetes IN Docker), so Kubernetes nodes themselves run as Docker containers.

```text
Windows host
`-- Docker Engine
    `-- kind node container
        `-- Kubernetes
            |-- AlertOps pods
            |-- PostgreSQL pod + persistent volume
            |-- RabbitMQ pod + persistent volume
            `-- Redis pod + persistent volume
```

This phase runs stateful dependencies inside Kubernetes for learning. A production
cloud design should evaluate managed PostgreSQL, RabbitMQ, and Redis services rather
than assuming every database or broker belongs inside the application cluster.

## Why kind

The workstation already has Docker Desktop and `kubectl`. kind adds one local-cluster
tool and directly reuses Docker images and networking. Clusters are disposable, making
failure drills and clean rebuilds safe. We will pin the kind node image by Kubernetes
version and digest for the same reproducibility reasons used in Phase 1.

## Task sequence

1. Install compatible kind and kubectl versions.
2. Create a pinned, single-control-plane learning cluster.
3. Learn contexts, clusters, nodes, namespaces, and basic `kubectl` inspection.
4. Build AlertOps and load its local image into kind.
5. Create a dedicated `alertops` namespace.
6. Separate non-secret ConfigMap values from Secret values.
7. Deploy PostgreSQL with a Service and PersistentVolumeClaim.
8. Deploy RabbitMQ with stable identity, a Service, and persistent storage.
9. Deploy Redis with authentication, a Service, and persistent storage.
10. Deploy AlertOps with startup, liveness, and readiness probes plus resources.
11. Expose AlertOps locally through a Kubernetes Service/port-forward.
12. Scale AlertOps to multiple replicas and verify shared Redis intent state.
13. Perform pod deletion, dependency outage, rollout, rollback, and storage drills.
14. Document evidence and complete the Phase 2 checklist.

## Compose-to-Kubernetes map

| Compose | Kubernetes | Purpose |
|---|---|---|
| service container | Pod controlled by Deployment/StatefulSet | Run workload processes |
| service name | Service + cluster DNS | Stable network discovery |
| `environment` | ConfigMap and Secret references | Runtime configuration |
| `healthcheck` | startup/liveness/readiness probes | Lifecycle and traffic decisions |
| named volume | PersistentVolumeClaim | Data independent of a pod |
| `cpus`/`mem_limit` | resource requests and limits | Scheduling and enforcement |
| `depends_on` | probes, retries, and init logic | Dependency readiness |
| `restart` | controller reconciliation | Replace failed workload instances |

Kubernetes has no direct equivalent of Compose `depends_on`. Applications must retry
remote dependencies, and probes/controllers express whether a workload should receive
traffic or be restarted.

## Phase boundary

Do not begin by writing every manifest at once. First prove the cluster, context, node,
and local image workflow. Then deploy one workload at a time and inspect the Kubernetes
objects created at each step.
