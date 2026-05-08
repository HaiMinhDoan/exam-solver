package com.examsolver.repository;

import com.examsolver.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    /** Lấy các customer hết hạn nhưng vẫn còn active — dùng cho cleanup task. */
    @Query("SELECT c FROM Customer c WHERE c.active = true AND c.role = 'CUSTOMER' " +
            "AND c.accessExpiresAt IS NOT NULL AND c.accessExpiresAt < :now")
    List<Customer> findExpiredActiveCustomers(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Customer c SET c.active = false WHERE c.id IN :ids")
    void deactivateByIds(@Param("ids") List<Long> ids);

    Page<Customer> findByRoleAndActiveTrue(Customer.Role role, Pageable pageable);
}