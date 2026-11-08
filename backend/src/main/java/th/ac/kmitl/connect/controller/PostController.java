package th.ac.kmitl.connect.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.interceptor.annotation.RequireLogin;
import th.ac.kmitl.connect.service.PostService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/boards/{boardId}/posts")
    public PostDtos.PageResponse<PostDtos.PostSummary> listByBoard(
            @PathVariable Long boardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort) {
        return postService.listByBoard(boardId, page, size, sort);
    }

    @GetMapping("/posts/search")
    public PostDtos.PageResponse<PostDtos.PostSummary> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return postService.search(q, page, size);
    }

    /** Top 5 posts by views. Declared before /posts/{id}; Spring prefers the literal path anyway. */
    @GetMapping("/posts/popular")
    public java.util.List<PostDtos.PostSummary> popular() {
        return postService.popular();
    }

    @GetMapping("/posts/{id}")
    public PostDtos.PostDetail get(@PathVariable Long id, HttpSession session) {
        return postService.get(id, session);
    }

    @PostMapping("/boards/{boardId}/posts")
    @RequireLogin
    public PostDtos.PostSummary create(@PathVariable Long boardId,
                                       @Valid @RequestBody PostDtos.PostRequest req,
                                       HttpSession session) {
        return postService.create(boardId, req, session);
    }

    @PutMapping("/posts/{id}")
    @RequireLogin
    public PostDtos.PostSummary update(@PathVariable Long id,
                                       @Valid @RequestBody PostDtos.PostRequest req,
                                       HttpSession session) {
        return postService.update(id, req, session);
    }

    @DeleteMapping("/posts/{id}")
    @RequireLogin
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, HttpSession session) {
        postService.delete(id, session);
        return ResponseEntity.ok(Map.of("message", "ลบกระทู้เรียบร้อยแล้ว"));
    }
}
