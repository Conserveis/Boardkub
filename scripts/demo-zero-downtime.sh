#!/usr/bin/env bash
# Proves a deployment causes no downtime:
#   1. constant traffic starts (30 users) and runs for 4 minutes
#   2. after 60 s every backend Pod is replaced with `kubectl rollout restart`
#   3. k6 fails if even ONE request errors — so "PASSED" means zero downtime
#
#   ./scripts/demo-zero-downtime.sh boardkub.example.com
set -euo pipefail

DOMAIN="${1:?usage: $0 <domain>}"
NS_APP=boardkub-production
HERE="$(cd "$(dirname "$0")" && pwd)"

PROFILE=steady DURATION=4m "$HERE/loadtest.sh" "$DOMAIN" &
LOAD_PID=$!

sleep 60
echo
echo "==> Rolling restart of the backend while traffic is flowing"
kubectl -n "$NS_APP" rollout restart deployment/backend
kubectl -n "$NS_APP" rollout status deployment/backend --timeout=10m
kubectl -n "$NS_APP" get pods -l app=backend -o wide

wait "$LOAD_PID"
