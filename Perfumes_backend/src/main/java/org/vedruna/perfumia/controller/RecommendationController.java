package org.vedruna.perfumia.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vedruna.perfumia.controller.dto.PerfumeRecommendationDTO;
import org.vedruna.perfumia.controller.dto.PerfumeRatingSummaryDTO;
import org.vedruna.perfumia.controller.dto.UpdateFavoriteRequestDTO;
import org.vedruna.perfumia.controller.dto.UpdateRatingRequestDTO;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.service.PerfumeAdvisorService;
import org.vedruna.perfumia.service.RecommendationPersistenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/recommendations")
@AllArgsConstructor
@Tag(name = "Recomendaciones", description = "Endpoints para consultar y aceptar recomendaciones de perfumes.")
public class RecommendationController {

    private final PerfumeAdvisorService perfumeAdvisorService;
    private final RecommendationPersistenceService recommendationPersistenceService;

    /**
     * Lista las recomendaciones guardadas para el usuario autenticado.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @return recomendaciones asociadas al perfil del usuario.
     */
    @GetMapping
    @Operation(summary = "Listar recomendaciones", description = "Devuelve el historial de recomendaciones de perfumes del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendaciones obtenidas correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public List<PerfumeRecommendationDTO> list(@AuthenticationPrincipal User user) {
        return perfumeAdvisorService.listRecommendations(user);
    }

    /**
     * Consulta los perfumes mejor valorados por la comunidad de usuarios.
     *
     * @return resumen global de perfumes con mejor media de puntuacion.
     */
    @GetMapping("/ratings/top")
    @Operation(summary = "Perfumes mejor valorados", description = "Devuelve un ranking global con los perfumes que tienen mejor media de rating en PerfumIA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking obtenido correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public List<PerfumeRatingSummaryDTO> topRated() {
        return recommendationPersistenceService.findTopRatedPerfumes(5);
    }

    /**
     * Consulta los perfumes peor valorados por la comunidad de usuarios.
     *
     * @return resumen global de perfumes con peor media de puntuacion.
     */
    @GetMapping("/ratings/worst")
    @Operation(summary = "Perfumes peor valorados", description = "Devuelve un ranking global con los perfumes que tienen peor media de rating en PerfumIA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking obtenido correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public List<PerfumeRatingSummaryDTO> worstRated() {
        return recommendationPersistenceService.findWorstRatedPerfumes(5);
    }

    /**
     * Calcula y guarda hasta tres recomendaciones nuevas excluyendo las que el
     * usuario ya tiene guardadas, aceptadas o rechazadas.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @return nuevas recomendaciones pendientes ordenadas por compatibilidad.
     */
    @PostMapping("/more")
    @Operation(summary = "Mostrar mas recomendaciones", description = "Devuelve hasta tres recomendaciones nuevas usando el perfil actual, Fragella y el scoring con historial, excluyendo perfumes ya recomendados al usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nuevas recomendaciones obtenidas correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido")
    })
    public List<PerfumeRecommendationDTO> more(@AuthenticationPrincipal User user) {
        return perfumeAdvisorService.moreRecommendations(user);
    }

    /**
     * Marca una recomendacion como aceptada y la guarda como preferencia del usuario.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param recommendationId identificador de la recomendacion que se acepta.
     * @return recomendacion actualizada.
     */
    @PostMapping("/{recommendationId}/accept")
    @Operation(summary = "Aceptar recomendacion", description = "Confirma una recomendacion concreta para guardarla como perfume aceptado por el usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendacion aceptada correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido"),
            @ApiResponse(responseCode = "404", description = "Recomendacion no encontrada")
    })
    public PerfumeRecommendationDTO accept(@AuthenticationPrincipal User user, @PathVariable Integer recommendationId) {
        return perfumeAdvisorService.acceptRecommendation(user, recommendationId);
    }

    /**
     * Marca una recomendacion como rechazada para descartarla de futuras opciones.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param recommendationId identificador de la recomendacion que se descarta.
     * @return recomendacion actualizada.
     */
    @PostMapping("/{recommendationId}/reject")
    @Operation(summary = "Descartar recomendacion", description = "Marca una recomendacion concreta como rechazada para no volver a proponerla.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendacion descartada correctamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido"),
            @ApiResponse(responseCode = "404", description = "Recomendacion no encontrada")
    })
    public PerfumeRecommendationDTO reject(@AuthenticationPrincipal User user, @PathVariable Integer recommendationId) {
        return recommendationPersistenceService.rejectRecommendation(user, recommendationId);
    }

    /**
     * Marca o desmarca una recomendacion como favorita.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param recommendationId identificador de la recomendacion.
     * @param request cuerpo con el nuevo valor de favorito.
     * @return recomendacion actualizada.
     */
    @PatchMapping("/{recommendationId}/favorite")
    @Operation(summary = "Actualizar favorito", description = "Marca o desmarca una recomendacion concreta como favorita sin cambiar su estado de aceptacion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorito actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de favorito no validos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido"),
            @ApiResponse(responseCode = "404", description = "Recomendacion no encontrada")
    })
    public PerfumeRecommendationDTO updateFavorite(@AuthenticationPrincipal User user,
            @PathVariable Integer recommendationId,
            @Valid @RequestBody UpdateFavoriteRequestDTO request) {
        return recommendationPersistenceService.updateFavorite(user, recommendationId, request.getFavorite());
    }

    /**
     * Actualiza o elimina la puntuacion de una recomendacion.
     *
     * @param user usuario autenticado obtenido desde el JWT.
     * @param recommendationId identificador de la recomendacion.
     * @param request cuerpo con puntuacion entre 1 y 5, o null para quitarla.
     * @return recomendacion actualizada.
     */
    @PatchMapping("/{recommendationId}/rating")
    @Operation(summary = "Actualizar puntuacion", description = "Guarda una puntuacion personal entre 1 y 5 para una recomendacion, o la elimina si se envia null.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Puntuacion actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Puntuacion no valida"),
            @ApiResponse(responseCode = "401", description = "Token ausente, caducado o no valido"),
            @ApiResponse(responseCode = "404", description = "Recomendacion no encontrada")
    })
    public PerfumeRecommendationDTO updateRating(@AuthenticationPrincipal User user,
            @PathVariable Integer recommendationId,
            @Valid @RequestBody UpdateRatingRequestDTO request) {
        return recommendationPersistenceService.updateRating(user, recommendationId, request.getRating());
    }
}
