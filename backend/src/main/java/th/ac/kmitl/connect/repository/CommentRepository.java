package th.ac.kmitl.connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import th.ac.kmitl.connect.entity.Comment;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    long countByPostId(Long postId);
    void deleteByPostId(Long postId);
}
