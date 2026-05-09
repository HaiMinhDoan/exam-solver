package com.examsolver.controller;

import com.examsolver.dto.response.ApiKeyResponse;
import com.examsolver.dto.response.ApiResponse;
import com.examsolver.entity.Customer;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Khách hàng tự xem thông tin API key của mình.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Object>> profile(Authentication auth) {
        Customer customer = customerRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of(
                "email", customer.getEmail(),
                "fullName", customer.getFullName() != null ? customer.getFullName() : "",
                "phoneNumber", customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "",
                "role", customer.getRole().name(),
                "createdAt", customer.getCreatedAt()
        )));
    }
}
