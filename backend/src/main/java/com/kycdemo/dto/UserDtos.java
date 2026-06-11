package com.kycdemo.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class UserDtos {

    @Data
    public static class RegisterRequest {
        @NotBlank private String nombre;
        @NotBlank private String apellido;
        @Email @NotBlank private String email;
        @NotBlank private String password;
        private String dni;
        private String telefono;
        private String fechaNacimiento;
    }

    @Data
    public static class LoginRequest {
        @Email @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    public static class UserResponse {
        private Long id;
        private String nombre;
        private String apellido;
        private String email;
        private String dni;
        private String kycStatus;
        private String kycRejectReason;
        private String kycAdminComment;
        private boolean admin;
        private String kycSubmittedAt;
        private String kycVerifiedAt;
    }

    // Respuesta al iniciar KYC: devuelve la URL donde el usuario completa el proceso
    @Data
    public static class KycSessionResponse {
        private String sessionId;
        private String verificationUrl; // URL del widget de Didit
        private String userId;
    }

    @Data
    public static class KycDecisionRequest {
        @NotBlank private String decision;
        private String rejectReason;
        private String adminComment;
    }
}
