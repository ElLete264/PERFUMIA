package org.vedruna.perfumia.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerfumeRatingSummaryDTO {
    String perfumeName;
    String brand;
    String imageUrl;
    Double averageRating;
    Long ratingCount;
}
