package com.kycdemo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String dni;
    private String telefono;
    private String fechaNacimiento;

    /**
     * ID de la sesión de verificación en Didit.
     * Didit lo llama "session_id" y lo devuelve al crear una sesión.
     * Lo usamos para vincular el webhook con nuestro usuario:
     * cuando Didit llama al webhook, incluye este session_id
     * y nosotros encontramos al usuario correspondiente.
     *
     * Nota: los documentos del usuario (fotos del DNI, selfie)
     * nunca llegan a nuestro backend — Didit los procesa en sus
     * servidores y solo nos notifica el resultado via webhook.
     */
    @Column(name = "didit_session_id")
    private String diditSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

    @Column(name = "kyc_reject_reason")
    private String kycRejectReason;

    @Column(name = "kyc_admin_comment")
    private String kycAdminComment;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(name = "kyc_submitted_at")
    private LocalDateTime kycSubmittedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum KycStatus {
        NOT_STARTED,
        PENDING,     // Sesión creada en Didit, usuario haciendo el proceso
        IN_REVIEW,   // Didit está procesando (liveness, doc check, etc.)
        APPROVED,    // Webhook recibido: verification_status = APPROVED
        DECLINED     // Webhook recibido: verification_status = DECLINED
    }
}
