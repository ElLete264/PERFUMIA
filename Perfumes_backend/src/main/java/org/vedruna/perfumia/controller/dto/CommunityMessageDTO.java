package org.vedruna.perfumia.controller.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommunityMessageDTO {
    Integer messageId;
    Integer userId;
    String username;
    String profileImageUrl;
    String content;
    LocalDateTime createDate;
}
