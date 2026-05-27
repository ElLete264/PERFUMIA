package org.vedruna.perfumia.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.controller.dto.PerfumeRecommendationDTO;
import org.vedruna.perfumia.controller.dto.PerfumeRatingSummaryDTO;
import org.vedruna.perfumia.persistance.model.ChatMessage;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.ChatMessageRepository;
import org.vedruna.perfumia.persistance.repository.PerfumeRecommendationRepository;
import org.vedruna.perfumia.service.dto.PerfumeItem;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class RecommendationPersistenceService {

    private final ChatMessageRepository chatMessageRepo;
    private final PerfumeRecommendationRepository recommendationRepo;

    /**
     * Guarda un mensaje del historial de chat del usuario.
     *
     * @param user usuario propietario del mensaje.
     * @param role rol del mensaje: USER o ASSISTANT.
     * @param content texto del mensaje.
     */
    @Transactional
    public void saveMessage(User user, String role, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUser(user);
        chatMessage.setRoleName(role);
        chatMessage.setContent(content);
        chatMessage.setCreateDate(LocalDateTime.now());
        chatMessageRepo.save(chatMessage);
    }

    /**
     * Recupera los ultimos mensajes del usuario para construir contexto de IA.
     *
     * @param user usuario autenticado.
     * @return historial reciente ordenado originalmente por fecha descendente.
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> findRecentMessages(User user) {
        return chatMessageRepo.findTop12ByUserOrderByCreateDateDesc(user);
    }

    /**
     * Elimina el historial de mensajes del chat del usuario para empezar una
     * recomendacion desde cero sin perder sus recomendaciones guardadas.
     *
     * @param user usuario autenticado.
     */
    @Transactional
    public void clearChatMessages(User user) {
        chatMessageRepo.deleteByUser(user);
    }

    /**
     * Lista las recomendaciones del usuario convertidas a DTO.
     *
     * @param user usuario autenticado.
     * @return recomendaciones ordenadas por fecha descendente.
     */
    @Transactional(readOnly = true)
    public List<PerfumeRecommendationDTO> listRecommendations(User user) {
        return findRecommendations(user).stream()
                .filter(item -> item.getAccepted() != null)
                .map(this::toDto)
                .toList();
    }

    /**
     * Devuelve los perfumes mejor valorados por todos los usuarios de la
     * aplicacion, agrupando por marca y nombre.
     *
     * @param limit numero maximo de perfumes a devolver.
     * @return ranking global descendente por media de puntuacion.
     */
    @Transactional(readOnly = true)
    public List<PerfumeRatingSummaryDTO> findTopRatedPerfumes(int limit) {
        return buildGlobalRatingRanking(limit, false);
    }

    /**
     * Devuelve los perfumes peor valorados por todos los usuarios de la aplicacion,
     * agrupando por marca y nombre.
     *
     * @param limit numero maximo de perfumes a devolver.
     * @return ranking global ascendente por media de puntuacion.
     */
    @Transactional(readOnly = true)
    public List<PerfumeRatingSummaryDTO> findWorstRatedPerfumes(int limit) {
        return buildGlobalRatingRanking(limit, true);
    }

    /**
     * Recupera todas las recomendaciones del usuario.
     *
     * @param user usuario autenticado.
     * @return recomendaciones ordenadas por fecha descendente.
     */
    @Transactional(readOnly = true)
    public List<PerfumeRecommendation> findRecommendations(User user) {
        return recommendationRepo.findByUserOrderByCreateDateDesc(user);
    }

    /**
     * Recupera las recomendaciones aceptadas. En el modelo actual accepted=true
     * significa aceptada.
     *
     * @param user usuario autenticado.
     * @return recomendaciones aceptadas.
     */
    @Transactional(readOnly = true)
    public List<PerfumeRecommendation> findAcceptedRecommendations(User user) {
        return findRecommendations(user).stream()
                .filter(item -> Boolean.TRUE.equals(item.getAccepted()))
                .toList();
    }

    /**
     * Recupera las recomendaciones rechazadas. En el modelo actual accepted=null
     * significa rechazada.
     *
     * @param user usuario autenticado.
     * @return recomendaciones rechazadas.
     */
    @Transactional(readOnly = true)
    public List<PerfumeRecommendation> findRejectedRecommendations(User user) {
        return findRecommendations(user).stream()
                .filter(item -> item.getAccepted() == null)
                .toList();
    }

    /**
     * Acepta una recomendacion concreta del usuario.
     *
     * @param user usuario autenticado.
     * @param recommendationId identificador de la recomendacion.
     * @return recomendacion aceptada convertida a DTO.
     */
    @Transactional
    public PerfumeRecommendationDTO acceptRecommendation(User user, Integer recommendationId) {
        PerfumeRecommendation recommendation = findOwnedRecommendation(user, recommendationId);
        recommendation.setAccepted(true);
        return toDto(recommendationRepo.save(recommendation));
    }

    /**
     * Rechaza una recomendacion concreta del usuario.
     *
     * @param user usuario autenticado.
     * @param recommendationId identificador de la recomendacion.
     * @return recomendacion rechazada convertida a DTO.
     */
    @Transactional
    public PerfumeRecommendationDTO rejectRecommendation(User user, Integer recommendationId) {
        PerfumeRecommendation recommendation = findOwnedRecommendation(user, recommendationId);
        recommendation.setAccepted(null);
        return toDto(recommendationRepo.save(recommendation));
    }

    /**
     * Marca o desmarca una recomendacion como favorita sin cambiar su estado de
     * aceptacion.
     *
     * @param user usuario autenticado propietario de la recomendacion.
     * @param recommendationId identificador de la recomendacion.
     * @param favorite true para marcar como favorita, false para quitar favorito.
     * @return recomendacion actualizada convertida a DTO.
     */
    @Transactional
    public PerfumeRecommendationDTO updateFavorite(User user, Integer recommendationId, Boolean favorite) {
        PerfumeRecommendation recommendation = findOwnedRecommendation(user, recommendationId);
        recommendation.setFavorite(Boolean.TRUE.equals(favorite));
        return toDto(recommendationRepo.save(recommendation));
    }

    /**
     * Actualiza la puntuacion personal de una recomendacion. Null elimina la
     * puntuacion; cualquier valor numerico debe estar entre 1 y 5.
     *
     * @param user usuario autenticado propietario de la recomendacion.
     * @param recommendationId identificador de la recomendacion.
     * @param rating puntuacion entre 1 y 5, o null para quitarla.
     * @return recomendacion actualizada convertida a DTO.
     */
    @Transactional
    public PerfumeRecommendationDTO updateRating(User user, Integer recommendationId, Integer rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("La puntuacion debe estar entre 1 y 5");
        }

        PerfumeRecommendation recommendation = findOwnedRecommendation(user, recommendationId);
        recommendation.setRating(rating);
        return toDto(recommendationRepo.save(recommendation));
    }

    /**
     * Comprueba si el usuario tiene una recomendacion pendiente. En el modelo
     * actual accepted=false significa pendiente.
     *
     * @param user usuario autenticado.
     * @return true si existe una recomendacion pendiente.
     */
    @Transactional(readOnly = true)
    public boolean hasPendingRecommendation(User user) {
        return findLatestPendingRecommendation(user).isPresent();
    }

    /**
     * Recupera la ultima recomendacion pendiente del usuario.
     *
     * @param user usuario autenticado.
     * @return recomendacion pendiente mas reciente, si existe.
     */
    @Transactional(readOnly = true)
    public Optional<PerfumeRecommendation> findLatestPendingRecommendation(User user) {
        return recommendationRepo.findFirstByUserAndAcceptedFalseOrderByCreateDateDesc(user);
    }

    /**
     * Guarda una recomendacion como aceptada.
     *
     * @param recommendation recomendacion pendiente.
     * @return recomendacion guardada.
     */
    @Transactional
    public PerfumeRecommendation markAccepted(PerfumeRecommendation recommendation) {
        recommendation.setAccepted(true);
        return recommendationRepo.save(recommendation);
    }

    /**
     * Guarda una recomendacion como rechazada. Se mantiene el comportamiento
     * actual: accepted=null representa rechazo.
     *
     * @param recommendation recomendacion pendiente.
     * @return recomendacion guardada.
     */
    @Transactional
    public PerfumeRecommendation markRejected(PerfumeRecommendation recommendation) {
        recommendation.setAccepted(null);
        return recommendationRepo.save(recommendation);
    }

    /**
     * Crea una recomendacion pendiente evitando duplicar una misma propuesta
     * pendiente para el usuario.
     *
     * @param user usuario autenticado.
     * @param item perfume propuesto por el recomendador.
     * @return recomendacion pendiente existente o nueva.
     */
    @Transactional
    public PerfumeRecommendation savePendingRecommendation(User user, PerfumeItem item) {
        String perfumeName = safeText(item != null ? item.getName() : "", 120);
        String brand = safeText(item != null ? item.getBrand() : "", 120);
        if (!StringUtils.hasText(perfumeName)) {
            throw new IllegalArgumentException("Perfume name is required");
        }

        PerfumeRecommendation existing = recommendationRepo
                .findFirstByUserAndPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndAcceptedFalseOrderByCreateDateDesc(
                        user, perfumeName, brand)
                .orElse(null);
        if (existing != null) {
            String resolvedImageUrl = PerfumeImageResolver.resolve(brand, perfumeName, safeText(item.getImageUrl(), 500));
            boolean changed = false;
            if (shouldRefreshImageUrl(existing.getImageUrl(), resolvedImageUrl)) {
                existing.setImageUrl(resolvedImageUrl);
                changed = true;
            }
            if (!StringUtils.hasText(existing.getFragellaRating()) && StringUtils.hasText(item.getFragellaRating())) {
                existing.setFragellaRating(item.getFragellaRating());
                changed = true;
            }
            if (changed) {
                return recommendationRepo.save(existing);
            }
            return existing;
        }

        PerfumeRecommendation recommendation = new PerfumeRecommendation();
        recommendation.setUser(user);
        recommendation.setPerfumeName(perfumeName);
        recommendation.setBrand(brand);
        recommendation.setDescription(item.getDescription());
        recommendation.setSeason(safeText(item.getSeason(), 80));
        recommendation.setNotes(safeText(item.getNotes(), 500));
        recommendation.setSource(safeText(item.getSource(), 40));
        recommendation.setImageUrl(PerfumeImageResolver.resolve(brand, perfumeName, safeText(item.getImageUrl(), 500)));
        recommendation.setAccepted(false);
        recommendation.setFragellaRating(safeText(item.getFragellaRating(), 20));
        recommendation.setCreateDate(LocalDateTime.now());
        return recommendationRepo.save(recommendation);
    }

    /**
     * Guarda varias recomendaciones pendientes reutilizando la misma regla de
     * deduplicacion que una recomendacion individual.
     *
     * @param user usuario autenticado.
     * @param perfumes perfumes propuestos por el recomendador.
     * @return recomendaciones pendientes creadas o ya existentes convertidas a DTO.
     */
    @Transactional
    public List<PerfumeRecommendationDTO> savePendingRecommendations(User user, List<PerfumeItem> perfumes) {
        if (perfumes == null || perfumes.isEmpty()) {
            return List.of();
        }

        return uniquePerfumes(perfumes).stream()
                .map(item -> savePendingRecommendation(user, item))
                .map(this::toDto)
                .toList();
    }

    /**
     * Convierte una entidad de recomendacion al DTO usado por la API.
     *
     * @param recommendation entidad de recomendacion.
     * @return DTO de recomendacion o null si no hay entidad.
     */
    public PerfumeRecommendationDTO toDto(PerfumeRecommendation recommendation) {
        if (recommendation == null) {
            return null;
        }
        return PerfumeRecommendationDTO.builder()
                .recommendationId(recommendation.getRecommendationId())
                .perfumeName(recommendation.getPerfumeName())
                .brand(recommendation.getBrand())
                .description(recommendation.getDescription())
                .season(recommendation.getSeason())
                .notes(recommendation.getNotes())
                .source(recommendation.getSource())
                .imageUrl(PerfumeImageResolver.resolve(
                        recommendation.getBrand(),
                        recommendation.getPerfumeName(),
                        recommendation.getImageUrl()))
                .priceEstimate(PerfumePriceEstimator.estimate(recommendation))
                .accepted(recommendation.getAccepted())
                .favorite(Boolean.TRUE.equals(recommendation.getFavorite()))
                .rating(recommendation.getRating())
                .fragellaRating(recommendation.getFragellaRating())
                .communityAverageRating(communityAverageRating(recommendation))
                .communityRatingCount(communityRatingCount(recommendation))
                .createDate(recommendation.getCreateDate())
                .build();
    }

    private List<PerfumeRatingSummaryDTO> buildGlobalRatingRanking(int limit, boolean ascending) {
        if (limit <= 0) {
            return List.of();
        }

        Map<String, RatingAccumulator> grouped = new HashMap<>();
        for (PerfumeRecommendation recommendation : recommendationRepo.findByRatingIsNotNull()) {
            if (!StringUtils.hasText(recommendation.getPerfumeName()) || !StringUtils.hasText(recommendation.getBrand())
                    || recommendation.getRating() == null) {
                continue;
            }

            String key = normalizeKey(recommendation.getBrand()) + "::" + normalizeKey(recommendation.getPerfumeName());
            grouped.computeIfAbsent(key, ignored -> new RatingAccumulator(recommendation))
                    .add(recommendation);
        }

        Comparator<PerfumeRatingSummaryDTO> comparator = Comparator
                .comparing(PerfumeRatingSummaryDTO::getAverageRating, Comparator.nullsLast(Double::compareTo));
        if (!ascending) {
            comparator = comparator.reversed();
        }

        return grouped.values().stream()
                .filter(item -> item.count > 0)
                .map(RatingAccumulator::toDto)
                .sorted(comparator
                        .thenComparing(PerfumeRatingSummaryDTO::getRatingCount, Comparator.reverseOrder())
                        .thenComparing(PerfumeRatingSummaryDTO::getPerfumeName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();
    }

    private Double communityAverageRating(PerfumeRecommendation recommendation) {
        List<Integer> ratings = communityRatingsFor(recommendation);
        if (ratings.isEmpty()) {
            return null;
        }
        return ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private Long communityRatingCount(PerfumeRecommendation recommendation) {
        return (long) communityRatingsFor(recommendation).size();
    }

    private List<Integer> communityRatingsFor(PerfumeRecommendation recommendation) {
        if (!StringUtils.hasText(recommendation.getPerfumeName()) || !StringUtils.hasText(recommendation.getBrand())) {
            return List.of();
        }

        Integer currentUserId = recommendation.getUser() != null ? recommendation.getUser().getUserId() : null;
        return recommendationRepo
                .findByPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndRatingIsNotNull(
                        recommendation.getPerfumeName(), recommendation.getBrand())
                .stream()
                .filter(item -> item.getRating() != null)
                .filter(item -> currentUserId == null
                        || item.getUser() == null
                        || item.getUser().getUserId() == null
                        || !currentUserId.equals(item.getUser().getUserId()))
                .map(PerfumeRecommendation::getRating)
                .toList();
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private List<PerfumeItem> uniquePerfumes(List<PerfumeItem> perfumes) {
        Map<String, PerfumeItem> unique = new LinkedHashMap<>();
        for (PerfumeItem item : perfumes) {
            if (item == null || !StringUtils.hasText(item.getName())) {
                continue;
            }
            String key = normalizeKey(safeText(item.getBrand(), 120))
                    + "::"
                    + normalizeKey(safeText(item.getName(), 120));
            unique.putIfAbsent(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    private String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String clean = value.trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength);
    }

    private PerfumeRecommendation findOwnedRecommendation(User user, Integer recommendationId) {
        return recommendationRepo.findById(recommendationId)
                .filter(item -> item.getUser() != null
                        && item.getUser().getUserId() != null
                        && item.getUser().getUserId().equals(user.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found for this user"));
    }

    private boolean shouldRefreshImageUrl(String existingImageUrl, String newImageUrl) {
        if (!StringUtils.hasText(newImageUrl)) {
            return false;
        }
        if (!StringUtils.hasText(existingImageUrl)) {
            return true;
        }
        return existingImageUrl.startsWith("https://cdn.fragella.com/")
                && !existingImageUrl.equals(newImageUrl);
    }

    private static class RatingAccumulator {
        private final String perfumeName;
        private final String brand;
        private String imageUrl;
        private long count;
        private double sum;

        RatingAccumulator(PerfumeRecommendation recommendation) {
            this.perfumeName = recommendation.getPerfumeName();
            this.brand = recommendation.getBrand();
            this.imageUrl = recommendation.getImageUrl();
        }

        void add(PerfumeRecommendation recommendation) {
            count++;
            sum += recommendation.getRating();
            if (!StringUtils.hasText(imageUrl) && StringUtils.hasText(recommendation.getImageUrl())) {
                imageUrl = recommendation.getImageUrl();
            }
        }

        PerfumeRatingSummaryDTO toDto() {
            return PerfumeRatingSummaryDTO.builder()
                    .perfumeName(perfumeName)
                    .brand(brand)
                    .imageUrl(PerfumeImageResolver.resolve(brand, perfumeName, imageUrl))
                    .averageRating(count == 0 ? null : sum / count)
                    .ratingCount(count)
                    .build();
        }
    }
}
