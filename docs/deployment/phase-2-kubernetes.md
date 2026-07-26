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

## Tool and cluster versions used

The completed local environment uses:

| Component | Version or value |
|---|---|
| Docker Desktop | 4.83 |
| WSL | 2.7.11 |
| kubectl client | v1.36.3 |
| kind | v0.32.0 |
| kind cluster | `alertops` |
| Kubernetes node | v1.36.1 |
| Linux control groups | cgroup v2 |

Helm is intentionally deferred. Plain manifests expose the basic Kubernetes resources
and relationships instead of hiding them behind templates.

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

## Phase 2 implementation journal

### 1. Local cluster and terminology

The cluster was created as a pinned kind cluster named `alertops`:

```powershell
kind create cluster --name alertops --image <pinned-kind-node-image>
```

- `kind create cluster` creates and initializes the local Kubernetes cluster.
- `--name alertops` gives the cluster a stable local name.
- `--image` selects a kind node image containing Kubernetes components and the node
  runtime. It is not the AlertOps application image.

The commonly confused terms have different scopes:

```text
kind cluster: alertops
`-- Docker node container: alertops-control-plane
    `-- Kubernetes components and containerd
        `-- application and system Pods
            `-- one or more workload containers
```

- A **container** is an isolated process created from an image.
- `alertops-control-plane` is a Docker container acting as the kind Kubernetes node.
- The **cluster** is the complete Kubernetes system: API, desired state, nodes,
  networking, storage integration, and workloads.

The cluster was inspected with:

```powershell
kubectl config get-contexts
kubectl get nodes -o wide
kubectl get namespaces
kubectl get pods --all-namespaces
```

A **context** connects a cluster, credentials, and a default namespace. The `*` in
`kubectl config get-contexts` marks the current context. `-o wide` requests extra
columns such as IP, OS, kernel, and container runtime. `--all-namespaces` searches
across every namespace.

The node output showed:

```text
alertops-control-plane   Ready   control-plane   v1.36.1
```

`Ready` means the node can accept workloads. This single-node learning cluster runs
the control plane and application Pods together. Production clusters normally use a
replicated managed control plane and separate worker nodes.

The initial namespaces were:

| Namespace | Purpose |
|---|---|
| `default` | Used when no namespace is specified |
| `kube-system` | Kubernetes system components |
| `kube-public` | Publicly readable cluster information |
| `kube-node-lease` | Lightweight node heartbeats |
| `local-path-storage` | kind's local storage provisioner |

`kubectl get pods` initially returned `No resources found in default namespace`
because the cluster was healthy but no user workload existed in `default`.

### 2. Local platform troubleshooting

Docker Desktop initially failed while starting the kind control-plane container.
Docker Desktop and WSL were upgraded. An obsolete `disableHardwareAcceleration`
setting was removed after creating this backup:

```text
C:\Users\ASUS\AppData\Roaming\Docker\settings.json.pre-4.83-backup-20260725-133516
```

An older `kubectl` executable appeared earlier in `PATH` than the newly installed
client. A PowerShell profile alias was added so new sessions resolve `kubectl` to
v1.36.3:

```text
C:\Users\ASUS\OneDrive\Documents\WindowsPowerShell\profile.ps1
```

Existing shells do not automatically reread a changed `PATH`. Close and reopen
PowerShell. If only the PowerShell profile changed, reload it with:

```powershell
. $PROFILE
```

`kubectl version --client` verifies the client executable. It does not prove that a
cluster exists or report its server version.

#### Cgroup and CPU fundamentals

Linux **control groups**, or cgroups, account for and constrain CPU, memory, and other
resources used by process groups. Kubernetes asks the runtime to place each container
into cgroups according to its resource configuration. This machine uses cgroup v2,
the newer unified hierarchy.

CPU values express CPU capacity over time:

- `1000m`, or `1`, means one CPU core of capacity.
- `500m` means half a core.
- `250m` means one quarter of a core.

A CPU limit is enforced by throttling CPU time over small accounting periods. It does
not mean that one Java task always runs for exactly 50 ms and then stops. An eight-core
machine can execute roughly eight CPU-bound threads simultaneously, while many more
threads can exist and wait for CPU or I/O. A scheduling window is a short interval
during which the kernel accounts for CPU usage.

High CPU in Grafana is a signal to investigate, not automatically a fault. Correlate
it with traffic, throughput, latency, errors, garbage collection, retries, queue
depth, and CPU-throttling metrics.

### 3. Application image workflow

Docker Compose was not needed to build the Kubernetes application image:

