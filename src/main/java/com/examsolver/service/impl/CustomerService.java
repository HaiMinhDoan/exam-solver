package com.examsolver.service.impl;

import com.examsolver.dto.request.CustomerUpdateRequest;
import com.examsolver.dto.response.CustomerResponse;
import com.examsolver.entity.Customer;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Page<CustomerResponse> listCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable).map(this::toResponse);
    }

    public CustomerResponse getCustomer(Long id) {
        return toResponse(customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id)));
    }

    /**
     * Admin cập nhật: thời hạn truy cập, AI mode, trạng thái active.
     */
    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerUpdateRequest req) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));

        if (req.getAccessExpiresAt() != null) {
            c.setAccessExpiresAt(req.getAccessExpiresAt());
            c.setActive(true); // Tự động re-activate khi gia hạn
            log.info("Extended access for customer [{}] until [{}]", c.getEmail(), req.getAccessExpiresAt());
        }
        if (req.getAiModeEnabled() != null) {
            c.setAiModeEnabled(req.getAiModeEnabled());
            log.info("AI mode [{}] for customer [{}]", req.getAiModeEnabled(), c.getEmail());
        }
        if (req.getActive() != null) {
            c.setActive(req.getActive());
        }

        return toResponse(customerRepository.save(c));
    }

    /**
     * Admin gia hạn nhanh: thêm N ngày kể từ hôm nay (hoặc từ ngày hết hạn hiện tại nếu chưa hết).
     */
    @Transactional
    public CustomerResponse extendAccess(Long id, int days) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));

        LocalDateTime base = (c.getAccessExpiresAt() != null && c.getAccessExpiresAt().isAfter(LocalDateTime.now()))
                ? c.getAccessExpiresAt()
                : LocalDateTime.now();

        c.setAccessExpiresAt(base.plusDays(days));
        c.setActive(true);
        log.info("Extended access +{}d for customer [{}], new expiry [{}]",
                days, c.getEmail(), c.getAccessExpiresAt());

        return toResponse(customerRepository.save(c));
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .email(c.getEmail())
                .fullName(c.getFullName())
                .phoneNumber(c.getPhoneNumber())
                .role(c.getRole().name())
                .active(c.isActive())
                .aiModeEnabled(c.isAiModeEnabled())
                .accessExpiresAt(c.getAccessExpiresAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}