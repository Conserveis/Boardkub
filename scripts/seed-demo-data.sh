#!/usr/bin/env bash
# Fills the database with fictional demo data (91 students, 420 posts, ~1,500 comments)
# so the site looks busy in a presentation, or removes that data again.
#
# Run from Cloud Shell after the app is deployed:
#   ./scripts/seed-demo-data.sh                 # add demo data to production
#   ./scripts/seed-demo-data.sh --remove        # delete every demo row
#   NAMESPACE=boardkub-staging ./scripts/seed-demo-data.sh
#
# Demo accounts use e-mails ending in @demo.boardkub.local and the password Student@123.
# The SQL runs inside the cluster, because Cloud SQL has only a private IP.
set -euo pipefail

NAMESPACE="${NAMESPACE:-boardkub-production}"
REGION="${REGION:-asia-southeast1}"
MODE="${1:-add}"
DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="$DIR/demo-data/demo-data.sql"
JOB=demo-data

case "$MODE" in
  add|--add) MODE=add ;;
  --remove) MODE=remove ;;
  *) echo "Usage: $0 [--remove]"; exit 1 ;;
esac

gcloud container clusters get-credentials boardkub --region "$REGION" >/dev/null 2>&1 || true
kubectl -n "$NAMESPACE" get configmap backend-infra >/dev/null \
  || { echo "Namespace $NAMESPACE has no backend-infra ConfigMap. Deploy the app first."; exit 1; }

# DB_URL looks like jdbc:mysql://10.x.x.x:3306/boardkub_production?sslMode=REQUIRED&...
DB_URL=$(kubectl -n "$NAMESPACE" get configmap backend-infra -o jsonpath='{.data.DB_URL}')
DB_HOST=$(echo "$DB_URL" | sed -E 's#^jdbc:mysql://([^:/]+).*#\1#')
DB_NAME=$(echo "$DB_URL" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')
echo "Database: $DB_NAME on $DB_HOST (namespace $NAMESPACE)"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

DEMO="'%@demo.boardkub.local'"
cat > "$WORK/check.sql" <<EOF
SELECT CONCAT('DEMO_USERS=', COUNT(*)) FROM users WHERE email LIKE $DEMO;
EOF

if [ "$MODE" = add ]; then
  cp "$SQL_FILE" "$WORK/run.sql"
else
  cat > "$WORK/run.sql" <<EOF
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE demo_posts AS
  SELECT p.id FROM posts p JOIN users u ON p.user_id = u.id WHERE u.email LIKE $DEMO;
DELETE FROM comments WHERE post_id IN (SELECT id FROM demo_posts);
DELETE FROM comments WHERE user_id IN (SELECT id FROM users WHERE email LIKE $DEMO);
DELETE FROM post_images WHERE post_id IN (SELECT id FROM demo_posts);
DELETE FROM posts WHERE id IN (SELECT id FROM demo_posts);
DELETE FROM otp_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE $DEMO);
DELETE FROM users WHERE email LIKE $DEMO;
COMMIT;
SELECT 'Demo data removed' AS result;
EOF
fi

# Runs one SQL file in a short-lived Pod; the file reaches it through a ConfigMap.
run_sql() {
  local file="$1"
  kubectl -n "$NAMESPACE" delete job "$JOB" --ignore-not-found >/dev/null
  kubectl -n "$NAMESPACE" delete configmap "$JOB" --ignore-not-found >/dev/null
  kubectl -n "$NAMESPACE" create configmap "$JOB" --from-file=run.sql="$file" >/dev/null
  cat <<EOF | kubectl -n "$NAMESPACE" apply -f - >/dev/null
apiVersion: batch/v1
kind: Job
metadata:
  name: $JOB
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 600
  template:
    metadata:
      labels: { app: demo-data }
    spec:
      restartPolicy: Never
      containers:
        - name: mysql
          image: mysql:8.0
          command: ["sh", "-c"]
          args:
            - mysql -h "$DB_HOST" -u "\$DB_USERNAME" --password="\$DB_PASSWORD"
              --ssl-mode=REQUIRED --default-character-set=utf8mb4 -N -s "$DB_NAME" < /sql/run.sql
          env:
            - name: DB_USERNAME
              valueFrom: { secretKeyRef: { name: backend-secret, key: DB_USERNAME } }
            - name: DB_PASSWORD
              valueFrom: { secretKeyRef: { name: backend-secret, key: DB_PASSWORD } }
          resources:
            requests: { cpu: 250m, memory: 256Mi }
          volumeMounts:
            - { name: sql, mountPath: /sql }
      volumes:
        - name: sql
          configMap: { name: $JOB }
EOF
  local status="" i
  for i in $(seq 1 120); do                     # up to 10 minutes
    status=$(kubectl -n "$NAMESPACE" get job "$JOB" -o jsonpath='{.status.succeeded}/{.status.failed}')
    case "$status" in 1/*) status=ok; break ;; */1) status=failed; break ;; esac
    sleep 5
  done
  if [ "$status" != ok ]; then
    echo "The SQL job failed. Its output:"
    kubectl -n "$NAMESPACE" logs "job/$JOB" || true
    kubectl -n "$NAMESPACE" delete job "$JOB" --ignore-not-found >/dev/null
    kubectl -n "$NAMESPACE" delete configmap "$JOB" --ignore-not-found >/dev/null
    exit 1
  fi
  kubectl -n "$NAMESPACE" logs "job/$JOB"
  kubectl -n "$NAMESPACE" delete job "$JOB" --ignore-not-found >/dev/null
  kubectl -n "$NAMESPACE" delete configmap "$JOB" --ignore-not-found >/dev/null
}

echo "Checking for existing demo data..."
COUNT=$(run_sql "$WORK/check.sql" | sed -nE 's/.*DEMO_USERS=([0-9]+).*/\1/p')
echo "Demo accounts already in the database: ${COUNT:-unknown}"

if [ "$MODE" = add ] && [ "${COUNT:-0}" != 0 ]; then
  echo "Demo data is already there. Run '$0 --remove' first if you want to add it again."
  exit 1
fi
if [ "$MODE" = remove ] && [ "${COUNT:-0}" = 0 ]; then
  echo "Nothing to remove."
  exit 0
fi

echo "Running ($MODE). The first run pulls the mysql image, so allow a minute or two..."
run_sql "$WORK/run.sql"
echo "Done. Demo accounts log in with demoNNN@demo.boardkub.local / Student@123"
