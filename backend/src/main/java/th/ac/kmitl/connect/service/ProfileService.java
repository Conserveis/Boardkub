package th.ac.kmitl.connect.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.dto.AuthDtos;
import th.ac.kmitl.connect.dto.ProfileDtos;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.ratelimit.RateLimiter;
import th.ac.kmitl.connect.ratelimit.RateLimiter.Limit;
import th.ac.kmitl.connect.repository.CommentRepository;
import th.ac.kmitl.connect.repository.PostRepository;
import th.ac.kmitl.connect.repository.UserRepository;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final CurrentUser currentUser;
    private final AuthService authService;

    public ProfileService(UserRepository userRepository, PostRepository postRepository,
                          CommentRepository commentRepository, PasswordEncoder passwordEncoder,
                          RateLimiter rateLimiter, CurrentUser currentUser, AuthService authService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public ProfileDtos.ProfileResponse get(Long id, HttpSession session) {
        User u = find(id);
        boolean me = id.equals(currentUser.idOrNull(session));
        return new ProfileDtos.ProfileResponse(
                u.getId(), u.getDisplayName(), u.getRole().name(), u.getCreatedAt(),
                postRepository.countByUserIdAndBoardActiveTrue(id),
                commentRepository.countByUserId(id),
                postRepository.sumViewsByUser(id),
                me, me ? u.getEmail() : null);
    }

    /** Throws 404 for unknown members, so the posts list cannot probe for ids. */
    public void requireExists(Long id) {
        find(id);
    }

    @Transactional
    public AuthDtos.MeResponse updateDisplayName(ProfileDtos.UpdateProfileRequest req, HttpSession session) {
        User me = currentUser.require(session);
        me.setDisplayName(req.displayName().trim());
        userRepository.save(me);
        return authService.toMe(me);
    }

    @Transactional
    public void changePassword(ProfileDtos.ChangePasswordRequest req, HttpServletRequest request) {
        User me = currentUser.require(request.getSession(false));
        // Shares the login budget, so a stolen session cannot be used to guess the password
        rateLimiter.ensureAllowed(Limit.LOGIN, me.getEmail());
        if (!passwordEncoder.matches(req.currentPassword(), me.getPasswordHash())) {
            rateLimiter.recordFailure(Limit.LOGIN, me.getEmail());
            throw ApiException.badRequest("รหัสผ่านปัจจุบันไม่ถูกต้อง");
        }
        rateLimiter.reset(Limit.LOGIN, me.getEmail());
        if (passwordEncoder.matches(req.newPassword(), me.getPasswordHash())) {
            throw ApiException.badRequest("รหัสผ่านใหม่ต้องไม่ซ้ำกับรหัสผ่านเดิม");
        }
        me.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(me);
        // New session id after a credential change, like after login
        request.changeSessionId();
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบสมาชิกที่ต้องการ"));
    }
}
