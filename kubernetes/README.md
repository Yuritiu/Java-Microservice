# Kubernetes Deployment for User Service

Runs the User Service, PostgreSQL and Redis on Kubernetes, with Traefik routing
traffic in. The same manifests deploy to a local minikube cluster and to Oracle
Cloud (OKE), using Kustomize overlays for the few differences.

## Layout

```
kubernetes/
├── base/                    shared by every environment
│   ├── kustomization.yaml   lists the files below, sets the user-service namespace
│   ├── namespace.yaml
│   ├── deployment.yaml      the app: 2 replicas, startup/liveness/readiness probes
│   ├── service.yaml         one stable address for the app pods
│   ├── ingress.yaml         routes outside traffic to the app, through Traefik
│   ├── middlewares.yaml     Traefik rate limiting and request size limit
│   ├── postgres-deployment.yaml
│   ├── postgres-pvc.yaml    storage for Postgres that survives restarts
│   ├── redis-deployment.yaml
│   └── demo-reset.yaml      nightly CronJob that resets the demo data
├── overlays/
│   ├── minikube/            uses the image built straight into minikube
│   └── oracle/              image from GitHub Container Registry, Oracle block
│                            storage, smaller CPU reservations for the free node
└── traefik/
    ├── values-minikube.yaml Traefik on port 80 of the minikube node
    └── values-oracle.yaml   Traefik behind a free Oracle Network Load Balancer
```

To see exactly what an overlay produces, without touching any cluster:

```bash
kubectl kustomize kubernetes/overlays/oracle
```

The database password is not stored in any file. The deploy scripts generate a
random one the first time and keep it in a Kubernetes Secret called
`user-service-db`.

## Tools (Arch Linux)

- **Docker**: builds images.
- **kubectl**: the official command line tool for talking to Kubernetes clusters.
- **minikube**: runs a single-node Kubernetes cluster in a Docker container on your PC.
- **Helm**: the package manager for Kubernetes. Installs ready-made apps (called
  charts) like Traefik with one command, and upgrades or removes them cleanly.
- **OCI CLI**: Oracle's command line tool. kubectl uses it to log in to the Oracle cluster.

```bash
sudo pacman -S kubectl minikube helm python-pipx
pipx install oci-cli
oci setup bootstrap
```

## Deploy locally (minikube)

```bash
./deploy-minikube.sh
```

Starts minikube, installs Traefik, builds the image inside minikube, applies the
minikube overlay and waits until everything is ready. Run it again after code
changes. It always targets minikube, whichever cluster kubectl is pointed at.

## Deploy to Oracle Cloud

1. Commit and push to `main`.
2. Wait for the GitHub Actions run to go green (it builds the image for that commit).
3. Run:

```bash
./deploy-oracle.sh
```

Installs Traefik and the load balancer, then deploys the image for your current
commit (`ghcr.io/yuritiu/java-microservice:sha-<commit>`) and prints the public
URL. It asks for confirmation before changing anything.

## Public demo safeguards

- **Rate limiting** (Traefik): about 10 requests a second per visitor IP, bursts of up to 50, then 429.
- **Request size limit**: bodies over 16 KB are rejected.
- **User cap**: the app refuses new users past 100 (`DEMO_MAXUSERS` in `deployment.yaml`) and returns 429.
- **Nightly reset**: the `demo-reset` CronJob wipes and reseeds the data at 03:00 UK time.
- **Health details hidden**: `/actuator/health` only reports UP or DOWN.

## Useful commands

Add `--context minikube` or `--context <oracle context>` to pick a cluster, or
switch with `kubectl config use-context <name>` (`kubectl config get-contexts` lists them).

```bash
kubectl get pods -n user-service                 # what's running
kubectl logs -n user-service deploy/user-service-deployment   # app logs
kubectl describe pod -n user-service <pod-name>  # why a pod is stuck
kubectl get svc traefik -n traefik               # load balancer and its public IP
kubectl create job --from=cronjob/demo-reset demo-reset-manual -n user-service   # reset the demo data now
minikube stop                                    # pause the local cluster, keeps data
```

## Health checks

Spring Boot Actuator exposes `/actuator/health/liveness` and
`/actuator/health/readiness`.

- **Startup probe**: gives the app up to 3 minutes to boot before the other checks begin.
- **Liveness**: is the app process healthy? Failing restarts the pod. Does not check the database.
- **Readiness**: can it serve requests? Checks Postgres and Redis too. Failing only stops traffic to that pod.