```powershell
docker build --tag alertops:3514a9d28585 .
kind load docker-image alertops:3514a9d28585 --name alertops
```

- `docker build` reads the local Dockerfile and creates the AlertOps image.
- `--tag`, or `-t`, assigns the readable image name and tag.
- `.` is the Docker build context.
- `kind load docker-image` copies the built image into the kind node's containerd
  image store.
- `--name alertops` selects the kind cluster.

Compose previously appeared to build the application because its `app` service has a
`build` instruction. Compose only orchestrated the same Docker build; the Dockerfile
remained the build definition.

Docker and Git tags solve different problems:

| Item | Points to | Purpose |
|---|---|---|
| Git commit | Exact source snapshot | Source history |
| Git tag | Named Git commit | Release marker or CI trigger |
| Docker image | Packaged runnable artifact | Deployment |
| Docker image tag | Readable image reference | Artifact selection |
| Image digest | Immutable image content | Exact reproducibility |

`docker build -t alertops:3514a9d28585 .` creates an image and attaches that tag.
`docker tag old-name new-name` does not rebuild or copy its layers; it adds another
reference to the same local image. In a company workflow, a pushed Git tag often
triggers CI, which builds and pushes a Docker image. Kubernetes deploys that image,
not the Git tag.

The image was loaded into kind because Docker Desktop's image store and the kind
node's containerd store are separate. Cloud worker nodes instead pull images from
OCIR, ECR, or another registry.

### 4. Namespace, configuration, and secrets

The dedicated namespace was created and inspected with:

```powershell
kubectl apply -f k8s/base/namespace.yaml
kubectl get namespace alertops --show-labels
```

- `apply` creates or updates declared state.
- `-f` means read the following file.
- `--show-labels` adds labels to the table output.

Every namespaced manifest explicitly contains:

```yaml
metadata:
  namespace: alertops
```

`kind: ConfigMap` identifies an API resource type; it does not create a namespace.
`metadata.namespace` places the resource into an already existing namespace.

Non-secret values are committed in `alertops-config`. Sensitive local values were
created without committing their source file:

```powershell
kubectl create secret generic alertops-secrets `
  --namespace alertops `
  --from-env-file=.env.k8s.local
```

- `generic` creates an ordinary `Opaque` Secret.
- `--namespace`, or `-n`, selects the namespace.
- `--from-env-file` reads key/value pairs from the ignored local file.

The Deployment injects both resources:

```yaml
envFrom:
  - configMapRef:
      name: alertops-config
  - secretRef:
      name: alertops-secrets
```

Spring resolves expressions such as `${SPRING_RABBITMQ_HOST}` from the container's
environment when the application starts.

Secret values can be retrieved and Base64-decoded, but Base64 is encoding rather than
encryption. Normal inspection should show key names, not values:

```powershell
kubectl describe secret alertops-secrets -n alertops
```

Production needs least-privilege RBAC, encryption at rest, and usually an external
source such as OCI Vault, AWS Secrets Manager, or HashiCorp Vault.

### 5. Labels and selectors

Labels are searchable metadata:

```yaml
labels:
  app.kubernetes.io/name: postgres
  app.kubernetes.io/part-of: alertops
```

- `name: postgres` identifies the component.
- `part-of: alertops` groups it with the complete system.
- Labels under `spec.template.metadata` are copied onto created Pods.

A selector is an active matching rule:

```yaml
selector:
  matchLabels:
    app.kubernetes.io/name: postgres
```

A StatefulSet selector identifies the Pods it owns. A Service selector identifies
the Pods that receive its traffic. Selectors must match the Pod-template labels.
Labels do not route traffic by themselves; selectors use them to form relationships.

The `-l` command option uses the same mechanism for filtering:

```powershell
kubectl get pods -n alertops -l app.kubernetes.io/name=redis
```

### 6. StorageClass, provisioner, PV, and PVC

The local StorageClass reported:

```text
standard (default)   rancher.io/local-path   Delete   WaitForFirstConsumer
```

| Field | Meaning |
|---|---|
| `standard` | Storage policy selected by the PVC |
| `rancher.io/local-path` | Driver that creates backing local storage |
| Reclaim policy `Delete` | Delete the PV when its claim is deleted |
| `WaitForFirstConsumer` | Wait for a Pod before choosing storage placement |
| Expansion `false` | Existing claims cannot be enlarged through this class |

A provisioner translates a PVC request into real storage. Creating a StorageClass does
not install a new storage driver; its `provisioner` must name an installed driver.
OKE and EKS use CSI drivers to provision OCI or AWS block/file volumes.

