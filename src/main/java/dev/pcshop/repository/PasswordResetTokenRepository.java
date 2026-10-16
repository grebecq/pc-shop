package dev.pcshop.repository;

import dev.pcshop.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findForUpdateByTokenHash(String tokenHash);

    boolean existsByAccountIdAndCreatedAtAfter(long accountId, Instant since);

    @Modifying(flushAutomatically = true)
    @Query("delete from PasswordResetToken t where t.accountId = :accountId")
    void deleteAllByAccountId(long accountId);
}
