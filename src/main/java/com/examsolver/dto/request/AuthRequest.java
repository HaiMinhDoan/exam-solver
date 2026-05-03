package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthRequest {

    @Data
    public static class Register {
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        private String email;

        @Pattern(regexp = "^[0-9]{10,11}$", message = "Invalid phone number")
        private String phoneNumber;

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
    }

    @Data
    public static class Login {
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }
}
