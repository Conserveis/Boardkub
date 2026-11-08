# KMITL Connect

เว็บบอร์ดประกาศและปรึกษาสำหรับนักศึกษา จัดกลุ่มตามหมวดหมู่
สถาปัตยกรรม **Three-tier** deploy บน **Google Kubernetes Engine (GKE Autopilot)** ตาม Project Proposal
หน้าตาใช้ดีไซน์ **แบบ B** (พื้นกระดาษอุ่น + เขียวเข้ม, Maitree + Sarabun)

## โครงสร้างโปรเจกต์

```
kmitl-connect/
├── backend/                 Spring Boot REST API (Application Tier)
│   ├── src/main/java/th/ac/kmitl/connect/
│   │   ├── entity/          User, OtpToken, Category, Board, Post, Comment
│   │   ├── repository/      Spring Data JPA
│   │   ├── service/         Business logic (Auth, Otp, Board, Post, Comment)
│   │   ├── controller/      REST endpoints
│   │   ├── interceptor/     AuthInterceptor + @RequireLogin / @RequireAdmin
│   │   └── config/          WebConfig, DataInitializer, BCrypt bean
│   ├── src/test/            JUnit tests (H2 in-memory)
│   └── Dockerfile           Multi-stage: Maven → JRE, non-root (UID 10001)
├── frontend/                HTML5 + CSS3 + Vanilla JS (Presentation Tier)
│   ├── *.html               9 หน้า
│   ├── css/style.css        ธีมแบบ B
│   ├── js/app.js            API client + helpers
│   ├── nginx.conf           เสิร์ฟ static + proxy /api ไป backend
│   └── Dockerfile
├── k8s/
│   ├── base/                Deployment, Service, HPA, PDB, NetworkPolicy (ใช้ร่วมกัน)
│   └── overlays/            local (minikube) · staging · production (GKE)
├── scripts/                 gcp-setup.sh · deploy.sh · gcp-teardown.sh · loadtest.sh · demo-zero-downtime.sh
├── loadtest/boardkub.js     k6 load test
├── monitoring/dashboard.json Cloud Monitoring dashboard
├── docs/GCP_DEPLOY.md       คู่มือ deploy บน Google Cloud ทีละขั้น
├── docs/DEMO.md             ขั้นตอนสาธิตตอนนำเสนอ (ผูกกับเกณฑ์คะแนน)
├── .github/workflows/       CI/CD → Artifact Registry + GKE (staging → production)
└── docker-compose.yml       รันทั้งระบบในเครื่องด้วยคำสั่งเดียว
```

## รันเร็วที่สุด

```bash
docker compose up --build
```

- เว็บ: http://localhost:8081
- API: http://localhost:8080/api
- Health: http://localhost:8080/actuator/health

ครั้งแรกระบบจะสร้างหมวดหมู่ บอร์ดตัวอย่าง และบัญชี Admin ให้อัตโนมัติ

**Admin เริ่มต้น:** `admin@kmitl.ac.th` / `Admin@12345`
(เปลี่ยนผ่าน env `ADMIN_EMAIL` และ `ADMIN_PASSWORD`)

### OTP ตอนพัฒนา

ค่าเริ่มต้น `OTP_DEV_MODE=true` จะ **พิมพ์รหัส OTP ลง log แทนการส่งอีเมลจริง**:

```bash
docker compose logs backend | grep "DEV OTP"
```

ถ้าต้องการส่งอีเมลจริง ตั้ง `OTP_DEV_MODE=false` แล้วกรอก `MAIL_USERNAME` / `MAIL_PASSWORD`
(ถ้าใช้ Gmail ต้องใช้ App Password ไม่ใช่รหัสผ่านปกติ)

## Deploy บน Minikube

```bash
minikube start
minikube addons enable ingress
eval $(minikube docker-env)

docker build -t boardkub-backend:local  ./backend
docker build -t boardkub-frontend:local ./frontend

kubectl apply -k k8s/overlays/local
kubectl -n boardkub-local get pods -w

echo "$(minikube ip) boardkub.local" | sudo tee -a /etc/hosts
# เปิด http://boardkub.local
```

### พิสูจน์ว่า Session ทำงานข้าม Pod ได้จริง (จุดสำคัญของโปรเจกต์)

ทุกหน้ามีแถบล่างบอกว่า **Pod ไหนให้บริการ request นี้**

```bash
# 1. Scale backend เป็น 4 Pod
kubectl -n boardkub-local scale deployment/backend --replicas=4

# 2. เข้าสู่ระบบผ่านเว็บ แล้วกด refresh ซ้ำๆ
#    ชื่อ Pod ที่แถบล่างจะเปลี่ยนไปเรื่อยๆ แต่ยังล็อกอินอยู่
#    → Session อยู่ที่ Redis ไม่ใช่ในหน่วยความจำของ Pod

# 3. ดูฝั่งตรงข้าม: ถ้าถอด Spring Session ออก ผู้ใช้จะหลุดทันทีที่สลับ Pod
```

## Deploy บน Google Cloud (GKE)

ดูคู่มือเต็มที่ [`docs/GCP_DEPLOY.md`](docs/GCP_DEPLOY.md) สรุปสั้นๆ:

1. `PROJECT_ID=... GITHUB_REPO=owner/repo ./scripts/gcp-setup.sh` สร้าง VPC, Cloud NAT, Cloud SQL, Memorystore, Artifact Registry, GKE Autopilot, Cloud Armor, Workload Identity Federation
2. ใส่ค่าที่สคริปต์พิมพ์ออกมาลงใน GitHub (repository variables + environments `staging` / `production`)
3. push `develop` → deploy staging + smoke test, push `main` → ต่อไปที่ production
4. ไม่ใช้แล้ว `./scripts/gcp-teardown.sh`

