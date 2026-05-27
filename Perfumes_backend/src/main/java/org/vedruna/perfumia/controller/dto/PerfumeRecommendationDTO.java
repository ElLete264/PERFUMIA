package org.vedruna.perfumia.controller.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerfumeRecommendationDTO {
    Integer recommendationId;
    String perfumeName;
    String brand;
    String description;
    String season;
    String notes;
    String source;
    String imageUrl;
    String priceEstimate;
    String longevity;
    String sillage;
    String oilType;
    String fragellaRating;
    String gender;
    String priceValue;
    String reason;
    Boolean accepted;
    Boolean favorite;
    Integer rating;
    Double communityAverageRating;
    Long communityRatingCount;
    LocalDateTime createDate;
}
