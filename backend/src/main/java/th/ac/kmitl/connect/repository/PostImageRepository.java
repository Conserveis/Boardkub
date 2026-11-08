package th.ac.kmitl.connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import th.ac.kmitl.connect.entity.PostImage;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdOrderBySortOrderAscIdAsc(Long postId);
    long countByPostId(Long postId);
}
