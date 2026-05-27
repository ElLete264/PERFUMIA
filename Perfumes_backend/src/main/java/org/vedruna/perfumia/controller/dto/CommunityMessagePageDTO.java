package org.vedruna.perfumia.controller.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommunityMessagePageDTO {
    List<CommunityMessageDTO> messages;
    int page;
    int size;
    int totalPages;
    long totalElements;
    boolean first;
    boolean last;
}
