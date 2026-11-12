package th.ac.kmitl.connect.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import th.ac.kmitl.connect.dto.AuthDtos;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.dto.ProfileDtos;
import th.ac.kmitl.connect.interceptor.annotation.RequireLogin;
import th.ac.kmitl.connect.service.PostService;
import th.ac.kmitl.connect.service.ProfileService;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;
    private final PostService postService;

    public ProfileController(ProfileService profileService, PostService postService) {
        this.profileService = profileService;
        this.postService = postService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileDtos.ProfileResponse> profile(@PathVariable Long id, HttpSession session) {
        return ResponseEntity.ok(profileService.get(id, session));
    }

    @GetMapping("/{id}/posts")
    public ResponseEntity<PostDtos.PageResponse<PostDtos.PostSummary>> posts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        profileService.requireExists(id);
        return ResponseEntity.ok(postService.listByUser(id, page, size));
    }

    @PutMapping("/me")
    @RequireLogin
    public ResponseEntity<AuthDtos.MeResponse> updateMe(@Valid @RequestBody ProfileDtos.UpdateProfileRequest req,
                                                        HttpSession session) {
        return ResponseEntity.ok(profileService.updateDisplayName(req, session));
    }

    @PutMapping("/me/password")
    @RequireLogin
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ProfileDtos.ChangePasswordRequest req, HttpServletRequest request) {
        profileService.changePassword(req, request);
        return ResponseEntity.ok(Map.of("message", "เปลี่ยนรหัสผ่านเรียบร้อยแล้ว"));
    }
}
