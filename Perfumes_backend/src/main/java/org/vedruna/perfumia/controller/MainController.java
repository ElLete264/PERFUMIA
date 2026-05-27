package org.vedruna.perfumia.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/")
@Tag(name = "Estado API", description = "Endpoints simples para comprobar acceso publico, privado y de administrador.")
public class MainController {
    
    /**
     * Endpoint publico de comprobacion basica de disponibilidad.
     *
     * @return mensaje de disponibilidad de la API.
     */
    @GetMapping("public")
    @Operation(summary = "Comprobar API publica", description = "Endpoint publico para verificar que el backend responde.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API disponible")
    })
    public String index() {
        return "PerfumIA API disponible";
    }

    /**
     * Endpoint privado para comprobar que un token JWT valido permite acceder.
     *
     * @return mensaje de acceso privado.
     */
    @GetMapping("private")
    @Operation(summary = "Comprobar acceso privado", description = "Endpoint protegido para verificar autenticacion con JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Acceso privado permitido"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public String privateIndex() {
        return "Hello world desde endpoint private";
    }

    /**
     * Endpoint de prueba reservado a usuarios con rol ADMIN.
     *
     * @return mensaje de acceso de administrador.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("admin")
    @Operation(summary = "Comprobar acceso admin", description = "Endpoint protegido para verificar autorizacion con rol ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Acceso admin permitido"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido"),
            @ApiResponse(responseCode = "403", description = "Usuario autenticado sin rol ADMIN")
    })
    public String adminIndex() {
        return "Hello world desde endpoint protegido para admin";
    }
}

