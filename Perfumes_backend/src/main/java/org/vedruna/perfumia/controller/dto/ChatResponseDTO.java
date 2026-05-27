package org.vedruna.perfumia.controller.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponseDTO {
    String answer;
    PerfumeRecommendationDTO proposedRecommendation;
    List<PerfumeRecommendationDTO> proposedRecommendations;
    List<PerfumeRecommendationDTO> savedRecommendations;
}
