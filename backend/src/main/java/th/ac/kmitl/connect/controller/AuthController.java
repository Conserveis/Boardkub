package th.ac.kmitl.connect.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import th.ac.kmitl.connect.dto.AuthDtos;
import th.ac.kmitl.connect.interceptor.annotation.RequireAdmin;
import th.ac.kmitl.connect.interceptor.annotation.RequireLogin;
import th.ac.kmitl.connect.service.AuthService;
import th.ac.kmitl.connect.service.CurrentUser;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.ok(Map.of("message", "สมัครสมาชิกสำเร็จ กรุณายืนยันอีเมลด้วยรหัส OTP"));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@Valid @RequestBody AuthDtos.VerifyOtpRequest req) {
        authService.verifyOtp(req);
        return ResponseEntity.ok(Map.of("message", "ยืนยันอีเมลสำเร็จ กรุณาเข้าสู่ระบบ"));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resend(@Valid @RequestBody AuthDtos.ResendOtpRequest req) {
        authService.resendOtp(req.email());
        return ResponseEntity.ok(Map.of("message", "ส่งรหัส OTP ใหม่เรียบร้อยแล้ว"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.MeResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                                     HttpServletRequest request) {
        return ResponseEntity.ok(authService.login(req, request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok(Map.of("message", "ออกจากระบบเรียบร้อยแล้ว"));
    }

    @GetMapping("/me")
    @RequireLogin
    public ResponseEntity<AuthDtos.MeResponse> me(HttpSession session) {
        return ResponseEntity.ok(authService.toMe(currentUser.require(session)));
    }

    /**
     * Reports which Pod served this request. Used to demonstrate that the
     * session survives across Pods once the Deployment is scaled out.
     */
    @GetMapping("/whoami")
    @RequireAdmin   // Pod names are internal details, so only admins (the demo account) see them
    public ResponseEntity<Map<String, Object>> whoami(HttpSession session) {
        Long id = currentUser.idOrNull(session);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loggedIn", id != null);
        body.put("userId", id == null ? "-" : id);
        body.put("servedByPod", System.getenv().getOrDefault("HOSTNAME", "local"));
        return ResponseEntity.ok(body);
    }
}
