#!/usr/bin/env bash
# Deploys the User Service to Oracle Cloud (OKE).
#
# It deploys the image GitHub Actions built for your current commit, so first
# commit, push, and wait for the Actions run to go green. Then:
#
#   ./deploy-oracle.sh
#
# If you have more than one Oracle cluster in your kubeconfig, say which:
#   ORACLE_CONTEXT=<context name> ./deploy-oracle.sh
set -euo pipefail

cd "$(dirname "$0")"

NAMESPACE=user-service
IMAGE_REPO=ghcr.io/yuritiu/java-microservice
TRAEFIK_CHART_VERSION=42.1.3

fail() { echo "Error: $*" >&2; exit 1; }

for cmd in kubectl helm git openssl oci; do
  command -v "$cmd" >/dev/null 2>&1 || fail "missing '$cmd'. Install it first (see kubernetes/README.md)."
done

# ---- Which cluster? ------------------------------------------------------
# Oracle's kubeconfig contexts are named "context-<cluster name>-<id>". Use
# that one unless ORACLE_CONTEXT says otherwise.
if [ -z "${ORACLE_CONTEXT:-}" ]; then
  mapfile -t FOUND < <(kubectl config get-contexts -o name | grep '^context-' || true)
  [ "${#FOUND[@]}" -eq 1 ] || fail "couldn't pick the Oracle cluster automatically.
Run 'kubectl config get-contexts', then: ORACLE_CONTEXT=<name> ./deploy-oracle.sh"
  ORACLE_CONTEXT=${FOUND[0]}
fi
[ "$ORACLE_CONTEXT" != "minikube" ] || fail "that's the local cluster. Use ./deploy-minikube.sh for minikube."

k() { kubectl --context "$ORACLE_CONTEXT" "$@"; }

CLUSTER=$(kubectl config view -o jsonpath="{.contexts[?(@.name=='$ORACLE_CONTEXT')].context.cluster}")
SERVER=$(kubectl config view -o jsonpath="{.clusters[?(@.name=='$CLUSTER')].cluster.server}")

# ---- Which image? --------------------------------------------------------
# GitHub Actions tags each image with the first 7 characters of its commit.
COMMIT=$(git rev-parse HEAD | cut -c1-7)
TAG="sha-$COMMIT"

if ! git diff --quiet HEAD; then
  echo "Note: you have uncommitted changes. The app image is from commit $COMMIT (code changes"
  echo "      need committing, pushing and a green CI run), but the Kubernetes files are used as"
  echo "      they are in your folder."
fi
git branch -r --contains HEAD | grep -q 'origin/main' \
  || fail "commit $COMMIT isn't on GitHub's main branch yet, so no image has been built for it. Push first."

if command -v docker >/dev/null 2>&1; then
  docker manifest inspect "$IMAGE_REPO:$TAG" >/dev/null 2>&1 \
    || fail "no image $IMAGE_REPO:$TAG yet. Check the GitHub Actions run for this commit finished and went green."
fi

echo
echo "About to deploy $IMAGE_REPO:$TAG"
echo "to cluster $ORACLE_CONTEXT ($SERVER)"
read -r -p "Continue? [y/N] " ANSWER
[[ "$ANSWER" =~ ^[Yy]$ ]] || exit 1

# ---- Traefik + the public load balancer ------------------------------------
echo "==> Installing Traefik and the Oracle Network Load Balancer"
helm repo add traefik https://traefik.github.io/charts --force-update >/dev/null
helm upgrade --install traefik traefik/traefik \
  --version "$TRAEFIK_CHART_VERSION" \
  --kube-context "$ORACLE_CONTEXT" \
  --namespace traefik --create-namespace \
  -f kubernetes/traefik/values-oracle.yaml \
  --wait --timeout 10m

echo "==> Waiting for the load balancer's public IP (can take a few minutes)"
LB_IP=""
for _ in $(seq 1 60); do
  LB_IP=$(k get svc traefik -n traefik -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  [ -n "$LB_IP" ] && break
  sleep 5
done
[ -n "$LB_IP" ] || fail "no public IP after 5 minutes. Check: kubectl --context $ORACLE_CONTEXT describe svc traefik -n traefik"

# ---- The app ---------------------------------------------------------------
echo "==> Creating the namespace and database secret"
k apply -f kubernetes/base/namespace.yaml
if ! k get secret user-service-db -n "$NAMESPACE" >/dev/null 2>&1; then
  # Random password, generated once and stored only in the cluster.
  DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
  k create secret generic user-service-db -n "$NAMESPACE" \
    --from-literal=username=userservice_user \
    --from-literal=password="$DB_PASSWORD"
fi

ALREADY_DEPLOYED=false
if k get deployment user-service-deployment -n "$NAMESPACE" >/dev/null 2>&1; then
  ALREADY_DEPLOYED=true
fi

echo "==> Applying manifests"
# Build the oracle overlay and swap the IMAGE_TAG placeholder for this commit's tag.
RENDERED=$(kubectl kustomize kubernetes/overlays/oracle | sed "s|$IMAGE_REPO:IMAGE_TAG|$IMAGE_REPO:$TAG|")
grep -q "$IMAGE_REPO:$TAG" <<<"$RENDERED" || fail "couldn't set the image tag in the oracle overlay."
k apply -f - <<<"$RENDERED"

echo "==> Waiting for everything to be ready"
# Postgres waits for Oracle to create its 50 GB block volume, which takes a minute or two.
k rollout status deployment/user-service-postgres   -n "$NAMESPACE" --timeout=300s
k rollout status deployment/user-service-redis      -n "$NAMESPACE" --timeout=180s
# Two Java apps starting on one small CPU take a while.
k rollout status deployment/user-service-deployment -n "$NAMESPACE" --timeout=600s

if [ "$ALREADY_DEPLOYED" = false ]; then
  echo "==> Adding sample users"
  k create job --from=cronjob/demo-reset demo-reset-initial -n "$NAMESPACE"
  k wait --for=condition=complete job/demo-reset-initial -n "$NAMESPACE" --timeout=180s
fi

echo
echo "Done. $IMAGE_REPO:$TAG is live at:"
echo "  http://$LB_IP/"
