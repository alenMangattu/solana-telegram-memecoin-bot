package com.lunax.telegramBot.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import com.lunax.telegramBot.model.db.CopyTrading;

@Repository
public interface CopyTradingRepository extends JpaRepository<CopyTrading, Long> {
    // Add any custom query methods here if needed

    // Find all copy trading records for a specific user
    @Cacheable(value = "copyTradingConfigs", key = "'user_' + #userId")
    List<CopyTrading> findByUserId(Integer userId);
    
    // Find a specific copy trading record by its ID
    @Cacheable(value = "copyTradingConfigs", key = "'id_' + #id")
    Optional<CopyTrading> findById(Integer id);
    
    // New security method to check if config belongs to user
    @Query("SELECT COUNT(c) > 0 FROM CopyTrading c WHERE c.id = :configId AND c.user.id = :userId")
    @Cacheable(value = "copyTradingConfigs", key = "'exists_' + #configId + '_' + #userId")
    boolean existsByIdAndUserId(@Param("configId") Long configId, @Param("userId") Long userId);
    
    // Add another overload if needed for Integer userId
    @Query("SELECT COUNT(c) > 0 FROM CopyTrading c WHERE c.id = :configId AND c.user.id = :userId")
    @Cacheable(value = "copyTradingConfigs", key = "'exists_' + #configId + '_' + #userId")
    boolean existsByIdAndUserId(@Param("configId") Long configId, @Param("userId") Integer userId);

    // Override save method to update cache
    @Override
    @CachePut(value = "copyTradingConfigs", key = "'id_' + #result.id")
    @CacheEvict(value = "copyTradingConfigs", key = "'user_' + #result.user.id")
    <S extends CopyTrading> S save(S entity);

    // Override deleteById to evict cache
    @Override
    @CacheEvict(value = "copyTradingConfigs", key = "'id_' + #id")
    void deleteById(Long id);

    // Find all copy trading records for a specific user
    @Cacheable(value = "copyTradingConfigs", key = "'user_' + #userId")
    List<CopyTrading> findByUserIdOrderByCreatedAtAsc(Long userId);
} 