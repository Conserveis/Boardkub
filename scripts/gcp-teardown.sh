#!/usr/bin/env bash
# Deletes everything gcp-setup.sh created, so the project stops costing money.
# THIS DELETES THE DATABASE. Export anything you want to keep first:
#   gcloud sql export sql boardkub-mysql gs://<bucket>/backup.sql --database=boardkub_production
#
#   export PROJECT_ID=my-boardkub-project
#   ./scripts/gcp-teardown.sh
set -uo pipefail

: "${PROJECT_ID:?set PROJECT_ID first}"
REGION="${REGION:-asia-southeast1}"
gcloud config set project "$PROJECT_ID" >/dev/null

read -r -p "Delete ALL BoardKub resources in $PROJECT_ID, including the database? Type the project ID to confirm: " answer
[ "$answer" = "$PROJECT_ID" ] || { echo "Cancelled."; exit 1; }

q="--quiet"
# The Ingress owns load-balancer pieces; delete it first so GKE cleans them up
if gcloud container clusters get-credentials boardkub --region "$REGION" >/dev/null 2>&1; then
  kubectl delete namespace boardkub-production boardkub-staging --ignore-not-found --wait=true
fi
gcloud container clusters delete boardkub --region "$REGION" $q
gcloud sql instances delete boardkub-mysql $q
gcloud redis instances delete boardkub-redis --region "$REGION" $q
gcloud compute security-policies delete boardkub-armor $q
gcloud compute addresses delete boardkub-ip --global $q
gcloud artifacts repositories delete boardkub --location "$REGION" $q
gcloud storage rm --recursive "gs://${MEDIA_BUCKET:-${PROJECT_ID}-boardkub-media}"   # deletes every uploaded image
gcloud compute routers nats delete boardkub-nat --router boardkub-router --region "$REGION" $q
gcloud compute routers delete boardkub-router --region "$REGION" $q
gcloud services vpc-peerings delete --service=servicenetworking.googleapis.com --network=boardkub-vpc $q
gcloud compute addresses delete google-managed-services --global $q
gcloud compute networks subnets delete gke-subnet --region "$REGION" $q
gcloud compute networks delete boardkub-vpc $q
gcloud iam service-accounts delete "github-deployer@$PROJECT_ID.iam.gserviceaccount.com" $q
gcloud iam workload-identity-pools delete github --location global $q
for d in $(gcloud monitoring dashboards list --format='value(name)' --filter='displayName="BoardKub — Production"'); do
  gcloud monitoring dashboards delete "$d" $q
done
echo "Done. Check Billing > Reports over the next day to confirm costs stopped."
