#!/usr/bin/env python3
"""
Generates demo-data.sql: fictional students, posts and comments so the site looks
busy during a demo. Every demo account uses the e-mail domain @demo.boardkub.local,
so seed-demo-data.sh can find and remove them again.

Re-run to regenerate:  python3 scripts/demo-data/generate.py
Output is deterministic (fixed random seed), so the same SQL comes out every time.
"""
import random
from pathlib import Path

random.seed(20261112)

USERS = 90
POSTS = 420
DOMAIN = "demo.boardkub.local"
# BCrypt of "Student@123", the same password SampleDataSeeder uses
PASSWORD_HASH = "$2a$10$RlOcmvwHUG166DNrSnUjnumkwACwktsaPIq0.5eDZI9kHa58PB7M6"

NICK = ["ต้นข้าว", "มายด์", "ภูมิ", "แพรว", "บอส", "ปาล์ม", "ฟ้า", "เจ", "มิ้นท์", "ไอซ์",
        "ก้อง", "เฟิร์น", "ตั้ม", "พลอย", "นัท", "เบนซ์", "ข้าวหอม", "กัปตัน", "แบม", "โอ๊ต",
        "ปลื้ม", "เกม", "ใบเตย", "ซัน", "นิว", "อิ๊งค์", "ภีม", "น้ำหวาน", "ฟิล์ม", "แทน"]
TAG = ["ปี 1", "ปี 2", "ปี 3", "ปี 4", "วิศวะคอม", "วิทยาการคอมฯ", "ไอที", "สถาปัตย์",
       "วิศวะไฟฟ้า", "วิทยาศาสตร์", "บริหาร", "ครุศาสตร์", "ศิลปศาสตร์", "อุตสาหกรรมเกษตร"]

SUBJECTS = ["Calculus 1", "Physics 1", "Data Structures", "OOP", "Computer Networks",
            "Database Systems", "Discrete Math", "Operating Systems", "Linear Algebra",
            "Probability & Statistics", "Software Engineering", "Computer Architecture",
            "Cloud Computing", "Web Programming", "Machine Learning", "English for Engineers"]
TOPICS = {
    "Calculus 1": ["ลิมิต", "อนุพันธ์ฟังก์ชันแฝง", "อินทิเกรตทีละส่วน", "Related rates"],
    "Physics 1": ["โมเมนตัม", "งานและพลังงาน", "การหมุน", "การเคลื่อนที่แบบโพรเจกไทล์"],
    "Data Structures": ["AVL tree", "Heap", "Hash table", "Graph traversal"],
    "OOP": ["Polymorphism", "Interface กับ Abstract class", "Design pattern", "Exception"],
    "Computer Networks": ["Subnetting", "TCP handshake", "Routing", "ACL"],
    "Database Systems": ["Normalization", "JOIN", "Index", "Transaction"],
    "Discrete Math": ["Induction", "Graph", "Combinatorics", "Relation"],
    "Operating Systems": ["Deadlock", "Scheduling", "Paging", "Semaphore"],
    "Linear Algebra": ["Eigenvalue", "Determinant", "Vector space", "Gaussian elimination"],
    "Probability & Statistics": ["Bayes", "การแจกแจงปกติ", "Hypothesis testing", "Confidence interval"],
    "Software Engineering": ["Use case", "Sequence diagram", "Agile", "Unit test"],
    "Computer Architecture": ["Pipeline", "Cache", "Assembly", "Floating point"],
    "Cloud Computing": ["IaaS/PaaS/SaaS", "Kubernetes", "Auto scaling", "Load balancer"],
    "Web Programming": ["REST API", "CORS", "Session กับ JWT", "React state"],
    "Machine Learning": ["Overfitting", "Gradient descent", "Confusion matrix", "CNN"],
    "English for Engineers": ["Presentation", "Report writing", "TOEIC", "Email"],
}
FOODS = ["ข้าวมันไก่", "ก๋วยเตี๋ยวเรือ", "ข้าวกะเพรา", "ส้มตำ", "ชาไข่มุก", "ข้าวขาหมู",
         "หมูกระทะ", "ราเมง", "สุกี้", "ข้าวผัดปู", "ไก่ทอด", "บิงซู", "ข้าวซอย", "โจ๊ก"]
PLACES = ["โรงอาหารกลาง", "ตึก ECC", "ห้องสมุด", "หอใน", "ลานจอดรถ", "ตึกโหล",
          "สนามกีฬา", "ศูนย์เรียนรวม", "ป้ายรถตู้", "หน้าคณะ", "ร้านถ่ายเอกสาร"]
