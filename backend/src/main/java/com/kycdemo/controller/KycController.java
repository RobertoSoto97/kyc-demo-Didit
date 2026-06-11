package com.kycdemo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kycdemo.dto.UserDtos.*;
import com.kycdemo.service.DiditService.DiditSession;
import com.kycdemo.service.KycService;
import com.kycdemo.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class KycController {

    private static final Logger log = LoggerFactory.getLogger(KycController.class);

    private final KycService kycService;
    private final UserService userService;
    private final ObjectMapper mapper = new ObjectMapper();

    public KycController(KycService kycService, UserService userService) {
        this.kycService = kycService;
        this.userService = userService;
    }

    // ─── USUARIO ─────────────────────────────────────────────────────────────

    /**
     * Inicia el proceso KYC para un usuario.
     * Crea una sesión en Didit y devuelve la URL donde el usuario
     * completa la verificación (captura del DNI + liveness).
     *
     * El frontend redirige al usuario a esa URL.
     */
    @PostMapping("/api/kyc/initiate/{userId}")
    public ResponseEntity<?> initiate(@PathVariable Long userId) {
        try {
            DiditSession session = kycService.initiateKyc(userId);

            KycSessionResponse response = new KycSessionResponse();
            response.setSessionId(session.sessionId());
            response.setVerificationUrl(session.verificationUrl());
            response.setUserId(String.valueOf(userId));

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error iniciando KYC para userId {}: ", userId, e);
            return ResponseEntity.internalServerError().body(
                "No se pudo iniciar la verificación: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }

    /**
     * Polling del estado KYC del usuario.
     * El frontend llama esto cada 5s para saber si Didit ya respondió.
     */
    @GetMapping("/api/kyc/status/{userId}")
    public ResponseEntity<?> status(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(userService.getById(userId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ─── WEBHOOK DE DIDIT ────────────────────────────────────────────────────

    /**
     * Endpoint que Didit llama cuando termina una verificación.
     *
     * Didit envía un POST con el resultado de la sesión.
     * Este endpoint es el equivalente del webhook de Sumsub —
     * cuando llega, actualizamos el estado del usuario automáticamente.
     *
     * URL a configurar en Didit Dashboard → Webhooks → Añadir destino:
     *   Para desarrollo local: https://TU-NGROK-URL/api/kyc/webhook
     *   Para producción: https://TU-DOMINIO/api/kyc/webhook
     *
     * Estructura del payload de Didit:
     * {
     *   "session_id": "...",
     *   "status": "APPROVED" | "DECLINED" | "IN_REVIEW",
     *   "vendor_data": "userId-nuestro",
     *   "decline_reasons": ["FAKE_DOCUMENT", "LIVENESS_FAILED", ...]
     * }
     *
     * En producción: verificar la firma del webhook con el header
     * X-Didit-Signature para validar que viene realmente de Didit.
     */
    @PostMapping("/api/kyc/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Didit-Signature", required = false) String signature) {
        try {
            log.info("Webhook Didit recibido: {}", rawBody);
            JsonNode payload = mapper.readTree(rawBody);

            String sessionId    = payload.path("session_id").asText(null);
            String status       = payload.path("status").asText(null);
            String vendorData   = payload.path("vendor_data").asText(null);

            // Extraer primera razón de rechazo si existe
            String declineReason = null;
            JsonNode reasons = payload.path("decline_reasons");
            if (reasons.isArray() && reasons.size() > 0) {
                declineReason = reasons.get(0).asText();
            }

            if (sessionId == null || status == null) {
                log.warn("Webhook Didit con payload incompleto: {}", rawBody);
                return ResponseEntity.badRequest().build();
            }

            kycService.processWebhook(sessionId, status, vendorData, declineReason);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error procesando webhook Didit: {}", e.getMessage());
            // Devolver 200 para que Didit no reintente indefinidamente
            return ResponseEntity.ok().build();
        }
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/kyc/users")
    public ResponseEntity<List<UserResponse>> listUsers(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(userService.getUsersByStatus(status));
    }

    @PostMapping("/api/admin/kyc/decide/{userId}")
    public ResponseEntity<?> decide(@PathVariable Long userId,
                                    @Valid @RequestBody KycDecisionRequest req) {
        try {
            kycService.processAdminDecision(userId, req.getDecision(),
                req.getRejectReason(), req.getAdminComment());
            return ResponseEntity.ok(Map.of(
                "message", "Decisión aplicada: " + req.getDecision(),
                "user", userService.getById(userId)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
