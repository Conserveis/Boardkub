package th.ac.kmitl.connect.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import th.ac.kmitl.connect.config.SessionKeys;
import th.ac.kmitl.connect.entity.Role;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.interceptor.annotation.RequireAdmin;
import th.ac.kmitl.connect.interceptor.annotation.RequireLogin;

/**
 * Enforces authentication and authorisation from the HttpSession, which Spring
 * Session keeps in Redis so that any Pod can serve any request.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        boolean needsAdmin = method.hasMethodAnnotation(RequireAdmin.class)
                || method.getBeanType().isAnnotationPresent(RequireAdmin.class);
        boolean needsLogin = needsAdmin
                || method.hasMethodAnnotation(RequireLogin.class)
                || method.getBeanType().isAnnotationPresent(RequireLogin.class);

        if (!needsLogin) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            throw ApiException.unauthorized("กรุณาเข้าสู่ระบบก่อนใช้งาน");
        }

        if (needsAdmin && !Role.ADMIN.name().equals(String.valueOf(session.getAttribute(SessionKeys.USER_ROLE)))) {
            throw ApiException.forbidden("เฉพาะผู้ดูแลระบบเท่านั้นที่ทำรายการนี้ได้");
        }
        return true;
    }
}