ITEMS = ["บัตรนักศึกษา", "กระเป๋าตังค์", "หูฟังไร้สาย", "ร่มสีดำ", "กุญแจรถ",
         "เครื่องคิดเลข", "ขวดน้ำ", "สายชาร์จ", "แว่นตา", "เสื้อช็อป", "แฟลชไดรฟ์"]
CLUBS = ["ชมรมดนตรี", "ชมรมถ่ายภาพ", "ชมรมหุ่นยนต์", "ชมรมบาสเกตบอล", "ชมรมอาสา",
         "ชมรมเกมกระดาน", "ชมรมการ์ตูน", "ชมรมวิ่ง", "ชมรมภาษาญี่ปุ่น", "ชมรม E-sport"]
EVENTS = ["งานรับน้อง", "งานกีฬาสี", "ค่ายอาสา", "งาน Open House", "ค่าย Hackathon",
          "งานลอยกระทง", "งานบายเนียร์", "สัมมนาเตรียมฝึกงาน", "งาน Job Fair"]


def pick(xs):
    return random.choice(xs)


def posts_for(board):
    """Returns (title, body, comment pool) for one post in the given board."""
    s = pick(SUBJECTS)
    t = pick(TOPICS[s])
    f, p, it, c, e = pick(FOODS), pick(PLACES), pick(ITEMS), pick(CLUBS), pick(EVENTS)
    gpa = pick(["2.50", "2.75", "3.00", "3.25"])
    k = random.randint(1, 4)
    T = {
        "ทุน ก.": [
            (f"ทุน ก. ปีนี้ใช้เกรดขั้นต่ำเท่าไหร่ครับ",
             f"ได้ยินมาว่าปีนี้ขยับเกรดขั้นต่ำเป็น {gpa} จริงไหมครับ ใครมีประกาศล่าสุดช่วยแปะหน่อย",
             ["ในประกาศล่าสุดยังเท่าเดิมนะ", "ถามที่ฝ่ายกิจการนักศึกษาจะชัวร์สุดครับ", "ขอบคุณครับ ช่วยได้มาก"]),
            (f"ส่งเอกสารทุน ก. ทางออนไลน์ได้ไหม",
             "ช่วงนี้ไม่ได้อยู่หอ อยากรู้ว่าส่งเอกสารเป็นไฟล์ได้ไหม หรือต้องไปยื่นเองเท่านั้น",
             ["รอบที่แล้วส่งออนไลน์ได้ แต่ต้องไปเซ็นตัวจริงทีหลัง", "สแกนเป็น PDF ไฟล์เดียวนะ ไม่งั้นโดนตีกลับ"]),
            (f"สัมภาษณ์ทุน ก. ถามอะไรบ้าง",
             "พรุ่งนี้มีสัมภาษณ์ทุน ก. ตื่นเต้นมาก ใครเคยไปแล้วช่วยเล่าหน่อย",
             ["ถามเรื่องครอบครัวกับรายจ่ายต่อเดือนเป็นหลัก", "ตอบตามจริง ไม่ต้องเกร็ง", "สู้ ๆ นะ"]),
        ],
        "ทุน ข.": [
            (f"ทุน ข. โอนเงินงวด {k} แล้วหรือยัง",
             f"ของเรายังไม่เข้าเลย ของใครเข้าแล้วบ้าง",
             ["ของเราเข้าเมื่อวานตอนบ่าย", "ยังเหมือนกัน รออยู่", "ปกติดีเลย์ 2-3 วันทำการ"]),
            ("ทุน ข. ต้องทำชั่วโมงจิตอาสากี่ชั่วโมง",
             "ในสัญญาเขียนว่าต้องมีชั่วโมงจิตอาสา แต่ไม่ได้บอกจำนวน ใครทราบบ้าง",
             ["ปีละ 20 ชั่วโมง เก็บจากกิจกรรมคณะได้", f"ไปช่วย{e}ได้ชั่วโมงเยอะดี"]),
        ],
        "ทุนภายนอก": [
            ("แชร์ประสบการณ์สมัครทุนบริษัทสาย IT",
             "เพิ่งผ่านรอบสัมภาษณ์ทุนบริษัทมา มีทั้งสอบเขียนโปรแกรมและสัมภาษณ์ภาษาอังกฤษ ใครสนใจถามได้",
             ["สอบเขียนโปรแกรมใช้ภาษาอะไรครับ", "ยินดีด้วยนะ", "มีฝึกงานต่อด้วยไหม"]),
            (f"ทุนเรียนดีจากมูลนิธิ ต้องใช้จดหมายรับรองกี่ฉบับ",
             "อาจารย์ที่ปรึกษาเซ็นให้ฉบับนึงแล้ว ต้องมีอีกไหม",
             ["ต้องมีสองฉบับ ฉบับที่สองให้อาจารย์ประจำวิชาก็ได้", "ฝากอาจารย์ล่วงหน้าเป็นอาทิตย์นะ"]),
        ],
        "ปรึกษาปัญหาการเรียน": [
            (f"{s}: เรื่อง {t} งงมาก",
             f"อ่านชีทเรื่อง {t} แล้วยังไม่เข้าใจ มีใครอธิบายแบบง่าย ๆ หรือมีคลิปแนะนำบ้างไหม",
             [f"ลองดูคลิปที่อาจารย์อัปไว้ในคลาสรูม ตอนท้ายอธิบาย {t} ดีมาก",
              "ลองทำโจทย์ท้ายบทข้อ 1-5 ก่อน จะเริ่มเห็นภาพ", "เหมือนกันเลย ช่วยด้วย", "ไปถามในคาบ office hour ได้นะ"]),
            (f"การบ้าน {s} ข้อ {random.randint(2, 12)} ใครทำได้แล้วบ้าง",
             f"ติดตรง {t} ไม่รู้จะเริ่มยังไง ไม่ต้องให้คำตอบ ขอแค่ใบ้ทางหน่อย",
             ["เริ่มจากเขียนสิ่งที่โจทย์ให้มาก่อน", f"ทวนเรื่อง {t} ในสไลด์บทที่ {k} ก่อน", "ได้แล้ว ขอบคุณมาก"]),
            (f"ถอน {s} ทันไหม เกรดกลางภาคแย่มาก",
             "คะแนนกลางภาคได้ต่ำกว่าค่าเฉลี่ยเยอะ ลังเลว่าจะถอนหรือสู้ต่อ",
             ["ดูน้ำหนักคะแนนปลายภาคก่อน บางทียังพลิกได้", "ปรึกษาอาจารย์ที่ปรึกษาก่อนถอนนะ", "สู้ต่อ! เราก็เคยรอดมาแล้ว"]),
        ],
        "ติวและแชร์ชีทสรุป": [
            (f"แจกชีทสรุป {s} บทที่ {k}-{k + 2}",
             f"สรุปจากสไลด์กับที่อาจารย์พูดในคาบ เน้นเรื่อง {t} ใครเจอจุดผิดบอกได้เลย",
             ["ขอบคุณมาก ๆ ช่วยชีวิตเลย", "สรุปละเอียดมาก", f"มีของบทที่ {k + 3} ไหม", "เซฟเรียบร้อย"]),
            (f"นัดติว {s} ก่อนสอบ ที่{p}",
             f"จะติวกันเรื่อง {t} กับทำข้อสอบเก่า ใครจะมาคอมเมนต์ไว้ จะได้จองที่ถูก",
             ["ไป 1 คน", "ไปด้วย ๆ", "ขอตามมาทีหลังได้ไหม เลิกแล็บช้า", "เอาข้อสอบเก่าไปด้วยนะ"]),
        ],
        "แนะแนวการลงทะเบียน": [
            (f"ลง {s} กับวิชาเลือกอีก 2 ตัวในเทอมเดียว หนักไปไหม",
             "แผนการเรียนเทอมหน้าแน่นมาก อยากรู้ว่าคนที่เคยลงพร้อมกันรอดไหม",
             ["หนักแต่รอด ถ้าแบ่งเวลาดี", "แนะนำเลื่อนวิชาเลือกไปเทอมฤดูร้อน", "ดูวันสอบชนกันด้วยนะ"]),
            ("ระบบลงทะเบียนเปิดกี่โมง",
             "จะได้ตั้งนาฬิกาปลุก กลัววิชายอดฮิตเต็ม",
             ["เปิด 9 โมงตรง เข้าก่อน 5 นาที", "เตรียมรหัสวิชาไว้ก่อนเลย", "ปีที่แล้วเว็บล่มช่วงแรก ใจเย็นนะ"]),
            ("วิชาเลือกเสรีตัวไหนได้ความรู้แต่ไม่หนัก",
             "เหลือ 3 หน่วยกิต อยากได้วิชาที่ใช้ได้จริง",
             ["การเงินส่วนบุคคลดีมาก", "ภาษาญี่ปุ่นเบื้องต้นสนุก", "จิตวิทยาเบื้องต้นงานไม่เยอะ"]),
        ],
        "พูดคุยทั่วไป": [
            (f"อ่านหนังสือที่{p}ตอนดึกได้ไหม",
             "ห้องสมุดปิดเร็ว อยากได้ที่นั่งอ่านหลังสองทุ่ม",
             ["ศูนย์เรียนรวมเปิดถึงเที่ยงคืนช่วงสอบ", "หอในมีห้องอ่านหนังสือรวม", "ร้านกาแฟหน้ามอเปิดดึก"]),
            ("ฝนตกทุกเย็นเลย ใครมีทางเดินไม่เปียกแนะนำบ้าง",
             f"เดินจาก{p}ไปตึกเรียนเปียกทุกวัน",
             ["เดินใต้ทางเชื่อมตึกได้เกือบตลอด", "พกร่มพับไว้ในกระเป๋าเลย", "รอรถรางดีกว่า"]),
            ("ช่วงสอบนอนกันกี่ชั่วโมง",
             "ตอนนี้เหลือวันละ 4 ชั่วโมง เริ่มไม่ไหว",
             ["นอนให้พอเถอะ ความจำดีกว่าเยอะ", "เหมือนกัน 555", "ลองงีบ 20 นาทีตอนบ่าย"]),
            ("แนะนำ Notebook สำหรับเด็กวิศวะงบไม่เกิน 30k",
             "ต้องใช้รันโปรแกรมเขียนโค้ดกับ Docker ด้วย",
             ["RAM 16GB ขึ้นไปนะถ้าจะใช้ Docker", "ดูรุ่นที่จอสว่าง ๆ ด้วย", "ลองดูช่วงลดราคาปลายปี"]),
        ],
        "รีวิวร้านอาหาร": [
            (f"รีวิว{f}ใกล้{p} ราคานักศึกษา",
             f"ไปกินมาเมื่อวาน จานละ {random.choice([40, 45, 50, 55, 60])} บาท รสชาติดี ช่วงเที่ยงคิวยาว แนะนำไปก่อน 11 โมง\n\nให้ {random.randint(3, 5)}/5",
             ["ขอพิกัดหน่อย", "ร้านนี้อร่อยจริง", "ลองแล้ว เค็มไปนิด", "พรุ่งนี้ไปลองบ้าง"]),
            (f"{f}ร้านไหนอร่อยสุดแถวมอ",
             "เพิ่งย้ายมาอยู่หอ ยังไม่รู้จักร้านเลย",
             [f"ร้านใกล้{pick(PLACES)}ดีมาก", "ซอยหลังมอมีหลายร้าน", "ลองทุกร้านแล้วมารีวิวด้วยนะ"]),
        ],
        "ของหาย-ของเจอ": [
            (f"[เจอของ] เก็บ{it}ได้ที่{p}",
             f"เก็บ{it}ได้ช่วงบ่ายวันนี้ ฝากไว้ที่จุดประชาสัมพันธ์แล้ว เจ้าของไปรับได้เลย",
             ["ขอบคุณที่ช่วยนะคะ", "แชร์ต่อให้แล้ว", "ใจดีมาก"]),
            (f"[ตามหา] {it}หายแถว{p}",
             f"ทำ{it}หายเมื่อวานช่วงเย็น ใครเจอติดต่อในกระทู้นี้ได้เลย ขอบคุณมาก",
             ["ลองถามแม่บ้านประจำตึกดู", "แชร์ให้แล้วนะ", "เจอแล้วหรือยัง"]),
        ],
        "ชมรม": [
            (f"{c}เปิดรับสมาชิกใหม่",
             f"{c}เปิดรับสมาชิกใหม่ ไม่ต้องมีพื้นฐานก็มาได้ เจอกันทุกวันพุธหลังเลิกเรียนที่{p}",
             ["สนใจค่ะ ต้องเตรียมอะไรไหม", "ปีที่แล้วสนุกมาก", "ไปด้วยคน"]),
            (f"{c}มีกิจกรรมอะไรบ้างทั้งปี",
             "อยากรู้ก่อนสมัครว่าใช้เวลาเยอะแค่ไหน",
             ["ส่วนใหญ่สัปดาห์ละครั้ง", f"มีจัดงานใหญ่ช่วง{pick(EVENTS)}", "ไม่บังคับมาทุกครั้ง"]),
        ],
        "ประกาศกิจกรรม": [
            (f"[ประกาศ] รับสมัครสตาฟ{e}",
             f"เปิดรับสมัครสตาฟ{e} ได้ชั่วโมงกิจกรรมตามระเบียบ สมัครโดยคอมเมนต์ชื่อ ชั้นปี และฝ่ายที่สนใจ",
             ["สนใจฝ่ายประชาสัมพันธ์ค่ะ", "ฝ่ายสถานที่ครับ", "ปี 1 สมัครได้ไหม", "ฝ่ายพยาบาลค่ะ"]),
            (f"{e} ปีนี้จัดที่ไหน",
             "ยังไม่เห็นประกาศสถานที่เลย",
             [f"น่าจะที่{p}เหมือนปีที่แล้ว", "รอเพจคณะประกาศ", "ปีนี้ขยายงานด้วย"]),
        ],
    }
    return pick(T[board])


