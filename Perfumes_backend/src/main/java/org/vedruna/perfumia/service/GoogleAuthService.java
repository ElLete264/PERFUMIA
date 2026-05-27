package org.vedruna.perfumia.service;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.vedruna.perfumia.service.dto.GoogleAccount;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GoogleAuthService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    @Value("${google.token-info-url}")
    private String tokenInfoUrl;

    @Value("${google.client-id}")
    private String googleClientId;

    public GoogleAuthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GoogleAccount verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("Google token is required");
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(tokenInfoUrl)
                    .queryParam("id_token", idToken)
                    .build()
                    .toUri();
            String response = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);

            if (StringUtils.hasText(googleClientId) && !googleClientId.equals(root.path("aud").asText())) {
                throw new IllegalArgumentException("Google token audience is not valid");
            }

            String verified = root.path("email_verified").asText("false");
            if (!"true".equalsIgnoreCase(verified)) {
                throw new IllegalArgumentException("Google email is not verified");
            }

            return new GoogleAccount(
                    root.path("sub").asText(),
                    root.path("email").asText(),
                    root.path("name").asText(root.path("email").asText()),
                    root.path("picture").asText(""));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Google token could not be verified");
        }
    }
}
