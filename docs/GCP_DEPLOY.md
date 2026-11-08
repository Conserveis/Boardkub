# Deploy BoardKub บน Google Cloud (GKE Autopilot)

คู่มือนี้ตรงกับข้อ 3 ของ Project Proposal (ฉบับ Google Cloud)
ทำครั้งเดียวประมาณ 1 ชั่วโมง หลังจากนั้นทุกครั้งที่ `git push` ระบบจะ build → test → scan → deploy ให้เอง

```
GitHub Actions ──(Workload Identity Federation, ไม่มี key)──► Artifact Registry
       │                                                          │ image pull
       ▼                                                          ▼
   GKE Autopilot ─ ns boardkub-staging     (1 Pod/tier, ไม่มี Ingress)
                 └ ns boardkub-production  (Ingress + HTTPS + Cloud Armor, HPA 2–5)
                         │ private IP (Private Services Access)
                         ├─► Cloud SQL for MySQL 8.0   db: boardkub_staging / boardkub_production
                         └─► Memorystore for Redis     DB 1 = staging / DB 0 = production
                         │ Workload Identity (ไม่มีไฟล์ key)
                         └─► Cloud Storage  gs://<project>-boardkub-media/{staging,production}/
                                  ▲ เบราว์เซอร์โหลดรูปจากที่นี่โดยตรง ไม่ผ่าน backend
```

| ข้อมูล | เก็บที่ | เหตุผล |
|---|---|---|
| ผู้ใช้ กระทู้ ความคิดเห็น | Cloud SQL | ข้อมูลมีความสัมพันธ์กัน ต้องการ transaction |
| Session, ตัวนับ | Memorystore | อ่านเขียนเร็ว มีอายุ (TTL) และใช้ร่วมกันทุก Pod |
| รูปประกอบกระทู้ | Cloud Storage | ไฟล์ใหญ่ ไม่จำกัดพื้นที่ ราคาถูก ส่งตรงถึงเบราว์เซอร์ได้ และทำให้ backend เป็น stateless |

## ค่าใช้จ่าย (อ่านก่อน)

| รายการ | ประมาณการต่อเดือน |
|---|---|
| GKE cluster fee | $0 (free tier ครอบคลุม 1 cluster) |
| Autopilot Pods (prod 2+2, staging 1+1) | ~$40–60 |
| Cloud SQL db-f1-micro + 10 GB SSD | ~$10–12 |
| Memorystore Basic 1 GB | ~$40–50 |
| Load Balancer + Cloud Armor (policy + 3 rules) | ~$25–30 |
| Cloud Storage (รูป ~10 GB + การโหลดรูป) | ~$1–3 |
| Cloud NAT, Artifact Registry, Logging | ~$5–10 |

รวมราว **$120–160/เดือน** เครดิตทดลอง $300 จึงพอใช้ประมาณ 2 เดือน
ตัวเลขเป็นค่าประมาณ ตรวจราคาล่าสุดที่ cloud.google.com/products/calculator

- ตั้ง **Budget alert** ทันที: Billing → Budgets & alerts → Create budget (เช่น $50, แจ้งเตือนที่ 50/90/100%)
- ไม่ใช้แล้วรัน `./scripts/gcp-teardown.sh` เพื่อลบทุกอย่าง
- ช่วงยังไม่สาธิต ลดค่าใช้จ่ายได้ด้วย `kubectl -n boardkub-staging scale deploy --all --replicas=0`

## ขั้นที่ 1 — สร้างทรัพยากรบน Google Cloud

เปิด **Cloud Shell** (ไอคอน `>_` มุมขวาบนของ console) แล้วอัปโหลดโปรเจกต์ หรือ `git clone` repo ของคุณ

```bash
export PROJECT_ID=<project-id ของคุณ>        # ดูได้จาก gcloud projects list
export GITHUB_REPO=<github-user>/<repo-name>  # repo ที่จะรัน CI/CD
./scripts/gcp-setup.sh
```

สคริปต์จะสร้างตามลำดับ (รันซ้ำได้ อะไรที่มีอยู่แล้วจะข้ามไป):

