package th.ac.kmitl.connect.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.interceptor.annotation.RequireLogin;
import th.ac.kmitl.connect.service.CommentService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/posts/{postId}/comments")
    public List<PostDtos.CommentResponse> list(@PathVariable Long postId, HttpSession session) {
        return commentService.listByPost(postId, session);
    }

    @PostMapping("/posts/{postId}/comments")
    @RequireLogin
    public PostDtos.CommentResponse create(@PathVariable Long postId,
                                           @Valid @RequestBody PostDtos.CommentRequest req,
                                           HttpSession session) {
        return commentService.create(postId, req, session);
    }

    @PutMapping("/comments/{id}")
    @RequireLogin
    public PostDtos.CommentResponse update(@PathVariable Long id,
                                           @Valid @RequestBody PostDtos.CommentRequest req,
                                           HttpSession session) {
        return commentService.update(id, req, session);
    }

    @DeleteMapping("/comments/{id}")
    @RequireLogin
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, HttpSession session) {
        commentService.delete(id, session);
        return ResponseEntity.ok(Map.of("message", "ลบความคิดเห็นเรียบร้อยแล้ว"));
    }
}
