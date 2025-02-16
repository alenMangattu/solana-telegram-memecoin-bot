package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.EncryptionKeyMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncryptionKeyMetadataRepository extends JpaRepository<EncryptionKeyMetadata, Long> {
    Optional<EncryptionKeyMetadata> findByWalletIdAndActive(UUID walletId, boolean active);
    
    List<EncryptionKeyMetadata> findByWalletIdOrderByVersionDesc(UUID walletId);
    
    @Query("SELECT e FROM EncryptionKeyMetadata e WHERE e.active = true AND e.expiresAt <= :date")
    List<EncryptionKeyMetadata> findExpiredActiveKeys(LocalDateTime date);
    
    Optional<EncryptionKeyMetadata> findFirstByWalletIdOrderByVersionDesc(UUID walletId);
    
    List<EncryptionKeyMetadata> findAllByWalletIdAndActive(UUID walletId, boolean active);
}
