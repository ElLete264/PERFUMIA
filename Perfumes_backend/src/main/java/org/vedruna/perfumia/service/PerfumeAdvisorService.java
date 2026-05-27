package org.vedruna.perfumia.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.controller.dto.ChatResponseDTO;
import org.vedruna.perfumia.controller.dto.PerfumeRecommendationDTO;
import org.vedruna.perfumia.persistance.model.ChatMessage;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.PerfumeProfileRepository;
import org.vedruna.perfumia.service.dto.PerfumeItem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class PerfumeAdvisorService {

    private final PerfumeProfileRepository perfumeProfileRepo;
    private final RecommendationPersistenceService recommendationPersistenceService;
    private final AiDecisionService aiDecisionService;
    private final PerfumeScoringService perfumeScoringService;
    private final PromptBuilderService promptBuilderService;
    private final PerfumeCatalogService perfumeCatalogService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatResponseDTO chat(User user, String message) {
        String cleanMessage = StringUtils.hasText(message) ? message.trim() : "";
        if (!StringUtils.hasText(cleanMessage)) {
            return welcome(user);
        }

        recommendationPersistenceService.saveMessage(user, "USER", cleanMessage);

        PerfumeProfile profile = perfumeProfileRepo.findByUser(user).orElseGet(() -> createProfile(user));

        String normalized = normalize(cleanMessage);
        if (shouldHandlePendingRecommendation(user, cleanMessage) && aiDecisionService.isAcceptance(cleanMessage)) {
            return acceptLatestRecommendation(user);
        }

        ChatResponseDTO aiResponse = handleWithGeminiIfPossible(user, profile, cleanMessage);
        if (aiResponse != null) {
            return aiResponse;
        }

        ChatResponseDTO similarPerfumeResponse = handleSimilarPerfumeRequest(user, profile, cleanMessage);
        if (similarPerfumeResponse != null) {
            return similarPerfumeResponse;
        }

        ChatResponseDTO directConversation = handleDirectConversation(user, profile, cleanMessage);
        if (directConversation != null) {
            return directConversation;
        }

        if (recommendationPersistenceService.hasPendingRecommendation(user)
                && (aiDecisionService.wantsSearchNow(normalized) || aiDecisionService.wantsAnotherPerfume(normalized))
                && hasEnoughUsefulProfile(profile)) {
            recommendationPersistenceService.findLatestPendingRecommendation(user)
                    .ifPresent(recommendationPersistenceService::markRejected);
            return recommendFromProfile(user, profile, cleanMessage, true);
        }

        if (shouldHandlePendingRecommendation(user, cleanMessage)) {
            if (aiDecisionService.isAcceptance(cleanMessage)) {
                return acceptLatestRecommendation(user);
            }
            if (aiDecisionService.isRejection(cleanMessage)) {
                return rejectLatestRecommendation(user);
            }
        }

        if ((aiDecisionService.wantsSearchNow(normalized)
                || (aiDecisionService.isShortNegative(cleanMessage) && hasEnoughUsefulProfile(profile)))) {
            aiDecisionService.updateProfileFromMessage(profile, cleanMessage);
            perfumeProfileRepo.save(profile);
            if (hasEnoughUsefulProfile(profile)) {
                applyRecommendationDefaults(profile);
                perfumeProfileRepo.save(profile);
            }
            return recommendFromProfile(user, profile, cleanMessage, true);
        }

        aiDecisionService.updateProfileFromMessage(profile, cleanMessage);
        perfumeProfileRepo.save(profile);

        ChatResponseDTO advisorClarification = handleAdvisorClarification(user, profile, cleanMessage);
        if (advisorClarification != null) {
            return advisorClarification;
        }

        List<String> missingFields = blockingRecommendationFields(profile);
        if (!missingFields.isEmpty()) {
            String answer = buildQuestionAnswer(user, profile, cleanMessage, missingFields);
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        applyRecommendationDefaults(profile);
        perfumeProfileRepo.save(profile);

        String query = aiDecisionService.buildSearchQuery(profile, cleanMessage);
        List<PerfumeItem> catalog = filterUnsuitablePerfumes(user,
                perfumeCatalogService.searchPerfumes(query), profile);
        List<PerfumeItem> topPerfumes = chooseTopPerfumesWithHistory(user, catalog, profile, cleanMessage, 3);
        topPerfumes = expandTopPerfumesIfNeeded(user, profile, cleanMessage, topPerfumes, catalog, 3, false, query);
        if (topPerfumes.isEmpty()) {
            String answer = buildGeminiAnswer(user, profile, cleanMessage, catalog, missingFields, null);

            if (!StringUtils.hasText(answer)) {
                answer = buildFallbackAnswer(profile, missingFields, null);
            }

            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);

            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        List<PerfumeRecommendationDTO> proposedRecommendations = recommendationPersistenceService
                .savePendingRecommendations(user, topPerfumes);
        enrichReasons(profile, topPerfumes, proposedRecommendations);
        String answer = buildTopRecommendationsAnswer(profile, topPerfumes);

        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);

        return ChatResponseDTO.builder()
                .answer(answer)
                .proposedRecommendation(firstRecommendation(proposedRecommendations))
                .proposedRecommendations(proposedRecommendations)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    @Transactional
    public ChatResponseDTO welcome(User user) {
        PerfumeProfile profile = perfumeProfileRepo.findByUser(user).orElseGet(() -> createProfile(user));
        resetProfileForNewConversation(profile);
        perfumeProfileRepo.save(profile);

        String answer = "Hola, soy PerfumIA. Cuéntame un poco qué buscas: algo fresco para diario, algo más intenso, o todavía no lo tienes claro?";
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);

        return ChatResponseDTO.builder()
                .answer(answer)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    @Transactional
    public ChatResponseDTO resetConversation(User user) {
        recommendationPersistenceService.clearChatMessages(user);
        return welcome(user);
    }

    @Transactional(readOnly = true)
    public List<PerfumeRecommendationDTO> listRecommendations(User user) {
        return recommendationPersistenceService.listRecommendations(user);
    }

    @Transactional
    public List<PerfumeRecommendationDTO> moreRecommendations(User user) {
        PerfumeProfile profile = perfumeProfileRepo.findByUser(user).orElse(null);
        if (profile == null) {
            return List.of();
        }

        String message = "mostrar mas";
        String query = aiDecisionService.buildSearchQuery(profile, message);
        List<PerfumeItem> catalog = filterUnsuitablePerfumes(user, perfumeCatalogService.searchPerfumes(query), profile);
        List<PerfumeItem> freshCatalog = excludeAlreadyRecommended(user, catalog);
        List<PerfumeItem> topPerfumes = chooseTopPerfumesWithHistory(user, freshCatalog, profile, message, 3);
        topPerfumes = expandTopPerfumesIfNeeded(user, profile, message, topPerfumes, freshCatalog, 3, true, query);
        List<PerfumeRecommendationDTO> proposedRecommendations = recommendationPersistenceService
                .savePendingRecommendations(user, topPerfumes);
        enrichReasons(profile, topPerfumes, proposedRecommendations);
        return proposedRecommendations;
    }

    @Transactional
    public PerfumeRecommendationDTO acceptRecommendation(User user, Integer recommendationId) {
        return recommendationPersistenceService.acceptRecommendation(user, recommendationId);
    }

    private ChatResponseDTO handleSimilarPerfumeRequest(User user, PerfumeProfile profile, String message) {
        Optional<String> referenceName = extractSimilarPerfumeName(message);
        if (referenceName.isEmpty()) {
            return null;
        }

        aiDecisionService.updateProfileFromMessage(profile, message);

        List<PerfumeItem> referenceMatches = perfumeCatalogService.searchReferencePerfumes(referenceName.get());
        PerfumeItem reference = findBestReferenceMatch(referenceMatches, referenceName.get());
        if (reference == null || !StringUtils.hasText(reference.getNotes())) {
            String answer = "No encuentro informacion suficiente de \"" + referenceName.get()
                    + "\" en Fragella para copiar su perfil olfativo. Prueba con el nombre completo y la marca, por ejemplo \"Dior Sauvage\".";
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        PerfumeProfile referenceProfile = profileFromReference(profile, reference);
        String query = buildSimilarPerfumeQuery(reference, profile, message);
        List<PerfumeItem> catalog = filterUnsuitablePerfumes(user, perfumeCatalogService.searchPerfumes(query), referenceProfile);
        List<PerfumeItem> alternatives = catalog.stream()
                .filter(item -> !isSamePerfume(item, reference))
                .toList();
        List<PerfumeItem> topPerfumes = chooseTopPerfumesWithHistory(user, alternatives, referenceProfile, message, 3);

        if (topPerfumes.isEmpty()) {
            String answer = "He encontrado \"" + reference.getName() + "\" de " + reference.getBrand()
                    + ", pero no tengo alternativas claras con notas parecidas ahora mismo. Puedo intentarlo con la marca completa o con una nota concreta de ese perfume.";
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        applyReferenceToProfile(profile, referenceProfile, reference);
        perfumeProfileRepo.save(profile);

        List<PerfumeRecommendationDTO> proposedRecommendations = recommendationPersistenceService
                .savePendingRecommendations(user, topPerfumes);
        enrichReasons(referenceProfile, topPerfumes, proposedRecommendations);
        String answer = "He tomado como referencia " + reference.getName() + " de " + reference.getBrand()
                + " por sus notas de " + compactNotes(reference.getNotes())
                + ". Te dejo 3 alternativas con un perfil parecido.";
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
        return ChatResponseDTO.builder()
                .answer(answer)
                .proposedRecommendation(firstRecommendation(proposedRecommendations))
                .proposedRecommendations(proposedRecommendations)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    private Optional<String> extractSimilarPerfumeName(String message) {
        if (!StringUtils.hasText(message)) {
            return Optional.empty();
        }

        String normalized = normalize(message);
        if (!containsAny(normalized, "parecido", "similar", "alternativa", "dupe", "clon", "clon de", "inspirado")) {
            return Optional.empty();
        }

        String quoted = extractQuotedText(message);
        if (StringUtils.hasText(quoted)) {
            return Optional.of(cleanReferenceName(quoted));
        }

        for (String marker : List.of("parecido a", "similar a", "alternativa a", "dupe de", "clon de",
                "inspirado en", "como ")) {
            int index = normalized.indexOf(marker);
            if (index >= 0) {
                String rawName = message.substring(Math.min(message.length(), index + marker.length()));
                String cleaned = cleanReferenceName(rawName);
                if (StringUtils.hasText(cleaned)) {
                    return Optional.of(cleaned);
                }
            }
        }

        return Optional.empty();
    }

    private String extractQuotedText(String message) {
        int start = Math.max(message.indexOf('"'), message.indexOf('“'));
        if (start < 0) {
            start = message.indexOf('\'');
        }
        if (start < 0 || start + 1 >= message.length()) {
            return "";
        }

        char quote = message.charAt(start);
        char closingQuote = quote == '“' ? '”' : quote;
        int end = message.indexOf(closingQuote, start + 1);
        if (end <= start) {
            return "";
        }
        return message.substring(start + 1, end);
    }

    private String cleanReferenceName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = value
                .replaceAll("(?i)\\b(para hombre|para mujer|de hombre|de mujer|unisex|por menos de .*|menos de .*|con presupuesto.*)$", " ")
                .replaceAll("[¿?¡!.,;:]+$", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }

    private PerfumeItem findBestReferenceMatch(List<PerfumeItem> matches, String referenceName) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }

        String normalizedReference = normalize(referenceName);
        return matches.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getName()))
                .filter(item -> {
                    String candidate = normalize(item.getName() + " " + item.getBrand());
                    return candidate.contains(normalizedReference)
                            || normalizedReference.contains(normalize(item.getName()));
                })
                .findFirst()
                .orElse(matches.get(0));
    }

    private PerfumeProfile profileFromReference(PerfumeProfile currentProfile, PerfumeItem reference) {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget(StringUtils.hasText(currentProfile.getGenderTarget())
                ? currentProfile.getGenderTarget()
                : reference.getGender());
        profile.setSeason(StringUtils.hasText(currentProfile.getSeason()) ? currentProfile.getSeason() : reference.getSeason());
        profile.setPreferredNotes(reference.getNotes());
        profile.setIntensity(currentProfile.getIntensity());
        profile.setOccasion(currentProfile.getOccasion());
        profile.setBudget(currentProfile.getBudget());
        profile.setDislikedNotes(currentProfile.getDislikedNotes());
        profile.setLastSummary(String.join(" | ", List.of(
                valueOrDash(profile.getGenderTarget()),
                valueOrDash(profile.getSeason()),
                valueOrDash(profile.getIntensity()),
                valueOrDash(profile.getPreferredNotes()),
                valueOrDash(profile.getOccasion()),
                valueOrDash(profile.getBudget()),
                valueOrDash(profile.getDislikedNotes()))));
        return profile;
    }

    private String buildSimilarPerfumeQuery(PerfumeItem reference, PerfumeProfile profile, String message) {
        return String.join(" ", List.of(
                valueOrDash(reference.getNotes()),
                valueOrDash(reference.getSeason()),
                valueOrDash(reference.getGender()),
                valueOrDash(profile.getGenderTarget()),
                valueOrDash(profile.getSeason()),
                valueOrDash(profile.getBudget()),
                valueOrDash(message)))
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void applyReferenceToProfile(PerfumeProfile profile, PerfumeProfile referenceProfile, PerfumeItem reference) {
        if (!StringUtils.hasText(profile.getPreferredNotes())) {
            profile.setPreferredNotes(reference.getNotes());
        }
        if (!StringUtils.hasText(profile.getSeason()) && StringUtils.hasText(referenceProfile.getSeason())) {
            profile.setSeason(referenceProfile.getSeason());
        }
        if (!StringUtils.hasText(profile.getGenderTarget()) && StringUtils.hasText(referenceProfile.getGenderTarget())) {
            profile.setGenderTarget(referenceProfile.getGenderTarget());
        }
        profile.setLastSummary(referenceProfile.getLastSummary());
    }

    private ChatResponseDTO handleAdvisorClarification(User user, PerfumeProfile profile, String message) {
        if (!shouldAskAdvisorClarification(user, profile, message)) {
            return null;
        }

        String missingField = selectAdvisorMissingField(profile, message);
        String answer = buildAdvisorClarificationAnswer(user, profile, message, missingField);
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
        return ChatResponseDTO.builder()
                .answer(answer)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    private boolean shouldAskAdvisorClarification(User user, PerfumeProfile profile, String message) {
        String normalized = normalize(message);
        if (recommendationPersistenceService.hasPendingRecommendation(user)) {
            return false;
        }

        List<String> missingFields = blockingRecommendationFields(profile);
        if (missingFields.isEmpty()) {
            return false;
        }

        return isDiscoveryRelevantMessage(normalized)
                || hasAnyProfileClue(profile)
                || latestAssistantAskedAdvisorClarification(user)
                || latestAssistantAskedForPreferences(user);
    }

    private boolean isProfessionalProfile(PerfumeProfile profile, String normalizedMessage) {
        return containsAny(normalizedMessage, "abogado", "abogada", "juicio", "juzgado", "despacho",
                "oficina", "empresa", "empresario", "reunion", "profesional", "presencia")
                || "trabajo".equals(normalize(profile.getOccasion()))
                || containsAny(normalize(profile.getPreferredNotes()), "profesional", "elegante");
    }

    private boolean hasCoreOlfactiveFamily(PerfumeProfile profile) {
        String notes = normalize(profile.getPreferredNotes());
        return containsAny(notes, "fresco", "dulce", "amaderado", "floral", "especiado", "marino",
                "acuatico", "limpio", "citrico", "jabonoso", "madera", "cedro", "sandalo", "vetiver",
                "vainilla", "cuero", "oud", "ahumado", "humo", "tabaco", "ambar", "almizcle", "mineral",
                "metalico", "industrial", "gasolina", "salino", "animalico", "gourmand", "cremoso",
                "arroz", "canela", "coco", "caramelo", "frutal", "fresa", "chocolate", "miel",
                "incienso", "cafe", "iris", "lavanda");
    }

    private List<String> missingRecommendationFields(PerfumeProfile profile) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(profile.getGenderTarget())) {
            missing.add("si lo quieres para hombre, mujer o unisex");
        }
        if (!hasCoreOlfactiveFamily(profile)) {
            missing.add("familia olfativa: fresco, dulce, amaderado, floral o especiado");
        }
        if (!StringUtils.hasText(profile.getSeason())) {
            missing.add("la epoca del ano");
        }
        if (!StringUtils.hasText(profile.getIntensity())) {
            missing.add("intensidad: suave o potente");
        }
        if (!StringUtils.hasText(profile.getOccasion())) {
            missing.add("ocasion: diario, trabajo, cita, noche o versatil");
        }
        if (!StringUtils.hasText(profile.getBudget())) {
            missing.add("presupuesto: economico, medio o premium");
        }
        return missing;
    }

    private List<String> blockingRecommendationFields(PerfumeProfile profile) {
        return missingRecommendationFields(profile).stream()
                .filter(field -> !isDefaultableField(field))
                .toList();
    }

    private boolean isDefaultableField(String field) {
        return field.startsWith("la epoca")
                || field.startsWith("intensidad");
    }

    private boolean hasAnyProfileClue(PerfumeProfile profile) {
        return StringUtils.hasText(profile.getGenderTarget())
                || StringUtils.hasText(profile.getSeason())
                || StringUtils.hasText(profile.getPreferredNotes())
                || StringUtils.hasText(profile.getIntensity())
                || StringUtils.hasText(profile.getOccasion())
                || StringUtils.hasText(profile.getBudget())
                || StringUtils.hasText(profile.getDislikedNotes());
    }

    private boolean isDiscoveryRelevantMessage(String normalizedMessage) {
        return containsAny(normalizedMessage,
                "perfume", "colonia", "fragancia", "olor", "oler", "huela", "huele", "aroma",
                "quiero", "busco", "necesito", "para", "diario", "trabajo", "cita", "noche",
                "hombre", "mujer", "unisex", "fresco", "limpio", "jabonoso", "citrico", "acuatico",
                "dulce", "madera", "amaderado", "invierno", "verano", "barato", "premium",
                "primavera", "otono", "estacion", "calor", "frio", "precio", "euros", "presupuesto",
                "gama media", "suave", "potente", "intenso", "vainilla", "ambar", "almizcle", "oud",
                "tabaco", "incienso", "cafe", "iris", "lavanda", "frutal", "fresa", "fresas", "fruta",
                "gourmand", "caramelo", "coco", "chocolate", "miel", "cague", "culo", "mierda", "sudor");
    }

    private boolean isOdorControlMessage(String normalizedMessage) {
        return containsAny(normalizedMessage, "cague", "cagar", "culo", "mierda", "mal olor",
                "huela mal", "oler mal", "sudor", "sudar", "axila", "bano", "wc");
    }

    private String selectAdvisorMissingField(PerfumeProfile profile, String message) {
        List<String> missingFields = blockingRecommendationFields(profile);
        if (missingFields.isEmpty()) {
            return "";
        }

        String normalized = normalize(message);
        if ((isOdorControlMessage(normalized)
                || containsAny(normalized, "diario", "limpio", "ducha", "jabonoso", "fresco"))
                && missingFields.contains("familia olfativa: fresco, dulce, amaderado, floral o especiado")) {
            return "familia olfativa: fresco, dulce, amaderado, floral o especiado";
        }

        if (isProfessionalProfile(profile, normalized)
                && missingFields.contains("familia olfativa: fresco, dulce, amaderado, floral o especiado")) {
            return "familia olfativa: fresco, dulce, amaderado, floral o especiado";
        }

        return missingFields.get(0);
    }

    private boolean latestAssistantAskedAdvisorClarification(User user) {
        return recommendationPersistenceService.findRecentMessages(user).stream()
                .filter(item -> "ASSISTANT".equals(item.getRoleName()))
                .findFirst()
                .map(item -> {
                    String text = normalize(item.getContent());
                    return containsAny(text, "para afinar en fragella", "antes de buscar en fragella",
                            "quiero acotar el estilo", "perfil serio", "despacho y reuniones");
                })
                .orElse(false);
    }

    private String buildAdvisorClarificationAnswer(PerfumeProfile profile) {
        String missingField = selectAdvisorMissingField(profile, "");
        return buildAdvisorClarificationAnswer(profile, "", missingField);
    }

    private String buildAdvisorClarificationAnswer(User user, PerfumeProfile profile, String message, String missingField) {
        String fallback = buildAdvisorClarificationAnswer(profile, message, missingField);
        return buildConversationalAnswer(user, """
                El asesor local ya ha decidido que falta exactamente este dato: %s.
                Redacta una respuesta de asesor olfativo premium, maximo 2 frases.
                Pregunta solo por ese dato y no recomiendes nombres de perfumes todavia.
                No cambies el significado del perfil ni el genero interpretado.
                Si el usuario dice que quiere atraer a mujeres, no lo conviertas en perfume femenino: tratelo como perfil masculino o unisex sensual.
                Si menciona arroz con leche, interpretalo como dulce/gourmand/cremoso con vainilla y canela.
                Respuesta base a mejorar manteniendo la misma pregunta: %s
                Perfil actual: %s
                Ultimo mensaje del usuario: %s
                """.formatted(
                missingField,
                fallback,
                valueOrDash(profile.getLastSummary()),
                message),
                fallback);
    }

    private String buildAdvisorClarificationAnswer(PerfumeProfile profile, String message, String missingField) {
        String normalizedMessage = normalize(message);
        String notes = normalize(profile.getPreferredNotes());
        String gender = "mujer".equals(normalize(profile.getGenderTarget())) ? "femenino"
                : "hombre".equals(normalize(profile.getGenderTarget())) ? "masculino" : "profesional";

        if (isOdorControlMessage(normalizedMessage) && missingField.startsWith("familia olfativa")) {
            return "Te entiendo: buscas un perfume limpio de diario que de sensacion de recien duchado, aunque no debe usarse para tapar mal olor fuerte. Para afinar en Fragella, lo quieres mas jabonoso/ropa limpia, citrico fresco o acuatico?";
        }

        if (missingField.startsWith("familia olfativa") && isProfessionalProfile(profile, normalizedMessage)
                && !StringUtils.hasText(profile.getSeason())) {
            return "Lo leo como un perfume " + gender
                    + ", serio y con presencia para un entorno legal, pero antes de buscar en Fragella quiero acotar el estilo. Lo ves mas limpio-amaderado para despacho y reuniones, o mas oscuro/especiado para invierno y noche?";
        }

        if (missingField.startsWith("familia olfativa") && isProfessionalProfile(profile, normalizedMessage)) {
            return "Ya tengo claro el contexto profesional y la presencia que buscas. Para afinar en Fragella: prefieres una madera limpia tipo cedro/vetiver, una madera cremosa tipo sandalo, o algo mas oscuro con cuero/oud?";
        }

        if (missingField.startsWith("familia olfativa")) {
            String scope = StringUtils.hasText(profile.getGenderTarget()) ? gender : "ese uso";
            if (containsAny(notes, "casual", "diario")) {
                return "Perfecto, lo planteo para diario. Para que no sea una colonia generica, lo quieres mas limpio/jabonoso, citrico fresco, acuatico, amaderado suave o dulce?";
            }
            return "Ya tengo una parte del perfil, pero falta la direccion del olor. Te tira mas algo limpio y fresco, amaderado, dulce/gourmand, especiado o floral para " + scope + "?";
        }

        if (missingField.startsWith("si lo quieres")) {
            return "El estilo ya empieza a tomar forma, pero me falta para quien lo orientamos. Lo buscamos para hombre, mujer o prefieres que sea unisex?";
        }

        if (missingField.startsWith("la epoca")) {
            if ("diario".equals(normalize(profile.getOccasion()))) {
                return "Vale, lo enfoco para diario. Lo quieres para todo el ano, para calor/verano o para frio/invierno?";
            }
            if (isProfessionalProfile(profile, normalizedMessage)) {
                return "Tengo clara la linea profesional y olfativa. Lo quieres para invierno, todo el ano, o mas bien para diario de oficina?";
            }
            return "Tengo claro el estilo olfativo que buscas. Lo quieres para verano/calor, invierno/frio o para usar todo el ano?";
        }

        if (missingField.startsWith("intensidad")) {
            return "Con esa direccion ya puedo afinar bastante. Prefieres que se note suave y limpio de cerca, o que tenga mas potencia y dure varias horas?";
        }

        if (missingField.startsWith("ocasion")) {
            return "Ya se por donde va el olor, pero me falta el uso principal. Lo quieres para diario, trabajo, citas/noche o algo todoterreno?";
        }

        if (missingField.startsWith("presupuesto")) {
            return "Tengo el perfil bastante enfocado, pero me falta el presupuesto. Para no irme fuera de rango, prefieres economico, medio o premium?";
        }

        return "Dame una pista mas de como quieres sentirlo y sigo afinando contigo.";
    }

    private String compactNotes(String notes) {
        List<String> tokens = List.of(valueOrDash(notes).split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
        return tokens.isEmpty() ? "su perfil olfativo" : String.join(", ", tokens);
    }

    private ChatResponseDTO recommendFromProfile(User user, PerfumeProfile profile, String message,
            boolean excludeExistingRecommendations) {
        List<String> missingFields = blockingRecommendationFields(profile);
        if (!missingFields.isEmpty()) {
            String answer = buildQuestionAnswer(user, profile, message, missingFields);
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        applyRecommendationDefaults(profile);
        perfumeProfileRepo.save(profile);

        String query = aiDecisionService.buildSearchQuery(profile, message);
        List<PerfumeItem> catalog = filterUnsuitablePerfumes(user, perfumeCatalogService.searchPerfumes(query), profile);
        if (excludeExistingRecommendations) {
            List<PerfumeItem> freshCatalog = excludeAlreadyRecommended(user, catalog);
            if (!freshCatalog.isEmpty()) {
                catalog = freshCatalog;
            }
        }

        List<PerfumeItem> topPerfumes = chooseTopPerfumesWithHistory(user, catalog, profile, message, 3);
        topPerfumes = expandTopPerfumesIfNeeded(user, profile, message, topPerfumes, catalog, 3,
                excludeExistingRecommendations, query);
        if (topPerfumes.isEmpty()) {
            String answer = "Tengo tus gustos, pero no he encontrado opciones distintas ahora mismo. Prueba cambiando una pista, por ejemplo mas fresco, menos dulce o otro presupuesto.";
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        List<PerfumeRecommendationDTO> proposedRecommendations = recommendationPersistenceService
                .savePendingRecommendations(user, topPerfumes);
        enrichReasons(profile, topPerfumes, proposedRecommendations);
        String answer = buildTopRecommendationsAnswer(profile, topPerfumes);
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
        return ChatResponseDTO.builder()
                .answer(answer)
                .proposedRecommendation(firstRecommendation(proposedRecommendations))
                .proposedRecommendations(proposedRecommendations)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    private ChatResponseDTO acceptLatestRecommendation(User user) {
        PerfumeRecommendation recommendation = recommendationPersistenceService.findLatestPendingRecommendation(user)
                .orElse(null);

        if (recommendation == null) {
            String answer = "Hola, soy PerfumIA. Cuéntame un poco qué buscas: algo fresco para diario, algo más intenso, o todavía no lo tienes claro?";
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        recommendation = recommendationPersistenceService.markAccepted(recommendation);

        String answer = "Hola, soy PerfumIA. Cuéntame un poco qué buscas: algo fresco para diario, algo más intenso, o todavía no lo tienes claro?";
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);

        return ChatResponseDTO.builder()
                .answer(answer)
                .proposedRecommendation(recommendationPersistenceService.toDto(recommendation))
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    private ChatResponseDTO rejectLatestRecommendation(User user) {
        PerfumeRecommendation recommendation = recommendationPersistenceService.findLatestPendingRecommendation(user)
                .orElse(null);

        String answer;
        if (recommendation == null) {
            answer = buildConversationalAnswer(user, """
                    El usuario rechaza algo, pero no hay propuesta pendiente.
                    Responde natural y preguntale que tipo de olor o estilo quiere evitar.
                    """,
                    "Vale, no pasa nada. Dime que no te encaja: demasiado fresco, dulce, intenso, caro, formal o masculino/femenino, y busco otra direccion.");
        } else {
            recommendationPersistenceService.markRejected(recommendation);
            answer = buildConversationalAnswer(user, """
                    El usuario rechaza esta propuesta: %s de %s, notas: %s.
                    Responde natural, confirma que la descartas y pregunta que no le convence para buscar otra alternativa.
                    No recomiendes todavia otro perfume.
                    """.formatted(recommendation.getPerfumeName(), recommendation.getBrand(), valueOrDash(recommendation.getNotes())),
                    "Perfecto, descartamos " + recommendation.getPerfumeName()
                            + ". Dime que no te convence de esa opcion: olor, intensidad, precio, estacion o estilo, y busco otra alternativa.");
        }
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);

        return ChatResponseDTO.builder()
                .answer(answer)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    private PerfumeProfile createProfile(User user) {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        return profile;
    }

    private void resetProfileForNewConversation(PerfumeProfile profile) {
        profile.setGenderTarget(null);
        profile.setSeason(null);
        profile.setPreferredNotes(null);
        profile.setIntensity(null);
        profile.setOccasion(null);
        profile.setBudget(null);
        profile.setDislikedNotes(null);
        profile.setLastSummary(null);
    }

    private ChatResponseDTO handleWithGeminiIfPossible(User user, PerfumeProfile profile, String message) {
        if (!geminiService.isConfigured()) {
            return null;
        }

        AiDecision decision = buildAiDecision(user, profile, message);
        if (decision == null) {
            return null;
        }

        if ((!decision.perfumeRelated() || aiDecisionService.isCasualIntent(decision.intent()))
                && !hasPerfumeRelevantContext(profile, message)) {
            String answer = StringUtils.hasText(decision.answer())
                    ? decision.answer()
                    : "No tengo una respuesta clara para eso, pero dime un poco mas y lo intento.";
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        if (aiDecisionService.isAnotherPerfumeIntent(decision.intent())) {
            aiDecisionService.updateProfileFromMessage(profile, message);
            applyDecisionToProfile(profile, decision);
            perfumeProfileRepo.save(profile);
            if (decision.readyToSearch() || hasEnoughUsefulProfile(profile)) {
                return recommendFromProfile(user, profile, message, true);
            }
            String answer = StringUtils.hasText(decision.answer())
                    ? decision.answer()
                    : buildAnotherPerfumeAnswer(user, profile);
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        aiDecisionService.updateProfileFromMessage(profile, message);
        applyDecisionToProfile(profile, decision);
        perfumeProfileRepo.save(profile);

        List<String> missingFields = blockingRecommendationFields(profile);
        if (shouldSearchWithProfileDefaults(profile, decision, message)) {
            applyRecommendationDefaults(profile);
            perfumeProfileRepo.save(profile);
            return recommendFromProfile(user, profile, message, false);
        }

        if (!missingFields.isEmpty()) {
            String answer = buildGeminiGuidedQuestion(user, profile, message, missingFields, decision);
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        String clarificationAnswer = promptBuilderService.answerClarificationQuestion(message, missingFields);
        if (StringUtils.hasText(clarificationAnswer)
                && !decision.readyToSearch()
                && !aiDecisionService.wantsSearchNow(normalize(message))) {
            String answer = clarificationAnswer;
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        applyRecommendationDefaults(profile);
        perfumeProfileRepo.save(profile);

        String query = mergeSearchQueries(decision.searchQuery(), aiDecisionService.buildSearchQuery(profile, message));
        List<PerfumeItem> catalog = filterUnsuitablePerfumes(user, perfumeCatalogService.searchPerfumes(query), profile);
        List<PerfumeItem> topPerfumes = chooseTopPerfumesWithHistory(user, catalog, profile, message, 3);
        topPerfumes = expandTopPerfumesIfNeeded(user, profile, message, topPerfumes, catalog, 3, false, query);
        if (topPerfumes.isEmpty()) {
            String answer = buildGeminiAnswer(user, profile, message, catalog, List.of(), null);

            if (!StringUtils.hasText(answer)) {
                answer = buildFallbackAnswer(profile, List.of(), null);
            }

            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        List<PerfumeRecommendationDTO> proposedRecommendations = recommendationPersistenceService
                .savePendingRecommendations(user, topPerfumes);
        enrichReasons(profile, topPerfumes, proposedRecommendations);
        String answer = buildTopRecommendationsAnswer(profile, topPerfumes);
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
        return ChatResponseDTO.builder()
                .answer(answer)
                .proposedRecommendation(firstRecommendation(proposedRecommendations))
                .proposedRecommendations(proposedRecommendations)
                .savedRecommendations(listRecommendations(user))
                .build();
    }

    private AiDecision buildAiDecision(User user, PerfumeProfile profile, String message) {
        List<ChatMessage> history = recommendationPersistenceService.findRecentMessages(user);
        Collections.reverse(history);
        String historyText = history.stream()
                .map(item -> item.getRoleName() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));

        String accepted = recommendationPersistenceService.findAcceptedRecommendations(user).stream()
                .map(item -> item.getPerfumeName() + " de " + item.getBrand() + " (" + item.getNotes() + ")")
                .collect(Collectors.joining("\n"));

        String prompt = """
                Eres el cerebro conversacional de PerfumIA, un asesor olfativo profesional, cercano y premium.
                Tu trabajo NO es elegir perfumes todavia, sino entender al usuario, completar el perfil y decidir si ya hay datos suficientes para buscar en Fragella.

                Responde SOLO con JSON valido, sin markdown:
                {
                  "intent": "perfume_chat",
                  "perfumeRelated": true,
                  "answer": "respuesta natural al usuario, o siguiente pregunta si falta informacion",
                  "readyToSearch": false,
                  "searchQuery": "consulta corta para API de perfumes cuando readyToSearch sea true",
                  "profile": {
                    "genderTarget": "",
                    "season": "",
                    "preferredNotes": "",
                    "intensity": "",
                    "occasion": "",
                    "budget": "",
                    "dislikedNotes": ""
                  }
                }

                Reglas:
                - Usa valores canonicos en profile: genderTarget=hombre|mujer|unisex; season=verano|invierno|primavera|otono|versatil; intensity=suave|intenso; occasion=diario|trabajo|especial|versatil; budget=economico|economico-medio|medio|premium.
                - Bandas de precio: economico hasta 60 euros, medio entre 60 y 150 euros, premium mas de 150 euros. Si el usuario dice "menos de 80", "hasta 100" o "gama media", usa budget="medio"; si dice "sin limite" o "lujo", usa budget="premium".
                - preferredNotes y dislikedNotes deben ser etiquetas separadas por comas, no frases largas. Etiquetas validas: fresco, citrico, limpio, marino, acuatico, salino, dulce, gourmand, cremoso, vainilla, caramelo, coco, frutal, fresa, chocolate, miel, amaderado, cuero, ahumado, industrial, mineral, floral, especiado, canela, ambar, almizcle, oud, tabaco, incienso, cafe, iris, lavanda, sexy, sensual, elegante, profesional, lujoso, juvenil, oscuro, minimalista, misterioso, calido, casual, empalagoso.
                - Si el usuario rechaza una nota ("sin vainilla", "no me gusta la rosa", "dulce pero no empalagoso"), ponla en dislikedNotes y no la mezcles como preferencia positiva.
                - intent debe ser uno de: casual, greeting, perfume_chat, another_perfume, recommend_now.
                - perfumeRelated debe ser true solo si el mensaje trata de perfumes, colonias, fragancias, olores, notas, marcas de perfumes, recomendaciones o el usuario responde a una pregunta anterior sobre perfumes.
                - Si el mensaje es un saludo tipo "hola", "buenas" o charla ligera, usa intent=greeting o casual, perfumeRelated=false, readyToSearch=false y responde normal, recordando brevemente que eres recomendador de perfumes.
                - Si el mensaje mezcla saludo con una pista de perfume u olor ("hola, me gusta...", "hoola no entiendo de perfumes pero..."), NO lo trates como greeting: usa perfumeRelated=true, extrae la pista y continua la asesoria.
                - No repitas el saludo inicial si ya aparece en el historial. Continua desde el ultimo mensaje del usuario.
                - Si el usuario pide "otro", "otra opcion", "quiero otro", "busca", "busca otro", "recomiendame", "que me recomiendas" o similar y ya hay perfil suficiente, usa readyToSearch=true para buscar en Fragella; no hagas preguntas de confirmacion innecesarias.
                - Si el usuario dice "ni idea", "no se", "nose" o algo ambiguo, no repitas la misma pregunta. Explica 2-3 opciones sencillas, recomienda una direccion razonable y pide confirmacion.
                - Si el usuario habla de tapar mal olor corporal, bano, sudor, cagar, culo, mierda o similar, traduce la necesidad a un perfume limpio/jabonoso/fresco de diario, pero aclara con tacto que un perfume no sustituye higiene o desodorante. No recomiendes todavia: pregunta por estilo limpio, citrico o acuatico.
                - Si el usuario hace una duda o pregunta de perfumes, respondela primero y no marques readyToSearch salvo que pida recomendar ya claramente o haya datos suficientes y no falte contexto importante.
                - No recomiendes nombres de perfumes en esta fase si readyToSearch=false.
                - No inventes datos. Deja vacio lo que no se sepa.
                - Si faltan datos importantes, answer debe ser UNA unica pregunta natural y contextual, no una pregunta de formulario.
                - Datos obligatorios antes de buscar: genero objetivo, ocasion/uso principal, familia olfativa real y presupuesto. Si falta uno, di claramente que falta ese dato y pregunta solo eso.
                - Estacion e intensidad son opcionales si el resto esta claro: el backend puede usar versatil y una intensidad razonable.
                - Aprovecha el contexto del usuario: si habla de abogado, juicios, empresario, oficina o reuniones, orienta la conversacion hacia un perfil profesional, elegante, limpio, con presencia y creible.
                - Si dice "abogado" o "empresario" y no aclara genero, interpreta genderTarget="hombre"; si dice "abogada" o "empresaria", genderTarget="mujer".
                - Si dice que quiere atraer a mujeres, gustar a mujeres, ligar con mujeres o que mujeres se acerquen a el, NO lo interpretes como perfume femenino: usa genderTarget="hombre" salvo que pida explicitamente "para mujer".
                - Si menciona "arroz con leche", guardalo como preferredNotes="dulce, gourmand, cremoso, arroz, vainilla, canela" y orientalo como gourmand lactonico.
                - Para perfiles profesionales con presencia, no recomiendes al primer mensaje si aun no hay familia olfativa concreta o temporada. Haz una pregunta asesora para elegir direccion: limpio-amaderado, madera cremosa, oscuro/especiado, invierno o todo el ano.
                - No repitas preguntas sobre campos ya respondidos, aunque el usuario los haya dicho con palabras equivalentes. Ejemplos: "todo el ano", "todas las estaciones" o "versatil" ya resuelven season; "juicio", "oficina" o "empresario" ya resuelven occasion profesional/trabajo; "olor a mar" ya resuelve notas frescas/marinas.
                - Antes de preguntar, demuestra en una frase corta que has entendido lo que ya dijo el usuario.
                - Manten la conversacion natural: no conviertas el chat en un formulario rigido ni enumeres campos.
                - Evita frases secas como "Primera decision", "Ultima pregunta" o listas tipo test.
                - Tu answer debe sonar humano y de asesor olfativo profesional: maximo 2 frases, una de lectura/contexto y una pregunta.
                - MantÃ©n la conversacion natural: no conviertas el chat en un formulario rigido.
                - Si el usuario dice que no sabe, guiale con ejemplos sencillos y deja que elija una direccion.
                - Aunque el perfil este completo, no marques readyToSearch si el ultimo mensaje es saludo, charla casual o duda teorica.
                - readyToSearch debe ser true cuando el perfil tenga: genero o unisex, ocasion, una familia olfativa real y presupuesto. Si la ocasion es diario y no hay estacion, usa season="versatil". Si falta intensidad pero el olor es limpio/jabonoso/fresco de diario, usa intensity="suave"; si busca seduccion, noche o mucha presencia, usa intensity="potente".
                - "diario", "casual", "elegante" o "profesional" NO cuentan como familia olfativa real por si solos. Necesitas algo como fresco, limpio, jabonoso, citrico, acuatico, dulce, amaderado, especiado o floral.
                - Si el usuario pide recomendar ya pero falta algun dato esencial, no marques readyToSearch: haz la siguiente pregunta asesora.
                - Si ya estan genero, ocasion, familia olfativa y presupuesto, no preguntes por campos opcionales: marca readyToSearch=true y deja que el backend elija perfumes reales.
                - Si falta informacion de verdad, haz una sola pregunta. Nunca juntes varias preguntas en la misma respuesta.
                - No hagas preguntas genericas de formulario si el usuario ya dio una imagen olfativa. Ejemplo: "gasolina" o "tubo de escape" ya es una direccion olfativa; traduce eso a algo usable como cuero ahumado, vetiver, madera oscura, incienso, birch tar, mineral o metalico, y pregunta por el siguiente dato que falte.
                - Si el usuario pide olores poco ponibles o de broma, como gasolina, tubo de escape, humo de motor o similares, responde con naturalidad y criterio: no guardes la literalidad como unica preferencia, reinterpretala como un perfume oscuro, ahumado, cuero/mineral/industrial y potencialmente seductor. No repitas una lista de familias basicas.
                - Si el usuario quiere atraer a mujeres con ese tipo de perfil, orientalo a "masculino o unisex seductor"; no lo conviertas en perfume femenino.
                - searchQuery debe combinar solo los datos conocidos: genero, estacion, familia olfativa, notas, estilo, ocasion e intensidad.
                - En preferredNotes guarda gustos separados por comas. Usa solo valores claros del usuario, por ejemplo: vainilla, caramelo, coco, frutal, fresa, chocolate, miel, limpio, sexy, sensual, elegante, juvenil, oscuro, lujoso, minimalista, misterioso, calido, casual, floral, fresco, amaderado, especiado, dulce, cuero, ahumado, mineral, metalico, industrial.
                - En dislikedNotes guarda rechazos separados por comas. Detecta notas o estilos que el usuario quiera evitar, por ejemplo: rosa, incienso, pachuli, oud, citricos, vainilla, caramelo, coco, frutal, chocolate, miel, dulce, empalagoso, pesado.
                - Si el usuario dice "dulce pero no empalagoso", puedes poner preferredNotes="dulce" y dislikedNotes="empalagoso".
                - Si el usuario pide algo "limpio", "de ducha" o "jabonoso", usa preferredNotes="limpio".
                - Si el usuario pide algo "con estela", "que se note", "potente", "potencia", "potecia", "que dure", "dure mucho" o "larga duracion", usa intensity="potente"; si pide "discreto" o "suave", usa intensity="suave".
                - Si el usuario dice "todo el ano", "todas las estaciones", "cualquier estacion" o "versatil", usa season="versatil".
                - Si el usuario menciona "abogado", "juicio", "juzgado", "oficina", "empresa", "empresario", "reunion", "despacho" o contexto profesional, usa occasion="trabajo" y preferredNotes con "elegante, profesional"; si encaja, tambien "lujoso".
                - Si el usuario pide "olor a mar", "marino", "acuatico", "sea", "marine" u "oceanico", usa preferredNotes con "fresco, marino, acuatico".
                - Si el usuario pide "atun" o un olor raro parecido, no lo guardes literal como comida: traducelo a preferredNotes="marino, salino, animalico" y preguntale solo si no ha confirmado esa direccion.
                - Si el usuario confirma "marino y animalico", ya hay familia olfativa real. Si ademas hay genero, ocasion y presupuesto, marca readyToSearch=true aunque falten estacion o intensidad; puedes usar season="versatil" e intensity="suave" como valores por defecto.
                - Si el usuario dice "busca", "pero busca" o "necesitas algo mas" despues de haber confirmado la direccion olfativa, no vuelvas a preguntar familias. Si falta algun dato obligatorio, pregunta solo por ese dato; si no falta, marca readyToSearch=true.
                - Si el usuario dice "presupuesto medio", "gama media" o "precio medio", usa budget="medio".
                - Usa espanol cercano y breve.

                Perfil actual: %s
                Perfumes aceptados antes:
                %s

                Historial reciente:
                %s

                Usuario: %s
                """.formatted(
                valueOrDash(profile.getLastSummary()),
                StringUtils.hasText(accepted) ? accepted : "ninguno",
                historyText,
                message);

        String rawAnswer = geminiService.generateJsonAnswer(prompt);
        if (!StringUtils.hasText(rawAnswer)) {
            log.info("Gemini AiDecision empty response (status={}). Falling back to local rules.", geminiService.status());
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(extractJson(rawAnswer));
            JsonNode profileNode = root.path("profile");
            return new AiDecision(
                    text(root, "intent"),
                    root.path("perfumeRelated").asBoolean(true),
                    text(root, "answer"),
                    root.path("readyToSearch").asBoolean(false),
                    text(root, "searchQuery"),
                    text(profileNode, "genderTarget"),
                    text(profileNode, "season"),
                    text(profileNode, "preferredNotes"),
                    text(profileNode, "intensity"),
                    text(profileNode, "occasion"),
                    text(profileNode, "budget"),
                    text(profileNode, "dislikedNotes"));
        } catch (Exception ex) {
            log.info("Gemini AiDecision JSON parse failed (status={}). Raw (trimmed): {}",
                    geminiService.status(), rawAnswer.replaceAll("\\s+", " ").trim());
            return null;
        }
    }

    private void applyDecisionToProfile(PerfumeProfile profile, AiDecision decision) {
        setIfText(canonicalGender(decision.genderTarget()), profile::setGenderTarget);
        setIfText(canonicalSeason(decision.season()), profile::setSeason);
        setIfText(canonicalIntensity(decision.intensity()), profile::setIntensity);
        setIfText(canonicalOccasion(decision.occasion()), profile::setOccasion);
        setIfText(canonicalBudget(decision.budget()), profile::setBudget);
        mergeNotes(profile.getPreferredNotes(), canonicalNotes(decision.preferredNotes()), profile::setPreferredNotes);
        mergeNotes(profile.getDislikedNotes(), canonicalNotes(decision.dislikedNotes()), profile::setDislikedNotes);

        profile.setLastSummary(String.join(" | ", List.of(
                valueOrDash(profile.getGenderTarget()),
                valueOrDash(profile.getSeason()),
                valueOrDash(profile.getIntensity()),
                valueOrDash(profile.getPreferredNotes()),
                valueOrDash(profile.getOccasion()),
                valueOrDash(profile.getBudget()),
                valueOrDash(profile.getDislikedNotes()))));
    }

    private boolean shouldSearchWithProfileDefaults(PerfumeProfile profile, AiDecision decision, String message) {
        if (!hasEnoughUsefulProfile(profile)) {
            return false;
        }

        List<String> missingFields = missingRecommendationFields(profile);
        boolean onlyDefaultableFieldsMissing = missingFields.stream()
                .allMatch(this::isDefaultableField);
        if (!onlyDefaultableFieldsMissing) {
            return false;
        }

        String normalizedMessage = normalize(message);
        return decision.readyToSearch()
                || aiDecisionService.wantsSearchNow(normalizedMessage)
                || answerSuggestsSearch(decision.answer())
                || currentMessageAddsConcreteOlfactiveDirection(normalizedMessage);
    }

    private boolean hasPerfumeRelevantContext(PerfumeProfile profile, String message) {
        String normalizedMessage = normalize(message);
        return hasAnyProfileClue(profile)
                || isDiscoveryRelevantMessage(normalizedMessage)
                || currentMessageAddsConcreteOlfactiveDirection(normalizedMessage)
                || containsAny(normalizedMessage, "perfume", "colonia", "fragancia", "olor", "olores",
                        "aroma", "nota", "notas", "fresas", "fresa", "frutal", "gourmand");
    }

    private String buildGeminiGuidedQuestion(User user, PerfumeProfile profile, String message,
            List<String> missingFields, AiDecision decision) {
        String answer = buildGeminiAnswer(user, profile, message, List.of(), missingFields, null);
        if (isUsableClarificationAnswer(answer, missingFields)) {
            return answer;
        }

        if (isUsableClarificationAnswer(decision.answer(), missingFields)) {
            return decision.answer();
        }

        return buildQuestionAnswer(user, profile, message, missingFields);
    }

    private boolean isUsableClarificationAnswer(String answer, List<String> missingFields) {
        if (!StringUtils.hasText(answer) || looksLikeInitialWelcome(answer)) {
            return false;
        }
        if (missingFields == null || missingFields.isEmpty()) {
            return true;
        }
        String normalized = normalize(answer);
        if (answerSuggestsSearch(normalized)) {
            return false;
        }
        return looksLikeQuestion(normalized);
    }

    private boolean looksLikeInitialWelcome(String answer) {
        String normalized = normalize(answer);
        return containsAny(normalized, "hola soy perfumia", "cuentame un poco que buscas")
                && containsAny(normalized, "algo fresco para diario", "algo mas intenso", "todavia no lo tienes claro");
    }

    private boolean looksLikeQuestion(String normalizedText) {
        return normalizedText.contains("?")
                || containsAny(normalizedText, "que prefieres", "cual prefieres", "para quien",
                        "con cual te quedas", "lo quieres", "lo buscamos", "para que lo quieres",
                        "que presupuesto", "que uso", "que estilo");
    }

    private boolean hasMinimumRecommendationProfile(PerfumeProfile profile) {
        return hasEnoughUsefulProfile(profile);
    }

    private boolean answerSuggestsSearch(String answer) {
        String normalized = normalize(answer);
        return containsAny(normalized, "ya podemos buscar", "puedo buscar", "vamos a buscar",
                "con estos datos", "ya puedo buscar", "buscar algo que te encaje", "buscar en fragella",
                "podemos empezar a buscar", "empezar a buscar opciones", "buscar opciones");
    }

    private boolean currentMessageAddsConcreteOlfactiveDirection(String normalizedMessage) {
        return containsAny(normalizedMessage, "jabonoso", "jabon", "limpio", "ducha", "ropa limpia",
                "citrico", "citrica", "acuatico", "marino", "dulce", "vainilla", "amaderado", "madera",
                "cuero", "ahumado", "especiado", "floral", "fresco", "salino", "animalico", "atun",
                "gourmand", "cremoso", "arroz", "canela", "coco", "caramelo", "frutal", "fresa",
                "chocolate", "miel", "ambar", "almizcle", "oud", "tabaco", "incienso", "cafe",
                "iris", "lavanda", "citrico");
    }

    private void applyRecommendationDefaults(PerfumeProfile profile) {
        if (!StringUtils.hasText(profile.getOccasion())) {
            profile.setOccasion("versatil");
        }
        if (!StringUtils.hasText(profile.getSeason())) {
            profile.setSeason("versatil");
        }
        if (!StringUtils.hasText(profile.getIntensity())) {
            String notes = normalize(profile.getPreferredNotes());
            String occasion = normalize(profile.getOccasion());
            if ("especial".equals(occasion)
                    || "noche".equals(occasion)
                    || "cita".equals(occasion)
                    || containsAny(notes, "sexy", "sensual", "oscuro", "cuero", "ahumado", "industrial")) {
                profile.setIntensity("intenso");
            } else {
                profile.setIntensity("suave");
            }
        }

        profile.setLastSummary(String.join(" | ", List.of(
                valueOrDash(profile.getGenderTarget()),
                valueOrDash(profile.getSeason()),
                valueOrDash(profile.getIntensity()),
                valueOrDash(profile.getPreferredNotes()),
                valueOrDash(profile.getOccasion()),
                valueOrDash(profile.getBudget()),
                valueOrDash(profile.getDislikedNotes()))));
    }

    private void setIfText(String value, java.util.function.Consumer<String> setter) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String clean = value.trim();
        String normalized = normalize(clean);
        if (!List.of("null", "unknown", "desconocido", "ninguno", "none", "n/a", "-").contains(normalized)) {
            setter.accept(clean);
        }
    }

    private void mergeNotes(String existingNotes, String newNotes, java.util.function.Consumer<String> setter) {
        if (!StringUtils.hasText(newNotes)) {
            return;
        }
        List<String> merged = new ArrayList<>();
        addNoteTokens(merged, existingNotes);
        addNoteTokens(merged, newNotes);
        if (!merged.isEmpty()) {
            setter.accept(String.join(", ", merged));
        }
    }

    private void addNoteTokens(List<String> target, String notes) {
        if (!StringUtils.hasText(notes)) {
            return;
        }
        for (String note : notes.split("[,;/|]+")) {
            String clean = note.trim();
            if (StringUtils.hasText(clean) && !target.contains(clean)) {
                target.add(clean);
            }
        }
    }

    private String canonicalGender(String value) {
        String normalized = normalize(value);
        if (containsAny(normalized, "unisex", "cualquiera", "ambos")) {
            return "unisex";
        }
        if (containsAny(normalized, "hombre", "masculino", "male", "men", "man", "homme")) {
            return "hombre";
        }
        if (containsAny(normalized, "mujer", "femenino", "female", "women", "woman", "femme")) {
            return "mujer";
        }
        return "";
    }

    private String canonicalSeason(String value) {
        String normalized = normalize(value);
        if (containsAny(normalized, "todo el ano", "todas las estaciones", "cualquier estacion", "versatil",
                "all year", "year round", "all seasons")) {
            return "versatil";
        }
        if (containsAny(normalized, "verano", "summer", "calor", "playa")) {
            return "verano";
        }
        if (containsAny(normalized, "invierno", "winter", "frio", "cold", "navidad")) {
            return "invierno";
        }
        if (containsAny(normalized, "primavera", "spring")) {
            return "primavera";
        }
        if (containsAny(normalized, "otono", "autumn", "fall")) {
            return "otono";
        }
        return "";
    }

    private String canonicalIntensity(String value) {
        String normalized = normalize(value);
        if (containsAny(normalized, "potente", "intenso", "fuerte", "duradero", "larga duracion", "long lasting",
                "strong", "powerful", "project", "estela", "proyeccion")) {
            return "intenso";
        }
        if (containsAny(normalized, "suave", "ligero", "discreto", "soft", "light", "subtle", "intimate")) {
            return "suave";
        }
        return "";
    }

    private String canonicalOccasion(String value) {
        String normalized = normalize(value);
        if (containsAny(normalized, "trabajo", "oficina", "profesional", "empresa", "reunion", "juicio",
                "juzgado", "business", "work", "office", "formal")) {
            return "trabajo";
        }
        if (containsAny(normalized, "diario", "dia a dia", "clase", "universidad", "daily", "casual")) {
            return "diario";
        }
        if (containsAny(normalized, "cita", "noche", "fiesta", "evento", "salir", "date", "night", "evening",
                "party", "special")) {
            return "especial";
        }
        if (containsAny(normalized, "versatil", "todoterreno", "cualquiera", "todo")) {
            return "versatil";
        }
        return "";
    }

    private String canonicalBudget(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (containsAny(normalized, "sin limite", "da igual", "lo que cueste", "premium", "lujo", "nicho",
                "exclusive", "luxury")) {
            return "premium";
        }
        if (containsAny(normalized, "calidad precio", "economico medio", "medio economico", "good value")) {
            return "economico-medio";
        }
        Double amount = firstNumber(normalized);
        if (amount != null) {
            if (amount <= 60) {
                return "economico";
            }
            if (amount <= 150) {
                return "medio";
            }
            return "premium";
        }
        if (containsAny(normalized, "barato", "economico", "asequible", "cheap", "affordable")) {
            return "economico";
        }
        if (containsAny(normalized, "medio", "gama media", "mid range", "normal")) {
            return "medio";
        }
        if (containsAny(normalized, "caro", "premium", "lujo", "luxury")) {
            return "premium";
        }
        return "";
    }

    private Double firstNumber(String normalized) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)")
                .matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1).replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String canonicalNotes(String notes) {
        String normalized = normalize(notes);
        List<String> values = new ArrayList<>();
        addCanonicalNote(values, normalized, "dulce", "dulce", "sweet", "azucar", "golos");
        addCanonicalNote(values, normalized, "gourmand", "gourmand", "postre", "dessert", "reposteria");
        addCanonicalNote(values, normalized, "cremoso", "cremoso", "cremosa", "lactonico", "milk", "leche");
        addCanonicalNote(values, normalized, "arroz", "arroz", "rice");
        addCanonicalNote(values, normalized, "vainilla", "vainilla", "vanilla");
        addCanonicalNote(values, normalized, "caramelo", "caramelo", "caramel", "toffee", "praline");
        addCanonicalNote(values, normalized, "coco", "coco", "coconut");
        addCanonicalNote(values, normalized, "frutal", "frutal", "fruta", "fruity", "pear", "peach", "apple");
        addCanonicalNote(values, normalized, "fresa", "fresa", "strawberry", "berries");
        addCanonicalNote(values, normalized, "chocolate", "chocolate", "cacao", "cocoa");
        addCanonicalNote(values, normalized, "miel", "miel", "honey");
        addCanonicalNote(values, normalized, "limpio", "limpio", "jabonoso", "ducha", "ropa limpia", "clean",
                "soapy");
        addCanonicalNote(values, normalized, "fresco", "fresco", "fresh", "citrico", "citrica", "citricos",
                "bergamota", "limon", "naranja", "aquatic", "marine");
        addCanonicalNote(values, normalized, "citrico", "citrico", "citrica", "citricos", "bergamota", "limon",
                "naranja", "pomelo", "grapefruit");
        addCanonicalNote(values, normalized, "marino", "marino", "olor a mar", "marine", "ocean", "sea");
        addCanonicalNote(values, normalized, "acuatico", "acuatico", "aquatic", "watery", "ozonico");
        addCanonicalNote(values, normalized, "salino", "salino", "salty", "salt");
        addCanonicalNote(values, normalized, "animalico", "animalico", "animalic", "civet", "castoreum",
                "ambergris");
        addCanonicalNote(values, normalized, "amaderado", "amaderado", "madera", "woody", "cedar", "cedro",
                "sandalwood", "sandalo", "vetiver", "oud");
        addCanonicalNote(values, normalized, "cuero", "cuero", "leather", "suede");
        addCanonicalNote(values, normalized, "ahumado", "ahumado", "humo", "smoky", "smoke", "incense");
        addCanonicalNote(values, normalized, "industrial", "industrial", "gasolina", "petrol", "metallic");
        addCanonicalNote(values, normalized, "mineral", "mineral", "metalico", "metallic", "asfalto");
        addCanonicalNote(values, normalized, "floral", "floral", "flores", "rose", "rosa", "jasmine", "jazmin",
                "iris");
        addCanonicalNote(values, normalized, "especiado", "especiado", "spicy", "pimienta", "pepper",
                "cardamomo", "canela", "saffron");
        addCanonicalNote(values, normalized, "canela", "canela", "cinnamon");
        addCanonicalNote(values, normalized, "ambar", "ambar", "amber", "ambroxan");
        addCanonicalNote(values, normalized, "almizcle", "almizcle", "musk");
        addCanonicalNote(values, normalized, "oud", "oud", "agarwood");
        addCanonicalNote(values, normalized, "tabaco", "tabaco", "tobacco");
        addCanonicalNote(values, normalized, "incienso", "incienso", "incense");
        addCanonicalNote(values, normalized, "cafe", "cafe", "coffee");
        addCanonicalNote(values, normalized, "iris", "iris", "orris");
        addCanonicalNote(values, normalized, "lavanda", "lavanda", "lavender");
        addCanonicalNote(values, normalized, "sexy", "sexy", "seductor", "seductive");
        addCanonicalNote(values, normalized, "sensual", "sensual", "skin");
        addCanonicalNote(values, normalized, "elegante", "elegante", "elegant", "sophisticated");
        addCanonicalNote(values, normalized, "profesional", "profesional", "professional", "business");
        addCanonicalNote(values, normalized, "lujoso", "lujoso", "lujo", "premium", "luxury", "exclusive");
        addCanonicalNote(values, normalized, "juvenil", "juvenil", "joven", "young", "modern");
        addCanonicalNote(values, normalized, "oscuro", "oscuro", "dark", "nocturno");
        addCanonicalNote(values, normalized, "minimalista", "minimalista", "minimal", "transparent");
        addCanonicalNote(values, normalized, "misterioso", "misterioso", "mysterious");
        addCanonicalNote(values, normalized, "calido", "calido", "warm");
        addCanonicalNote(values, normalized, "casual", "casual", "informal", "daily");
        addCanonicalNote(values, normalized, "empalagoso", "empalagoso", "empalagosa", "pesado", "pesada",
                "cloying", "too sweet");
        return String.join(", ", values);
    }

    private void addCanonicalNote(List<String> values, String normalizedText, String note, String... aliases) {
        if (containsAny(normalizedText, aliases) && !values.contains(note)) {
            values.add(note);
        }
    }

    private String mergeSearchQueries(String aiQuery, String localQuery) {
        if (!StringUtils.hasText(aiQuery)) {
            return valueOrDash(localQuery).replace("-", " ").replaceAll("\\s+", " ").trim();
        }
        if (!StringUtils.hasText(localQuery)) {
            return aiQuery.replace("-", " ").replaceAll("\\s+", " ").trim();
        }
        return (localQuery + " " + aiQuery)
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private ChatResponseDTO handleDirectConversation(User user, PerfumeProfile profile, String message) {
        String normalized = normalize(message);
        if (aiDecisionService.isGreeting(normalized)) {
            String answer = "Hola, soy PerfumIA. Cuéntame un poco qué buscas: algo fresco para diario, algo más intenso, o todavía no lo tienes claro?";
        recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        if (!recommendationPersistenceService.hasPendingRecommendation(user) && aiDecisionService.wantsAnotherPerfume(normalized)) {
            if (hasEnoughUsefulProfile(profile)) {
                return recommendFromProfile(user, profile, message, true);
            }
            String answer = buildAnotherPerfumeAnswer(user, profile);
            recommendationPersistenceService.saveMessage(user, "ASSISTANT", answer);
            return ChatResponseDTO.builder()
                    .answer(answer)
                    .savedRecommendations(listRecommendations(user))
                    .build();
        }

        return null;
    }

    private String buildAnotherPerfumeAnswer(User user, PerfumeProfile profile) {
        return buildConversationalAnswer(user, """
                El usuario quiere otro perfume, pero no hay una propuesta pendiente que rechazar.
                Usa sus gustos guardados y perfumes aceptados como memoria.
                No busques ni recomiendes todavia un nombre concreto.
                Dile que puedes seguir por esa linea y preguntale si quiere mantener esos gustos o cambiar algo.
                Perfil actual: %s
                Perfumes aceptados: %s
                """.formatted(valueOrDash(profile.getLastSummary()), acceptedPerfumesText(user)),
                "Tengo tus gustos anteriores guardados. Puedo buscar otro por esa misma linea o cambiar algo, como hacerlo mas fresco, mas dulce, mas intenso o de otra temporada. Quieres seguir por tus gustos guardados?");
    }

    private String acceptedPerfumesText(User user) {
        String accepted = recommendationPersistenceService.findAcceptedRecommendations(user).stream()
                .map(item -> item.getPerfumeName() + " de " + item.getBrand() + " (" + valueOrDash(item.getNotes()) + ")")
                .collect(Collectors.joining("; "));
        return StringUtils.hasText(accepted) ? accepted : "ninguno";
    }

    private String buildConversationalAnswer(User user, String instruction, String fallback) {
        if (!geminiService.isConfigured()) {
            return fallback;
        }

        List<ChatMessage> history = recommendationPersistenceService.findRecentMessages(user);
        Collections.reverse(history);
        String historyText = history.stream()
                .map(item -> item.getRoleName() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Eres PerfumIA, el asistente conversacional de una app de perfumes.
                Responde en espanol cercano, breve y natural.
                Sigue esta instruccion:
                %s

                Historial reciente:
                %s
                """.formatted(instruction, historyText);

        String answer = geminiService.generateAnswer(prompt);
        return StringUtils.hasText(answer) ? answer : fallback;
    }

    private List<PerfumeItem> filterUnsuitablePerfumes(User user, List<PerfumeItem> catalog, PerfumeProfile profile) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }

        List<PerfumeRecommendation> rejected = recommendationPersistenceService.findRejectedRecommendations(user);

        List<PerfumeItem> filtered = catalog.stream()
                .filter(item -> rejected.stream().noneMatch(recommendation -> isSamePerfume(item, recommendation)))
                .filter(item -> !perfumeScoringService.containsDislikedNote(item, profile.getDislikedNotes()))
                .toList();

        if (!filtered.isEmpty()) {
            List<PerfumeItem> fresh = excludeAlreadyRecommended(user, filtered);
            return fresh.isEmpty() ? filtered : fresh;
        }

        List<PerfumeItem> withoutRejected = catalog.stream()
                .filter(item -> rejected.stream().noneMatch(recommendation -> isSamePerfume(item, recommendation)))
                .toList();
        List<PerfumeItem> fresh = excludeAlreadyRecommended(user, withoutRejected);
        return fresh.isEmpty() ? withoutRejected : fresh;
    }

    private List<PerfumeItem> excludeAlreadyRecommended(User user, List<PerfumeItem> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }

        List<PerfumeRecommendation> existingRecommendations = recommendationPersistenceService.findRecommendations(user);
        if (existingRecommendations.isEmpty()) {
            return catalog;
        }

        return catalog.stream()
                .filter(item -> existingRecommendations.stream()
                        .noneMatch(recommendation -> isSamePerfume(item, recommendation)))
                .toList();
    }

    private boolean isSamePerfume(PerfumeItem item, PerfumeRecommendation recommendation) {
        return normalize(item.getName()).equals(normalize(recommendation.getPerfumeName()))
                && normalize(item.getBrand()).equals(normalize(recommendation.getBrand()));
    }

    private boolean isSamePerfume(PerfumeItem item, PerfumeItem reference) {
        return normalize(item.getName()).equals(normalize(reference.getName()))
                && normalize(item.getBrand()).equals(normalize(reference.getBrand()));
    }

    private List<PerfumeItem> chooseTopPerfumesWithHistory(User user, List<PerfumeItem> catalog, PerfumeProfile profile,
            String message, int limit) {
        List<PerfumeRecommendation> acceptedRecommendations = recommendationPersistenceService
                .findAcceptedRecommendations(user);
        List<PerfumeRecommendation> rejectedRecommendations = recommendationPersistenceService
                .findRejectedRecommendations(user);

        return perfumeScoringService.chooseTopPerfumes(
                catalog,
                profile,
                message,
                acceptedRecommendations,
                rejectedRecommendations,
                limit);
    }

    private List<PerfumeItem> expandTopPerfumesIfNeeded(User user, PerfumeProfile profile, String message,
            List<PerfumeItem> currentTop, List<PerfumeItem> currentCatalog, int limit,
            boolean excludeExistingRecommendations, String primaryQuery) {
        List<PerfumeItem> candidates = new ArrayList<>();
        addUniquePerfumes(candidates, currentCatalog);

        List<PerfumeItem> topPerfumes = uniquePerfumes(currentTop);
        boolean expandingForDiversityOnly = topPerfumes.size() >= limit
                && !hasEnoughRecommendationDiversity(topPerfumes, limit);
        if (topPerfumes.size() >= limit && !expandingForDiversityOnly) {
            return topPerfumes;
        }

        int diversityExpansionQueries = 0;
        for (String query : recommendationSearchQueries(profile, message, primaryQuery)) {
            if (!StringUtils.hasText(query) || normalize(query).equals(normalize(primaryQuery))) {
                continue;
            }
            if (expandingForDiversityOnly && diversityExpansionQueries >= 3) {
                break;
            }

            List<PerfumeItem> catalog = filterUnsuitablePerfumes(user, perfumeCatalogService.searchPerfumes(query),
                    profile);
            if (excludeExistingRecommendations) {
                catalog = excludeAlreadyRecommended(user, catalog);
            }
            addUniquePerfumes(candidates, catalog);

            topPerfumes = uniquePerfumes(chooseTopPerfumesWithHistory(user, candidates, profile, message, limit));
            if (topPerfumes.size() >= limit && hasEnoughRecommendationDiversity(topPerfumes, limit)) {
                return topPerfumes;
            }
            expandingForDiversityOnly = topPerfumes.size() >= limit;
            if (expandingForDiversityOnly) {
                diversityExpansionQueries++;
            }
        }

        if (!candidates.isEmpty()) {
            return uniquePerfumes(chooseTopPerfumesWithHistory(user, candidates, profile, message, limit));
        }
        return topPerfumes;
    }

    private List<String> recommendationSearchQueries(PerfumeProfile profile, String message, String primaryQuery) {
        List<String> queries = new ArrayList<>();
        addQuery(queries, primaryQuery);
        addQuery(queries, aiDecisionService.buildSearchQuery(profile, ""));

        String gender = genderSearchTerms(profile);
        String season = seasonSearchTerms(profile);
        String occasion = occasionSearchTerms(profile);
        String intensity = intensitySearchTerms(profile);
        String notes = noteSearchTerms(profile);
        String budget = budgetSearchTerms(profile);

        addQuery(queries, joinTerms(gender, season, notes, intensity, occasion, budget));
        addQuery(queries, joinTerms(gender, notes, season, "best rated long lasting", budget));

        String normalizedNotes = normalize(profile != null ? profile.getPreferredNotes() : "");
        if (containsAny(normalizedNotes, "dulce", "gourmand", "vainilla", "arroz", "cremoso")) {
            addQuery(queries, joinTerms(gender, season, "gourmand sweet vanilla tonka amber creamy cinnamon",
                    intensity, occasion, budget));
            addQuery(queries, joinTerms(gender, "unisex", season, "rice milk gourmand vanilla cinnamon dessert",
                    intensity, budget));
        }
        if (containsAny(normalizedNotes, "frutal", "fresa")) {
            addQuery(queries, joinTerms(gender, season, "strawberry berries red fruits fruity sweet",
                    intensity, occasion, budget));
            addQuery(queries, joinTerms(gender, "unisex", season, "fruity strawberry affordable daily casual",
                    budget));
        }
        String normalizedSeason = normalize(profile != null ? profile.getSeason() : "");
        String normalizedOccasion = normalize(profile != null ? profile.getOccasion() : "");
        String normalizedBudget = normalize(profile != null ? profile.getBudget() : "");
        String freshContext = normalizedNotes + " " + normalizedSeason + " " + normalizedOccasion + " "
                + normalize(message);
        if (containsAny(freshContext, "fresco", "citrico", "verano", "trabajo", "oficina", "profesional")) {
            addQuery(queries, joinTerms(gender, season, "fresh citrus aquatic aromatic clean office professional",
                    budget));
            addQuery(queries, joinTerms(gender, "summer fresh citrus bergamot clean musk office daytime",
                    "designer affordable good value"));
            if ("medio".equals(normalizedBudget)) {
                addQuery(queries, joinTerms(gender,
                        "summer fresh citrus aquatic office eau de toilette designer affordable good value"));
                addQuery(queries, joinTerms("unisex", "summer fresh clean citrus office daytime good value"));
            }
        }
        if (containsAny(normalizedNotes, "amaderado", "madera", "cedro", "sandalo", "vetiver")) {
            addQuery(queries, joinTerms(gender, season, "woody cedar sandalwood vetiver amber", intensity,
                    occasion, budget));
            String versatileDailyContext = normalizedSeason + " " + normalizedOccasion + " " + normalize(message);
            if (containsAny(versatileDailyContext, "versatil", "diario", "todo el ano", "todas las estaciones",
                    "daily")) {
                addQuery(queries, joinTerms(gender,
                        "woody aromatic vetiver cedar sandalwood clean musk daily versatile all year", budget));
                addQuery(queries, joinTerms(gender,
                        "men woody vetiver cedar everyday signature office eau de toilette mid range"));
                addQuery(queries, joinTerms("unisex",
                        "woody musk cedar sandalwood clean versatile daily good value"));
            }
        }
        if ("premium".equals(normalize(profile != null ? profile.getBudget() : ""))) {
            addQuery(queries, joinTerms(gender, season, notes, "luxury niche exclusive royal extrait parfum",
                    intensity, occasion));
        }

        addQuery(queries, joinTerms(gender, season, notes, valueOrDash(message)));
        return queries;
    }

    private void addUniquePerfumes(List<PerfumeItem> target, List<PerfumeItem> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        Map<String, PerfumeItem> unique = new LinkedHashMap<>();
        for (PerfumeItem item : target) {
            unique.putIfAbsent(perfumeKey(item), item);
        }
        for (PerfumeItem item : source) {
            if (item != null && StringUtils.hasText(item.getName())) {
                unique.putIfAbsent(perfumeKey(item), item);
            }
        }
        target.clear();
        target.addAll(unique.values());
    }

    private List<PerfumeItem> uniquePerfumes(List<PerfumeItem> perfumes) {
        if (perfumes == null || perfumes.isEmpty()) {
            return List.of();
        }
        Map<String, PerfumeItem> unique = new LinkedHashMap<>();
        for (PerfumeItem item : perfumes) {
            if (item != null && StringUtils.hasText(item.getName())) {
                unique.putIfAbsent(perfumeKey(item), item);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean hasEnoughRecommendationDiversity(List<PerfumeItem> perfumes, int limit) {
        if (perfumes == null || perfumes.size() < limit || limit < 3) {
            return perfumes != null && perfumes.size() >= limit;
        }
        return distinctKnownBrandCount(perfumes) != 1;
    }

    private int distinctKnownBrandCount(List<PerfumeItem> perfumes) {
        return perfumes.stream()
                .filter(item -> item != null)
                .map(item -> normalize(item.getBrand()))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new))
                .size();
    }

    private String perfumeKey(PerfumeItem item) {
        if (item == null) {
            return "";
        }
        return normalize(item.getBrand()) + "::" + normalize(item.getName());
    }

    private void addQuery(List<String> queries, String query) {
        String clean = valueOrDash(query).replace("-", " ").replaceAll("\\s+", " ").trim();
        if (StringUtils.hasText(clean) && !"-".equals(clean)
                && queries.stream().noneMatch(existing -> normalize(existing).equals(normalize(clean)))) {
            queries.add(clean);
        }
    }

    private String joinTerms(String... terms) {
        List<String> values = new ArrayList<>();
        for (String term : terms) {
            if (StringUtils.hasText(term) && !"-".equals(term.trim())) {
                values.add(term.trim());
            }
        }
        return String.join(" ", values).replaceAll("\\s+", " ").trim();
    }

    private String genderSearchTerms(PerfumeProfile profile) {
        String gender = normalize(profile != null ? profile.getGenderTarget() : "");
        if ("hombre".equals(gender)) {
            return "men male masculine homme";
        }
        if ("mujer".equals(gender)) {
            return "women female feminine femme";
        }
        if ("unisex".equals(gender)) {
            return "unisex";
        }
        return "";
    }

    private String seasonSearchTerms(PerfumeProfile profile) {
        String season = normalize(profile != null ? profile.getSeason() : "");
        if ("verano".equals(season)) {
            return "summer";
        }
        if ("invierno".equals(season)) {
            return "winter cold";
        }
        if ("primavera".equals(season)) {
            return "spring";
        }
        if ("otono".equals(season)) {
            return "fall autumn";
        }
        if ("versatil".equals(season)) {
            return "versatile all year";
        }
        return "";
    }

    private String occasionSearchTerms(PerfumeProfile profile) {
        String occasion = normalize(profile != null ? profile.getOccasion() : "");
        if ("especial".equals(occasion)) {
            return "night evening date special occasion";
        }
        if ("diario".equals(occasion)) {
            return "daily clean casual";
        }
        if ("trabajo".equals(occasion)) {
            return "office professional business formal";
        }
        if ("versatil".equals(occasion)) {
            return "versatile";
        }
        return "";
    }

    private String intensitySearchTerms(PerfumeProfile profile) {
        String intensity = normalize(profile != null ? profile.getIntensity() : "");
        if ("intenso".equals(intensity) || "potente".equals(intensity)) {
            return "long lasting strong intense";
        }
        if ("suave".equals(intensity)) {
            return "soft light subtle";
        }
        return "";
    }

    private String budgetSearchTerms(PerfumeProfile profile) {
        String budget = normalize(profile != null ? profile.getBudget() : "");
        if ("premium".equals(budget)) {
            return "premium luxury niche";
        }
        if ("medio".equals(budget) || "economico-medio".equals(budget)) {
            return "mid range";
        }
        if ("economico".equals(budget)) {
            return "cheap affordable good value budget";
        }
        return "";
    }

    private String noteSearchTerms(PerfumeProfile profile) {
        String notes = normalize(profile != null ? profile.getPreferredNotes() : "");
        List<String> terms = new ArrayList<>();
        if (containsAny(notes, "dulce")) {
            terms.add("sweet vanilla caramel tonka amber gourmand honey coconut chocolate");
        }
        if (containsAny(notes, "frutal")) {
            terms.add("fruity fruit berries red fruits strawberry apple peach pear");
        }
        if (containsAny(notes, "fresa")) {
            terms.add("strawberry berries red fruits fruity sweet");
        }
        if (containsAny(notes, "gourmand")) {
            terms.add("gourmand dessert vanilla caramel tonka");
        }
        if (containsAny(notes, "cremoso")) {
            terms.add("creamy lactonic milk vanilla musk");
        }
        if (containsAny(notes, "arroz")) {
            terms.add("rice milk creamy vanilla");
        }
        if (containsAny(notes, "canela")) {
            terms.add("cinnamon spicy warm");
        }
        if (containsAny(notes, "citrico")) {
            terms.add("citrus bergamot lemon orange grapefruit fresh");
        }
        if (containsAny(notes, "fresco")) {
            terms.add("fresh citrus clean aquatic");
        }
        if (containsAny(notes, "marino", "acuatico")) {
            terms.add("marine aquatic ocean ozonic");
        }
        if (containsAny(notes, "salino")) {
            terms.add("salty marine aquatic mineral ambergris");
        }
        if (containsAny(notes, "animalico")) {
            terms.add("animalic musk civet castoreum ambergris salty");
        }
        if (containsAny(notes, "amaderado", "madera")) {
            terms.add("woody cedar sandalwood vetiver");
        }
        if (containsAny(notes, "cuero")) {
            terms.add("leather suede birch tar");
        }
        if (containsAny(notes, "ahumado", "humo")) {
            terms.add("smoky smoke incense birch tar vetiver");
        }
        if (containsAny(notes, "industrial", "gasolina")) {
            terms.add("industrial petrol gasoline leather smoky metallic mineral");
        }
        if (containsAny(notes, "mineral", "metalico")) {
            terms.add("mineral metallic vetiver smoke");
        }
        if (containsAny(notes, "floral")) {
            terms.add("floral jasmine rose iris");
        }
        if (containsAny(notes, "especiado")) {
            terms.add("spicy cinnamon pepper cardamom saffron");
        }
        if (containsAny(notes, "sexy", "sensual")) {
            terms.add("sensual seductive amber musk vanilla");
        }
        if (containsAny(notes, "lujoso")) {
            terms.add("luxury premium exclusive oud saffron amber");
        }
        if (containsAny(notes, "elegante", "profesional")) {
            terms.add("elegant professional clean woods musk iris");
        }
        if (containsAny(notes, "ambar")) {
            terms.add("amber warm resinous vanilla labdanum");
        }
        if (containsAny(notes, "almizcle")) {
            terms.add("musk clean skin white musk");
        }
        if (containsAny(notes, "oud")) {
            terms.add("oud agarwood woody smoky dark");
        }
        if (containsAny(notes, "tabaco")) {
            terms.add("tobacco honey vanilla warm spicy");
        }
        if (containsAny(notes, "incienso")) {
            terms.add("incense smoky resinous dark");
        }
        if (containsAny(notes, "cafe")) {
            terms.add("coffee gourmand vanilla tonka");
        }
        if (containsAny(notes, "iris")) {
            terms.add("iris powdery elegant floral");
        }
        if (containsAny(notes, "lavanda")) {
            terms.add("lavender aromatic clean fresh");
        }
        return String.join(" ", terms);
    }

    private PerfumeItem choosePerfumeWithGemini(List<PerfumeItem> catalog, PerfumeProfile profile, String message) {
        if (!geminiService.isConfigured() || catalog == null || catalog.isEmpty()) {
            return null;
        }

        String catalogText = "";
        for (int i = 0; i < catalog.size(); i++) {
            PerfumeItem item = catalog.get(i);
            catalogText += (i + 1) + ". " + item.getName() + " / " + item.getBrand()
                    + " / notas: " + item.getNotes()
                    + " / temporada: " + item.getSeason()
                    + " / descripcion: " + item.getDescription() + "\n";
        }

        String prompt = """
                Elige el mejor perfume del catalogo para este perfil.
                Responde SOLO con JSON valido: {"index": 1, "reason": "motivo breve"}.
                No inventes perfumes. El index debe existir en el catalogo.

                Perfil: %s
                Ultimo mensaje del usuario: %s

                Catalogo:
                %s
                """.formatted(valueOrDash(profile.getLastSummary()), message, catalogText);

        String rawAnswer = geminiService.generateJsonAnswer(prompt);
        if (!StringUtils.hasText(rawAnswer)) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(extractJson(rawAnswer));
            int index = root.path("index").asInt(0);
            if (index >= 1 && index <= catalog.size()) {
                return catalog.get(index - 1);
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private String buildQuestionAnswer(User user, PerfumeProfile profile, String message, List<String> missingFields) {
        String clarification = promptBuilderService.answerClarificationQuestion(message, missingFields);
        if (StringUtils.hasText(clarification)) {
            return clarification;
        }
        return promptBuilderService.nextQuestion(selectNextMissingField(missingFields, message), message);
    }

    private String selectNextMissingField(List<String> missingFields, String message) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "";
        }

        String normalized = normalize(message);
        if ((isOdorControlMessage(normalized)
                || containsAny(normalized, "diario", "limpio", "ducha", "jabonoso", "fresco"))
                && missingFields.contains("familia olfativa: fresco, dulce, amaderado, floral o especiado")) {
            return "familia olfativa: fresco, dulce, amaderado, floral o especiado";
        }

        if (containsAny(normalized, "no lo tengo claro", "ni idea", "no se", "nose", "nunca he usado")
                && missingFields.contains("familia olfativa: fresco, dulce, amaderado, floral o especiado")) {
            return "familia olfativa: fresco, dulce, amaderado, floral o especiado";
        }

        return missingFields.get(0);
    }

    private String buildGeminiAnswer(User user, PerfumeProfile profile, String message, List<PerfumeItem> catalog,
            List<String> missingFields, PerfumeItem proposedPerfume) {
        String catalogText = catalog.stream()
                .map(item -> "- " + item.getName() + " / " + item.getBrand() + " / " + item.getNotes() + " / " + item.getSeason())
                .collect(Collectors.joining("\n"));

        String accepted = recommendationPersistenceService.findAcceptedRecommendations(user).stream()
                .map(item -> item.getPerfumeName() + " de " + item.getBrand())
                .collect(Collectors.joining(", "));

        List<ChatMessage> history = recommendationPersistenceService.findRecentMessages(user);
        Collections.reverse(history);
        String historyText = history.stream()
                .map(item -> item.getRoleName() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Eres PerfumIA, un asesor olfativo cercano, elegante y conversacional.
                Responde en espanol natural, con tono humano y util.
                Objetivo: entender el gusto del usuario con pocas preguntas, sin sonar a formulario, hasta poder recomendar perfumes.
                Si el usuario dice que nunca ha usado perfumes, explica familias olfativas con ejemplos sencillos.
                Si faltan datos, pregunta solo por el siguiente dato mas importante, pero primero resume en media frase lo que ya has entendido.
                Si ya hay suficientes datos, recomienda una opcion concreta y pregunta si quiere guardarla.
                Usa solo el perfume propuesto por el sistema o el catalogo disponible. No inventes nombres.
                No enumeres preguntas ni uses frases secas tipo test. Maximo 2 frases si estas preguntando.

                Perfil actual: %s
                Datos que faltan: %s
                Perfumes aceptados anteriormente: %s
                Perfume propuesto por el sistema: %s

                Catalogo disponible:
                %s

                Historial reciente:
                %s

                Usuario: %s
                """.formatted(
                valueOrDash(profile.getLastSummary()),
                missingFields.isEmpty() ? "ninguno" : String.join(", ", missingFields),
                StringUtils.hasText(accepted) ? accepted : "ninguno",
                proposedPerfume == null ? "ninguno" : proposedPerfume.getName() + " de " + proposedPerfume.getBrand(),
                catalogText,
                historyText,
                message);

        return geminiService.generateAnswer(prompt);
    }

    private String buildFallbackAnswer(PerfumeProfile profile, List<String> missingFields, PerfumeItem proposedPerfume) {
        if (!missingFields.isEmpty()) {
            return promptBuilderService.nextQuestion(missingFields.get(0), "");
        }

        if (proposedPerfume == null) {
            return "Tengo una idea bastante clara, pero necesito que me digas una nota o sensacion que quieras evitar para afinar mejor.";
        }

        String seasonPhrase = buildSeasonPhrase(profile, proposedPerfume);
        return "Por lo que me has contado, probaria con " + proposedPerfume.getName() + " de "
                + proposedPerfume.getBrand() + ". Encaja por sus notas de " + proposedPerfume.getNotes()
                + seasonPhrase
                + ". Si te convence, dime 'acepto' y lo guardo en tu perfil.";
    }

    private String buildTopRecommendationsAnswer(PerfumeProfile profile, List<PerfumeItem> topPerfumes) {
        if (topPerfumes == null || topPerfumes.isEmpty()) {
            return buildFallbackAnswer(profile, List.of(), null);
        }

        String recap = buildRecommendationRecap(profile, topPerfumes);
        if (topPerfumes.size() == 1) {
            return recap + " He encontrado una opcion que encaja contigo. Te la dejo abajo para que puedas revisarla.";
        }

        return recap + " He encontrado " + topPerfumes.size()
                + " opciones que encajan contigo. Te las dejo abajo ordenadas por compatibilidad para que elijas la que mas te guste.";
    }

    private String buildRecommendationRecap(PerfumeProfile profile, List<PerfumeItem> topPerfumes) {
        List<String> traits = List.of(
                profileTrait("hombre".equals(normalize(profile.getGenderTarget())), "masculina"),
                profileTrait("mujer".equals(normalize(profile.getGenderTarget())), "femenina"),
                profileTrait("unisex".equals(normalize(profile.getGenderTarget())), "unisex"),
                profileTrait(StringUtils.hasText(profile.getOccasion()), occasionRecap(profile.getOccasion())),
                profileTrait(StringUtils.hasText(profile.getPreferredNotes()), profile.getPreferredNotes()),
                profileTrait(StringUtils.hasText(profile.getIntensity()), intensityRecap(profile.getIntensity())),
                profileTrait(StringUtils.hasText(profile.getSeason()), "para " + profile.getSeason()),
                profileTrait(StringUtils.hasText(profile.getBudget()), "presupuesto " + profile.getBudget()))
                .stream()
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();

        String sourcePrefix = recommendationSourcePrefix(topPerfumes);
        if (traits.isEmpty()) {
            return sourcePrefix + " con lo que me has contado.";
        }

        return sourcePrefix + " buscando una linea " + String.join(", ", traits) + ".";
    }

    private String recommendationSourcePrefix(List<PerfumeItem> topPerfumes) {
        if (topPerfumes == null || topPerfumes.isEmpty()) {
            return "He filtrado el catalogo";
        }

        boolean allFragella = topPerfumes.stream()
                .allMatch(item -> "fragella".equalsIgnoreCase(valueOrDash(item.getSource())));
        boolean allLocal = topPerfumes.stream()
                .allMatch(item -> "local".equalsIgnoreCase(valueOrDash(item.getSource())));

        if (allFragella) {
            return "He filtrado Fragella";
        }
        if (allLocal) {
            return "Como Fragella no esta disponible ahora, he usado el catalogo local";
        }
        return "He combinado Fragella y el catalogo local";
    }

    private String profileTrait(boolean include, String value) {
        return include ? value : "";
    }

    private String occasionRecap(String occasion) {
        String normalized = normalize(occasion);
        if ("trabajo".equals(normalized)) {
            return "profesional";
        }
        if ("especial".equals(normalized)) {
            return "para ocasiones especiales";
        }
        return occasion;
    }

    private String intensityRecap(String intensity) {
        String normalized = normalize(intensity);
        if ("intenso".equals(normalized) || "potente".equals(normalized)) {
            return "con presencia";
        }
        if ("suave".equals(normalized)) {
            return "suave";
        }
        return intensity;
    }

    private PerfumeRecommendationDTO firstRecommendation(List<PerfumeRecommendationDTO> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }
        return recommendations.get(0);
    }

    private void enrichReasons(PerfumeProfile profile, List<PerfumeItem> perfumes,
            List<PerfumeRecommendationDTO> recommendations) {
        if (perfumes == null || recommendations == null) {
            return;
        }

        int size = Math.min(perfumes.size(), recommendations.size());
        for (int index = 0; index < size; index++) {
            PerfumeItem perfume = perfumes.get(index);
            PerfumeRecommendationDTO recommendation = recommendations.get(index);
            recommendation.setReason(perfumeScoringService.buildRecommendationReason(profile, perfume));
            recommendation.setPriceEstimate(PerfumePriceEstimator.estimate(profile, perfume));
            recommendation.setImageUrl(PerfumeImageResolver.resolve(
                    recommendation.getBrand(),
                    recommendation.getPerfumeName(),
                    recommendation.getImageUrl()));
            recommendation.setLongevity(perfume.getLongevity());
            recommendation.setSillage(perfume.getSillage());
            recommendation.setOilType(perfume.getOilType());
            recommendation.setFragellaRating(perfume.getFragellaRating());
            recommendation.setGender(perfume.getGender());
            recommendation.setPriceValue(perfume.getPriceValue());
        }
    }

    private String buildSeasonPhrase(PerfumeProfile profile, PerfumeItem proposedPerfume) {
        String perfumeSeason = valueOrDash(proposedPerfume.getSeason());
        String requestedSeason = normalize(profile.getSeason());
        if (StringUtils.hasText(requestedSeason)
                && perfumeScoringService.seasonScore(normalize(proposedPerfume.getSeason()), requestedSeason) < 0) {
            return ", aunque la API lo marca mas como " + perfumeSeason
                    + "; te lo propongo por el encaje de notas, pero puedo buscar una opcion mas claramente de "
                    + profile.getSeason();
        }
        return " y funciona bien en " + perfumeSeason;
    }

    private boolean shouldHandlePendingRecommendation(User user, String message) {
        if (!recommendationPersistenceService.hasPendingRecommendation(user)) {
            return false;
        }

        if (aiDecisionService.isShortNegative(message) && latestAssistantAskedForPreferences(user)) {
            return false;
        }

        return aiDecisionService.isAcceptance(message)
                || aiDecisionService.isRejection(message);
    }

    private boolean latestAssistantAskedForPreferences(User user) {
        return recommendationPersistenceService.findRecentMessages(user).stream()
                .filter(item -> "ASSISTANT".equals(item.getRoleName()))
                .findFirst()
                .map(item -> {
                    String text = normalize(item.getContent());
                    return containsAny(text, "que prefieres evitar", "prefieres evitar", "nota en particular",
                            "notas que quieras evitar", "que no te encaja", "que buscas");
                })
                .orElse(false);
    }

    private boolean hasEnoughUsefulProfile(PerfumeProfile profile) {
        return StringUtils.hasText(profile.getGenderTarget())
                && StringUtils.hasText(profile.getOccasion())
                && hasCoreOlfactiveFamily(profile)
                && StringUtils.hasText(profile.getBudget());
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalized = normalized
                .replace("ã¡", "a")
                .replace("ã©", "e")
                .replace("ã­", "i")
                .replace("ã³", "o")
                .replace("ãº", "u")
                .replace("ã¼", "u")
                .replace("ã±", "n")
                .replace("â‚¬", "\u20ac");
        return normalized
                .replace("Ã¡", "a")
                .replace("Ã©", "e")
                .replace("Ã­", "i")
                .replace("Ã³", "o")
                .replace("Ãº", "u")
                .replace("Ã¼", "u");
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String extractJson(String rawAnswer) {
        String trimmed = rawAnswer.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private record AiDecision(
            String intent,
            boolean perfumeRelated,
            String answer,
            boolean readyToSearch,
            String searchQuery,
            String genderTarget,
            String season,
            String preferredNotes,
            String intensity,
            String occasion,
            String budget,
            String dislikedNotes) {
    }
}

