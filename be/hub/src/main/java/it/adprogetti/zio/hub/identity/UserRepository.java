package it.adprogetti.zio.hub.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UserAccount> findAllByOrderByUsernameAsc();

    @Query("select count(u) from UserAccount u join u.roles r where r = ?1 and u.enabled = true")
    long countEnabledWithRole(String roleCode);

    @Query("select count(u) from UserAccount u join u.roles r where r = ?1")
    long countWithRole(String roleCode);
}
