package org.vedruna.perfumia.service;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.vedruna.perfumia.service.dto.PerfumeItem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PerfumeCatalogService {

    private static final int SEARCH_PAGE_LIMIT = 20;
    private static final int MAX_SEARCH_PAGES = 5;
    private static final int MAX_SEARCH_RESULTS = SEARCH_PAGE_LIMIT * MAX_SEARCH_PAGES;
    private static final Duration DEFAULT_RATE_LIMIT_BACKOFF = Duration.ofSeconds(60);

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private volatile Instant unavailableUntil = Instant.EPOCH;
    private volatile String lastStatus = "not_checked";

    @Value("${perfume.api.base-url}")
    private String perfumeApiBaseUrl;

    @Value("${perfume.api.key}")
    private String perfumeApiKey;

    public PerfumeCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PerfumeItem> searchPerfumes(String query) {
        List<PerfumeItem> perfumes = searchFragellaPerfumes(query);
        if (!perfumes.isEmpty()) {
            return perfumes;
        }

        log.warn("Fragella no devolvio perfumes para la busqueda '{}'. Se usa catalogo local.", query);
        return fallbackCatalog();
    }

    public List<PerfumeItem> searchReferencePerfumes(String query) {
        return searchFragellaPerfumes(query);
    }

    private List<PerfumeItem> searchFragellaPerfumes(String query) {
        if (!isConfigured()) {
            lastStatus = "not_configured";
            return List.of();
        }

        if (!canAttemptRequest()) {
            return List.of();
        }

        try {
            List<PerfumeItem> perfumes = new ArrayList<>();
            for (int page = 1; page <= MAX_SEARCH_PAGES && perfumes.size() < MAX_SEARCH_RESULTS; page++) {
                URI uri = buildSearchUri(query, SEARCH_PAGE_LIMIT, (page - 1) * SEARCH_PAGE_LIMIT, page);
                String response = restClient.get()
                        .uri(uri)
                        .header("x-api-key", perfumeApiKey)
                        .retrieve()
                        .body(String.class);

                List<PerfumeItem> pagePerfumes = parsePerfumeResponse(response, SEARCH_PAGE_LIMIT);
                int beforeAdd = perfumes.size();
                perfumes = uniquePerfumes(perfumes, pagePerfumes);
                if (pagePerfumes.size() < SEARCH_PAGE_LIMIT || perfumes.size() == beforeAdd) {
                    break;
                }
            }
            lastStatus = perfumes.isEmpty() ? "empty_response" : "available";
            return perfumes;
        } catch (RestClientResponseException ex) {
            handleFragellaHttpError(ex);
            return List.of();
        } catch (RestClientException | IllegalArgumentException ex) {
            lastStatus = "error";
            log.warn("No se pudo consultar Fragella: {}", ex.getMessage());
            return List.of();
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(perfumeApiBaseUrl) && StringUtils.hasText(perfumeApiKey);
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

    private boolean canAttemptRequest() {
        return isConfigured() && Instant.now().isAfter(unavailableUntil);
    }

    private void handleFragellaHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            Duration retryDelay = retryDelayFromHeader(ex);
            unavailableUntil = Instant.now().plus(retryDelay);
            lastStatus = "rate_limited";
            log.warn("Fragella devolvio 429. Se usara catalogo local durante {} segundos.",
                    retryDelay.toSeconds());
            return;
        }

        lastStatus = "http_" + status;
        log.warn("Fragella devolvio HTTP {}. Se usara catalogo local: {}", status, ex.getResponseBodyAsString());
    }

    private Duration retryDelayFromHeader(RestClientResponseException ex) {
        if (ex.getResponseHeaders() == null) {
            return DEFAULT_RATE_LIMIT_BACKOFF;
        }
        String retryAfter = ex.getResponseHeaders().getFirst("Retry-After");
        if (!StringUtils.hasText(retryAfter)) {
            return DEFAULT_RATE_LIMIT_BACKOFF;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException ignored) {
            return DEFAULT_RATE_LIMIT_BACKOFF;
        }
    }

    private URI buildSearchUri(String query, int limit, int offset, int page) {
        String baseUrl = perfumeApiBaseUrl.endsWith("/")
                ? perfumeApiBaseUrl.substring(0, perfumeApiBaseUrl.length() - 1)
                : perfumeApiBaseUrl;
        String searchUrl = baseUrl.endsWith("/fragrances") ? baseUrl : baseUrl + "/fragrances";

        return UriComponentsBuilder.fromUriString(searchUrl)
                .queryParam("search", StringUtils.hasText(query) ? query : "perfume")
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("page", page)
                .build()
                .toUri();
    }

    private List<PerfumeItem> parsePerfumeResponse(String response, int maxResults) {
        List<PerfumeItem> perfumes = new ArrayList<>();
        if (!StringUtils.hasText(response)) {
            return perfumes;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode collection = findCollection(root);

            if (collection == null || !collection.isArray()) {
                return perfumes;
            }

            for (JsonNode node : collection) {
                String name = firstText(node, "name", "perfume", "title", "fragrance_name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }

                String brand = readBrand(node);
                String imageUrl = PerfumeImageResolver.resolveCatalogImage(brand, name, readImageUrl(node));

                perfumes.add(PerfumeItem.builder()
                        .name(name)
                        .brand(brand)
                        .description(firstText(node, "description", "summary", "text"))
                        .notes(readNotes(node))
                        .season(readSeason(node))
                        .source("fragella")
                        .imageUrl(imageUrl)
                        .price(firstText(node, "Price", "price"))
                        .longevity(firstText(node, "Longevity", "longevity"))
                        .sillage(firstText(node, "Sillage", "sillage"))
                        .oilType(firstText(node, "OilType", "Oil Type", "oil_type"))
                        .fragellaRating(firstText(node, "rating", "Rating"))
                        .gender(firstText(node, "Gender", "gender"))
                        .priceValue(firstText(node, "Price Value", "price_value"))
                        .build());

                if (perfumes.size() == maxResults) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo interpretar la respuesta de Fragella: {}", ex.getMessage());
            return List.of();
        }

        return perfumes;
    }

    private List<PerfumeItem> uniquePerfumes(List<PerfumeItem> current, List<PerfumeItem> incoming) {
        Map<String, PerfumeItem> unique = new LinkedHashMap<>();
        if (current != null) {
            for (PerfumeItem item : current) {
                if (item != null && StringUtils.hasText(item.getName())) {
                    unique.putIfAbsent(perfumeKey(item), item);
                }
            }
        }
        if (incoming != null) {
            for (PerfumeItem item : incoming) {
                if (item != null && StringUtils.hasText(item.getName())) {
                    unique.putIfAbsent(perfumeKey(item), item);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private String perfumeKey(PerfumeItem item) {
        return normalizeKey(item.getBrand()) + "::" + normalizeKey(item.getName());
    }

    private JsonNode findCollection(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        for (String key : List.of("fragrances", "perfumes", "data", "results", "items")) {
            JsonNode node = root.get(key);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        for (JsonNode child : root) {
            JsonNode nested = findCollection(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = findValue(node, key);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
            if (value != null && value.isObject()) {
                String nested = firstText(value, "name", "title", "label");
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        return "";
    }

    private JsonNode findValue(JsonNode node, String key) {
        JsonNode direct = node.get(key);
        if (direct != null) {
            return direct;
        }

        String normalizedKey = normalizeKey(key);
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (normalizeKey(entry.getKey()).equals(normalizedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String readBrand(JsonNode node) {
        String brand = firstText(node, "brand", "brand_name", "house", "designer");
        return StringUtils.hasText(brand) ? brand : "Marca no indicada";
    }

    private String readNotes(JsonNode node) {
        List<String> combinedNotes = new ArrayList<>();
        for (String key : List.of("general notes", "main accords", "notes", "main_notes", "top", "middle", "base", "accords")) {
            JsonNode value = findValue(node, key);
            if (value == null) {
                continue;
            }
            if (value.isTextual()) {
                addNoteText(combinedNotes, value.asText());
                continue;
            }
            if (value.isArray()) {
                List<String> notes = new ArrayList<>();
                value.forEach(note -> {
                    if (note.isTextual()) {
                        notes.add(note.asText());
                    } else if (note.isObject()) {
                        String noteName = firstText(note, "name", "note", "accord");
                        if (StringUtils.hasText(noteName)) {
                            notes.add(noteName);
                        }
                    }
                });
                if (!notes.isEmpty()) {
                    notes.forEach(note -> addNoteText(combinedNotes, note));
                }
            }
            if (value.isObject()) {
                List<String> notes = new ArrayList<>();
                value.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        notes.add(entry.getValue().asText());
                    } else if (entry.getValue().isArray()) {
                        entry.getValue().forEach(note -> {
                            if (note.isTextual()) {
                                notes.add(note.asText());
                            } else if (note.isObject()) {
                                String noteName = firstText(note, "name", "note", "accord");
                                if (StringUtils.hasText(noteName)) {
                                    notes.add(noteName);
                                }
                            }
                        });
                    } else {
                        notes.add(entry.getKey());
                    }
                });
                if (!notes.isEmpty()) {
                    notes.forEach(note -> addNoteText(combinedNotes, note));
                }
            }
        }
        return String.join(", ", combinedNotes);
    }

    private void addNoteText(List<String> notes, String note) {
        if (StringUtils.hasText(note) && !notes.contains(note.trim())) {
            notes.add(note.trim());
        }
    }

    private String readSeason(JsonNode node) {
        String season = firstText(node, "season", "recommendedSeason", "recommended_season");
        if (StringUtils.hasText(season)) {
            return season;
        }

        JsonNode ranking = findValue(node, "season ranking");
        if (ranking != null && ranking.isArray() && !ranking.isEmpty()) {
            JsonNode first = ranking.get(0);
            String name = firstText(first, "name");
            if (StringUtils.hasText(name)) {
                return name;
            }
        }
        return "";
    }

    private String readImageUrl(JsonNode node) {
        for (String key : List.of("Image Fallbacks", "imageFallbacks", "image_fallbacks", "images")) {
            JsonNode value = findValue(node, key);
            if (value != null && value.isArray() && !value.isEmpty()) {
                for (JsonNode image : value) {
                    if (image.isTextual() && StringUtils.hasText(image.asText())) {
                        return image.asText();
                    }
                    if (image.isObject()) {
                        String nested = firstText(image, "url", "src", "imageUrl", "image_url");
                        if (StringUtils.hasText(nested)) {
                            return nested;
                        }
                    }
                }
            }
        }

        String imageUrl = firstText(node,
                "Image URL",
                "imageUrl",
                "image_url",
                "image",
                "picture",
                "photo",
                "thumbnail",
                "bottleImage",
                "bottle_image",
                "mainImage",
                "main_image",
                "photoUrl",
                "photo_url",
                "src",
                "url");
        if (StringUtils.hasText(imageUrl)) {
            return optimizePrimaryImageUrl(imageUrl);
        }
        return "";
    }

    private List<PerfumeItem> fallbackCatalog() {
        return List.of(
                fallback("Bleu de Chanel", "Chanel", "Aromatico fresco, elegante y facil de llevar.",
                        "pomelo, incienso, cedro, sandalo", "otono-invierno", "men", "90-140 euros", "mid_range",
                        "Long Lasting", "Moderate"),
                fallback("Acqua di Gio Profondo", "Giorgio Armani", "Marino, limpio y moderno para diario.",
                        "notas marinas, bergamota, romero, almizcle", "primavera-verano", "men", "70-120 euros",
                        "mid_range", "Long Lasting", "Moderate"),
                fallback("Mukhallat", "Montale", "Dulce, frutal y almizclado con fresa y vainilla.",
                        "fresa, vainilla, almendra, balsamo de peru, almizcle", "otono-invierno", "unisex",
                        "120-180 euros", "premium", "Long Lasting", "Strong"),
                fallback("Erba Pura", "Xerjoff", "Frutal, ambarado y potente, con mucha presencia.",
                        "frutas, naranja, bergamota, ambar, vainilla, almizcle", "primavera-verano", "unisex",
                        "180-260 euros", "premium", "Long Lasting", "Strong"),
                fallback("Lost Cherry", "Tom Ford", "Frutal oscuro, dulce y licoroso para noche.",
                        "cereza, almendra amarga, haba tonka, vainilla, balsamo", "otono-invierno", "unisex",
                        "250-390 euros", "premium", "Long Lasting", "Strong"),
                fallback("Baccarat Rouge 540", "Maison Francis Kurkdjian",
                        "Ambarado, luminoso y muy reconocible para ocasiones especiales.",
                        "azafran, jazmin, madera, ambargris, cedro", "todo el ano", "unisex", "250-350 euros",
                        "premium", "Long Lasting", "Strong"),
                fallback("La Vie Est Belle", "Lancome", "Dulce, femenino y luminoso.",
                        "iris, vainilla, praline, pera", "otono-invierno", "women", "70-130 euros", "mid_range",
                        "Long Lasting", "Moderate"),
                fallback("Light Blue", "Dolce & Gabbana", "Citrico y fresco, ideal para calor.",
                        "limon, manzana, cedro, bambu", "verano", "women", "45-90 euros", "mid_range",
                        "Moderate", "Moderate"),
                fallback("Black Opium", "Yves Saint Laurent", "Dulce, nocturno y con cafe.",
                        "cafe, vainilla, flores blancas", "invierno-noche", "women", "75-130 euros", "mid_range",
                        "Long Lasting", "Strong"),
                fallback("CK One", "Calvin Klein", "Unisex, limpio y muy facil para empezar.",
                        "bergamota, te verde, almizcle", "todo el ano", "unisex", "25-55 euros", "good_value",
                        "Moderate", "Moderate"),
                fallback("Nautica Voyage", "Nautica", "Fresco, acuatico y frutal verde para diario.",
                        "manzana verde, loto, almizcle, cedro", "primavera-verano", "men", "20-45 euros",
                        "good_value", "Moderate", "Moderate"),
                fallback("Bade'e Al Oud Sublime", "Lattafa", "Frutal dulce y juvenil, con sensacion de frutas rojas.",
                        "manzana, lichi, frutas rojas, vainilla, musgo", "todo el ano", "unisex", "25-45 euros",
                        "good_value", "Long Lasting", "Moderate"),
                fallback("Halloween Man X", "Halloween", "Dulce, moderno y facil de llevar con punto gourmand.",
                        "cafe, tonka, cardamomo, lavanda, whisky", "otono-invierno", "men", "30-55 euros",
                        "good_value", "Long Lasting", "Moderate"),
                fallback("Al Rehab Soft", "Al Rehab", "Dulce, limpio y economico, con vainilla citrica para diario.",
                        "limon, vainilla, caramelo, almizcle", "todo el ano", "unisex", "8-18 euros",
                        "good_value", "Moderate", "Moderate"),
                fallback("Terre d'Hermes", "Hermes", "Amaderado y citrico con un punto mineral.",
                        "naranja, vetiver, pimienta, cedro", "otono-primavera", "men", "70-120 euros",
                        "mid_range", "Long Lasting", "Moderate"),
                fallback("Good Girl", "Carolina Herrera", "Dulce, floral y potente para ocasiones especiales.",
                        "jazmin, haba tonka, cacao, almendra", "otono-invierno", "women", "70-140 euros",
                        "mid_range", "Long Lasting", "Strong"));
    }

    private PerfumeItem fallback(String name, String brand, String description, String notes, String season,
            String gender, String price, String priceValue, String longevity, String sillage) {
        return PerfumeItem.builder()
                .name(name)
                .brand(brand)
                .description(description)
                .notes(notes)
                .season(season)
                .source("local")
                .imageUrl(PerfumeImageResolver.resolve(brand, name, ""))
                .gender(gender)
                .price(price)
                .priceValue(priceValue)
                .longevity(longevity)
                .sillage(sillage)
                .build();
    }

    private String optimizePrimaryImageUrl(String imageUrl) {
        if (imageUrl.contains("cdn.fragella.com/images/") && imageUrl.endsWith(".jpg")) {
            return imageUrl.substring(0, imageUrl.length() - 4) + ".webp";
        }
        return imageUrl;
    }
}
