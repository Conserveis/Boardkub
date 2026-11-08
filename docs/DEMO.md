# ขั้นตอนสาธิต BoardKub (ใช้ตอนนำเสนอ)

ใช้ได้หลัง deploy ขึ้น GKE แล้ว (ดู `docs/GCP_DEPLOY.md`) ใช้เวลารวมประมาณ 15 นาที
แต่ละขั้นเขียนกำกับไว้ว่าตอบเกณฑ์คะแนนข้อไหน

## เตรียมก่อนขึ้นนำเสนอ (ทำล่วงหน้า 15 นาที)

```bash
export DOMAIN=boardkub.example.com          # โดเมนจริงของคุณ
gcloud container clusters get-credentials boardkub --region asia-southeast1
kubectl -n boardkub-production get pods,hpa  # ทุก Pod ต้อง Running และ HPA แสดง 2 Pod
```

เปิดไว้ 4 หน้าต่าง:

| หน้าต่าง | เปิดอะไร |
|---|---|
| เบราว์เซอร์แท็บ 1 | `https://$DOMAIN` (ล็อกอินไว้แล้ว) |
| เบราว์เซอร์แท็บ 2 | Cloud Console → Monitoring → Dashboards → **BoardKub — Production** (ตั้งช่วงเวลา 1 ชั่วโมงล่าสุด) |
| Terminal A | `kubectl -n boardkub-production get hpa backend -w` |
| Terminal B | ไว้รันคำสั่งสาธิต |

> **เคล็ดลับ:** load test แบบ ramp ใช้เวลา 10 นาที ให้เริ่มรันตั้งแต่ตอนเริ่มเล่าสถาปัตยกรรม
> พอถึงช่วงสาธิต HPA จำนวน Pod ก็เพิ่มขึ้นให้เห็นพอดี

---

## 1. ใช้งานจริงบน cloud ผ่านทุกบริการ — เกณฑ์ข้อ 3

1. เปิด `https://$DOMAIN` แล้วชี้ให้ดูรูปกุญแจ (HTTPS จาก Google-managed certificate)
2. ตั้งกระทู้ใหม่พร้อมแนบรูป 2 รูป
3. ในหน้ากระทู้ คลิกขวาที่รูป → Open image in new tab แล้วชี้ให้ดู URL
   `https://storage.googleapis.com/<bucket>/production/....jpg`
   ซึ่งแสดงว่ารูปมาจาก **Object Storage** โดยตรง ไม่ได้ผ่าน Pod
4. แถบล่างของเว็บแสดงชื่อ Pod ที่ตอบ request นี้อยู่

**สิ่งที่พูด:** ข้อความเก็บใน Cloud SQL, session เก็บใน Memorystore, รูปเก็บใน Cloud Storage
แต่ละบริการตอบโจทย์ข้อมูลคนละชนิด

## 2. Stateless — เกณฑ์ข้อ 3

```bash
kubectl -n boardkub-production delete pod -l app=backend --wait=false
kubectl -n boardkub-production get pods -l app=backend -w     # Ctrl+C เมื่อขึ้น Running ครบ
```

กลับไปกด refresh ที่เว็บ แล้วชี้ให้ดูว่า:
- **ยังล็อกอินอยู่** เพราะ session อยู่ใน Redis ไม่ได้อยู่ใน Pod
- **รูปยังอยู่ครบ** เพราะรูปอยู่ใน Cloud Storage
- **ชื่อ Pod ที่แถบล่างเปลี่ยนไปแล้ว** แปลว่าเป็น Pod ใหม่ทั้งหมด

กราฟ **Container restarts** และ **จำนวน Backend Pod** ใน dashboard จะขยับให้เห็นด้วย (self-healing)

## 3. Auto-scaling (HPA) — เกณฑ์ข้อ 2

```bash
./scripts/loadtest.sh $DOMAIN          # จำลองนักศึกษา 150 คนเข้าเว็บพร้อมกัน ~10 นาที
```

ชี้ให้ดูตามลำดับ:
1. Terminal A: `TARGETS` เกิน 70% แล้ว `REPLICAS` เพิ่มจาก 2 ไปถึง 5
2. Dashboard: กราฟ **CPU ต่อ Backend Pod** ข้ามเส้น 70% → กราฟ **จำนวน Backend Pod** เพิ่ม → CPU ต่อ Pod ลดลง
3. กราฟ **Latency p95** ไม่พุ่ง และกราฟ **5xx** เป็นศูนย์
4. ตอนจบ k6 จะแสดง `PASSED` พร้อมสรุปเวลาตอบสนอง

