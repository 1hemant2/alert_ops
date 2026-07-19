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

Every task in this phase will record the problem being solved, explain each command,
show how to read its output, cause one controlled failure, and map the local concept
to OCI OKE and AWS EKS.

## What Kubernetes is

Kubernetes is a container orchestrator. We declare the desired state of containerized
applications through its API, and control-plane components continuously work to make
the cluster's actual state match it.

For example, desired state can say:

```text
Run three AlertOps replicas.
Keep only ready replicas behind one stable network address.
Replace failed replicas.
Give every replica defined CPU and memory resources.
Roll gradually from image version A to version B.
```

Kubernetes is not a replacement for the Dockerfile or container image. AlertOps still
uses the image built in Phase 1. Kubernetes decides where and how instances of that
image run and continuously reconciles their lifecycle.

## Direct Docker deployment on EC2 or OCI Compute

A valid small production architecture can use one virtual machine:

```text
Internet
   |
load balancer / reverse proxy
   |
EC2 or OCI Compute instance
   |-- AlertOps container
   |-- systemd or Docker Compose
   `-- logging/monitoring agent

Managed PostgreSQL, RabbitMQ and Redis are reached over the network.
```

The team creates the VM and firewall rules, installs Docker, authenticates to the
registry, pulls the pinned image, injects configuration, starts the container, and
operates TLS, monitoring, backups and deployment automation. This is not wrong. It can
be the best design for one or a few services, a small team, predictable traffic,
modest availability requirements, or strict cost constraints.

The work grows when there are multiple hosts and replicas:

- choose a machine with enough CPU and memory;
- keep the desired replica count after process or VM failure;
- discover changing container addresses;
- route traffic only to ready replicas;
- deploy gradually and roll back safely;
- scale services independently;
- drain work before termination;
- apply configuration, access policy and monitoring consistently.

Scripts can automate these behaviours, but the organization eventually owns a custom
orchestrator made from scripts. Kubernetes supplies a standard API, scheduler and
controllers for these recurring problems.

## EC2 and Kubernetes are layers, not alternatives

Kubernetes still needs computers. In AWS, worker nodes are commonly EC2 instances; in
OCI, they are Compute instances in OKE node pools.

```text
AWS account / OCI tenancy
        |
VPC / VCN
        |
Managed Kubernetes control plane: EKS / OKE
        |
Worker nodes: EC2 / OCI Compute
        |
Pods containing AlertOps containers
```

The practical comparison is:

```text
Manage containers directly on VMs
                versus
Use Kubernetes to manage containers across a pool of VMs
```

EKS and OKE manage the control plane. The team still owns workload manifests, node
capacity, IAM, network policy, observability, upgrades, security, cost and application
reliability. “Managed Kubernetes” does not mean “no operations.” AWS documents the
same separation between the EKS control plane and EC2 worker nodes in its
[Kubernetes concepts](https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-concepts.html).

## Desired state and reconciliation

Docker Compose creates a declared stack on one Docker Engine. Kubernetes adds a
continuous control loop:

```text
Desired state in Kubernetes API
        |
controller compares desired and actual state
        |
        +-- equal: continue watching
        `-- different: create, replace, update or remove resources
```

Example:

```text
Desired replicas: 3
Actual replicas:  2
        |
Deployment controller creates a replacement Pod
        |
Actual replicas:  3
```

