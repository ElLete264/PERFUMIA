package org.vedruna.perfumia.service;

import java.time.Duration;
import java.time.Instant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GeminiService {

    private static final Duration DEFAULT_QUOTA_BACKOFF = Duration.ofSeconds(30);
    private static final Duration DEFAULT_UPSTREAM_BACKOFF = Duration.ofSeconds(15);
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s\"");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private volatile Instant unavailableUntil = Instant.EPOCH;
    private volatile String lastStatus = "not_checked";

    @Value("${gemini.api-key:}")
    private String configuredApiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.base-url}")
    private String baseUrl;

    public GeminiService(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .build();
    }

    @PostConstruct
    void logConfigurationState() {
        boolean envPresent = StringUtils.hasText(System.getenv("GEMINI_API_KEY"));
        boolean importedRawPresent = StringUtils.hasText(environment.getProperty("GEMINI_API_KEY"));
        boolean importedBomPresent = StringUtils.hasText(environment.getProperty("\uFEFFGEMINI_API_KEY"));
        boolean importedCanonicalPresent = StringUtils.hasText(environment.getProperty("gemini.api.key"));
        boolean envFilePresent = StringUtils.hasText(envFileValue("GEMINI_API_KEY"));
        boolean injectedPresent = StringUtils.hasText(configuredApiKey);
        boolean resolvedPresent = StringUtils.hasText(apiKey());

        // Never log API keys. This is just to debug config wiring.
        log.info("Gemini config: envKeyPresent={}, importedRawKeyPresent={}, importedBomKeyPresent={}, importedCanonicalKeyPresent={}, envFileKeyPresent={}, injectedKeyPresent={}, resolvedKeyPresent={}, model='{}', baseUrl='{}'",
                envPresent,
                importedRawPresent,
                importedBomPresent,
                importedCanonicalPresent,
                envFilePresent,
                injectedPresent,
                resolvedPresent,
                safeValue(model),
                safeValue(baseUrl));
    }

    public String generateAnswer(String prompt) {
        return generateAnswer(prompt, false);
    }

    public String generateJsonAnswer(String prompt) {
        return generateAnswer(prompt, true);
    }

    public Map<String, Object> probe() {
        String answer = generateAnswer("Responde exactamente con una frase corta: PerfumIA OK");
        return Map.of(
                "configured", isConfigured(),
                "available", isAvailable(),
                "status", status(),
                "model", safeValue(model),
                "baseUrl", safeValue(baseUrl),
                "retryAfterSeconds", retryAfterSeconds(),
                "success", StringUtils.hasText(answer),
                "responsePreview", StringUtils.hasText(answer) ? shortMessage(answer) : "");
    }

    private String generateAnswer(String prompt, boolean jsonMode) {
        String resolvedApiKey = apiKey();
        if (!StringUtils.hasText(resolvedApiKey)) {
            lastStatus = "not_configured";
            return "";
        }

        if (!canAttemptRequest()) {
            return "";
        }

        try {
            String url = baseUrl + "/" + model + ":generateContent?key=" + resolvedApiKey;
            log.debug("Gemini request: model='{}', baseUrl='{}', jsonMode={}, promptChars={}",
                    safeValue(model), safeValue(baseUrl), jsonMode, prompt == null ? 0 : prompt.length());

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", jsonMode ? 0.1 : 0.35);
            generationConfig.put("topP", jsonMode ? 0.65 : 0.9);
            generationConfig.put("candidateCount", 1);
            generationConfig.put("maxOutputTokens", jsonMode ? 1000 : 800);
            if (jsonMode) {
                generationConfig.put("responseMimeType", "application/json");
            }

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", generationConfig);

            String response = restClient.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode text = root.at("/candidates/0/content/parts/0/text");
            lastStatus = "available";
            return text.isTextual() ? text.asText() : "";
        } catch (RestClientResponseException ex) {
            handleGeminiHttpError(ex);
            return "";
        } catch (RestClientException | IllegalArgumentException ex) {
            lastStatus = "error";
            log.warn("No se pudo obtener respuesta de Gemini. Se usara fallback local: {}", shortMessage(ex.getMessage()));
            return "";
        } catch (Exception ex) {
            lastStatus = "error";
            log.warn("No se pudo procesar la respuesta de Gemini. Se usara fallback local: {}", shortMessage(ex.getMessage()));
            return "";
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey());
    }

    public boolean isAvailable() {
        return canAttemptRequest() && "available".equals(lastStatus);
    }

    public String status() {
        if (!isConfigured()) {
            return "not_configured";
        }
        if (Instant.now().isBefore(unavailableUntil)) {
            return lastStatus;
        }
        return lastStatus;
    }

    public long retryAfterSeconds() {
        if (Instant.now().isAfter(unavailableUntil)) {
            return 0;
        }
        return Math.max(1, Duration.between(Instant.now(), unavailableUntil).toSeconds());
    }

    private void handleGeminiHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString();

        if (status == 429) {
            Duration retryDelay = retryDelayFromBody(ex.getResponseBodyAsString());
            unavailableUntil = Instant.now().plus(retryDelay);
            lastStatus = "quota_exceeded";
            log.warn("Gemini ha superado la cuota o el limite temporal (429). Se usara fallback local durante {} segundos.",
                    retryDelay.toSeconds());
            return;
        }

        if (status == 503) {
            unavailableUntil = Instant.now().plus(DEFAULT_UPSTREAM_BACKOFF);
        }

        lastStatus = statusLabel(status, body);
        log.warn("Gemini devolvio HTTP {} (statusLabel={}). Se usara fallback local. Error: {}",
                status, lastStatus, shortMessage(body));
    }

    private boolean canAttemptRequest() {
        return isConfigured() && Instant.now().isAfter(unavailableUntil);
    }

    private String apiKey() {
        return firstText(
                configuredApiKey,
                environment.getProperty("GEMINI_API_KEY"),
                environment.getProperty("\uFEFFGEMINI_API_KEY"),
                environment.getProperty("gemini.api.key"),
                envFileValue("GEMINI_API_KEY"));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.replace("\uFEFF", "").trim();
            }
        }
        return "";
    }

    private String envFileValue(String key) {
        for (Path path : List.of(Path.of(".env.local"), Path.of("..", ".env.local"))) {
            String value = envFileValue(path, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String envFileValue(Path path, String key) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String cleanLine = line.replace("\uFEFF", "").trim();
                if (cleanLine.startsWith("#") || !cleanLine.contains("=")) {
                    continue;
                }
                String[] parts = cleanLine.split("=", 2);
                if (key.equals(parts[0].trim())) {
                    return parts[1].trim();
                }
            }
        } catch (IOException ex) {
            log.debug("No se pudo leer {} para resolver {}", path, key);
        }
        return "";
    }

    private Duration retryDelayFromBody(String body) {
        if (StringUtils.hasText(body)) {
            Matcher matcher = RETRY_DELAY_PATTERN.matcher(body);
            if (matcher.find()) {
                return Duration.ofSeconds(Long.parseLong(matcher.group(1)));
            }
        }
        return DEFAULT_QUOTA_BACKOFF;
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(12));
        return factory;
    }

    private String shortMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "sin detalle";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "..." : compact;
    }

    private String statusLabel(int status, String body) {
        if (status == 400) {
            return "bad_request";
        }
        if (status == 401 || status == 403) {
            return "auth_error";
        }
        if (status == 404) {
            // Common when model name is wrong or the endpoint path is wrong.
            return "not_found";
        }
        if (status >= 500) {
            return "upstream_error";
        }
        if (StringUtils.hasText(body) && body.toLowerCase(Locale.ROOT).contains("api key")) {
            return "auth_error";
        }
        return "http_error";
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "(empty)";
    }
}
