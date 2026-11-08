#!/usr/bin/env bash
# Deploys one environment of BoardKub to GKE. Called by .github/workflows/ci-cd.yml
# but also runnable by hand after `gcloud container clusters get-credentials`.
#
# Required environment variables
#   ENV_NAME        staging | production   (picks k8s/overlays/<ENV_NAME>)
#   NAMESPACE       boardkub-staging | boardkub-production
#   IMAGE_PREFIX    asia-southeast1-docker.pkg.dev/<project>/boardkub
#   IMAGE_TAG       usually the Git commit SHA
#   CLOUDSQL_IP     private IP of the Cloud SQL instance
#   DB_NAME         boardkub_staging | boardkub_production
#   DB_USERNAME     DB_PASSWORD
#   REDIS_HOST      private IP of Memorystore
#   REDIS_DATABASE  0-15 (staging and production use different numbers)
#   MEDIA_BUCKET    Cloud Storage bucket for images (objects go under <ENV_NAME>/)
#   ADMIN_EMAIL     ADMIN_PASSWORD
# Optional
#   DOMAIN          production domain, e.g. boardkub.example.com (creates the TLS certificate)
#   CORS_ORIGINS    defaults to https://$DOMAIN, or http://localhost:8080 without a domain
#   MAIL_USERNAME   MAIL_PASSWORD   Gmail address + App Password (needed when OTP_DEV_MODE=false)
set -euo pipefail

for v in ENV_NAME NAMESPACE IMAGE_PREFIX IMAGE_TAG CLOUDSQL_IP DB_NAME DB_USERNAME \
         DB_PASSWORD REDIS_HOST REDIS_DATABASE MEDIA_BUCKET ADMIN_EMAIL ADMIN_PASSWORD; do
  if [ -z "${!v:-}" ]; then echo "::error::missing variable $v"; exit 1; fi
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OVERLAY="$ROOT/k8s/overlays/$ENV_NAME"
DOMAIN="${DOMAIN:-}"
if [ -n "$DOMAIN" ]; then
  CORS_ORIGINS="${CORS_ORIGINS:-https://$DOMAIN}"
else
  CORS_ORIGINS="${CORS_ORIGINS:-http://localhost:8080}"
fi
DB_URL="jdbc:mysql://${CLOUDSQL_IP}:3306/${DB_NAME}?sslMode=REQUIRED&serverTimezone=Asia/Bangkok&characterEncoding=UTF-8"

echo "==> Namespace $NAMESPACE"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "==> Connection settings and credentials (values are never printed)"
changed=$(
  {
    kubectl -n "$NAMESPACE" create configmap backend-infra \
      --from-literal=DB_URL="$DB_URL" \
      --from-literal=REDIS_HOST="$REDIS_HOST" \
      --from-literal=REDIS_DATABASE="$REDIS_DATABASE" \
      --from-literal=CORS_ORIGINS="$CORS_ORIGINS" \
      --from-literal=GCS_BUCKET="$MEDIA_BUCKET" \
      --from-literal=STORAGE_PREFIX="$ENV_NAME" \
      --from-literal=MAIL_FROM="${MAIL_FROM:-${MAIL_USERNAME:-no-reply@boardkub.local}}" \
      --dry-run=client -o yaml | kubectl apply -f -
    kubectl -n "$NAMESPACE" create secret generic backend-secret \
      --from-literal=DB_USERNAME="$DB_USERNAME" \
      --from-literal=DB_PASSWORD="$DB_PASSWORD" \
      --from-literal=REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
      --from-literal=ADMIN_EMAIL="$ADMIN_EMAIL" \
      --from-literal=ADMIN_PASSWORD="$ADMIN_PASSWORD" \
      --from-literal=MAIL_USERNAME="${MAIL_USERNAME:-}" \
      --from-literal=MAIL_PASSWORD="${MAIL_PASSWORD:-}" \
      --dry-run=client -o yaml | kubectl apply -f -
  } | tee /dev/stderr | grep -c -E 'configured|created' || true
)

if [ -n "$DOMAIN" ]; then
  echo "==> Google-managed TLS certificate for $DOMAIN"
  kubectl -n "$NAMESPACE" apply -f - <<YAML
apiVersion: networking.gke.io/v1
kind: ManagedCertificate
metadata:
  name: boardkub-cert
spec:
  domains:
    - ${DOMAIN}
YAML
fi

echo "==> Manifests ($ENV_NAME, tag $IMAGE_TAG)"
(
  cd "$OVERLAY"
  kustomize edit set image \
    "boardkub-backend=${IMAGE_PREFIX}/backend:${IMAGE_TAG}" \
    "boardkub-frontend=${IMAGE_PREFIX}/frontend:${IMAGE_TAG}"
)
apply_out=$(kustomize build "$OVERLAY" | kubectl apply -f -)
echo "$apply_out"

# A changed Secret/ConfigMap is only read at Pod start. If the backend image did not
# change (e.g. re-running a job after rotating a password), restart it explicitly.
if [ "${changed:-0}" -gt 0 ] && echo "$apply_out" | grep -q 'deployment.apps/backend unchanged'; then
  echo "==> Settings changed but image did not: restarting backend"
  kubectl -n "$NAMESPACE" rollout restart deployment/backend
fi

echo "==> Waiting for rollout"
kubectl -n "$NAMESPACE" rollout status deployment/backend  --timeout=10m
kubectl -n "$NAMESPACE" rollout status deployment/frontend --timeout=5m

echo "==> Smoke test inside the cluster"
# shellcheck disable=SC2016  # the script below runs inside the Pod, not here
# The Pod label app=smoke-test is allowed through the backend NetworkPolicy.
kubectl -n "$NAMESPACE" run "smoke-${IMAGE_TAG:0:7}-$RANDOM" \
  --rm -i --restart=Never --quiet \
  --labels=app=smoke-test \
  --image=curlimages/curl:8.11.1 \
  --pod-running-timeout=5m \
  --command -- sh -ec '
    check() { echo "  $1"; curl -fsS --retry 5 --retry-delay 3 --retry-all-errors -o /tmp/out "$1"; }
    check http://backend:8080/actuator/health/readiness
    check http://backend:8080/api/categories;  grep -q "\[" /tmp/out
    check http://frontend/healthz
    check http://frontend/;                    grep -qi "<html" /tmp/out
    check http://frontend/api/categories;      grep -q "\[" /tmp/out
    echo "smoke test passed"
  '

if [ -n "$DOMAIN" ]; then
  echo "==> Public endpoint (informational: the certificate can take up to an hour the first time)"
  curl -fsS -o /dev/null -w "  https://$DOMAIN/ -> %{http_code}\n" --max-time 15 "https://$DOMAIN/" \
    || echo "::warning::https://$DOMAIN is not reachable yet. Check: kubectl -n $NAMESPACE describe managedcertificate boardkub-cert"
fi
echo "==> $ENV_NAME deployed"
