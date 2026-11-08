#!/usr/bin/env bash
# Creates every Google Cloud resource BoardKub needs (section 3 of the proposal).
# Safe to re-run: anything that already exists is skipped.
#
#   export PROJECT_ID=my-boardkub-project      # gcloud projects list
#   export GITHUB_REPO=my-github-user/boardkub # the repository that runs CI/CD
#   ./scripts/gcp-setup.sh
#
# Takes about 25-35 minutes (Cloud SQL and the GKE cluster are the slow parts).
# Run it from Cloud Shell or from a machine where `gcloud auth login` is done.
set -euo pipefail

: "${PROJECT_ID:?set PROJECT_ID first}"
: "${GITHUB_REPO:?set GITHUB_REPO=owner/repo first}"
REGION="${REGION:-asia-southeast1}"

NETWORK=boardkub-vpc
SUBNET=gke-subnet
SQL_INSTANCE=boardkub-mysql
REDIS_INSTANCE=boardkub-redis
AR_REPO=boardkub
CLUSTER=boardkub
STATIC_IP=boardkub-ip
ARMOR=boardkub-armor
WIF_POOL=github
WIF_PROVIDER_ID=github
DEPLOYER=github-deployer
DB_USER=boardkub
MEDIA_BUCKET="${MEDIA_BUCKET:-${PROJECT_ID}-boardkub-media}"

