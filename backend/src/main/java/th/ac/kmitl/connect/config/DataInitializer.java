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
import th.ac.kmitl.connect.entity.Category;
import th.ac.kmitl.connect.entity.Role;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.repository.BoardRepository;
import th.ac.kmitl.connect.repository.CategoryRepository;
import th.ac.kmitl.connect.repository.UserRepository;

import java.util.List;

/**
 * Seeds the admin account, and the default categories and boards when the
 * database is still empty. Runs before SampleDataSeeder, which needs the boards.
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoryRepository categoryRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    public DataInitializer(CategoryRepository categoryRepository,
                           BoardRepository boardRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.categoryRepository = categoryRepository;
        this.boardRepository = boardRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        if (categoryRepository.count() == 0) {
            seedBoards();
            log.info("Seeded default categories and boards");
        }
    }

    private void seedAdmin() {
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("ผู้ดูแลระบบ");
        admin.setRole(Role.ADMIN);
        admin.setVerified(true);
        userRepository.save(admin);
        log.info("Seeded admin account");
    }

    private void seedBoards() {
        createCategory("ทุนการศึกษา", "ประกาศและถาม-ตอบเรื่องทุนทุกประเภท", 1, List.of(
                new String[]{"ทุน ก.", "ประกาศและปรึกษาเรื่องเอกสารทุน ก."},
                new String[]{"ทุน ข.", "ประกาศและปรึกษาเรื่องเอกสารทุน ข."},
                new String[]{"ทุนภายนอก", "ทุนจากหน่วยงานภายนอกมหาวิทยาลัย"}
        ));
        createCategory("ปัญหาการเรียน", "ปรึกษาเรื่องวิชาเรียนและการลงทะเบียน", 2, List.of(
                new String[]{"ปรึกษาปัญหาการเรียน", "ถาม-ตอบเรื่องเนื้อหาวิชาและการบ้าน"},
                new String[]{"ติวและแชร์ชีทสรุป", "แบ่งปันสรุปและนัดติวก่อนสอบ"},
                new String[]{"แนะแนวการลงทะเบียน", "คำแนะนำการเลือกวิชาและวางแผนหน่วยกิต"}
        ));
        createCategory("เรื่องทั่วไป", "พูดคุยเรื่องทั่วไปในรั้วมหาวิทยาลัย", 3, List.of(
                new String[]{"พูดคุยทั่วไป", "เรื่องสัพเพเหระของนักศึกษา"},
                new String[]{"รีวิวร้านอาหาร", "รีวิวร้านอาหารรอบมหาวิทยาลัย"},
                new String[]{"ของหาย-ของเจอ", "ประกาศตามหาของหายและแจ้งของที่เก็บได้"}
        ));
        createCategory("กิจกรรมนักศึกษา", "ข่าวสารชมรมและกิจกรรม", 4, List.of(
                new String[]{"ชมรม", "ประชาสัมพันธ์และรับสมัครสมาชิกชมรม"},
                new String[]{"ประกาศกิจกรรม", "กิจกรรมและการรับสมัครสตาฟ"}
        ));
    }

    private void createCategory(String name, String desc, int order, List<String[]> boards) {
        Category c = new Category();
        c.setName(name);
        c.setDescription(desc);
        c.setDisplayOrder(order);
        categoryRepository.save(c);

        for (String[] b : boards) {
            Board board = new Board();
            board.setCategory(c);
            board.setName(b[0]);
            board.setDescription(b[1]);
            board.setActive(true);
            boardRepository.save(board);
        }
    }
}
