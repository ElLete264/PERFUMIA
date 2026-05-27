package org.vedruna.perfumia.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.controller.dto.CommunityMessageDTO;
import org.vedruna.perfumia.controller.dto.CommunityProfileDTO;
import org.vedruna.perfumia.controller.dto.PerfumeRecommendationDTO;
import org.vedruna.perfumia.persistance.model.CommunityMessage;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.CommunityMessageRepository;
import org.vedruna.perfumia.persistance.repository.UserRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CommunityChatService {

    private final CommunityMessageRepository communityMessageRepository;
    private final UserRepository userRepository;
    private final RecommendationPersistenceService recommendationPersistenceService;

    @Transactional(readOnly = true)
    public List<CommunityMessageDTO> listMessages() {
        List<CommunityMessage> messages = communityMessageRepository.findTop60ByOrderByCreateDateDesc();
        Collections.reverse(messages);
        return messages.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CommunityMessageDTO sendMessage(User user, String content) {
        String cleanContent = StringUtils.hasText(content) ? content.trim() : "";
        if (!StringUtils.hasText(cleanContent)) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }

        CommunityMessage message = new CommunityMessage();
        message.setUser(user);
        message.setContent(cleanContent);
        message.setCreateDate(LocalDateTime.now());
        return toDto(communityMessageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public CommunityProfileDTO findCommunityProfile(Integer userId) {
        User profileUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro ese usuario"));
        List<PerfumeRecommendationDTO> publicRecommendations = recommendationPersistenceService
                .listRecommendations(profileUser).stream()
                .filter(this::isPublicRecommendation)
                .limit(24)
                .toList();

        return CommunityProfileDTO.builder()
                .userId(profileUser.getUserId())
                .username(profileUser.getUsername())
                .description(profileUser.getDescription())
                .profileImageUrl(profileUser.getProfileImageUrl())
                .createDate(profileUser.getCreateDate())
                .recommendations(publicRecommendations)
                .build();
    }

    private CommunityMessageDTO toDto(CommunityMessage message) {
        User author = message.getUser();
        return CommunityMessageDTO.builder()
                .messageId(message.getMessageId())
                .userId(author == null ? null : author.getUserId())
                .username(author == null ? "Usuario" : author.getUsername())
                .profileImageUrl(author == null ? "" : author.getProfileImageUrl())
                .content(message.getContent())
                .createDate(message.getCreateDate())
                .build();
    }

    private boolean isPublicRecommendation(PerfumeRecommendationDTO recommendation) {
        return Boolean.TRUE.equals(recommendation.getAccepted())
                || Boolean.TRUE.equals(recommendation.getFavorite())
                || recommendation.getRating() != null;
    }
}
