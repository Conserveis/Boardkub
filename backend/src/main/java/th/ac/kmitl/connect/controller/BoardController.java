package th.ac.kmitl.connect.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import th.ac.kmitl.connect.dto.BoardDtos;
import th.ac.kmitl.connect.interceptor.annotation.RequireAdmin;
import th.ac.kmitl.connect.service.BoardService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/categories")
    public List<BoardDtos.CategoryResponse> categories() {
        return boardService.listCategories();
    }

    @GetMapping("/boards")
    public List<BoardDtos.BoardResponse> boards(@RequestParam(required = false) Long categoryId) {
        return boardService.listBoards(categoryId);
    }

    @GetMapping("/boards/{id}")
    public BoardDtos.BoardResponse board(@PathVariable Long id) {
        return boardService.getBoard(id);
    }

    // ----- Admin only -----

    @PostMapping("/admin/categories")
    @RequireAdmin
    public BoardDtos.CategoryResponse createCategory(@Valid @RequestBody BoardDtos.CategoryRequest req) {
        return boardService.createCategory(req);
    }

    @DeleteMapping("/admin/categories/{id}")
    @RequireAdmin
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        boardService.deleteCategory(id);
        return ResponseEntity.ok(Map.of("message", "ลบหมวดหมู่เรียบร้อยแล้ว"));
    }

    @PostMapping("/admin/boards")
    @RequireAdmin
    public BoardDtos.BoardResponse createBoard(@Valid @RequestBody BoardDtos.BoardRequest req) {
        return boardService.createBoard(req);
    }

    @DeleteMapping("/admin/boards/{id}")
    @RequireAdmin
    public ResponseEntity<Map<String, String>> deleteBoard(@PathVariable Long id) {
        boardService.deleteBoard(id);
        return ResponseEntity.ok(Map.of("message", "ปิดการใช้งานบอร์ดเรียบร้อยแล้ว"));
    }
}
