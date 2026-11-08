package th.ac.kmitl.connect.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.dto.BoardDtos;
import th.ac.kmitl.connect.entity.Board;
import th.ac.kmitl.connect.entity.Category;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.repository.BoardRepository;
import th.ac.kmitl.connect.repository.CategoryRepository;
import th.ac.kmitl.connect.repository.PostRepository;

import java.util.List;

@Service
public class BoardService {

    private final CategoryRepository categoryRepository;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;

    public BoardService(CategoryRepository categoryRepository,
                        BoardRepository boardRepository,
                        PostRepository postRepository) {
        this.categoryRepository = categoryRepository;
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public List<BoardDtos.CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(c -> {
                    long total = boardRepository.findByCategoryIdAndActiveTrueOrderByIdAsc(c.getId()).stream()
                            .mapToLong(b -> postRepository.countByBoardId(b.getId()))
                            .sum();
                    return new BoardDtos.CategoryResponse(c.getId(), c.getName(), c.getDescription(),
                            c.getDisplayOrder(), total);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BoardDtos.BoardResponse> listBoards(Long categoryId) {
        List<Board> boards = (categoryId == null)
                ? boardRepository.findByActiveTrueOrderByIdAsc()
                : boardRepository.findByCategoryIdAndActiveTrueOrderByIdAsc(categoryId);
        return boards.stream().map(this::toBoardResponse).toList();
    }

    @Transactional(readOnly = true)
    public BoardDtos.BoardResponse getBoard(Long id) {
        Board b = boardRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบอร์ดที่ต้องการ"));
        return toBoardResponse(b);
    }

    @Transactional
    public BoardDtos.CategoryResponse createCategory(BoardDtos.CategoryRequest req) {
        Category c = new Category();
        c.setName(req.name().trim());
        c.setDescription(req.description());
        c.setDisplayOrder(req.displayOrder());
        categoryRepository.save(c);
        return new BoardDtos.CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getDisplayOrder(), 0);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw ApiException.notFound("ไม่พบหมวดหมู่ที่ต้องการ");
        }
        if (!boardRepository.findByCategoryIdAndActiveTrueOrderByIdAsc(id).isEmpty()) {
            throw ApiException.badRequest("ไม่สามารถลบหมวดหมู่ที่ยังมีบอร์ดอยู่ได้");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    public BoardDtos.BoardResponse createBoard(BoardDtos.BoardRequest req) {
        Category c = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบหมวดหมู่ที่ระบุ"));
        Board b = new Board();
        b.setCategory(c);
        b.setName(req.name().trim());
        b.setDescription(req.description());
        b.setActive(true);
        boardRepository.save(b);
        return toBoardResponse(b);
    }

    @Transactional
    public void deleteBoard(Long id) {
        Board b = boardRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบอร์ดที่ต้องการ"));
        // Soft delete so existing posts remain readable.
        b.setActive(false);
        boardRepository.save(b);
    }

    private BoardDtos.BoardResponse toBoardResponse(Board b) {
        return new BoardDtos.BoardResponse(
                b.getId(), b.getCategory().getId(), b.getCategory().getName(),
                b.getName(), b.getDescription(), postRepository.countByBoardId(b.getId()));
    }
}
