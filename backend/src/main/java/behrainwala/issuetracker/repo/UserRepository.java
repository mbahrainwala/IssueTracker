package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.Role;
import behrainwala.issuetracker.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Used to refuse changes that would leave the system with no way back in. */
    long countByRoleAndEnabledTrue(Role role);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