```text
Pod mounts PVC
    -> PVC requests storage
        -> StorageClass selects policy and provisioner
            -> provisioner creates PV and backing storage
```

The PVC was initially `Pending` because `WaitForFirstConsumer` waits until a Pod uses
it. It became `Bound` when the StatefulSet Pod was scheduled. `RWO`, or
`ReadWriteOnce`, permits a read/write mount from a single node.

### 7. Why StatefulSet, Service, and PVC are separate

Each stateful dependency uses three objects because each solves a different problem:

```text
StatefulSet -> creates and replaces the process Pod with stable identity
Service     -> provides stable DNS and selects the current Pod
PVC         -> preserves data independently of Pod lifetime
```

For PostgreSQL:

- StatefulSet creates `postgres-0` and recreates it when necessary.
- Service provides `postgres:5432`; AlertOps never relies on a temporary Pod IP.
- PVC mounts at `/var/lib/postgresql/data`.

RabbitMQ and Redis use the same model. A StatefulSet alone would not provide the
application-facing Service or persistent disk. A Service alone would not create a
Pod. A PVC alone would only request storage.

The following command displays all four layers together:

```powershell
kubectl get statefulset,pod,pvc,service -n alertops
```

This single-replica design teaches identity and persistence, but it is not highly
available. Production PostgreSQL, RabbitMQ, and Redis require product-specific
replication, quorum, backups, restore tests, and disruption planning. Simply setting
their replica count to three does not safely create a database or broker cluster.

### 8. RabbitMQ startup-probe failure

RabbitMQ initially restarted because an executable startup probe could race with
Erlang cookie creation. A probe running as root could create a root-owned cookie
before RabbitMQ, running as UID 999, could read it.

The startup probe was changed to:

```yaml
startupProbe:
  tcpSocket:
    port: amqp
```

This checks whether AMQP is listening without executing a cookie-dependent command.
Readiness and liveness retain RabbitMQ diagnostics after startup. The replacement Pod
became ready with the same PVC, and the Service selected its new IP automatically.

Probes run inside the container and can have side effects. Probe commands should be
safe, lightweight, and permission-aware.

### 9. Redis authentication and AOF persistence

Redis returned an authenticated `PONG`. An unauthenticated request returned:

```text
NOAUTH Authentication required.
```

The configuration enables:

```text
appendonly yes
appendfsync everysec
```

Redis normally serves reads from memory. Its write path is approximately:

```text
SET/DEL/INCR
    -> update memory
    -> append the command to AOF under /data
    -> flush it to PVC-backed storage
```

`appendfsync everysec` balances performance and durability; a sudden host loss can
lose approximately the latest second of writes. On restart, Redis replays the AOF to
reconstruct memory. AOF improves recovery but is not a substitute for backups.

### 10. AlertOps Deployment and Service

AlertOps runs in a Deployment because shared intent state was moved to Redis and the
application replicas are intended to be stateless. The final desired count is:

```yaml
replicas: 3
```

The Deployment uses:

- the commit-SHA image tag;
- ConfigMap and Secret environment injection;
- resource requests and limits;
- startup, readiness, and liveness probes;
- container security restrictions;
- `maxUnavailable: 0` and `maxSurge: 1` rolling updates.

Requests are considered by the scheduler. Limits constrain actual use. A `250m` CPU
request reserves scheduling capacity equal to a quarter of a CPU. A CPU limit of `1`
allows up to one CPU of time. A memory limit is a hard ceiling and exceeding it may
cause an OOM kill.

The ClusterIP Service provides stable internal discovery:

```text
service/alertops -> ready AlertOps Pod endpoints on port 8096
```

Local access was tested with:

```powershell
kubectl port-forward service/alertops 8096:8096 -n alertops
Invoke-RestMethod http://localhost:8096/actuator/health
```

- `port-forward` creates a temporary local tunnel to the Service.
- It runs in the foreground until `Ctrl+C`.
- It is a debugging mechanism, not production ingress.

The response was:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

### 11. Scaling and shared state

After changing `replicas` from one to three, Kubernetes created three AlertOps Pods.
The Service EndpointSlice contained all three Pod IPs, proving Service discovery.

Every replica receives the same Redis host and credentials. This is the correct
infrastructure for shared intent state. The full invitation/login flow was not
repeated because the application workflow was no longer fresh in memory. The precise
result is:

> Multi-replica scheduling, readiness, and Service discovery were verified. Shared
> Redis intent state is structurally configured, but cross-replica intent creation
> and atomic consumption still require an application-level integration test.

