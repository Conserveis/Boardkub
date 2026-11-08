package th.ac.kmitl.connect.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import th.ac.kmitl.connect.config.SessionKeys;
import th.ac.kmitl.connect.entity.Role;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.repository.UserRepository;

@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long idOrNull(HttpSession session) {
        if (session == null) return null;
        Object v = session.getAttribute(SessionKeys.USER_ID);
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    public User require(HttpSession session) {
        Long id = idOrNull(session);
        if (id == null) throw ApiException.unauthorized("กรุณาเข้าสู่ระบบก่อนใช้งาน");
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("ไม่พบบัญชีผู้ใช้ กรุณาเข้าสู่ระบบใหม่"));
    }

    public boolean isAdmin(HttpSession session) {
        if (session == null) return false;
        return Role.ADMIN.name().equals(String.valueOf(session.getAttribute(SessionKeys.USER_ROLE)));
    }

    /** The owner of a record, or an admin, may modify it. */
    public void requireOwnerOrAdmin(HttpSession session, Long ownerId) {
        User me = require(session);
        if (me.getRole() == Role.ADMIN) return;
        if (!me.getId().equals(ownerId)) {
            throw ApiException.forbidden("คุณไม่มีสิทธิ์แก้ไขหรือลบรายการนี้");
        }
    }
}
