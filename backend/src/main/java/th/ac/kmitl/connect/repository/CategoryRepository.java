package th.ac.kmitl.connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import th.ac.kmitl.connect.entity.Category;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByDisplayOrderAscIdAsc();
}