Infrastructure configuration is useful evidence but does not replace a functional
test. A future automated test should create an intent through one replica and consume
it through another.

### 12. Controlled operational drills

#### Pod self-healing

One AlertOps Pod was deleted and the replicas were watched:

```powershell
kubectl delete pod <pod-name> -n alertops
kubectl get pods -n alertops -l app.kubernetes.io/name=alertops --watch
```

- `delete pod` removed only the disposable Pod, not its Deployment.
- `--watch` kept the query open and printed state changes.

The Deployment continued to desire three replicas. Kubernetes created a replacement,
which progressed from `0/1 Running` to `1/1 Running` when readiness succeeded. This
proved controller reconciliation and readiness gating.

#### Rolling restart

```powershell
kubectl rollout restart deployment/alertops -n alertops
kubectl rollout status deployment/alertops -n alertops
```

`rollout restart` modifies a Pod-template annotation and creates a new ReplicaSet
without changing the application image. `rollout status` follows progress. AlertOps
took around 50 seconds for each new Pod to become ready, and the rollout succeeded.

With `maxUnavailable: 0`, Kubernetes retained the desired number of ready replicas.
With `maxSurge: 1`, it could temporarily create one extra Pod.

#### Invalid image and rollback

An intentionally nonexistent image was set only in the live cluster:

```powershell
kubectl set image deployment/alertops `
  alertops=alertops:rollback-drill-missing `
  -n alertops

kubectl rollout status deployment/alertops -n alertops --timeout=90s
kubectl get pods -n alertops -l app.kubernetes.io/name=alertops
```

- `set image` updates a named container in the Deployment.
- `--timeout=90s` stops waiting after 90 seconds; it does not repair the rollout.
- This imperative command did not modify the committed YAML.

The result contained three healthy old Pods and one new `ImagePullBackOff` Pod.
Kubernetes could not pull the missing image, but `maxUnavailable: 0` protected the
healthy replicas.

Rollback used:

```powershell
kubectl rollout undo deployment/alertops -n alertops
kubectl rollout status deployment/alertops -n alertops
kubectl get deployment alertops -n alertops `
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

`rollout undo` restored the previous Pod template. JSONPath extracted only the image
field. The failed Pod disappeared and all three healthy replicas remained on:

```text
alertops:3514a9d28585
```

#### Redis Pod recreation and disk recovery

A harmless key was stored before deleting Redis:

```powershell
kubectl exec redis-0 -n alertops -- `
  redis-cli SET phase2:persistence "survives-pod-recreation"

kubectl delete pod redis-0 -n alertops
kubectl wait --for=condition=Ready pod/redis-0 -n alertops --timeout=180s

kubectl exec redis-0 -n alertops -- `
  redis-cli GET phase2:persistence
```

- `exec` runs a command inside a container.
- `--` ends kubectl options; everything after it is the container command.
- `wait --for=condition=Ready` waits for the recreated Pod to become ready.

The value survived. The StatefulSet recreated `redis-0`, mounted the same PVC, and
Redis replayed its AOF into memory. Cleanup returned `1`, meaning one key was deleted:

```powershell
kubectl exec redis-0 -n alertops -- redis-cli DEL phase2:persistence
```

### 13. Environment design: clusters versus namespaces

Companies use both patterns:

- namespaces commonly separate teams, applications, or lower-risk environments;
- separate clusters commonly isolate production from non-production;
- large organizations may use multiple clusters per region or security boundary.

A reasonable example is:

```text
non-production cluster
|-- alertops-dev namespace
`-- alertops-test namespace

production cluster
`-- alertops-prod namespace
```

Namespaces provide logical isolation but share a control plane and often nodes. They
are not as strong a failure, upgrade, or security boundary as separate clusters.
Production separation depends on risk, compliance, scale, cost, and platform policy.

### 14. Questions asked during Phase 2

**Why not deploy the Docker image directly to EC2 or OCI Compute?**

That remains valid for smaller systems. Kubernetes becomes valuable when consistent
scheduling, self-healing, discovery, rollout, scaling, policy, and resource management
are needed across many replicas and machines.

**Does an eight-core machine mean only eight tasks can exist?**

No. It can execute roughly eight CPU-bound threads simultaneously, but many more
threads can exist or wait for CPU and I/O. The operating system schedules runnable
work across the available CPUs.

**Does Kubernetes create a replacement immediately after a Pod is deleted?**

