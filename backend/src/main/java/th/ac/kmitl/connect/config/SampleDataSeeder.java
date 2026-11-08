package th.ac.kmitl.connect.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.entity.Board;
import th.ac.kmitl.connect.entity.Comment;
import th.ac.kmitl.connect.entity.Post;
import th.ac.kmitl.connect.entity.Role;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.repository.BoardRepository;
import th.ac.kmitl.connect.repository.CommentRepository;
import th.ac.kmitl.connect.repository.PostRepository;
import th.ac.kmitl.connect.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills every board with one sample post and a few comments so that each page
 * of the site can be checked by eye. Off by default; enable it with
 * SEED_SAMPLE_POSTS=true. It runs only while the posts table is empty, so it
 * never duplicates data and never touches real posts.
 */
@Component
@Order(2)
public class SampleDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    /** Shared password for every sample student account. */
    private static final String SAMPLE_PASSWORD = "Student@123";

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-sample-posts:false}")
    private boolean enabled;

    @Value("${app.admin.email}")
    private String adminEmail;

    public SampleDataSeeder(BoardRepository boardRepository,
                            PostRepository postRepository,
                            CommentRepository commentRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** One comment: who wrote it, what it says, and how many minutes after the post. */
    private record C(String author, String text, long minutesAfter) {}

    /** One post: the board it goes in, author, title, body, age, views, comments. */
    private record P(String board, String author, String title, String body,
                     long hoursAgo, long views, List<C> comments) {}

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (postRepository.count() > 0) {
            log.info("Sample posts skipped: the posts table already has data");
            return;
        }

        Map<String, User> users = new HashMap<>();
        users.put("admin", userRepository.findByEmail(adminEmail).orElseThrow());
        users.put("somchai", student("somchai@kmitl.ac.th", "สมชาย ใจดี"));
        users.put("somying", student("somying@kmitl.ac.th", "สมหญิง รักเรียน"));
        users.put("senior", student("senior@kmitl.ac.th", "รุ่นพี่ปี 4"));
        users.put("fresh", student("fresh@kmitl.ac.th", "น้องปี 1"));

        Map<String, Board> boards = new HashMap<>();
        for (Board b : boardRepository.findByActiveTrueOrderByIdAsc()) {
            boards.put(b.getName(), b);
        }

        int posts = 0;
        int comments = 0;
        for (P p : samplePosts()) {
            Board board = boards.get(p.board());
            if (board == null) {
                log.warn("Sample post skipped: no board named '{}'", p.board());
                continue;
            }

            Post post = new Post();
            post.setBoard(board);
            post.setUser(users.get(p.author()));
            post.setTitle(p.title());
            post.setContent(p.body());
            post.setViewCount(p.views());
            post.setCreatedAt(Instant.now().minus(p.hoursAgo(), ChronoUnit.HOURS));
            postRepository.save(post);
            posts++;

            for (C c : p.comments()) {
                Comment comment = new Comment();
                comment.setPost(post);
                comment.setUser(users.get(c.author()));
                comment.setContent(c.text());
                comment.setCreatedAt(post.getCreatedAt().plus(c.minutesAfter(), ChronoUnit.MINUTES));
                commentRepository.save(comment);
                comments++;
            }
        }
        log.info("Seeded {} sample posts and {} comments (sample student password: {})",
                posts, comments, SAMPLE_PASSWORD);
    }

    private User student(String email, String displayName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(SAMPLE_PASSWORD));
            u.setDisplayName(displayName);
            u.setRole(Role.STUDENT);
            u.setVerified(true);
            return userRepository.save(u);
        });
    }

    private List<P> samplePosts() {
        return List.of(
                // ---------- ทุนการศึกษา ----------
                new P("ทุน ก.", "somchai",
                        "ทุน ก. ปีนี้ต้องใช้เอกสารอะไรบ้าง",
                        "สอบถามครับ ปีนี้ยื่นทุน ก. ต้องเตรียมเอกสารอะไรบ้าง\n"
                                + "เห็นในประกาศเขียนไว้กว้าง ๆ เลยอยากถามคนที่เคยยื่นแล้วครับ\n\n"
                                + "ตอนนี้ผมเตรียมสำเนาบัตรประชาชนกับทะเบียนบ้านไว้แล้ว ขาดอะไรอีกไหมครับ",
                        3, 142, List.of(
                        new C("senior", "ที่ต้องมีเพิ่มคือ ใบแสดงผลการเรียนล่าสุด กับหนังสือรับรองรายได้ผู้ปกครองครับ\n"
                                + "สองตัวนี้คนลืมบ่อยสุด", 40),
                        new C("somying", "เสริมอีกนิด ถ้าผู้ปกครองไม่มีสลิปเงินเดือน ใช้หนังสือรับรองรายได้จากผู้ใหญ่บ้านแทนได้นะ", 75),
                        new C("admin", "รายการเอกสารฉบับเต็มดูได้จากประกาศของกองทุนโดยตรงนะครับ "
                                + "ถ้าระเบียบเปลี่ยนจะอัปเดตในบอร์ดนี้", 120))),

                new P("ทุน ข.", "admin",
                        "[ประกาศ] เปิดรับสมัครทุน ข. รอบที่ 2",
                        "เปิดรับสมัครทุน ข. รอบที่ 2 แล้ว\n\n"
                                + "คุณสมบัติ: นักศึกษาชั้นปีที่ 2 ขึ้นไป เกรดเฉลี่ยสะสมไม่ต่ำกว่า 2.50\n"
                                + "ช่วงรับสมัคร: [ใส่วันที่จริง]\n\n"
                                + "มีคำถามเรื่องคุณสมบัติหรือเอกสาร ถามไว้ใต้กระทู้นี้ได้เลย",
                        26, 318, List.of(
                        new C("fresh", "น้องปี 1 สมัครได้ไหมคะ", 90),
                        new C("admin", "รอบนี้ยังไม่ได้ครับ ต้องเป็นปี 2 ขึ้นไป แต่ทุน ก. เปิดให้ปี 1 สมัครได้", 140))),

                new P("ทุนภายนอก", "senior",
                        "รวมทุนภายนอกที่เปิดรับช่วงนี้ (อัปเดตเรื่อย ๆ)",
                        "รวบรวมทุนจากหน่วยงานภายนอกที่เห็นประกาศช่วงนี้ไว้ที่เดียว\n\n"
                                + "1. ทุนเรียนดีจากมูลนิธิ [ชื่อมูลนิธิ] — เน้นเกรดเฉลี่ย\n"
                                + "2. ทุนบริษัทเอกชนสาย IT — มีฝึกงานต่อ\n"
                                + "3. ทุนกิจกรรมเพื่อสังคม — ดูชั่วโมงจิตอาสา\n\n"
                                + "ใครเจอทุนอื่นเพิ่มมาคอมเมนต์ไว้ได้เลย จะรวมเข้าไปให้",
                        50, 205, List.of(
                        new C("somying", "ขอบคุณมากค่ะ ข้อ 2 น่าสนใจ มีใครเคยสมัครบ้างไหมคะ", 300))),

                // ---------- ปัญหาการเรียน ----------
                new P("ปรึกษาปัญหาการเรียน", "fresh",
                        "Data Structures เรื่อง Binary Search Tree ไม่เข้าใจตอนลบ node",
                        "ตอนลบ node ที่มีลูกสองข้าง ต้องเอาตัวไหนมาแทนคะ\n"
                                + "อ่านในชีทแล้วงง ว่าทำไมต้องหา inorder successor",
                        5, 64, List.of(
                        new C("senior", "หาตัวที่น้อยสุดใน subtree ขวา (inorder successor) มาแทนครับ\n"
                                + "เพราะมันมากกว่าทุกตัวทางซ้าย และน้อยกว่าทุกตัวทางขวา tree เลยยังถูกต้องตามกฎ BST", 30),
                        new C("somchai", "ลองวาดเป็นรูปดูจะเห็นภาพชัดกว่าครับ ใช้ตัวมากสุดใน subtree ซ้ายแทนก็ได้เหมือนกัน", 55))),

                new P("ติวและแชร์ชีทสรุป", "somying",
                        "นัดติว Calculus ก่อนสอบกลางภาค",
                        "จะนัดติว Calculus กันก่อนสอบกลางภาค เน้นเรื่องอนุพันธ์กับการอินทิเกรต\n\n"
                                + "สถานที่: ห้องสมุด ชั้น 2\n"
                                + "เวลา: [ใส่วันเวลา]\n\n"
                                + "ใครจะมาคอมเมนต์บอกจำนวนไว้หน่อย จะได้จองห้องถูก",
                        20, 97, List.of(
                        new C("fresh", "ไปด้วยค่ะ 1 คน", 45),
                        new C("somchai", "ไปครับ ขอเพิ่มเรื่อง limit ด้วยได้ไหม", 80))),

                new P("แนะแนวการลงทะเบียน", "somchai",
                        "วิชาเลือกเสรีตัวไหนดี ไม่หนักมากแต่ได้ความรู้",
                        "เทอมหน้าเหลือหน่วยกิตวิชาเลือกเสรี 3 หน่วยกิต\n"
                                + "อยากได้วิชาที่งานไม่หนักมาก แต่เรียนแล้วได้อะไรจริง ๆ ครับ มีแนะนำไหม",
                        72, 128, List.of(
                        new C("senior", "ถ้าชอบวิชาที่ใช้ได้จริง แนะนำพวกการเงินส่วนบุคคลหรือภาษาที่สามครับ "
                                + "งานเป็นโปรเจกต์เล็ก ๆ ไม่มีสอบหนัก", 120))),

                // ---------- เรื่องทั่วไป ----------
                new P("พูดคุยทั่วไป", "fresh",
                        "ช่วงสอบนั่งอ่านหนังสือที่ไหนกันบ้าง",
                        "ห้องสมุดคนเต็มตลอดเลย มีที่ไหนเงียบ ๆ แอร์เย็น ๆ แนะนำบ้างไหมคะ",
                        8, 76, List.of(
                        new C("somying", "ลองดูห้องอ่านหนังสือของคณะ ช่วงเช้าคนน้อยมาก", 25),
                        new C("somchai", "ร้านกาแฟหน้ามอก็ได้ครับ แต่ต้องสั่งเครื่องดื่มนะ", 60))),

                new P("รีวิวร้านอาหาร", "somchai",
                        "รีวิวร้านข้าวมันไก่หลังมอ ราคานักศึกษา",
                        "ไปกินมาเมื่อวาน จานละ 45 บาท ไก่นุ่ม น้ำจิ้มรสจัดดี\n"
                                + "ช่วงเที่ยงคิวยาว แนะนำไปก่อน 11 โมง\n\n"
                                + "ให้ 4/5 ครับ",
                        30, 154, List.of(
                        new C("fresh", "ขอพิกัดหน่อยค่ะ", 50))),

                new P("ของหาย-ของเจอ", "somying",
                        "เก็บบัตรนักศึกษาได้ที่โรงอาหาร",
                        "เก็บบัตรนักศึกษาได้ที่โรงอาหาร ช่วงเที่ยงวันนี้\n"
                                + "ฝากไว้ที่จุดประชาสัมพันธ์แล้ว เจ้าของไปรับได้เลย นำบัตรประชาชนไปยืนยันด้วยนะคะ",
                        2, 33, List.of()),

                // ---------- กิจกรรมนักศึกษา ----------
                new P("ชมรม", "senior",
                        "ชมรมดนตรีเปิดรับสมาชิกใหม่",
                        "ชมรมดนตรีเปิดรับสมาชิกใหม่ ไม่ต้องเล่นเป็นก็มาได้ มีพี่สอนตั้งแต่พื้นฐาน\n\n"
                                + "ซ้อมทุกวันพุธหลังเลิกเรียน\n"
                                + "สนใจคอมเมนต์ชื่อกับเครื่องดนตรีที่อยากเล่นไว้ได้เลย",
                        96, 88, List.of(
                        new C("fresh", "สนใจกีตาร์ค่ะ เล่นไม่เป็นเลยได้ไหมคะ", 200),
                        new C("senior", "ได้เลยครับ มาวันพุธนี้ได้เลย", 260))),

                new P("ประกาศกิจกรรม", "admin",
                        "[ประกาศ] รับสมัครสตาฟงานรับน้อง",
                        "เปิดรับสมัครสตาฟงานรับน้องประจำปี\n\n"
                                + "ตำแหน่งที่เปิด: ฝ่ายสถานที่, ฝ่ายประชาสัมพันธ์, ฝ่ายพยาบาล\n"
                                + "ได้ชั่วโมงกิจกรรมตามระเบียบ\n\n"
                                + "สมัครโดยคอมเมนต์ชื่อ ชั้นปี และฝ่ายที่สนใจ",
                        40, 276, List.of(
                        new C("somying", "สมหญิง ปี 2 สนใจฝ่ายประชาสัมพันธ์ค่ะ", 30),
                        new C("somchai", "สมชาย ปี 2 ฝ่ายสถานที่ครับ", 65)))
        );
    }
}
