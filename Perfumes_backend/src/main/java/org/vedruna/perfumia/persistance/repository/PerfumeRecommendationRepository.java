package org.vedruna.perfumia.persistance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.persistance.model.User;

@Repository
public interface PerfumeRecommendationRepository extends JpaRepository<PerfumeRecommendation, Integer> {
    List<PerfumeRecommendation> findByUserOrderByCreateDateDesc(User user);
    List<PerfumeRecommendation> findByRatingIsNotNull();
    List<PerfumeRecommendation> findByPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndRatingIsNotNull(
            String perfumeName, String brand);
    Optional<PerfumeRecommendation> findFirstByUserAndAcceptedFalseOrderByCreateDateDesc(User user);
    Optional<PerfumeRecommendation> findFirstByUserAndPerfumeNameIgnoreCaseAndBrandIgnoreCaseAndAcceptedFalseOrderByCreateDateDesc(
            User user, String perfumeName, String brand);
}
