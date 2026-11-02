package it.adprogetti.zio.hub.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = ?1")
    Optional<RefreshToken> findForUpdate(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = ?2 where t.familyId = ?1 and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = ?2 where t.userId = ?1 and t.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant now);
}
