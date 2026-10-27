#!/usr/bin/env bash
# Deploys the User Service to a local minikube cluster.
# Safe to run again after code changes: it rebuilds the image and rolls the pods.
#
#   ./deploy-k8s.sh            build and deploy
#   minikube stop              pause the cluster (keeps everything)
#   kubectl delete namespace user-service   remove the app and its data
set -euo pipefail

# Run from the folder this script lives in, wherever it's called from.
cd "$(dirname "$0")"

NAMESPACE=user-service
IMAGE=user-service:local

for cmd in docker kubectl minikube; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing '$cmd'. Install it first (see kubernetes/README.md)." >&2
    exit 1
  fi
done

echo "==> Starting minikube (skipped if already running)"
if ! minikube status >/dev/null 2>&1; then
  minikube start --driver=docker --cpus=2 --memory=4096
fi

echo "==> Enabling the nginx ingress controller"
minikube addons enable ingress >/dev/null
# The Ingress can't be created until the controller is up, so wait for it.
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=180s

echo "==> Building the image inside minikube (first build takes a few minutes)"
minikube image build -t "$IMAGE" .

echo "==> Creating the namespace and database secret"
kubectl apply -f kubernetes/namespace.yaml
if ! kubectl get secret user-service-db -n "$NAMESPACE" >/dev/null 2>&1; then
  # Random password, generated once and stored only in the cluster.
  # Don't delete this secret on its own: Postgres keeps the password it was
  # first created with, so a new one won't match. Delete the whole namespace instead.
  DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
  kubectl create secret generic user-service-db -n "$NAMESPACE" \
    --from-literal=username=userservice_user \
    --from-literal=password="$DB_PASSWORD"
fi

# Remember if the app was already deployed, so we know to restart it below.
ALREADY_DEPLOYED=false
if kubectl get deployment user-service-deployment -n "$NAMESPACE" >/dev/null 2>&1; then
  ALREADY_DEPLOYED=true
fi

echo "==> Applying manifests"
kubectl apply -k kubernetes/

if [ "$ALREADY_DEPLOYED" = true ]; then
  # Same image tag as before, so tell Kubernetes to pick up the new build.
  kubectl rollout restart deployment/user-service-deployment -n "$NAMESPACE"
fi

echo "==> Waiting for everything to be ready"
kubectl rollout status deployment/user-service-postgres   -n "$NAMESPACE" --timeout=180s
kubectl rollout status deployment/user-service-redis      -n "$NAMESPACE" --timeout=120s
kubectl rollout status deployment/user-service-deployment -n "$NAMESPACE" --timeout=300s

if [ "$ALREADY_DEPLOYED" = false ]; then
  # First deploy: the app has now created the users table, so add the
  # sample users straight away instead of waiting for the 03:00 reset.
  echo "==> Adding sample users"
  kubectl create job --from=cronjob/demo-reset demo-reset-initial -n "$NAMESPACE"
  kubectl wait --for=condition=complete job/demo-reset-initial -n "$NAMESPACE" --timeout=120s
fi

URL="http://$(minikube ip)"
echo
echo "Done. Open the API page in your browser:"
echo "  $URL/"
echo
echo "Or from the terminal:"
echo "  curl $URL/api/users"
echo "  curl -X POST $URL/api/users -H 'Content-Type: application/json' -d '{\"name\":\"Ben\",\"email\":\"ben@example.com\"}'"
echo
echo "Check on it with:  kubectl get pods -n $NAMESPACE"
