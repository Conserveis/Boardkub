package th.ac.kmitl.connect.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.interceptor.annotation.RequireLogin;
import th.ac.kmitl.connect.service.PostImageService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PostImageController {

    private final PostImageService imageService;

    public PostImageController(PostImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/posts/{postId}/images")
    public List<PostDtos.ImageResponse> list(@PathVariable Long postId) {
        return imageService.listForPost(postId);
    }

    @PostMapping(path = "/posts/{postId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireLogin
    public PostDtos.ImageResponse upload(@PathVariable Long postId,
                                         @RequestParam("file") MultipartFile file,
                                         HttpSession session) {
        return imageService.upload(postId, file, session);
    }

    @DeleteMapping("/images/{id}")
    @RequireLogin
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, HttpSession session) {
        imageService.delete(id, session);
        return ResponseEntity.ok(Map.of("message", "ลบรูปเรียบร้อยแล้ว"));
    }
}
