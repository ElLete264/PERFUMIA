package org.vedruna.perfumia.security.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.security.controller.converter.UserConverter;
import org.vedruna.perfumia.security.controller.dto.AuthResponseDTO;
import org.vedruna.perfumia.security.controller.dto.GoogleLoginRequestDTO;
import org.vedruna.perfumia.security.controller.dto.LoginRequestDTO;
import org.vedruna.perfumia.security.controller.dto.RefreshRequestDTO;
import org.vedruna.perfumia.security.controller.dto.RegisterRequestDTO;
import org.vedruna.perfumia.security.controller.dto.UserDTO;
import org.vedruna.perfumia.security.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Controlador REST encargado de manejar todas las peticiones relacionadas 
 * con la autenticación y gestión de usuarios (registro, login, obtener 
 * usuario actual).
 * 
 * Utiliza el path base "/auth". CORS se configura de forma centralizada en SecurityConfig.
 */
@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@Tag(name = "Autenticacion", description = "Endpoints de registro, login, Google Login, perfil actual y renovacion de tokens.")
public class AuthController {
    
    /** Servicio de lógica de negocio para las operaciones de autenticación. */
    private final AuthService authService;

    /** Componente para convertir entre entidades User y DTOs. */
    private final UserConverter userConverter;

    /**
     * Endpoint para registrar un nuevo usuario en el sistema.
     * 
     * Recibe un DTO de solicitud de registro, lo convierte a entidad, 
     * lo registra a través del servicio y devuelve el DTO del usuario 
     * creado con un estado HTTP 201 (CREATED).
     * 
     * @param request El DTO que contiene los datos de registro (nombre, 
     * contraseña, email, etc.).
     * @return ResponseEntity que contiene el UserDTO del usuario registrado 
     * y el estado HTTP CREATED.
     */
    @PostMapping(value = "/register")
    @Operation(summary = "Registrar usuario", description = "Crea una cuenta local y devuelve los datos publicos del usuario registrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de registro no validos"),
            @ApiResponse(responseCode = "409", description = "Ya existe un usuario con esos datos")
    })
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            userConverter.toDto(  
                authService.register(
                    userConverter.registerToEntity(request)
                )
            )
            
        );
    }

    /**
     * Endpoint para la autenticación (inicio de sesión) de un usuario.
     * 
     * Recibe un DTO de solicitud de login, lo convierte a entidad, 
     * llama al servicio para autenticar y obtener el token JWT, 
     * y devuelve el token con un estado HTTP 200 (OK).
     * 
     * @param request El DTO que contiene las credenciales de login 
     * (nombre de usuario y contraseña).
     * @return ResponseEntity que contiene el AuthResponseDTO (con el JWT) 
     * y el estado HTTP OK.
     */
    @PostMapping(value = "/login")
    @Operation(summary = "Iniciar sesion", description = "Valida las credenciales locales y devuelve access token y refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login correcto"),
            @ApiResponse(responseCode = "400", description = "Datos de login no validos"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(
            authService.login(
                    userConverter.loginToEntity(request)
            ));
    }

    /**
     * Endpoint para iniciar sesion mediante Google.
     *
     * @param request DTO con el id token emitido por Google.
     * @return tokens JWT propios de PerfumIA para usar el backend.
     */
    @PostMapping(value = "/google")
    @Operation(summary = "Iniciar sesion con Google", description = "Valida el id token de Google y crea o recupera el usuario asociado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login con Google correcto"),
            @ApiResponse(responseCode = "400", description = "Token de Google no valido"),
            @ApiResponse(responseCode = "401", description = "No se pudo verificar la identidad con Google")
    })
    public ResponseEntity<AuthResponseDTO> googleLogin(@Valid @RequestBody GoogleLoginRequestDTO request) {
        return ResponseEntity.ok(authService.googleLogin(request.getIdToken()));
    }

    /**
     * Endpoint para obtener los datos del usuario actualmente autenticado 
     * a través del token JWT.
     * 
     * Utiliza la anotación @AuthenticationPrincipal de Spring Security 
     * para inyectar automáticamente la entidad User del usuario logueado.
     * 
     * @param userLogueado La entidad User del usuario autenticado, 
     * inyectada por Spring Security.
     * @return UserDTO que contiene la información pública del usuario.
     */
    @GetMapping(value="/me")
    @Operation(summary = "Obtener perfil actual", description = "Devuelve los datos publicos del usuario autenticado con JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil obtenido correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public UserDTO me(@AuthenticationPrincipal User userLogueado) {
        return userConverter.toDto(userLogueado);
    }

    /**
     * Endpoint para renovar el Access Token utilizando un Refresh Token.
     *
     *  @param request El cuerpo de la solicitud que contiene el Refresh Token.
     * @return ResponseEntity con el nuevo Access Token y el Refresh Token.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Renovar token", description = "Genera un nuevo access token a partir de un refresh token valido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token renovado correctamente"),
            @ApiResponse(responseCode = "400", description = "Refresh token vacio o mal formado"),
            @ApiResponse(responseCode = "401", description = "Refresh token caducado o no valido")
    })
    public ResponseEntity<AuthResponseDTO> refreshToken(@Valid @RequestBody RefreshRequestDTO request) {
        // Devuelve 200 OK con el nuevo par de tokens
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }
}

