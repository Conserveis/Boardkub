package th.ac.kmitl.connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import th.ac.kmitl.connect.entity.Board;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findByCategoryIdAndActiveTrueOrderByIdAsc(Long categoryId);
    List<Board> findByActiveTrueOrderByIdAsc();
}
