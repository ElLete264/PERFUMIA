package org.vedruna.perfumia.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vedruna.perfumia.controller.dto.ChatRequestDTO;
import org.vedruna.perfumia.controller.dto.ChatResponseDTO;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.service.GeminiService;
import org.vedruna.perfumia.service.PerfumeCatalogService;
import org.vedruna.perfumia.service.PerfumeAdvisorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/chat")
@AllArgsConstructor
@Tag(name = "Chat IA", description = "Endpoints para conversar con PerfumIA y consultar el estado de Gemini y Fragella.")
public class ChatController {

    private final PerfumeAdvisorService perfumeAdvisorService;
    private final GeminiService geminiService;
    private final PerfumeCatalogService perfumeCatalogService;

    /**
     * Informa al frontend de si Gemini y Fragella estan configurados o si se usara
     * el modo local de respaldo.
     *
     * @return mapa con el estado de proveedores externos y catalogo.
     */
    @GetMapping("/status")
    @Operation(summary = "Estado del chat", description = "Comprueba si Gemini y Fragella estan configurados para el asesor de perfumes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado consultado correctamente")
    })
    public Map<String, Object> status() {
        return Map.of(
                "geminiConfigured", geminiService.isConfigured(),
                "geminiAvailable", geminiService.isAvailable(),
                "geminiStatus", geminiService.status(),
                "geminiRetryAfterSeconds", geminiService.retryAfterSeconds(),
                "fragellaConfigured", perfumeCatalogService.isConfigured(),
                "fragellaAvailable", perfumeCatalogService.isAvailable(),
                "fragellaStatus", perfumeCatalogService.status(),
                "fragellaRetryAfterSeconds", perfumeCatalogService.retryAfterSeconds(),
                "provider", geminiService.isAvailable() ? "Gemini" : "Local fallback",
                "catalog", perfumeCatalogService.isAvailable() ? "Fragella" : "Local fallback");
    }

    @GetMapping("/gemini/probe")
    @Operation(summary = "Prueba real de Gemini", description = "Hace una llamada corta a Gemini sin exponer la API key para diagnosticar la integracion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prueba ejecutada correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public Map<String, Object> geminiProbe() {
        return geminiService.probe();
    }

    /**
     * Devuelve el primer mensaje del asesor olfativo para el usuario autenticado.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @return respuesta inicial del chat.
     */
    @GetMapping("/welcome")
    @Operation(summary = "Mensaje inicial del chat", description = "Genera el saludo inicial de PerfumIA para comenzar la recomendacion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensaje inicial generado correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public ChatResponseDTO welcome(@AuthenticationPrincipal User user) {
        return perfumeAdvisorService.welcome(user);
    }

    /**
     * Limpia la conversacion actual y reinicia el perfil temporal para comenzar
     * una nueva recomendacion desde cero.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @return nuevo mensaje inicial del chat.
     */
    @PostMapping("/reset")
    @Operation(summary = "Reiniciar chat", description = "Borra el historial de mensajes del chat y empieza una recomendacion desde cero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chat reiniciado correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public ChatResponseDTO reset(@AuthenticationPrincipal User user) {
        return perfumeAdvisorService.resetConversation(user);
    }

    /**
     * Procesa un mensaje del usuario y devuelve la siguiente respuesta del asesor.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param request DTO con el mensaje escrito en el chat.
     * @return respuesta generada por el asesor olfativo.
     */
    @PostMapping
    @Operation(summary = "Enviar mensaje al chat", description = "Procesa el mensaje del usuario y avanza la recomendacion de perfumes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta generada correctamente"),
            @ApiResponse(responseCode = "400", description = "Mensaje no valido"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public ChatResponseDTO chat(@AuthenticationPrincipal User user, @Valid @RequestBody ChatRequestDTO request) {
        return perfumeAdvisorService.chat(user, request.getMessage());
    }
}
