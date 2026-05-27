package org.vedruna.perfumia.controller.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommunityProfileDTO {
    Integer userId;
    String username;
    String description;
    String profileImageUrl;
    LocalDate createDate;
    List<PerfumeRecommendationDTO> recommendations;
}