1. เปิด API ที่ต้องใช้
2. VPC `boardkub-vpc` + subnet `gke-subnet` (มี secondary range สำหรับ Pod/Service)
3. Cloud Router + Cloud NAT ให้ node แบบ private ส่งอีเมล OTP ออกไปได้
4. Private Services Access ให้ Cloud SQL / Memorystore ได้ IP ภายใน VPC
5. Cloud SQL `boardkub-mysql` (ไม่มี public IP, บังคับ TLS, backup ทุกวัน + PITR) พร้อม 2 database และ user `boardkub`
6. Memorystore `boardkub-redis`
7. Cloud Storage `gs://<project>-boardkub-media` สำหรับรูปประกอบกระทู้ (อ่านได้สาธารณะ ชื่อไฟล์เป็น UUID สุ่ม)
8. Artifact Registry `boardkub`
9. GKE Autopilot `boardkub` (private nodes)
10. สิทธิ์ให้ ServiceAccount `boardkub-backend` ใน Kubernetes เขียนรูปลง bucket ได้ผ่าน Workload Identity (ไม่มีไฟล์ key)
11. Static IP `boardkub-ip` สำหรับ Ingress
12. Cloud Armor `boardkub-armor`: กัน SQLi/XSS (ยกเว้น path อัปโหลดรูป) และจำกัด `/api/auth/*` ที่ 120 ครั้ง/นาที/IP
13. Cloud Monitoring dashboard **BoardKub — Production**
14. Workload Identity Federation + service account `github-deployer` (สิทธิ์แค่ deploy GKE และ push image)

ตอนจบสคริปต์จะพิมพ์ค่าทั้งหมดที่ต้องใส่ใน GitHub ออกมา **รหัสผ่าน DB แสดงครั้งเดียว** ให้คัดลอกเก็บทันที

## ขั้นที่ 2 — โดเมน

- มีโดเมน: สร้าง A record ชี้ไปที่ IP ที่สคริปต์พิมพ์ออกมา แล้วใช้ชื่อนั้นเป็น `DOMAIN`
- ไม่มีโดเมน: ใช้ `<ip ที่ใช้ - แทน .>.sslip.io` เช่น `34-120-10-20.sslip.io` ได้เลย ไม่ต้องตั้ง DNS

Google จะออกใบรับรอง HTTPS ให้อัตโนมัติ (ManagedCertificate) ครั้งแรกใช้เวลา 15–60 นาที

## ขั้นที่ 3 — ตั้งค่า GitHub

Settings → Secrets and variables → Actions

**Repository variables**

| ชื่อ | ค่า |
|---|---|
| `GCP_PROJECT_ID` | project id |
| `WIF_PROVIDER` | `projects/<number>/locations/global/workloadIdentityPools/github/providers/github` |
| `WIF_SERVICE_ACCOUNT` | `github-deployer@<project>.iam.gserviceaccount.com` |
| `MEDIA_BUCKET` | `<project>-boardkub-media` (staging กับ production ใช้ bucket เดียวกัน แยกโฟลเดอร์) |

จากนั้น Settings → **Environments** สร้าง `staging` และ `production` แล้วใส่ค่าในแต่ละ environment

| ชื่อ (Variable) | staging | production |
|---|---|---|
| `CLOUDSQL_IP` | IP จากสคริปต์ | เหมือนกัน |
| `REDIS_HOST` | IP จากสคริปต์ | เหมือนกัน |
| `DB_USERNAME` | `boardkub` | `boardkub` |
| `DB_NAME` | `boardkub_staging` | `boardkub_production` |
| `REDIS_DATABASE` | `1` | `0` |
| `ADMIN_EMAIL` | `admin@kmitl.ac.th` | อีเมลแอดมินจริง |
| `DOMAIN` | – | โดเมนจากขั้นที่ 2 |

| ชื่อ (Secret) | staging | production |
|---|---|---|
| `DB_PASSWORD` | รหัสจากสคริปต์ | รหัสจากสคริปต์ |
| `ADMIN_PASSWORD` | ตั้งเอง | ตั้งเองให้แข็งแรง |
| `MAIL_USERNAME` | เว้นว่างได้ | Gmail ที่ใช้ส่ง OTP |
| `MAIL_PASSWORD` | เว้นว่างได้ | Gmail **App Password** (ไม่ใช่รหัสผ่านปกติ) |

แนะนำ: ใน environment `production` เปิด **Required reviewers** แล้วใส่ชื่อตัวเอง
จะได้มีปุ่มกดอนุมัติก่อนขึ้น production ทุกครั้ง (ใช้สาธิตได้ดี)

## ขั้นที่ 4 — Push แล้วดู pipeline ทำงาน

```bash
git checkout -b develop && git push -u origin develop   # → build, scan, deploy staging, smoke test
git checkout main && git merge develop && git push      # → ทั้งหมดข้างบน + production
```

| Branch / เหตุการณ์ | สิ่งที่ทำ |
|---|---|
| Pull request | build + unit test + render manifests (ไม่ deploy) |
| push `develop` | + build image → Trivy scan → push → deploy staging → smoke test |
| push `main` | + deploy production (หลัง staging ผ่าน) |

## ทดสอบและสาธิต

```bash
gcloud container clusters get-credentials boardkub --region asia-southeast1

# staging ไม่มี public URL ใช้ port-forward แล้วเปิด http://localhost:8080
kubectl -n boardkub-staging port-forward svc/frontend 8080:80

# production
kubectl -n boardkub-production get pods,ingress,hpa
kubectl -n boardkub-production describe managedcertificate boardkub-cert   # รอจน Status: Active
```

