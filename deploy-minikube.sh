#!/usr/bin/env bash
# Deploys the User Service to the local minikube cluster.
# Safe to run again after code changes: it rebuilds the image and rolls the pods.
#
#   ./deploy-minikube.sh       build and deploy
#   minikube stop              pause the cluster (keeps everything)
#   kubectl --context minikube delete namespace user-service   remove the app and its data
#
# Every kubectl/helm command here targets minikube explicitly, so it's safe to
# run even while kubectl is pointed at the Oracle cluster.
set -euo pipefail

# Run from the folder this script lives in, wherever it's called from.
cd "$(dirname "$0")"

CTX=minikube
NAMESPACE=user-service
IMAGE=user-service:local
TRAEFIK_CHART_VERSION=42.1.3

k() { kubectl --context "$CTX" "$@"; }

for cmd in docker kubectl minikube helm openssl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing '$cmd'. Install it first (see kubernetes/README.md)." >&2
    exit 1
  fi
done

echo "==> Starting minikube (skipped if already running)"
if ! minikube status >/dev/null 2>&1; then
  minikube start --driver=docker --cpus=2 --memory=4096
fi

echo "==> Installing Traefik (the ingress controller)"
# The old nginx add-on would fight Traefik for port 80, so make sure it's off.
minikube addons disable ingress >/dev/null 2>&1 || true
helm repo add traefik https://traefik.github.io/charts --force-update >/dev/null
helm upgrade --install traefik traefik/traefik \
  --version "$TRAEFIK_CHART_VERSION" \
  --kube-context "$CTX" \
  --namespace traefik --create-namespace \
  -f kubernetes/traefik/values-minikube.yaml \
  --wait --timeout 5m

echo "==> Building the image inside minikube (first build takes a few minutes)"
minikube image build -t "$IMAGE" .

echo "==> Creating the namespace and database secret"
k apply -f kubernetes/base/namespace.yaml
if ! k get secret user-service-db -n "$NAMESPACE" >/dev/null 2>&1; then
  # Random password, generated once and stored only in the cluster.
  # Don't delete this secret on its own: Postgres keeps the password it was
  # first created with, so a new one won't match. Delete the whole namespace instead.
  DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
  k create secret generic user-service-db -n "$NAMESPACE" \
    --from-literal=username=userservice_user \
    --from-literal=password="$DB_PASSWORD"
fi

# Remember if the app was already deployed, so we know to restart it below.
ALREADY_DEPLOYED=false
if k get deployment user-service-deployment -n "$NAMESPACE" >/dev/null 2>&1; then
  ALREADY_DEPLOYED=true
fi

echo "==> Applying manifests"
k apply -k kubernetes/overlays/minikube

if [ "$ALREADY_DEPLOYED" = true ]; then
  # Same image tag as before, so tell Kubernetes to pick up the new build.
  k rollout restart deployment/user-service-deployment -n "$NAMESPACE"
fi

echo "==> Waiting for everything to be ready"
k rollout status deployment/user-service-postgres   -n "$NAMESPACE" --timeout=180s
k rollout status deployment/user-service-redis      -n "$NAMESPACE" --timeout=120s
k rollout status deployment/user-service-deployment -n "$NAMESPACE" --timeout=300s

if [ "$ALREADY_DEPLOYED" = false ]; then
  # First deploy: the app has now created the users table, so add the
  # sample users straight away instead of waiting for the 03:00 reset.
  echo "==> Adding sample users"
  k create job --from=cronjob/demo-reset demo-reset-initial -n "$NAMESPACE"
  k wait --for=condition=complete job/demo-reset-initial -n "$NAMESPACE" --timeout=120s
fi

URL="http://$(minikube ip)"
echo
echo "Done. Open the API page in your browser:"
echo "  $URL/"
echo
echo "Or from the terminal:"
echo "  curl $URL/api/users"