The controller observes that actual replicas are below desired replicas and creates a
replacement. The scheduler selects a suitable node. Creation can be delayed when no
node satisfies the resource or policy requirements.

**Is a Pod the same as a container?**

Not exactly. A Pod is the smallest Kubernetes scheduling unit and can contain one or
more containers sharing networking and volumes. AlertOps uses the common
one-container-per-Pod pattern.

**Why are readiness and liveness separate?**

Readiness decides whether the Pod receives traffic. Liveness decides whether a
container restart may repair it. A remote database outage should generally fail
readiness, not liveness, because restarting AlertOps cannot fix PostgreSQL.

**Does a ConfigMap create its namespace?**

No. Namespace is a separate resource. `metadata.namespace` places the ConfigMap into
an existing namespace.

**Why put a hostname in ConfigMap but a JWT key in Secret?**

A hostname is ordinary configuration. Passwords and signing keys are sensitive.
Secrets allow separate access controls and handling, although production also needs
encryption and external secret management.

**Why does a Service need selectors?**

Pod IPs change. The Service matches current Pods by label and maintains stable DNS
plus the ready endpoint set.

**Why was a PVC `Pending` before its Pod existed?**

The `standard` StorageClass uses `WaitForFirstConsumer`, so it waits for workload
scheduling information before provisioning and binding the volume.

**Why use StatefulSet when Service and PVC already exist?**

The PVC stores data and the Service provides networking; neither creates or
supervises the process. The StatefulSet creates and replaces the Pod with stable
identity.

**Why did a Service find the replacement Pod automatically?**

The replacement had the same labels. The Service selector matched it, and Kubernetes
updated the EndpointSlice with its new Pod IP.

**Why did Redis retain a value after Pod deletion?**

Redis reconstructed the value by replaying AOF data stored on the PVC. It then served
normal requests from memory again.

**What does `-f` mean?**

Use the following file as command input, such as `kubectl apply -f manifest.yaml`.

**What does `-n` mean?**

It is the short form of `--namespace` and scopes the command to that namespace.

**What does `-l` mean?**

It filters resources using a label selector.

**Does `kubectl` create containers?**

No. It sends requests to the Kubernetes API. Controllers, the scheduler, kubelet, and
the container runtime cooperate to create the workload.

**What is the difference between a Git tag and a Docker tag?**

A Git tag names source history. A Docker tag names a runnable image artifact. CI often
uses the Git tag as a trigger and then produces a Docker image tag for deployment.

**Why does kind need `kind load docker-image`?**

The image built by Docker is not automatically present in the kind node's containerd
store. Loading copies it into the node. Production nodes pull from a registry.

## Final validation evidence

The live cluster reported:

```text
Node
alertops-control-plane   Ready   control-plane   v1.36.1

Deployment
alertops   3/3 Ready

StatefulSets
postgres   1/1 Ready
rabbitmq   1/1 Ready
redis      1/1 Ready

PersistentVolumeClaims
postgres-data   Bound   1Gi   RWO
rabbitmq-data   Bound   1Gi   RWO
redis-data      Bound   1Gi   RWO

AlertOps Service endpoints
10.244.0.16:8096
10.244.0.17:8096
10.244.0.18:8096
```

Pod names and IPs are temporary and will change after recreation.

## Phase 2 completion checklist

- [x] Compatible Docker Desktop, WSL, kind, and kubectl installed.
- [x] Pinned kind cluster created and inspected.
- [x] AlertOps image built with a commit-SHA tag and loaded into kind.
- [x] Dedicated Namespace, ConfigMap, and Secret created.
- [x] PostgreSQL deployed with Service and bound PVC.
- [x] RabbitMQ deployed with Service and bound PVC.
- [x] Redis deployed with authentication, AOF, Service, and bound PVC.
- [x] AlertOps deployed with probes, resources, security context, and Service.
- [x] Health endpoint reached using a temporary port-forward.
- [x] AlertOps scaled to three ready replicas.
- [x] Service endpoint discovery across three replicas verified.
- [x] Pod self-healing and readiness transition observed.
- [x] Rolling restart completed.
- [x] Invalid-image rollout failed without removing healthy replicas.
- [x] Previous working rollout restored.
- [x] Redis data survived Pod recreation and the test key was cleaned up.
- [ ] Cross-replica intent create/consume behavior automated and functionally tested.
- [ ] Production-grade backup and restore procedures designed and tested.

Phase 2 is complete for the local Kubernetes learning objective. The unchecked items
are deliberately carried forward: application integration coverage and production
backup/restore work belong in later application and reliability phases.
