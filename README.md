# Boardkub

เว็บบอร์ดประกาศและปรึกษาสำหรับนักศึกษา จัดกลุ่มตามหมวดหมู่
Architecture Pattern **Three-tier** deploy บน **GKE Autopilot**

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

### พิสูจน์ว่า Session ทำงานข้าม Pod ได้จริง

ทุกหน้ามีแถบล่างบอกว่า **Pod ไหนให้บริการ request นี้**

```bash
# 1. Scale backend เป็น 4 Pod
kubectl -n boardkub-local scale deployment/backend --replicas=4

# 2. เข้าสู่ระบบผ่านเว็บ แล้วกด refresh ซ้ำๆ
#    ชื่อ Pod ที่แถบล่างจะเปลี่ยนไปเรื่อยๆ แต่ยังล็อกอินอยู่
#    → Session อยู่ที่ Redis ไม่ใช่ในหน่วยความจำของ Pod

# 3. ดูฝั่งตรงข้าม: ถ้าถอด Spring Session ออก ผู้ใช้จะหลุดทันทีที่สลับ Pod
```

## API Endpoints

| Method | Path | สิทธิ์ | คำอธิบาย |
|---|---|---|---|
| POST | `/api/auth/register` | – | สมัครสมาชิก (ส่ง OTP) |
| POST | `/api/auth/verify` | – | ยืนยัน OTP |
| POST | `/api/auth/resend-otp` | – | ขอ OTP ใหม่ |
| POST | `/api/auth/login` | – | เข้าสู่ระบบ |
| POST | `/api/auth/logout` | – | ออกจากระบบ |
| GET | `/api/auth/me` | login | ข้อมูลผู้ใช้ปัจจุบัน |
| GET | `/api/auth/whoami` | แอดมิน | บอกว่า Pod ไหนตอบ (ใช้สาธิต scaling) |
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

- เปลี่ยน `JPA_DDL_AUTO` จาก `update` เป็น `validate` บน production แล้วใช้ Flyway จัดการ schema

## เมลทดสอบ
- admin: admin@boardkub.local
- password: password123
- user: demo001@demo.boardkub.local
- password: Student@123