BOARDS = ["ทุน ก.", "ทุน ข.", "ทุนภายนอก", "ปรึกษาปัญหาการเรียน", "ติวและแชร์ชีทสรุป",
          "แนะแนวการลงทะเบียน", "พูดคุยทั่วไป", "รีวิวร้านอาหาร", "ของหาย-ของเจอ",
          "ชมรม", "ประกาศกิจกรรม"]
# Busier boards get more posts
WEIGHTS = [4, 3, 3, 10, 8, 5, 10, 8, 5, 4, 4]
GENERIC = ["+1", "ติดตามครับ", "ขอบคุณที่แชร์", "อยากรู้เหมือนกัน", "เห็นด้วย", "ดันกระทู้"]


def q(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def email(i):
    return f"demo{i:03d}@{DOMAIN}"


def uid(i):
    return f"(SELECT id FROM users WHERE email={q(email(i))})"


out = ["-- Generated by scripts/demo-data/generate.py. Fictional demo data only.",
       "SET NAMES utf8mb4;",
       "START TRANSACTION;",
       # Timestamps are relative to the newest user row, so they use the same
       # time convention the application already writes in.
       "SET @now = (SELECT MAX(created_at) FROM users);",
       # Announcements come from a demo "student union" account, not the real admin,
       # so --remove can take every demo row away again.
       "INSERT INTO users (email, password_hash, display_name, role, is_verified, created_at) "
       f"VALUES ({q(email(0))}, {q(PASSWORD_HASH)}, 'สโมสรนักศึกษา', 'STUDENT', b'1', @now - INTERVAL 210 DAY);"]

for i in range(1, USERS + 1):
    name = f"{pick(NICK)} {pick(TAG)}"
    days = random.randint(100, 200)
    out.append("INSERT INTO users (email, password_hash, display_name, role, is_verified, created_at) "
               f"VALUES ({q(email(i))}, {q(PASSWORD_HASH)}, {q(name)}, 'STUDENT', b'1', "
               f"@now - INTERVAL {days} DAY);")

n_comments = 0
for _ in range(POSTS):
    board = random.choices(BOARDS, WEIGHTS)[0]
    title, body, pool = posts_for(board)
    hours = int(random.triangular(1, 120 * 24, 1))      # more recent posts than old ones
    views = min(int(random.paretovariate(1.3) * 25) + random.randint(0, 20), 4800)
    author = uid(0) if title.startswith("[ประกาศ]") else uid(random.randint(1, USERS))
    out.append("INSERT INTO posts (board_id, user_id, title, content, view_count, created_at) VALUES ("
               f"(SELECT id FROM boards WHERE name={q(board)} LIMIT 1), {author}, {q(title)}, {q(body)}, "
               f"{views}, @now - INTERVAL {hours} HOUR);")
    out.append("SET @p = LAST_INSERT_ID();")
    n = min(int(random.expovariate(1 / 4)), 18)
    minutes = 0
    for _ in range(n):
        minutes += random.randint(3, 600)
        if minutes > hours * 60:          # no comments from the future
            break
        text = pick(pool) if random.random() < 0.75 else pick(GENERIC)
        out.append("INSERT INTO comments (post_id, user_id, content, created_at) VALUES ("
                   f"@p, {uid(random.randint(1, USERS))}, {q(text)}, "
                   f"@now - INTERVAL {hours * 60 - minutes} MINUTE);")
        n_comments += 1

out.append("COMMIT;")
out.append(f"SELECT '{USERS + 1} users, {POSTS} posts, {n_comments} comments added' AS result;")
Path(__file__).with_name("demo-data.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
print(f"demo-data.sql: {USERS + 1} users, {POSTS} posts, {n_comments} comments")