This is self-healing of declared workload state. Kubernetes can restart containers,
replace Pods and remove failed Pods from Service endpoints. It cannot fix incorrect
business logic, restore data without a backup design, or repair an unavailable remote
database. See the official
[Kubernetes self-healing documentation](https://kubernetes.io/docs/concepts/architecture/self-healing/).

## Cluster architecture

A cluster contains a control plane and worker nodes:

```text
Developer / Argo CD
        |
        | Kubernetes API through kubectl or GitOps
        v
+----------------------- Control plane -----------------------+
| API server | scheduler | controllers | etcd desired state  |
+------------------------------+-------------------------------+
                               |
               schedules and supervises workloads
                               |
          +--------------------+--------------------+
          |                                         |
+---------v---------+                     +---------v---------+
| Worker node 1     |                     | Worker node 2     |
| kubelet           |                     | kubelet           |
| container runtime |                     | container runtime |
| AlertOps Pod A    |                     | AlertOps Pod B    |
+-------------------+                     +-------------------+
```

| Component | Responsibility |
|---|---|
| API server | Front door for resource operations and validation |
| etcd | Durable control-plane store for cluster desired state |
| scheduler | Selects a suitable node for an unscheduled Pod |
| controllers | Reconcile Deployments, replicas, nodes and other resources |
| kubelet | Node agent ensuring assigned Pods and containers run |
| container runtime | Pulls images and runs containers on a node |

Application requests do not normally pass through the Kubernetes API server. It is the
management interface; Services and ingress/load balancers carry application traffic.

## Core Kubernetes resources

```text
Deployment
  `-- ReplicaSet
       |-- Pod: AlertOps container
       |-- Pod: AlertOps container
       `-- Pod: AlertOps container

Service -> stable DNS and traffic distribution to ready Pods
ConfigMap -> non-secret runtime configuration
Secret -> sensitive runtime values
PersistentVolumeClaim -> request for persistent storage
```

- **Pod:** smallest scheduling unit. Pods are disposable; their names and IPs can
  change, so clients do not depend on a Pod IP.
- **Deployment:** declares stateless replicas and rollout strategy. AlertOps belongs
  in a Deployment.
- **Service:** provides stable discovery and selects current Pods using labels.
- **StatefulSet:** provides stable identity and ordered lifecycle for stateful systems.
- **ConfigMap:** stores non-secret configuration.
- **Secret:** stores sensitive values as a dedicated API object, but base64 is not
  encryption; production still requires encryption, RBAC and Vault integration.
- **PersistentVolumeClaim:** requests storage whose lifecycle is separate from a Pod.

Kubernetes also separates application probes:

| Probe | Question | Failure result |
|---|---|---|
| Startup | Has the process completed initialization? | Delay other probes; eventually restart after repeated failure |
| Liveness | Is this process stuck and should it restart? | Restart the container |
| Readiness | Should this Pod receive traffic? | Remove it from Service endpoints |

The Phase 1 failure drills showed why a remote database outage should not make
AlertOps fail liveness: restarting AlertOps cannot repair PostgreSQL.

## AlertOps request flow

```text
Client
  |
Ingress / cloud load balancer
  |
Kubernetes Service
  |
  +-- ready AlertOps Pod A
  +-- ready AlertOps Pod B
  `-- ready AlertOps Pod C
         |-- PostgreSQL
         |-- RabbitMQ
         `-- shared Redis intent state
```

Redis integration from Phase 1 matters here: Pod A can create an intent and Pod B can
atomically consume the same shared value.

## Why organizations adopt Kubernetes

Kubernetes becomes useful when consistent operations across many workloads matter more
than its platform complexity. Common reasons include:

- declarative deployment and repeatable environments;
- replica management and self-healing;
- service discovery and stable networking;
- rolling updates and rollback history;
- workload and node scaling;
- CPU/memory scheduling and isolation;
- standard probes, configuration and secret interfaces;
- a shared platform for multiple teams and services;
- GitOps reconciliation with Argo CD;
- a common API and ecosystem across cloud providers.

Not every company or application uses or needs Kubernetes. Its costs include cluster
and node spend, a large learning surface, networking/storage/IAM complexity, upgrades,
more layers to debug, and the need for strong observability and security governance.

| Situation | Often a reasonable starting point |
|---|---|
| One service, small team | Docker on EC2/OCI Compute or a managed app platform |
| Several containers on one host | Docker Compose with automated VM provisioning |
| AWS containers without Kubernetes portability needs | ECS/Fargate |
| Many services/teams and frequent releases | EKS/OKE/Kubernetes |
| Existing company Kubernetes platform | Deploy into that established platform |
| Local Kubernetes learning and manifest tests | kind, minikube or Docker Desktop Kubernetes |

Kubernetes should be chosen for workload and organizational requirements, not merely
because it is popular.

## Why kind

`kind` means **Kubernetes IN Docker**. It creates a real local Kubernetes cluster whose
nodes are Docker containers:

```text
Windows laptop
  `-- Docker Desktop Linux VM
       |-- kind control-plane node container
       |    `-- Kubernetes control-plane components
       `-- optional kind worker node containers
            `-- application Pods
```

The node container provides an environment in which Kubernetes components and a
container runtime operate. This makes a cluster cheap, disposable and reproducible.
The workstation already has Docker Desktop and `kubectl`; kind adds one local-cluster
tool and directly reuses Docker images and networking.

kind is useful for learning Kubernetes APIs, validating manifests, testing rollouts
and probes, loading a locally built AlertOps image without a cloud registry, and
creating disposable CI clusters. It was primarily designed for Kubernetes testing and
is also useful for local development and CI.

kind is not the production platform. It does not reproduce cloud availability zones,
load balancers, IAM or managed storage. The same resource concepts will later move to
OKE, with EKS mappings for interviews. See the official [kind overview](https://kind.sigs.k8s.io/)
and [quick start](https://kind.sigs.k8s.io/docs/user/quick-start/).

We will pin the kind node image by Kubernetes version and digest for the same
reproducibility reasons used in Phase 1.

## Compose, kind and managed cloud Kubernetes

| Layer | Docker Compose | kind | OKE / EKS |
|---|---|---|---|
| Purpose | Multi-container stack on one host | Local Kubernetes learning/testing | Managed production Kubernetes |
| Nodes | One Docker Engine | Docker containers acting as nodes | OCI Compute / EC2 node pools |
| Reconciliation | Limited lifecycle operations | Kubernetes controllers | Kubernetes controllers |
| Load balancer | Local port publishing | Local simulation/port-forward | Cloud LB integration |
| Storage | Docker named volumes | Local test storage class | Cloud block/file storage |
| Availability | One host | One laptop | Multi-node and potentially multi-zone |

## Tool status before cluster creation

The workstation currently has the Docker CLI and `kubectl` client v1.29.2. kind,
minikube and Helm are not installed. Docker Engine was unavailable during the first
Phase 2 check, so no cluster tool was installed and no cluster was created.

We will restore Docker Desktop first, then install a pinned kind version and compatible
Kubernetes node image. Helm is intentionally deferred; plain manifests make the basic
resources easier to understand.

## Commands will be introduced gradually

Do not memorize this table. Each command will be explained when its resource exists:

| Command | What it asks the system to do |
|---|---|
| `kind create cluster` | Create Docker node containers and initialize Kubernetes |
| `kubectl cluster-info` | Show control-plane and cluster service addresses |
| `kubectl get nodes` | List cluster nodes and their readiness |
| `kubectl apply -f <file>` | Submit desired resource state from a manifest |
| `kubectl get <resource>` | Read current resource summaries |
| `kubectl describe <resource>` | Read detailed state, conditions and events |
| `kubectl logs <pod>` | Read application output from a Pod container |
| `kubectl get events` | Inspect chronological operational events |
| `kubectl rollout status deployment/<name>` | Follow Deployment rollout progress |
| `kind delete cluster` | Remove the disposable local cluster node containers |

`kubectl` is only a client for the Kubernetes API. It does not run containers. It
reads kubeconfig to find a cluster and credentials, sends API requests, and displays
the resource state returned by the API server.

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
