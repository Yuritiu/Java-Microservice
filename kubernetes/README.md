# Kubernetes Deployment for User Service

Runs the User Service, PostgreSQL and Redis on Kubernetes. Locally this uses
minikube. The same manifests will later be deployed to Oracle Cloud (OKE).

## What's in here

| File | What it does |
|---|---|
| `kustomization.yaml` | Lists every manifest so `kubectl apply -k kubernetes/` applies them all, in the `user-service` namespace |
| `namespace.yaml` | Keeps everything in one namespace, easy to inspect or delete |
| `deployment.yaml` | Runs 2 copies of the app, with startup, liveness and readiness probes |
| `service.yaml` | Gives the app pods one stable address inside the cluster |
| `ingress.yaml` | Routes outside HTTP traffic to that Service (needs the nginx ingress controller) |
| `postgres-deployment.yaml` | Postgres pod and its Service |
| `postgres-pvc.yaml` | 1Gi of storage for Postgres that survives restarts |
| `redis-deployment.yaml` | Redis pod and its Service |

The database password is not stored in any file. `deploy-k8s.sh` generates a
random one the first time and keeps it in a Kubernetes Secret called
`user-service-db`.

## One-time setup (Arch Linux)

- **Docker**: already used for docker-compose.
- **kubectl**: the official command line tool for talking to any Kubernetes cluster.
- **minikube**: the official tool for running a single-node Kubernetes cluster
  inside a Docker container on your PC. `minikube delete` removes it completely.

```bash
sudo pacman -S kubectl minikube
```

Maven and a JDK are not needed. The Dockerfile builds the jar inside Docker.

## Deploy

```bash
./deploy-k8s.sh
```

This starts minikube, enables the ingress controller, builds the image inside
minikube, creates the secret, applies the manifests and waits until everything
is ready. Then it prints the URL. Run it again after code changes to redeploy.

## Useful commands

```bash
kubectl get pods -n user-service                 # what's running
kubectl logs -n user-service deploy/user-service-deployment   # app logs
kubectl describe pod -n user-service <pod-name>  # why a pod is stuck
minikube dashboard                               # web UI for the cluster
minikube stop                                    # pause everything, keeps data
kubectl delete namespace user-service            # remove the app and its data
minikube delete                                  # remove the whole cluster
```

## Health checks

Spring Boot Actuator exposes `/actuator/health/liveness` and
`/actuator/health/readiness`.

- **Startup probe**: gives the app up to 3 minutes to boot before the other checks begin.
- **Liveness**: is the app process healthy? Failing restarts the pod. Does not check the database.
- **Readiness**: can it serve requests? Checks Postgres and Redis too. Failing only stops traffic to that pod.