ไม่มีรหัสผ่านจริงอยู่ใน Git: CI สร้าง Secret `backend-secret` และ ConfigMap `backend-infra` จาก GitHub environment ทุกครั้งที่ deploy
และเข้าถึง Google Cloud ด้วย Workload Identity Federation (ไม่มีไฟล์ key)

## API Endpoints

| Method | Path | สิทธิ์ | คำอธิบาย |
|---|---|---|---|
| POST | `/api/auth/register` | – | สมัครสมาชิก (ส่ง OTP) |
| POST | `/api/auth/verify` | – | ยืนยัน OTP |
| POST | `/api/auth/resend-otp` | – | ขอ OTP ใหม่ |
| POST | `/api/auth/login` | – | เข้าสู่ระบบ |
| POST | `/api/auth/logout` | – | ออกจากระบบ |
| GET | `/api/auth/me` | login | ข้อมูลผู้ใช้ปัจจุบัน |
| GET | `/api/auth/whoami` | – | บอกว่า Pod ไหนตอบ (ใช้สาธิต scaling) |
| GET | `/api/categories` | – | รายการหมวดหมู่ |
| GET | `/api/boards?categoryId=` | – | รายการบอร์ด |
| GET | `/api/boards/{id}/posts` | – | กระทู้ในบอร์ด (paginated, sort=latest\|views) |
| GET | `/api/posts/search?q=` | – | ค้นหากระทู้จากหัวข้อ |
| GET | `/api/posts/{id}` | – | รายละเอียดกระทู้ |
| POST | `/api/boards/{id}/posts` | login | ตั้งกระทู้ |
| PUT / DELETE | `/api/posts/{id}` | เจ้าของ/admin | แก้ไข / ลบกระทู้ (ลบรูปใน Object Storage ด้วย) |
| GET | `/api/posts/{id}/images` | – | รูปประกอบกระทู้ |
| POST | `/api/posts/{id}/images` | เจ้าของ/admin | อัปโหลดรูป (multipart `file`, JPG/PNG ≤ 5 MB, สูงสุด 4 รูป) |
| DELETE | `/api/images/{id}` | เจ้าของ/admin | ลบรูป |
| GET | `/api/media/{name}` | – | ส่งไฟล์รูป (เฉพาะตอนรันในเครื่อง; บน GKE เบราว์เซอร์โหลดจาก Cloud Storage โดยตรง) |
| GET | `/api/posts/{id}/comments` | – | ความคิดเห็น |
| POST | `/api/posts/{id}/comments` | login | เพิ่มความคิดเห็น |
| PUT / DELETE | `/api/comments/{id}` | เจ้าของ/admin | แก้ไข / ลบความคิดเห็น |
| POST / DELETE | `/api/admin/categories`, `/api/admin/boards` | admin | จัดการหมวดหมู่และบอร์ด |

## ความปลอดภัยที่ทำไว้แล้ว

- รหัสผ่านเข้ารหัสด้วย BCrypt (Spring Security Crypto)
- OTP เก็บเฉพาะค่าแฮช มีอายุจำกัด ใช้ได้ครั้งเดียว
- Session ใหม่หลังล็อกอินสำเร็จ (กัน Session Fixation), Cookie เป็น HttpOnly + SameSite
- ตรวจสอบความเป็นเจ้าของก่อนแก้ไข/ลบ แยกสิทธิ์ ADMIN จาก STUDENT
- Rate limit ต่อบัญชี (counter ใน Redis ใช้ร่วมทุก Pod): ส่ง OTP 3 ครั้ง/15 นาที, ใส่ OTP ผิด 5 ครั้ง/15 นาที, รหัสผ่านผิด 5 ครั้ง/15 นาที, ตั้งกระทู้ 5 ครั้ง/10 นาที, ความคิดเห็น 20 ครั้ง/10 นาที → ตอบ 429 + `Retry-After` · ส่วนการจำกัดตาม IP ทำที่ Cloud Armor
- Log ไม่มีอีเมลเต็ม (mask เป็น `so****@kmitl.ac.th`)
- รูปที่อัปโหลด: ตรวจชนิดจากเนื้อไฟล์จริง จำกัดขนาด วาดใหม่จาก pixel (ลบ EXIF/GPS) ตั้งชื่อเป็น UUID สุ่ม
- backend เขียน Cloud Storage ด้วย Workload Identity ไม่มีไฟล์ key และมีสิทธิ์เฉพาะ bucket รูป
- Escape ทุกข้อความก่อนใส่ DOM (กัน XSS), ใช้ JPA parameterized query (กัน SQL Injection)
- Container รันด้วย non-root, read-only root filesystem, drop capabilities ทั้งหมด
- NetworkPolicy: backend รับเฉพาะจาก frontend, smoke test และ Google Load Balancer
- Cloud SQL / Memorystore ไม่มี public IP, GKE node เป็น private, Cloud Armor กัน SQLi/XSS และจำกัดอัตรา `/api/auth/*`
- `/actuator` ไม่ถูกเปิดสู่ภายนอก (Ingress ส่งเฉพาะ `/api` ไป backend)

## สิ่งที่ต้องทำก่อนส่งงานจริง

- `k8s/overlays/local` มีรหัสผ่านตัวอย่างสำหรับเครื่องตัวเองเท่านั้น — **อย่า commit ค่าจริงขึ้น Git**
- เปลี่ยน `JPA_DDL_AUTO` จาก `update` เป็น `validate` บน production แล้วใช้ Flyway จัดการ schema
