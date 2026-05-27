package org.vedruna.perfumia.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vedruna.perfumia.controller.dto.CommunityMessageDTO;
import org.vedruna.perfumia.controller.dto.CommunityMessageRequestDTO;
import org.vedruna.perfumia.controller.dto.CommunityProfileDTO;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.service.CommunityChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/community/messages")
@AllArgsConstructor
@Tag(name = "Chat Comunidad", description = "Mensajes públicos entre usuarios de PerfumIA.")
public class CommunityChatController {

    private final CommunityChatService communityChatService;

    @GetMapping
    @Operation(summary = "Listar mensajes de comunidad", description = "Devuelve los últimos mensajes públicos de la comunidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensajes obtenidos correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public List<CommunityMessageDTO> list() {
        return communityChatService.listMessages();
    }

    @PostMapping
    @Operation(summary = "Enviar mensaje de comunidad", description = "Publica un mensaje público visible para otros usuarios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensaje publicado correctamente"),
            @ApiResponse(responseCode = "400", description = "Mensaje no valido"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public CommunityMessageDTO send(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CommunityMessageRequestDTO request) {
        return communityChatService.sendMessage(user, request.getContent());
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Ver perfil de comunidad", description = "Devuelve el perfil publico y las recomendaciones visibles de un usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil obtenido correctamente"),
            @ApiResponse(responseCode = "400", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public CommunityProfileDTO profile(@PathVariable Integer userId) {
        return communityChatService.findCommunityProfile(userId);
    }
}
