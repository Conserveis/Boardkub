package th.ac.kmitl.connect.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import th.ac.kmitl.connect.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByBoardId(Long boardId, Pageable pageable);

    long countByBoardId(Long boardId);

    /** Most viewed posts on boards that are still open; newer first when views tie. */
    List<Post> findTop5ByBoardActiveTrueOrderByViewCountDescCreatedAtDesc();

    /** A member's posts, for the profile page; posts on closed boards stay hidden. */
    Page<Post> findByUserIdAndBoardActiveTrue(Long userId, Pageable pageable);

    long countByUserIdAndBoardActiveTrue(Long userId);

    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Post p WHERE p.user.id = :userId AND p.board.active = true")
    long sumViewsByUser(@Param("userId") Long userId);

    @Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Post> searchByTitle(@Param("q") String q, Pageable pageable);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
