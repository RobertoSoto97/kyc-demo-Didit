package com.kycdemo.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Servicio actualizado para la API v3 de Didit.
 *
 * En la v3, ya no es necesario obtener un access_token via OAuth2.
 * Se utiliza directamente la API Key en el header 'x-api-key'.
 */
@Service
public class DiditService {

    private static final Logger log = LoggerFactory.getLogger(DiditService.class);

    @Value("${didit.api-key}")
    private String apiKey;

    @Value("${didit.workflow-id}")
    private String workflowId;

    @Value("${didit.api-url}")
    private String apiUrl;

    @Value("${didit.callback-url:}")
    private String callbackUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Crea una sesión de verificación en Didit v3.
     */
    public DiditSession createVerificationSession(String externalUserId, String userEmail) throws Exception {
        log.info("Iniciando creación de sesión Didit v3 para userId: {}", externalUserId);

        if (apiKey == null || workflowId == null || apiUrl == null) {
            throw new RuntimeException("Configuración de Didit incompleta en variables de entorno.");
        }

        String cleanApiKey = apiKey.trim();
        String cleanWorkflowId = workflowId.trim();

        // Construir el body para v3
        // IMPORTANTE: El campo 'callback' no puede ser un string vacío ("").
        // Si no tenemos URL de retorno, es mejor no enviarlo.
        ObjectNode bodyNode = mapper.createObjectNode();
        bodyNode.put("workflow_id", cleanWorkflowId);
        bodyNode.put("vendor_data", externalUserId);
        
        // Solo añadir callback si tuviéramos una URL real
        // bodyNode.put("callback", "https://tudominio.com/gracias");
        
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            bodyNode.put("callback", callbackUrl);
        }

        String body = mapper.writeValueAsString(bodyNode);

        log.debug("Enviando request a Didit v3 ({}): {}", apiUrl + "/v3/session/", body);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl + "/v3/session/"))
            .header("x-api-key", cleanApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.debug("Didit createSession v3 response → {}: {}", response.statusCode(), response.body());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            log.error("Error en createVerificationSession v3. Status: {} Body: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error creando sesión Didit: " + response.statusCode() + " " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String sessionId = json.path("session_id").asText();
        String verificationUrl = json.path("url").asText();

        if (sessionId.isBlank() || verificationUrl.isBlank()) {
            throw new RuntimeException("Respuesta de Didit v3 incompleta: " + response.body());
        }

        log.info("Sesión Didit v3 creada: {} para userId: {}", sessionId, externalUserId);
        return new DiditSession(sessionId, verificationUrl);
    }

    /**
     * Consulta el estado en v3.
     */
    public String getSessionStatus(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl + "/v3/session/" + sessionId))
            .header("x-api-key", apiKey.trim())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error consultando sesión Didit: " + response.statusCode());
        }

        JsonNode json = mapper.readTree(response.body());
        return json.path("status").asText("UNKNOWN");
    }

    public record DiditSession(String sessionId, String verificationUrl) {}
}
