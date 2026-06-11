package com.kycdemo.service;

import com.kycdemo.model.User;
import com.kycdemo.model.User.KycStatus;
import com.kycdemo.repository.UserRepository;
import com.kycdemo.service.DiditService.DiditSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Orquesta el flujo KYC usando Didit como proveedor.
 *
 * Responsabilidades:
 * 1. Iniciar una sesión de verificación en Didit y guardar el sessionId
 * 2. Procesar el webhook que Didit envía cuando termina la verificación
 * 3. Actualizar el estado del usuario (APPROVED / DECLINED) automáticamente
 * 4. Permitir al admin hacer overrides manuales desde el panel
 */
@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final UserRepository userRepository;
    private final DiditService diditService;

    public KycService(UserRepository userRepository, DiditService diditService) {
        this.userRepository = userRepository;
        this.diditService = diditService;
    }

    /**
     * Inicia el proceso KYC para un usuario:
     * 1. Llama a Didit para crear una sesión de verificación
     * 2. Guarda el sessionId en la DB (para vincular el webhook después)
     * 3. Devuelve la URL donde el usuario completa el proceso
     *
     * Si el usuario ya tiene una sesión previa no aprobada, crea una nueva
     * (permite reintentar en caso de rechazo).
     */
    @Transactional
    public DiditSession initiateKyc(Long userId) throws Exception {
        User user = findUser(userId);

        if (KycStatus.APPROVED.equals(user.getKycStatus())) {
            throw new IllegalStateException("El usuario ya tiene KYC aprobado");
        }

        // Crear sesión en Didit
        DiditSession session = diditService.createVerificationSession(
            String.valueOf(userId),
            user.getEmail()
        );

        // Guardar el sessionId para poder vincular el webhook
        user.setDiditSessionId(session.sessionId());
        user.setKycStatus(KycStatus.PENDING);
        user.setKycSubmittedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("KYC iniciado para usuario {} → sessionId: {}", user.getEmail(), session.sessionId());
        return session;
    }

    /**
     * Procesa el webhook que Didit envía al terminar una verificación.
     *
     * Didit envía un POST a nuestra URL con el resultado.
     * El campo vendor_data contiene nuestro userId (lo pusimos al crear la sesión).
     *
     * Posibles valores de verification_status que mapea Didit:
     * - "APPROVED"  → usuario verificado, puede operar
     * - "DECLINED"  → rechazado (documento falso, liveness fallido, sancionado, etc.)
     * - "IN_REVIEW" → requiere revisión humana adicional
     */
    @Transactional
    public void processWebhook(String sessionId, String status,
                               String vendorData, String declineReason) {
        // Buscar por sessionId o por vendorData (nuestro userId)
        Optional<User> optUser = userRepository.findByDiditSessionId(sessionId);

        if (optUser.isEmpty() && vendorData != null) {
            try {
                Long userId = Long.parseLong(vendorData);
                optUser = userRepository.findById(userId);
            } catch (NumberFormatException ignored) {}
        }

        if (optUser.isEmpty()) {
            log.warn("Webhook Didit: no se encontró usuario para sessionId={} vendorData={}", sessionId, vendorData);
            return;
        }

        User user = optUser.get();
        log.info("Webhook Didit → usuario: {} status: {}", user.getEmail(), status);

        switch (status) {
            case "APPROVED" -> {
                user.setKycStatus(KycStatus.APPROVED);
                user.setKycVerifiedAt(LocalDateTime.now());
                user.setKycRejectReason(null);
                log.info("✅ Usuario {} APROBADO por Didit", user.getEmail());
            }
            case "DECLINED" -> {
                user.setKycStatus(KycStatus.DECLINED);
                user.setKycRejectReason(declineReason != null ? declineReason : "VERIFICATION_FAILED");
                log.info("❌ Usuario {} RECHAZADO por Didit. Razón: {}", user.getEmail(), declineReason);
            }
            case "IN_REVIEW" -> {
                user.setKycStatus(KycStatus.IN_REVIEW);
                log.info("🔍 Usuario {} en revisión manual en Didit", user.getEmail());
            }
            default -> log.warn("Estado Didit desconocido: {}", status);
        }

        userRepository.save(user);
    }

    /**
     * Override manual del admin — mismo comportamiento de siempre.
     * Útil para casos excepcionales o cuando el webhook no llegó.
     */
    @Transactional
    public void processAdminDecision(Long userId, String decision,
                                     String rejectReason, String adminComment) {
        User user = findUser(userId);
        switch (decision) {
            case "APPROVED" -> {
                user.setKycStatus(KycStatus.APPROVED);
                user.setKycVerifiedAt(LocalDateTime.now());
                user.setKycRejectReason(null);
                user.setKycAdminComment(adminComment);
            }
            case "DECLINED" -> {
                user.setKycStatus(KycStatus.DECLINED);
                user.setKycRejectReason(rejectReason);
                user.setKycAdminComment(adminComment);
            }
            case "IN_REVIEW" -> {
                user.setKycStatus(KycStatus.IN_REVIEW);
                user.setKycAdminComment(adminComment);
            }
            default -> throw new IllegalArgumentException("Decisión inválida: " + decision);
        }
        log.info("Admin override para usuario {} → {}", user.getEmail(), decision);
        userRepository.save(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + id));
    }
}
