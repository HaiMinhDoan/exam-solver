package com.examsolver.config;

import com.examsolver.entity.Customer;
import com.examsolver.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@examsolver.vn}")
    private String adminEmail;

    @Value("${admin.password:Admin@12345}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!customerRepository.existsByEmail(adminEmail)) {
            Customer admin = Customer.builder()
                    .email(adminEmail)
                    .fullName("System Admin")
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Customer.Role.ADMIN)
                    .active(true)
                    .build();
            customerRepository.save(admin);
            log.info("Default admin created: {}", adminEmail);
        }
    }
}
