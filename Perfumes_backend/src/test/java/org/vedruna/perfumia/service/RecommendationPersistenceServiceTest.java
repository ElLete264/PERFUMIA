package org.vedruna.perfumia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedruna.perfumia.controller.dto.PerfumeRecommendationDTO;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.ChatMessageRepository;
import org.vedruna.perfumia.persistance.repository.PerfumeRecommendationRepository;
import org.vedruna.perfumia.service.dto.PerfumeItem;

class RecommendationPersistenceServiceTest {

    private ChatMessageRepository chatMessageRepository;
    private PerfumeRecommendationRepository recommendationRepository;
    private RecommendationPersistenceService recommendationPersistenceService;

    @BeforeEach
    void setUp() {
        chatMessageRepository = mock(ChatMessageRepository.class);
        recommendationRepository = mock(PerfumeRecommendationRepository.class);
        recommendationPersistenceService = new RecommendationPersistenceService(chatMessageRepository,
                recommendationRepository);
    }

    @Test
    void savePendingRecommendationsReturnsEmptyListWhenPerfumesIsNull() {
        List<PerfumeRecommendationDTO> result = recommendationPersistenceService.savePendingRecommendations(
                new User(), null);

        assertThat(result).isEmpty();
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void savePendingRecommendationsReturnsEmptyListWhenPerfumesIsEmpty() {
        List<PerfumeRecommendationDTO> result = recommendationPersistenceService.savePendingRecommendations(
                new User(), List.of());

        assertThat(result).isEmpty();
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void savePendingRecommendationsSavesEachPerfumeAsPendingAndReturnsDtos() {
        User user = new User();
        PerfumeItem first = perfume("Amber Night", "Test Brand");
        PerfumeItem second = perfume("Clean Iris", "Other Brand");

        when(recommendationRepository
                .findFirstByUserAndPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndAcceptedFalseOrderByCreateDateDesc(
                        eq(user), any(), any()))
                .thenReturn(Optional.empty());
        when(recommendationRepository.save(any(PerfumeRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<PerfumeRecommendationDTO> result = recommendationPersistenceService.savePendingRecommendations(user,
                List.of(first, second));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PerfumeRecommendationDTO::getPerfumeName)
                .containsExactly("Amber Night", "Clean Iris");
        assertThat(result).extracting(PerfumeRecommendationDTO::getAccepted)
                .containsExactly(false, false);
    }

    @Test
    void savePendingRecommendationsReusesExistingPendingRecommendationToAvoidDuplicates() {
        User user = new User();
        PerfumeItem item = perfume("Amber Night", "Test Brand");
        PerfumeRecommendation existing = new PerfumeRecommendation();
        existing.setRecommendationId(7);
        existing.setUser(user);
        existing.setPerfumeName("Amber Night");
        existing.setBrand("Test Brand");
        existing.setAccepted(false);

        when(recommendationRepository
                .findFirstByUserAndPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndAcceptedFalseOrderByCreateDateDesc(
                        user, "Amber Night", "Test Brand"))
                .thenReturn(Optional.of(existing));

        List<PerfumeRecommendationDTO> result = recommendationPersistenceService.savePendingRecommendations(user,
                List.of(item));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecommendationId()).isEqualTo(7);
        assertThat(result.get(0).getAccepted()).isFalse();
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void savePendingRecommendationsDeduplicatesIncomingBatch() {
        User user = new User();
        PerfumeItem item = perfume("Amber Night", "Test Brand");

        when(recommendationRepository
                .findFirstByUserAndPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndAcceptedFalseOrderByCreateDateDesc(
                        eq(user), any(), any()))
                .thenReturn(Optional.empty());
        when(recommendationRepository.save(any(PerfumeRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<PerfumeRecommendationDTO> result = recommendationPersistenceService.savePendingRecommendations(user,
                List.of(item, item));

        assertThat(result).hasSize(1);
        verify(recommendationRepository, times(1)).save(any(PerfumeRecommendation.class));
    }

    @Test
    void savePendingRecommendationsRefreshesFragellaCdnImageWithFallbackImage() {
        User user = new User();
        PerfumeItem item = PerfumeItem.builder()
                .name("Cardamom Tea")
                .brand("Fresh Line")
                .description("description")
                .notes("notes")
                .season("winter")
                .source("test")
                .imageUrl("https://d2k6fvhyk5xgx.cloudfront.net/images/cardamom-white-tea.jpg")
                .build();
        PerfumeRecommendation existing = new PerfumeRecommendation();
        existing.setRecommendationId(8);
        existing.setUser(user);
        existing.setPerfumeName("Cardamom Tea");
        existing.setBrand("Fresh Line");
        existing.setImageUrl("https://cdn.fragella.com/images/cardamom-&-white-tea.jpg");
        existing.setAccepted(false);

        when(recommendationRepository
                .findFirstByUserAndPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndAcceptedFalseOrderByCreateDateDesc(
                        user, "Cardamom Tea", "Fresh Line"))
                .thenReturn(Optional.of(existing));
        when(recommendationRepository.save(any(PerfumeRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<PerfumeRecommendationDTO> result = recommendationPersistenceService.savePendingRecommendations(user,
                List.of(item));

        assertThat(result.get(0).getImageUrl())
                .isEqualTo("https://d2k6fvhyk5xgx.cloudfront.net/images/cardamom-white-tea.jpg");
        verify(recommendationRepository).save(existing);
    }

    @Test
    void updateFavoriteMarksRecommendationAsFavorite() {
        User user = user(1);
        PerfumeRecommendation recommendation = recommendation(10, user);
        recommendation.setFavorite(false);

        when(recommendationRepository.findById(10)).thenReturn(Optional.of(recommendation));
        when(recommendationRepository.save(recommendation)).thenReturn(recommendation);

        PerfumeRecommendationDTO result = recommendationPersistenceService.updateFavorite(user, 10, true);

        assertThat(result.getFavorite()).isTrue();
        assertThat(recommendation.getAccepted()).isFalse();
        verify(recommendationRepository).save(recommendation);
    }

    @Test
    void updateFavoriteCanUnmarkRecommendationAsFavorite() {
        User user = user(1);
        PerfumeRecommendation recommendation = recommendation(10, user);
        recommendation.setFavorite(true);

        when(recommendationRepository.findById(10)).thenReturn(Optional.of(recommendation));
        when(recommendationRepository.save(recommendation)).thenReturn(recommendation);

        PerfumeRecommendationDTO result = recommendationPersistenceService.updateFavorite(user, 10, false);

        assertThat(result.getFavorite()).isFalse();
        verify(recommendationRepository).save(recommendation);
    }

    @Test
    void updateRatingStoresValidValueBetweenOneAndFive() {
        User user = user(1);
        PerfumeRecommendation recommendation = recommendation(10, user);

        when(recommendationRepository.findById(10)).thenReturn(Optional.of(recommendation));
        when(recommendationRepository.save(recommendation)).thenReturn(recommendation);

        PerfumeRecommendationDTO result = recommendationPersistenceService.updateRating(user, 10, 5);

        assertThat(result.getRating()).isEqualTo(5);
        assertThat(recommendation.getAccepted()).isFalse();
        verify(recommendationRepository).save(recommendation);
    }

    @Test
    void updateRatingAllowsNullToRemoveRating() {
        User user = user(1);
        PerfumeRecommendation recommendation = recommendation(10, user);
        recommendation.setRating(4);

        when(recommendationRepository.findById(10)).thenReturn(Optional.of(recommendation));
        when(recommendationRepository.save(recommendation)).thenReturn(recommendation);

        PerfumeRecommendationDTO result = recommendationPersistenceService.updateRating(user, 10, null);

        assertThat(result.getRating()).isNull();
        verify(recommendationRepository).save(recommendation);
    }

    @Test
    void updateRatingRejectsInvalidValue() {
        User user = user(1);

        assertThatThrownBy(() -> recommendationPersistenceService.updateRating(user, 10, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 y 5");

        verify(recommendationRepository, never()).findById(any());
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void updateFavoriteDoesNotAllowEditingAnotherUserRecommendation() {
        User currentUser = user(1);
        User otherUser = user(2);
        PerfumeRecommendation recommendation = recommendation(10, otherUser);

        when(recommendationRepository.findById(10)).thenReturn(Optional.of(recommendation));

        assertThatThrownBy(() -> recommendationPersistenceService.updateFavorite(currentUser, 10, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Recommendation not found for this user");

        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void toDtoReturnsFalseWhenFavoriteIsNull() {
        PerfumeRecommendation recommendation = recommendation(10, user(1));
        recommendation.setFavorite(null);

        PerfumeRecommendationDTO result = recommendationPersistenceService.toDto(recommendation);

        assertThat(result.getFavorite()).isFalse();
    }

    private PerfumeItem perfume(String name, String brand) {
        return PerfumeItem.builder()
                .name(name)
                .brand(brand)
                .description("description")
                .notes("notes")
                .season("winter")
                .source("test")
                .imageUrl("")
                .build();
    }

    private User user(Integer userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }

    private PerfumeRecommendation recommendation(Integer recommendationId, User user) {
        PerfumeRecommendation recommendation = new PerfumeRecommendation();
        recommendation.setRecommendationId(recommendationId);
        recommendation.setUser(user);
        recommendation.setPerfumeName("Amber Night");
        recommendation.setBrand("Test Brand");
        recommendation.setAccepted(false);
        recommendation.setFavorite(false);
        return recommendation;
    }
}
