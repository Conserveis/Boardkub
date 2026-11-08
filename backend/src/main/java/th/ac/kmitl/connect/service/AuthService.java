package th.ac.kmitl.connect.service;

import th.ac.kmitl.connect.ratelimit.RateLimiter;
import th.ac.kmitl.connect.ratelimit.RateLimiter.Limit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.config.SessionKeys;
import th.ac.kmitl.connect.dto.AuthDtos;
import th.ac.kmitl.connect.entity.Role;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final RateLimiter rateLimiter;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, OtpService otpService,
                       RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void register(AuthDtos.RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("อีเมลนี้ถูกใช้สมัครสมาชิกแล้ว");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName().trim());
        user.setRole(Role.STUDENT);
        user.setVerified(false);
        userRepository.save(user);

        rateLimiter.consume(Limit.OTP_SEND, email);
        otpService.issue(user);
    }

    @Transactional
    public void verifyOtp(AuthDtos.VerifyOtpRequest req) {
        String email = req.email().trim().toLowerCase();
        // Checked before anything else so guessing stops even for unknown emails
        rateLimiter.ensureAllowed(Limit.OTP_VERIFY, email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบัญชีผู้ใช้สำหรับอีเมลนี้"));
        if (user.isVerified()) {
            throw ApiException.badRequest("บัญชีนี้ยืนยันอีเมลเรียบร้อยแล้ว");
        }
        try {
            otpService.verify(user, req.code().trim());
        } catch (ApiException e) {
            rateLimiter.recordFailure(Limit.OTP_VERIFY, email);
            throw e;
        }
        rateLimiter.reset(Limit.OTP_VERIFY, email);
        user.setVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendOtp(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบัญชีผู้ใช้สำหรับอีเมลนี้"));
        if (user.isVerified()) {
            throw ApiException.badRequest("บัญชีนี้ยืนยันอีเมลเรียบร้อยแล้ว");
        }
        rateLimiter.consume(Limit.OTP_SEND, email);
        otpService.issue(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeResponse login(AuthDtos.LoginRequest req, HttpServletRequest request) {
        String email = req.email().trim().toLowerCase();
        rateLimiter.ensureAllowed(Limit.LOGIN, email);
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            rateLimiter.recordFailure(Limit.LOGIN, email);
            throw ApiException.unauthorized("อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        }
        rateLimiter.reset(Limit.LOGIN, email);
        if (!user.isVerified()) {
            throw ApiException.forbidden("กรุณายืนยันอีเมลด้วยรหัส OTP ก่อนเข้าสู่ระบบ");
        }

        // Discard any pre-login session to prevent session fixation.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setAttribute(SessionKeys.USER_ROLE, user.getRole().name());

        return toMe(user);
    }

    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    public AuthDtos.MeResponse toMe(User u) {
        return new AuthDtos.MeResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name());
    }
}
