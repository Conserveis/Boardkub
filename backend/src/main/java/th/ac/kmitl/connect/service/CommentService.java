package th.ac.kmitl.connect.service;

import th.ac.kmitl.connect.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.entity.Comment;
import th.ac.kmitl.connect.entity.Post;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.repository.CommentRepository;
import th.ac.kmitl.connect.repository.PostRepository;

import java.time.Instant;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          CurrentUser currentUser,
                          RateLimiter rateLimiter) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public List<PostDtos.CommentResponse> listByPost(Long postId, HttpSession session) {
        if (!postRepository.existsById(postId)) {
            throw ApiException.notFound("ไม่พบกระทู้ที่ต้องการ");
        }
        Long myId = currentUser.idOrNull(session);
        boolean admin = currentUser.isAdmin(session);
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> toResponse(c, myId, admin))
                .toList();
    }

    @Transactional
    public PostDtos.CommentResponse create(Long postId, PostDtos.CommentRequest req, HttpSession session) {
        User me = currentUser.require(session);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบกระทู้ที่ต้องการ"));
        rateLimiter.consume(RateLimiter.Limit.COMMENT_CREATE, me.getId());

        Comment c = new Comment();
        c.setPost(post);
        c.setUser(me);
        c.setContent(req.content());
        c.setCreatedAt(Instant.now());
        commentRepository.save(c);

        return toResponse(c, me.getId(), currentUser.isAdmin(session));
    }

    @Transactional
    public PostDtos.CommentResponse update(Long id, PostDtos.CommentRequest req, HttpSession session) {
        Comment c = commentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบความคิดเห็นที่ต้องการ"));
        currentUser.requireOwnerOrAdmin(session, c.getUser().getId());

        c.setContent(req.content());
        c.setUpdatedAt(Instant.now());
        commentRepository.save(c);

        return toResponse(c, currentUser.idOrNull(session), currentUser.isAdmin(session));
    }

    @Transactional
    public void delete(Long id, HttpSession session) {
        Comment c = commentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบความคิดเห็นที่ต้องการ"));
        currentUser.requireOwnerOrAdmin(session, c.getUser().getId());
        commentRepository.delete(c);
    }

    private PostDtos.CommentResponse toResponse(Comment c, Long myId, boolean admin) {
        boolean canEdit = myId != null && (myId.equals(c.getUser().getId()) || admin);
        return new PostDtos.CommentResponse(
                c.getId(), c.getPost().getId(), c.getContent(),
                c.getUser().getDisplayName(), c.getUser().getId(), c.getUser().getRole().name(),
                c.getCreatedAt(), c.getUpdatedAt(), canEdit);
    }
}
