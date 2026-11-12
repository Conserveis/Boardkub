package th.ac.kmitl.connect.service;

import th.ac.kmitl.connect.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.entity.Board;
import th.ac.kmitl.connect.entity.Post;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.repository.BoardRepository;
import th.ac.kmitl.connect.repository.CommentRepository;
import th.ac.kmitl.connect.repository.PostRepository;

import java.time.Instant;
import java.util.List;

@Service
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final CurrentUser currentUser;
    private final PostImageService imageService;
    private final RateLimiter rateLimiter;

    public PostService(PostRepository postRepository,
                       BoardRepository boardRepository,
                       CommentRepository commentRepository,
                       CurrentUser currentUser,
                       PostImageService imageService,
                       RateLimiter rateLimiter) {
        this.postRepository = postRepository;
        this.boardRepository = boardRepository;
        this.commentRepository = commentRepository;
        this.currentUser = currentUser;
        this.imageService = imageService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public PostDtos.PageResponse<PostDtos.PostSummary> listByBoard(Long boardId, int page, int size, String sort) {
        if (!boardRepository.existsById(boardId)) {
            throw ApiException.notFound("ไม่พบบอร์ดที่ต้องการ");
        }
        return toPage(postRepository.findByBoardId(boardId, pageable(page, size, sort)));
    }

    /** The five most viewed posts, shown at the top of the category page. */
    @Transactional(readOnly = true)
    public java.util.List<PostDtos.PostSummary> popular() {
        return postRepository.findTop5ByBoardActiveTrueOrderByViewCountDescCreatedAtDesc().stream()
                .map(p -> toSummary(p, commentRepository.countByPostId(p.getId())))
                .toList();
    }

    /** Posts written by one member, newest first, for the profile page. */
    @Transactional(readOnly = true)
    public PostDtos.PageResponse<PostDtos.PostSummary> listByUser(Long userId, int page, int size) {
        return toPage(postRepository.findByUserIdAndBoardActiveTrue(userId, pageable(page, size, "latest")));
    }

    @Transactional(readOnly = true)
    public PostDtos.PageResponse<PostDtos.PostSummary> search(String q, int page, int size) {
        if (q == null || q.isBlank()) {
            throw ApiException.badRequest("กรุณาระบุคำค้นหา");
        }
        return toPage(postRepository.searchByTitle(q.trim(), pageable(page, size, "latest")));
    }

    @Transactional
    public PostDtos.PostDetail get(Long id, HttpSession session) {
        Post p = postRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบกระทู้ที่ต้องการ"));
        postRepository.incrementViewCount(id);

        Long myId = currentUser.idOrNull(session);
        boolean canEdit = myId != null
                && (myId.equals(p.getUser().getId()) || currentUser.isAdmin(session));

        Board b = p.getBoard();
        return new PostDtos.PostDetail(
                p.getId(), b.getId(), b.getName(),
                b.getCategory().getId(), b.getCategory().getName(),
                p.getTitle(), p.getContent(),
                p.getUser().getDisplayName(), p.getUser().getId(),
                p.getViewCount() + 1, p.getCreatedAt(), p.getUpdatedAt(), canEdit,
                imageService.listForPost(p.getId()));
    }

    @Transactional
    public PostDtos.PostSummary create(Long boardId, PostDtos.PostRequest req, HttpSession session) {
        User me = currentUser.require(session);
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบอร์ดที่ต้องการ"));
        if (!board.isActive()) {
            throw ApiException.badRequest("บอร์ดนี้ถูกปิดการใช้งานแล้ว");
        }
        rateLimiter.consume(RateLimiter.Limit.POST_CREATE, me.getId());

        Post p = new Post();
        p.setBoard(board);
        p.setUser(me);
        p.setTitle(req.title().trim());
        p.setContent(req.content());
        p.setCreatedAt(Instant.now());
        postRepository.save(p);

        return toSummary(p, 0);
    }

    @Transactional
    public PostDtos.PostSummary update(Long id, PostDtos.PostRequest req, HttpSession session) {
        Post p = postRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบกระทู้ที่ต้องการ"));
        currentUser.requireOwnerOrAdmin(session, p.getUser().getId());

        p.setTitle(req.title().trim());
        p.setContent(req.content());
        p.setUpdatedAt(Instant.now());
        postRepository.save(p);

        return toSummary(p, commentRepository.countByPostId(p.getId()));
    }

    @Transactional
    public void delete(Long id, HttpSession session) {
        Post p = postRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบกระทู้ที่ต้องการ"));
        currentUser.requireOwnerOrAdmin(session, p.getUser().getId());
        imageService.deleteAllForPost(id);   // files leave Object Storage after the commit
        commentRepository.deleteByPostId(id);
        postRepository.delete(p);
    }

    private Pageable pageable(int page, int size, String sort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Sort s = "views".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "viewCount")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(safePage, safeSize, s);
    }

    private PostDtos.PageResponse<PostDtos.PostSummary> toPage(Page<Post> result) {
        List<PostDtos.PostSummary> items = result.getContent().stream()
                .map(p -> toSummary(p, commentRepository.countByPostId(p.getId())))
                .toList();
        return new PostDtos.PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private PostDtos.PostSummary toSummary(Post p, long commentCount) {
        return new PostDtos.PostSummary(
                p.getId(), p.getTitle(), p.getUser().getDisplayName(), p.getUser().getId(),
                p.getViewCount(), commentCount, p.getCreatedAt(),
                p.getBoard().getId(), p.getBoard().getName());
    }
}
