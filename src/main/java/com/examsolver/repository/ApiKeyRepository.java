package com.examsolver.repository;

import com.examsolver.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyValue(String keyValue);

    List<ApiKey> findByCustomerId(Long customerId);

    List<ApiKey> findByCustomerIdAndActiveTrue(Long customerId);

    @Query("SELECT k FROM ApiKey k WHERE k.keyValue = :keyValue AND k.active = true AND k.expiresAt > :now")
    Optional<ApiKey> findValidKey(@Param("keyValue") String keyValue, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE ApiKey k SET k.usageCount = k.usageCount + 1, k.lastUsedAt = :now WHERE k.id = :id")
    void incrementUsageCount(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(k) FROM ApiKey k WHERE k.customer.id = :customerId AND k.active = true")
    long countActiveKeysByCustomer(@Param("customerId") Long customerId);
}
