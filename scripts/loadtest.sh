#!/usr/bin/env bash
# Runs the k6 load test as a Pod inside GKE, aimed at the public HTTPS URL, so the
# traffic goes through Cloud Load Balancing + Cloud Armor like real users — and the
# dorm Wi-Fi is not the bottleneck.
#
#   ./scripts/loadtest.sh boardkub.example.com            # ramp to 150 users (~10 min)
#   PROFILE=steady DURATION=4m ./scripts/loadtest.sh boardkub.example.com
#
# While it runs, in other terminals:
#   kubectl -n boardkub-production get hpa backend -w
#   kubectl -n boardkub-production get pods -l app=backend -w
# and the Cloud Monitoring dashboard "BoardKub — Production".
set -euo pipefail

DOMAIN="${1:?usage: $0 <domain>   e.g. $0 boardkub.example.com}"
PROFILE="${PROFILE:-ramp}"
DURATION="${DURATION:-4m}"
PEAK_VUS="${PEAK_VUS:-150}"
NS=boardkub-loadtest
NAME="k6-$PROFILE-$(date +%H%M%S)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl -n "$NS" create configmap k6-script --from-file="$ROOT/loadtest/boardkub.js" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n "$NS" apply -f - >/dev/null <<YAML
apiVersion: batch/v1
kind: Job
metadata:
  name: $NAME
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 3600
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: k6
          image: grafana/k6:0.54.0
          args: ["run", "--quiet", "/scripts/boardkub.js"]
          env:
            - { name: BASE_URL, value: "https://$DOMAIN" }
            - { name: PROFILE,  value: "$PROFILE" }
            - { name: DURATION, value: "$DURATION" }
            - { name: PEAK_VUS, value: "$PEAK_VUS" }
          resources:
            requests: { cpu: "1", memory: "1Gi" }
            limits:   { cpu: "1", memory: "1Gi" }
          volumeMounts:
            - { name: script, mountPath: /scripts }
      volumes:
        - name: script
          configMap: { name: k6-script }
YAML

echo "==> Job $NAME started (profile=$PROFILE). Waiting for the k6 Pod..."
kubectl -n "$NS" wait --for=condition=Ready pod -l job-name="$NAME" --timeout=5m >/dev/null 2>&1 || true
kubectl -n "$NS" logs -f "job/$NAME"

if kubectl -n "$NS" wait --for=condition=Complete "job/$NAME" --timeout=30s >/dev/null 2>&1; then
  echo "==> PASSED: all k6 thresholds met"
else
  echo "==> FAILED: a k6 threshold was crossed (see the summary above)"
  exit 1
fi
