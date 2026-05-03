package com.examsolver.service.impl;

import com.examsolver.dto.request.AuthRequest;
import com.examsolver.dto.response.AuthResponse;
import com.examsolver.entity.Customer;
import com.examsolver.exception.BusinessException;
import com.examsolver.repository.CustomerRepository;
import com.examsolver.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(AuthRequest.Register req) {
        if (customerRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("Email đã tồn tại: " + req.getEmail());
        }
        if (req.getPhoneNumber() != null && customerRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new BusinessException("Số điện thoại đã tồn tại: " + req.getPhoneNumber());
        }

        Customer customer = Customer.builder()
                .email(req.getEmail())
                .phoneNumber(req.getPhoneNumber())
                .fullName(req.getFullName())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Customer.Role.CUSTOMER)
                .active(true)
                .build();

        customerRepository.save(customer);
        log.info("Registered new customer: {}", req.getEmail());

        String token = jwtService.generateToken(customer.getEmail(), customer.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .email(customer.getEmail())
                .fullName(customer.getFullName())
                .role(customer.getRole().name())
                .build();
    }

    public AuthResponse login(AuthRequest.Login req) {
        Customer customer = customerRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không đúng"));

        if (!customer.isActive()) {
            throw new BusinessException("Tài khoản đã bị vô hiệu hóa");
        }

        if (!passwordEncoder.matches(req.getPassword(), customer.getPasswordHash())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }

        String token = jwtService.generateToken(customer.getEmail(), customer.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .email(customer.getEmail())
                .fullName(customer.getFullName())
                .role(customer.getRole().name())
                .build();
    }
}