**สิ่งที่พูด:** HPA เพิ่ม Pod, Autopilot เพิ่ม Node ให้เอง และหลังโหลดลดลงประมาณ 5 นาที Pod จะลดกลับเหลือ 2
ระบบจ่ายเงินเฉพาะตอนที่ต้องใช้ ส่วนคอขวดถัดไปคือ Cloud SQL ซึ่งดูได้จากกราฟ CPU ของ Cloud SQL

## 4. Zero-downtime deployment — Bonus

```bash
./scripts/demo-zero-downtime.sh $DOMAIN
```

สคริปต์จะ:
1. ส่ง traffic คงที่ 30 ผู้ใช้เป็นเวลา 4 นาที
2. ผ่านไป 60 วินาที แทนที่ backend Pod **ทุกตัว** ด้วย rolling update
3. ให้ k6 **fail ทันทีถ้ามี request ผิดพลาดแม้แต่ครั้งเดียว**

ผลที่ต้องการคือ `PASSED` และ `http_req_failed: 0.00%`

**สิ่งที่พูด:** ไม่มีผู้ใช้คนไหนเห็น error ระหว่าง deploy เพราะ
- `maxUnavailable: 0`: เปิด Pod ใหม่ก่อนแล้วค่อยปิด Pod เก่า
- readiness probe: ส่ง traffic เข้า Pod ใหม่หลัง Spring Boot พร้อมแล้วเท่านั้น
- `preStop` + connection draining: Pod เก่าจะทำ request ที่ค้างอยู่จนเสร็จก่อนปิด

## 5. CI/CD — Bonus

1. แก้ข้อความเล็กๆ เช่น ข้อความในหน้าแรก แล้ว `git push` ขึ้น `main`
2. เปิด GitHub → Actions ให้ดู 8 ขั้น: build → test → scan → push → staging → smoke test → production
3. ถ้าตั้ง Required reviewers ไว้ ให้กดอนุมัติ production สดๆ
4. Refresh เว็บแล้วจะเห็นการเปลี่ยนแปลง โดยเว็บไม่ล่มระหว่าง deploy (เหตุผลเดียวกับข้อ 4)

## 6. ความปลอดภัย (ถ้ามีเวลาหรือโดนถาม) — เกณฑ์ข้อ 2

| สาธิต | คำสั่ง / วิธี | ผลที่เห็น |
|---|---|---|
| DB ไม่มี public IP | `gcloud sql instances describe boardkub-mysql --format='value(ipAddresses)'` | มีแต่ `PRIVATE` |
| เดา OTP | ใส่ OTP ผิด 6 ครั้ง | ครั้งที่ 6 ได้ "ทำรายการบ่อยเกินไป" (429) |
| เดารหัสผ่าน | ล็อกอินผิด 6 ครั้ง | ครั้งที่ 6 ถูกล็อก แม้จะใส่รหัสถูก |
| สแปมกระทู้ | ตั้งกระทู้ 6 ครั้งใน 10 นาที | ครั้งที่ 6 ถูกปฏิเสธ |
| ไม่มีรหัสผ่านใน Git | เปิด repo บน GitHub | มีแค่ชื่อ secret; ค่าจริงอยู่ใน GitHub Environments |
| รูปไม่มี GPS | อัปรูปจากมือถือ แล้วดาวน์โหลดรูปจากเว็บไปเช็ก EXIF | ไม่มีข้อมูลตำแหน่ง |

Rate limit มี 2 ชั้น: **Cloud Armor** จำกัดตาม IP ที่ขอบระบบ และ **แอป** จำกัดตามบัญชี (counter อยู่ใน Redis ใช้ร่วมกันทุก Pod)
จำกัดตามบัญชีด้วยเพราะนักศึกษาในหอเดียวกันออกเน็ตด้วย IP เดียวกัน ถ้าจำกัดแค่ตาม IP จะบล็อกคนทั้งหอ

## หลังนำเสนอ

```bash
kubectl delete namespace boardkub-loadtest       # ลบ Job ของ load test
./scripts/gcp-teardown.sh                        # ถ้าไม่ใช้แล้ว เพื่อหยุดค่าใช้จ่าย
```