step()   { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
exists() { "$@" >/dev/null 2>&1; }

gcloud config set project "$PROJECT_ID" >/dev/null
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
NETWORK_URI="projects/$PROJECT_ID/global/networks/$NETWORK"

step "Enabling APIs"
gcloud services enable \
  compute.googleapis.com container.googleapis.com sqladmin.googleapis.com \
  redis.googleapis.com artifactregistry.googleapis.com servicenetworking.googleapis.com \
  iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  cloudresourcemanager.googleapis.com logging.googleapis.com monitoring.googleapis.com

step "VPC $NETWORK and subnet $SUBNET (with Pod/Service ranges, Private Google Access)"
exists gcloud compute networks describe "$NETWORK" ||
  gcloud compute networks create "$NETWORK" --subnet-mode=custom
exists gcloud compute networks subnets describe "$SUBNET" --region "$REGION" ||
  gcloud compute networks subnets create "$SUBNET" \
    --network "$NETWORK" --region "$REGION" --range 10.0.0.0/20 \
    --secondary-range pods=10.4.0.0/14,services=10.8.0.0/20 \
    --enable-private-ip-google-access

step "Cloud Router + Cloud NAT (outbound only: private nodes need it for SMTP)"
exists gcloud compute routers describe boardkub-router --region "$REGION" ||
  gcloud compute routers create boardkub-router --network "$NETWORK" --region "$REGION"
exists gcloud compute routers nats describe boardkub-nat --router boardkub-router --region "$REGION" ||
  gcloud compute routers nats create boardkub-nat --router boardkub-router --region "$REGION" \
    --auto-allocate-nat-external-ips --nat-all-subnet-ip-ranges

step "Private Services Access (private IPs for Cloud SQL and Memorystore)"
exists gcloud compute addresses describe google-managed-services --global ||
  gcloud compute addresses create google-managed-services --global \
    --purpose=VPC_PEERING --prefix-length=16 --network="$NETWORK"
gcloud services vpc-peerings connect --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services --network="$NETWORK" 2>/dev/null ||
  echo "    (peering already exists)"

step "Cloud SQL for MySQL 8.0 — private IP only, TLS required, daily backup + PITR"
if ! exists gcloud sql instances describe "$SQL_INSTANCE"; then
  gcloud sql instances create "$SQL_INSTANCE" \
    --database-version=MYSQL_8_0 --edition=ENTERPRISE --tier=db-f1-micro \
    --region="$REGION" --availability-type=zonal \
    --network="$NETWORK_URI" --no-assign-ip --ssl-mode=ENCRYPTED_ONLY \
    --storage-type=SSD --storage-size=10 --storage-auto-increase \
    --backup-start-time=19:00 --enable-bin-log --retained-backups-count=7 \
    --database-flags=character_set_server=utf8mb4 \
    --time-zone=Asia/Bangkok
fi
for db in boardkub_staging boardkub_production; do
  exists gcloud sql databases describe "$db" --instance "$SQL_INSTANCE" ||
    gcloud sql databases create "$db" --instance "$SQL_INSTANCE" \
      --charset=utf8mb4 --collation=utf8mb4_unicode_ci
done
DB_PASSWORD_CREATED=""
if ! gcloud sql users list --instance "$SQL_INSTANCE" --format='value(name)' | grep -qx "$DB_USER"; then
  DB_PASSWORD_CREATED=$(openssl rand -base64 30 | tr -dc 'A-Za-z0-9' | head -c 28)
  gcloud sql users create "$DB_USER" --instance "$SQL_INSTANCE" --host=% \
    --password="$DB_PASSWORD_CREATED"
fi

step "Memorystore for Redis 7.2 — Basic tier 1 GB, private service access"
exists gcloud redis instances describe "$REDIS_INSTANCE" --region "$REGION" ||
  gcloud redis instances create "$REDIS_INSTANCE" --region "$REGION" \
    --tier=basic --size=1 --redis-version=redis_7_2 \
    --network="$NETWORK_URI" --connect-mode=private-service-access

step "Cloud Storage bucket gs://$MEDIA_BUCKET for post images (Object Storage)"
if ! exists gcloud storage buckets describe "gs://$MEDIA_BUCKET"; then
  gcloud storage buckets create "gs://$MEDIA_BUCKET" --location "$REGION" \
    --default-storage-class STANDARD --uniform-bucket-level-access \
    --no-public-access-prevention --soft-delete-duration 7d
fi
# Images are public (like any photo on a public web board); names are random UUIDs
gcloud storage buckets add-iam-policy-binding "gs://$MEDIA_BUCKET" \
  --member allUsers --role roles/storage.objectViewer >/dev/null ||
  echo "    WARNING: could not make the bucket publicly readable (an organization policy may block it)."


exists gcloud artifacts repositories describe "$AR_REPO" --location "$REGION" ||
  gcloud artifacts repositories create "$AR_REPO" --location "$REGION" \
    --repository-format=docker --description="BoardKub container images"

step "GKE Autopilot cluster $CLUSTER (private nodes)"
exists gcloud container clusters describe "$CLUSTER" --region "$REGION" ||
  gcloud container clusters create-auto "$CLUSTER" --region "$REGION" \
    --network "$NETWORK" --subnetwork "$SUBNET" \
    --cluster-secondary-range-name pods --services-secondary-range-name services \
    --enable-private-nodes --release-channel regular

# Autopilot nodes run as the Compute Engine default service account
NODE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
gcloud artifacts repositories add-iam-policy-binding "$AR_REPO" --location "$REGION" \
  --member "serviceAccount:$NODE_SA" --role roles/artifactregistry.reader >/dev/null

step "Workload Identity: backend Pods may write images to the bucket (no key file)"
# The Kubernetes ServiceAccount boardkub-backend in each namespace is the IAM principal
for ns in boardkub-staging boardkub-production; do
  gcloud storage buckets add-iam-policy-binding "gs://$MEDIA_BUCKET" \
    --role roles/storage.objectUser \
    --member "principal://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$PROJECT_ID.svc.id.goog/subject/ns/$ns/sa/boardkub-backend" \
    >/dev/null
done

step "Global static IP $STATIC_IP for the Ingress"
exists gcloud compute addresses describe "$STATIC_IP" --global ||
  gcloud compute addresses create "$STATIC_IP" --global --ip-version IPV4

step "Cloud Armor policy $ARMOR (SQLi/XSS rules + throttle on /api/auth/)"
exists gcloud compute security-policies describe "$ARMOR" ||
  gcloud compute security-policies create "$ARMOR" --description "BoardKub WAF"

# Creates the rule, or updates it so that re-running the script applies changes
armor_rule() {
  local priority="$1"; shift
  if exists gcloud compute security-policies rules describe "$priority" --security-policy "$ARMOR"; then
    gcloud compute security-policies rules update "$priority" --security-policy "$ARMOR" "$@" >/dev/null
  else
    gcloud compute security-policies rules create "$priority" --security-policy "$ARMOR" "$@" >/dev/null
  fi
}
# Many students share one public IP (campus Wi-Fi, dorms), so the limit is generous;
# per-account limits belong in the application.
armor_rule 900 --description "login/register/OTP: 120 requests per minute per IP" \
  --expression "request.path.matches('/api/auth/.*')" \
  --action throttle --rate-limit-threshold-count 120 --rate-limit-threshold-interval-sec 60 \
  --conform-action allow --exceed-action deny-429 --enforce-on-key IP
# Image uploads are binary data that WAF signatures misread as attacks, so the
# upload path skips them; the backend validates and re-encodes every image itself.
NOT_UPLOAD="!request.path.matches('^/api/posts/[0-9]+/images$')"
armor_rule 1000 --description "SQL injection" \
  --expression "evaluatePreconfiguredWaf('sqli-v33-stable', {'sensitivity': 1}) && $NOT_UPLOAD" \
  --action deny-403
armor_rule 1001 --description "Cross-site scripting" \
  --expression "evaluatePreconfiguredWaf('xss-v33-stable', {'sensitivity': 1}) && $NOT_UPLOAD" \
  --action deny-403

step "Cloud Monitoring dashboard \"BoardKub — Production\""
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DASHBOARD_FILE="$SCRIPT_DIR/../monitoring/dashboard.json"
existing=$(gcloud monitoring dashboards list --format='value(name)' \
  --filter='displayName="BoardKub — Production"' 2>/dev/null | head -1)
if [ -n "$existing" ]; then
  # Replace it so changes to dashboard.json are applied on re-run
  gcloud monitoring dashboards delete "$existing" --quiet >/dev/null
fi
gcloud monitoring dashboards create --config-from-file="$DASHBOARD_FILE" >/dev/null
echo "    open: https://console.cloud.google.com/monitoring/dashboards?project=$PROJECT_ID"

step "Workload Identity Federation for GitHub Actions ($GITHUB_REPO)"
exists gcloud iam workload-identity-pools describe "$WIF_POOL" --location global ||
  gcloud iam workload-identity-pools create "$WIF_POOL" --location global \
    --display-name "GitHub Actions"
exists gcloud iam workload-identity-pools providers describe "$WIF_PROVIDER_ID" \
  --workload-identity-pool "$WIF_POOL" --location global ||
  gcloud iam workload-identity-pools providers create-oidc "$WIF_PROVIDER_ID" \
    --workload-identity-pool "$WIF_POOL" --location global \
    --issuer-uri "https://token.actions.githubusercontent.com" \
    --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition "assertion.repository == '$GITHUB_REPO'"

DEPLOYER_EMAIL="$DEPLOYER@$PROJECT_ID.iam.gserviceaccount.com"
exists gcloud iam service-accounts describe "$DEPLOYER_EMAIL" ||
  gcloud iam service-accounts create "$DEPLOYER" --display-name "GitHub Actions deployer"
# Least privilege: deploy to GKE and push images, nothing else
gcloud projects add-iam-policy-binding "$PROJECT_ID" --condition=None \
  --member "serviceAccount:$DEPLOYER_EMAIL" --role roles/container.developer >/dev/null
gcloud artifacts repositories add-iam-policy-binding "$AR_REPO" --location "$REGION" \
  --member "serviceAccount:$DEPLOYER_EMAIL" --role roles/artifactregistry.writer >/dev/null
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER_EMAIL" \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$WIF_POOL/attribute.repository/$GITHUB_REPO" >/dev/null

# ------------------------------------------------------------------ summary
CLOUDSQL_IP=$(gcloud sql instances describe "$SQL_INSTANCE" --format='value(ipAddresses[0].ipAddress)')
REDIS_HOST=$(gcloud redis instances describe "$REDIS_INSTANCE" --region "$REGION" --format='value(host)')
LB_IP=$(gcloud compute addresses describe "$STATIC_IP" --global --format='value(address)')
WIF_PROVIDER="projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$WIF_POOL/providers/$WIF_PROVIDER_ID"

cat <<EOF

========================================================================
 Done. Put these into GitHub: Settings > Secrets and variables > Actions
========================================================================
Repository variables
  GCP_PROJECT_ID       = $PROJECT_ID
  MEDIA_BUCKET         = $MEDIA_BUCKET
  WIF_PROVIDER         = $WIF_PROVIDER
  WIF_SERVICE_ACCOUNT  = $DEPLOYER_EMAIL

Environment "staging" and environment "production" (variables)
  CLOUDSQL_IP          = $CLOUDSQL_IP
  REDIS_HOST           = $REDIS_HOST
  DB_USERNAME          = $DB_USER
  ADMIN_EMAIL          = admin@kmitl.ac.th            (or your own)
  DB_NAME              = boardkub_staging   | boardkub_production
  REDIS_DATABASE       = 1                  | 0
  DOMAIN               = (production only)  e.g. boardkub.example.com

Environment secrets (both environments)
  DB_PASSWORD          = ${DB_PASSWORD_CREATED:-<the password you set earlier; reset with: gcloud sql users set-password $DB_USER --instance $SQL_INSTANCE --host=% --prompt-for-password>}
  ADMIN_PASSWORD       = a strong password for the admin account
  MAIL_USERNAME        = Gmail address used to send OTP   (production)
  MAIL_PASSWORD        = Gmail App Password               (production)

DNS: create an A record  <your domain>  ->  $LB_IP
     no domain? use  ${LB_IP//./-}.sslip.io  as DOMAIN (no DNS setup needed)
========================================================================
EOF
if [ -n "$DB_PASSWORD_CREATED" ]; then
  echo "The DB password above is shown only once. Store it in GitHub now."
fi