**สาธิต Session ข้าม Pod:** scale backend เป็น 4 แล้วล็อกอิน กด refresh ชื่อ Pod ที่แถบล่างจะเปลี่ยน แต่ยังล็อกอินอยู่

```bash
kubectl -n boardkub-production scale deploy/backend --replicas=4   # HPA จะปรับกลับตาม CPU ภายหลัง
```

**สาธิต HPA, zero-downtime และอื่นๆ:** ดูขั้นตอนเต็มใน [`docs/DEMO.md`](DEMO.md)

```bash
./scripts/loadtest.sh <domain>               # จำลองผู้ใช้ 150 คน ดู HPA เพิ่ม Pod (~10 นาที)
./scripts/demo-zero-downtime.sh <domain>     # rolling update ระหว่างมี traffic ต้องไม่มี error เลย
```

Dashboard **BoardKub — Production** ถูกสร้างโดย `gcp-setup.sh` (Cloud Console → Monitoring → Dashboards)

## แก้ปัญหาที่พบบ่อย

| อาการ | สาเหตุ / วิธีแก้ |
|---|---|
| `google-github-actions/auth` ล้ม: *unauthorized* | `GITHUB_REPO` ตอนรันสคริปต์ไม่ตรงชื่อ repo (ตัวพิมพ์เล็กใหญ่มีผล) รันสคริปต์ใหม่ด้วยชื่อที่ถูก |
| Pod ค้าง `ImagePullBackOff` | node SA ไม่มีสิทธิ์อ่าน Artifact Registry ดูข้อความใน `kubectl describe pod` |
| backend ค้าง `0/1 Running` นาน | ต่อ DB ไม่ได้: ตรวจ `CLOUDSQL_IP`, `DB_PASSWORD` แล้วดู `kubectl logs deploy/backend` |
| เว็บขึ้น 502 ในช่วงแรกหลัง deploy | Load balancer ยังตรวจ health ไม่ผ่าน รอ 3–5 นาที |
| HTTPS ยังไม่ขึ้น | `describe managedcertificate` ต้องเป็น Active และ DNS ต้องชี้ถูก IP ก่อน |
| ได้ 429 ตอนล็อกอิน | Cloud Armor throttle ทำงาน (เกิน 120 ครั้ง/นาที/IP) รอ 1 นาที |
| ได้ 403 ตอนตั้งกระทู้ที่มีโค้ด/SQL | กฎ WAF ของ Cloud Armor มองว่าเป็นการโจมตี ปรับ sensitivity หรือตั้งกฎนั้นเป็น `--preview` |
| อัปรูปแล้วได้ 500, log มี `Metadata server returned HTTP 403/404` | สิทธิ์ Workload Identity ของ `boardkub-backend` ยังไม่มี รัน `gcp-setup.sh` ซ้ำ (หลังสร้าง cluster แล้ว) |
| อัปรูปแล้วได้ 500, log มี `Cloud Storage upload failed: HTTP 403` | ตรวจว่า `MEDIA_BUCKET` ตรงกับชื่อ bucket จริง |
| รูปขึ้นไม่ได้ (403 ตอนเปิดรูป) | bucket ยังไม่เปิดอ่านสาธารณะ: `gcloud storage buckets add-iam-policy-binding gs://<bucket> --member allUsers --role roles/storage.objectViewer` |
| ไม่ได้รับอีเมล OTP | ต้องใช้ Gmail App Password และ `OTP_DEV_MODE=false` (production ตั้งไว้แล้ว) |

## ไฟล์ที่เกี่ยวข้อง

```
k8s/base/                 Deployment, Service, HPA, PDB, NetworkPolicy ที่ใช้ร่วมกันทุก environment
k8s/overlays/local/       minikube: MySQL + Redis ใน cluster, NGINX Ingress
k8s/overlays/staging/     GKE ns boardkub-staging, 1 Pod/tier, ไม่มี Ingress
k8s/overlays/production/  GKE ns boardkub-production, GKE Ingress, BackendConfig, FrontendConfig
scripts/gcp-setup.sh      สร้างทรัพยากร Google Cloud ทั้งหมด
scripts/deploy.sh         ที่ CI เรียก: สร้าง Secret/ConfigMap → apply → รอ rollout → smoke test
scripts/gcp-teardown.sh   ลบทุกอย่าง
scripts/loadtest.sh       รัน k6 เป็น Job ใน GKE ยิงผ่าน Load Balancer จริง
scripts/demo-zero-downtime.sh   load คงที่ + rolling restart ต้องไม่มี request ผิดพลาด
loadtest/boardkub.js      สคริปต์ k6 (จำลองนักศึกษาเปิดอ่านบอร์ด)
monitoring/dashboard.json Cloud Monitoring dashboard
```
