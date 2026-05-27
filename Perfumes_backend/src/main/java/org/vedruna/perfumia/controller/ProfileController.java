package org.vedruna.perfumia.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.security.controller.converter.UserConverter;
import org.vedruna.perfumia.security.controller.dto.ProfileUpdateResponseDTO;
import org.vedruna.perfumia.security.controller.dto.UpdateProfileImageRequestDTO;
import org.vedruna.perfumia.security.controller.dto.UpdateProfileRequestDTO;
import org.vedruna.perfumia.security.controller.dto.UserDTO;
import org.vedruna.perfumia.security.service.JWTServiceImpl;
import org.vedruna.perfumia.service.ProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/profile")
@AllArgsConstructor
@Tag(name = "Perfil", description = "Endpoints para actualizar datos publicos del perfil del usuario autenticado.")
public class ProfileController {

    private final ProfileService profileService;
    private final UserConverter userConverter;
    private final JWTServiceImpl jwtService;

    /**
     * Actualiza la URL de imagen de perfil del usuario autenticado.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param request DTO con la URL segura de Cloudinary.
     * @return datos publicos actualizados del usuario.
     */
    @PatchMapping("/image")
    @Operation(summary = "Actualizar imagen de perfil", description = "Guarda una URL de imagen de perfil previamente subida a Cloudinary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen de perfil actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "URL de imagen no valida"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public UserDTO updateProfileImage(@AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileImageRequestDTO request) {
        return userConverter.toDto(profileService.updateProfileImage(user, request.getProfileImageUrl()));
    }

    /**
     * Actualiza nombre publico y descripcion del usuario autenticado. Devuelve
     * tambien tokens renovados porque el JWT usa el username como subject.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param request DTO con nombre publico y descripcion.
     * @return perfil actualizado y nuevos tokens.
     */
    @PatchMapping
    @Operation(summary = "Actualizar perfil", description = "Actualiza username y descripcion si el username esta disponible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de perfil no validos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public ProfileUpdateResponseDTO updateProfile(@AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        User updatedUser = profileService.updateProfile(user, request.getUsername(), request.getDescription());
        return ProfileUpdateResponseDTO.builder()
                .user(userConverter.toDto(updatedUser))
                .accessToken(jwtService.generateAccessToken(updatedUser))
                .refreshToken(jwtService.generateRefreshToken(updatedUser))
                .expiresIn(jwtService.getAccessTokenExpiresIn())
                .build();
    }
}
