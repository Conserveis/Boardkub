package th.ac.kmitl.connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import th.ac.kmitl.connect.entity.OtpToken;
import th.ac.kmitl.connect.entity.User;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findTopByUserAndUsedFalseOrderByIdDesc(User user);
}
