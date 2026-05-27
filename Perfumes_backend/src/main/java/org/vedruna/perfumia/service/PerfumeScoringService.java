package org.vedruna.perfumia.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.service.dto.PerfumeItem;

@Service
public class PerfumeScoringService {

    public PerfumeItem choosePerfume(List<PerfumeItem> catalog, PerfumeProfile profile, String message) {
        if (catalog == null || catalog.isEmpty()) {
            return null;
        }

        return catalog.stream()
                .sorted((left, right) -> Integer.compare(score(right, profile, message), score(left, profile, message)))
                .findFirst()
                .orElse(catalog.get(0));
    }

    public List<PerfumeItem> chooseTopPerfumes(List<PerfumeItem> catalog, PerfumeProfile profile, String message,
            int limit) {
        if (catalog == null || catalog.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<PerfumeItem> ranked = compatibleCatalog(catalog, profile, limit).stream()
                .sorted((left, right) -> Integer.compare(score(right, profile, message), score(left, profile, message)))
                .toList();
        return diversifyTopPerfumes(ranked, limit);
    }

    public List<PerfumeItem> chooseTopPerfumes(List<PerfumeItem> catalog, PerfumeProfile profile, String message,
            List<PerfumeRecommendation> acceptedRecommendations, List<PerfumeRecommendation> rejectedRecommendations,
            int limit) {
        if (catalog == null || catalog.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<PerfumeItem> ranked = compatibleCatalog(catalog, profile, limit).stream()
                .sorted((left, right) -> Integer.compare(
                        scoreWithHistory(right, profile, message, acceptedRecommendations, rejectedRecommendations),
                        scoreWithHistory(left, profile, message, acceptedRecommendations, rejectedRecommendations)))
                .toList();
        return diversifyTopPerfumes(ranked, limit);
    }

    public int score(PerfumeItem item, PerfumeProfile profile, String message) {
        String haystack = itemHaystack(item);
        int score = 0;
        for (String token : normalize(valueOrDash(profile.getPreferredNotes()) + " "
                + translateSeason(profile.getSeason()) + " " + translateOccasion(profile.getOccasion()) + " "
                + message).split("[ ,]+")) {
            if (token.length() > 3 && haystack.contains(token)) {
                score += 2;
            }
        }
        score += genderScore(item, profile);
        score += seasonScore(haystack, profile.getSeason());
        score += intensityScore(haystack, profile.getIntensity());
        score += occasionScore(haystack, profile.getOccasion());
        score += preferredNotesScore(haystack, profile.getPreferredNotes());
        score += preferredFamilyMismatchPenalty(haystack, profile.getPreferredNotes());
        score += budgetScore(item, profile);
        if (containsDislikedNote(item, profile.getDislikedNotes())) {
            score -= 100;
        }
        return score;
    }

    public String buildRecommendationReason(PerfumeProfile profile, PerfumeItem perfume) {
        List<String> profileTraits = profileTraits(profile);
        List<String> matchedNotes = matchingProfileNotesForReason(profile, perfume);
        String perfumeSeason = perfume == null ? "" : seasonLabel(perfume.getSeason());

        StringBuilder reason = new StringBuilder();
        if (profileTraits.isEmpty()) {
            reason.append("Encaja contigo porque se ha colocado entre las opciones mas compatibles con tus respuestas.");
        } else {
            reason.append("Encaja contigo porque buscas un perfume ")
                    .append(joinHuman(profileTraits))
                    .append(".");
        }

        List<String> missingCorePreferences = missingCorePreferencesForReason(profile, perfume);

        if (!matchedNotes.isEmpty()) {
            reason.append(" Ademas, sus notas de ")
                    .append(joinHuman(matchedNotes))
                    .append(" conectan con tus preferencias.");
        } else if (StringUtils.hasText(perfumeSeason) && seasonMatchesProfile(profile, perfume)) {
            reason.append(" Ademas, su perfil ")
                    .append(perfumeSeason)
                    .append(" refuerza la temporada que has pedido.");
            if (!missingCorePreferences.isEmpty()) {
                reason.append(" Lo dejo por debajo de opciones con un perfil mas ")
                        .append(joinHuman(missingCorePreferences))
                        .append(".");
            }
        } else if (StringUtils.hasText(perfumeSeason)) {
            reason.append(" Aunque la API lo marca mas como ")
                    .append(perfumeSeason.replace("para ", ""))
                    .append(", lo valoro por el encaje con tus gustos principales.");
            if (!missingCorePreferences.isEmpty()) {
                reason.append(" No es la opcion mas clara si priorizas ")
                        .append(joinHuman(missingCorePreferences))
                        .append(".");
            }
        } else if (perfume != null && StringUtils.hasText(perfume.getDescription())) {
            reason.append(" Ademas, la descripcion del perfume mantiene una linea coherente con lo que has contado.");
        }

        return trimReason(reason.toString());
    }

    private int scoreWithHistory(PerfumeItem item, PerfumeProfile profile, String message,
            List<PerfumeRecommendation> acceptedRecommendations, List<PerfumeRecommendation> rejectedRecommendations) {
        return score(item, profile, message)
                + historyScore(item, profile, acceptedRecommendations, rejectedRecommendations);
    }

    private List<PerfumeItem> seasonCompatibleCatalog(List<PerfumeItem> catalog, PerfumeProfile profile) {
        if (profile == null || !StringUtils.hasText(profile.getSeason())) {
            return catalog;
        }

        return catalog.stream()
                .filter(item -> seasonScore(itemHaystack(item), profile.getSeason()) >= 0)
                .toList();
    }

    private List<PerfumeItem> compatibleCatalog(List<PerfumeItem> catalog, PerfumeProfile profile, int limit) {
        List<PerfumeItem> seasonCompatible = seasonCompatibleCatalog(catalog, profile);
        if (seasonCompatible.isEmpty()) {
            seasonCompatible = catalog;
        }
        List<PerfumeItem> genderCompatible = genderCompatibleCatalog(seasonCompatible, profile);
        List<PerfumeItem> baseCatalog = genderCompatible.isEmpty() ? seasonCompatible : genderCompatible;
        List<PerfumeItem> budgetCompatible = budgetCompatibleCatalog(baseCatalog, profile, limit);
        return budgetCompatible.isEmpty() ? baseCatalog : budgetCompatible;
    }

    private List<PerfumeItem> diversifyTopPerfumes(List<PerfumeItem> ranked, int limit) {
        if (ranked == null || ranked.size() <= limit || limit < 3 || distinctKnownBrandCount(ranked) < 2) {
            return ranked == null ? List.of() : ranked.stream().limit(limit).toList();
        }

        List<PerfumeItem> selected = new ArrayList<>();
        List<PerfumeItem> delayed = new ArrayList<>();
        for (PerfumeItem item : ranked) {
            if (item == null) {
                continue;
            }
            String brand = normalize(item.getBrand());
            if (StringUtils.hasText(brand) && countBrand(selected, brand) >= 2) {
                delayed.add(item);
                continue;
            }
            selected.add(item);
            if (selected.size() >= limit) {
                break;
            }
        }

        for (PerfumeItem item : delayed) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(item);
        }
        return selected.stream().limit(limit).toList();
    }

    private long countBrand(List<PerfumeItem> perfumes, String brand) {
        return perfumes.stream()
                .filter(item -> item != null)
                .filter(item -> normalize(item.getBrand()).equals(brand))
                .count();
    }

    private int distinctKnownBrandCount(List<PerfumeItem> perfumes) {
        Set<String> brands = new LinkedHashSet<>();
        for (PerfumeItem item : perfumes) {
            if (item == null) {
                continue;
            }
            String brand = normalize(item.getBrand());
            if (StringUtils.hasText(brand)) {
                brands.add(brand);
            }
        }
        return brands.size();
    }

    private List<PerfumeItem> genderCompatibleCatalog(List<PerfumeItem> catalog, PerfumeProfile profile) {
        if (profile == null || !StringUtils.hasText(profile.getGenderTarget())) {
            return catalog;
        }

        String target = normalize(profile.getGenderTarget());
        if (!List.of("hombre", "mujer").contains(target)) {
            return catalog;
        }

        return catalog.stream()
                .filter(item -> !isGenderMismatch(item, target))
                .toList();
    }

    private int historyScore(PerfumeItem item, PerfumeProfile profile,
            List<PerfumeRecommendation> acceptedRecommendations, List<PerfumeRecommendation> rejectedRecommendations) {
        int acceptedScore = acceptedHistoryScore(item, profile, acceptedRecommendations);
        int rejectedScore = rejectedHistoryScore(item, rejectedRecommendations);
        return acceptedScore + rejectedScore;
    }

    private int acceptedHistoryScore(PerfumeItem item, PerfumeProfile profile,
            List<PerfumeRecommendation> acceptedRecommendations) {
        if (acceptedRecommendations == null || acceptedRecommendations.isEmpty()) {
            return 0;
        }

        String itemHaystack = itemHaystack(item);
        int notesScore = Math.min(10, matchingHistoryNotes(itemHaystack, acceptedRecommendations).size() * 2);
        int brandScore = acceptedRecommendations.stream()
                .anyMatch(recommendation -> sameBrand(item.getBrand(), recommendation.getBrand())) ? 2 : 0;
        int seasonScore = hasAcceptedSeasonMatch(item, profile, acceptedRecommendations) ? 3 : 0;

        return Math.min(15, notesScore + brandScore + seasonScore);
    }

    private int rejectedHistoryScore(PerfumeItem item, List<PerfumeRecommendation> rejectedRecommendations) {
        if (rejectedRecommendations == null || rejectedRecommendations.isEmpty()) {
            return 0;
        }

        int samePerfumePenalty = rejectedRecommendations.stream()
                .anyMatch(recommendation -> isSamePerfume(item, recommendation)) ? -100 : 0;
        int rejectedNotesPenalty = Math.max(-20,
                matchingHistoryNotes(itemHaystack(item), rejectedRecommendations).size() * -4);
        int rejectedBrandPenalty = rejectedRecommendations.stream()
                .anyMatch(recommendation -> sameBrand(item.getBrand(), recommendation.getBrand())) ? -3 : 0;

        return samePerfumePenalty + rejectedNotesPenalty + rejectedBrandPenalty;
    }

    public int seasonScore(String haystack, String season) {
        String normalizedSeason = normalize(season);
        if (!StringUtils.hasText(normalizedSeason)) {
            return 0;
        }
        if ("invierno".equals(normalizedSeason)) {
            if (containsAny(haystack, "winter", "invierno", "fall", "autumn", "otono", "cold", "night")) {
                return 20;
            }
            if (containsAny(haystack, "spring", "summer", "verano", "primavera")) {
                return -24;
            }
        }
        if ("verano".equals(normalizedSeason)) {
            if (containsAny(haystack, "summer", "verano")) {
                return 25;
            }
            if (containsAny(haystack, "spring", "primavera")) {
                return -10;
            }
            if (containsAny(haystack, "fall", "autumn", "otono")) {
                return -24;
            }
            if (containsAny(haystack, "winter", "invierno", "heavy")) {
                return -30;
            }
            if (containsAny(haystack, "fresh", "citrus", "aquatic", "marine", "clean")) {
                return 8;
            }
        }
        if ("otono".equals(normalizedSeason)) {
            if (containsAny(haystack, "fall", "autumn", "otono", "winter")) {
                return 18;
            }
            if (containsAny(haystack, "summer", "verano")) {
                return -18;
            }
        }
        if ("primavera".equals(normalizedSeason)) {
            if (containsAny(haystack, "spring", "primavera", "floral", "fresh")) {
                return 18;
            }
            if (containsAny(haystack, "winter", "invierno")) {
                return -18;
            }
        }
        if ("versatil".equals(normalizedSeason)) {
            if (containsAny(haystack, "all year", "all-year", "versatile", "signature", "daily", "everyday",
                    "office", "clean", "fresh")) {
                return 16;
            }
            if (containsAny(haystack, "spring", "summer", "fall", "autumn", "winter")) {
                return 4;
            }
        }
        return 0;
    }

    public int intensityScore(String haystack, String intensity) {
        String normalizedIntensity = normalize(intensity);
        if ("intenso".equals(normalizedIntensity) || "potente".equals(normalizedIntensity)) {
            int score = 0;
            if (containsAny(haystack, "very long lasting", "long lasting", "enormous", "strong", "intense",
                    "extrait")) {
                score += 22;
            } else if (containsAny(haystack, "moderate", "eau de parfum")) {
                score += 8;
            }
            if (containsAny(haystack, "weak", "poor", "soft", "intimate", "light")) {
                score -= 35;
            }
            if (containsAny(haystack, "night", "amber", "musk", "vanilla", "tonka", "oud", "patchouli", "leather",
                    "woody", "woods", "sandalwood", "cedar")) {
                score += 7;
            }
            return score;
        }
        if ("suave".equals(normalizedIntensity)) {
            int score = 0;
            if (containsAny(haystack, "soft", "intimate", "light", "fresh", "clean")) {
                score += 12;
            }
            if (containsAny(haystack, "enormous", "strong", "very long lasting", "intense")) {
                score -= 10;
            }
            return score;
        }
        return 0;
    }

    public int occasionScore(String haystack, String occasion) {
        String normalizedOccasion = normalize(occasion);
        if ("especial".equals(normalizedOccasion)
                && containsAny(haystack, "night", "evening", "date", "special", "amber", "vanilla", "musk")) {
            return 5;
        }
        if ("diario".equals(normalizedOccasion) && containsAny(haystack, "daily", "everyday", "office", "work",
                "casual", "professional", "clean", "fresh", "soft", "moderate", "versatile")) {
            return 4;
        }
        return 0;
    }

    public boolean containsDislikedNote(PerfumeItem item, String dislikedNotes) {
        if (!StringUtils.hasText(dislikedNotes)) {
            return false;
        }

        String haystack = normalize(item.getName() + " " + item.getBrand() + " " + item.getNotes() + " "
                + item.getDescription());
        for (String token : normalize(dislikedNotes).split("[, ]+")) {
            if (token.length() >= 3 && containsAny(haystack, noteAliases(token))) {
                return true;
            }
        }
        return false;
    }

    public String translateSeason(String season) {
        String normalized = normalize(season);
        if ("invierno".equals(normalized)) {
            return "winter cold night amber vanilla warm spicy";
        }
        if ("verano".equals(normalized)) {
            return "summer fresh citrus aquatic";
        }
        if ("primavera".equals(normalized)) {
            return "spring floral fresh green";
        }
        if ("otono".equals(normalized)) {
            return "autumn fall woody spicy amber";
        }
        if ("versatil".equals(normalized)) {
            return "versatile all year daily everyday signature";
        }
        return valueOrDash(season);
    }

    public String translateOccasion(String occasion) {
        String normalized = normalize(occasion);
        if ("especial".equals(normalized)) {
            return "night evening date special occasion";
        }
        if ("diario".equals(normalized)) {
            return "daily office casual";
        }
        if ("versatil".equals(normalized)) {
            return "versatile all year";
        }
        return valueOrDash(occasion);
    }

    private int preferredNotesScore(String haystack, String preferredNotes) {
        String normalized = normalize(preferredNotes);
        if (!StringUtils.hasText(normalized)) {
            return 0;
        }

        int score = 0;
        score += scoreIfPreferred(normalized, haystack, "dulce", 10, "sweet", "vanilla", "caramel", "toffee",
                "praline", "tonka", "honey", "chocolate", "coconut", "gourmand");
        score += scoreIfPreferred(normalized, haystack, "gourmand", 8, "gourmand", "dessert", "sweet", "vanilla",
                "caramel", "tonka", "praline", "honey", "chocolate");
        score += scoreIfPreferred(normalized, haystack, "cremoso", 6, "creamy", "milk", "lactonic", "vanilla",
                "sandalwood", "musk");
        score += scoreIfPreferred(normalized, haystack, "arroz", 6, "rice", "milk", "creamy", "vanilla",
                "gourmand");
        score += scoreIfPreferred(normalized, haystack, "canela", 5, "cinnamon", "spicy", "warm", "vanilla",
                "gourmand");
        score += scoreIfPreferred(normalized, haystack, "vainilla", 4, "vanilla", "tonka");
        score += scoreIfPreferred(normalized, haystack, "caramelo", 4, "caramel", "toffee", "praline");
        score += scoreIfPreferred(normalized, haystack, "coco", 4, "coconut", "coco");
        score += scoreIfPreferred(normalized, haystack, "frutal", 8, "fruity", "fruit", "pear", "peach", "apple",
                "berries", "apricot", "strawberry");
        score += scoreIfPreferred(normalized, haystack, "fresa", 8, "strawberry", "berries", "red fruits");
        score += scoreIfPreferred(normalized, haystack, "chocolate", 4, "chocolate", "cacao", "cocoa");
        score += scoreIfPreferred(normalized, haystack, "miel", 4, "honey");
        score += scoreIfPreferred(normalized, haystack, "limpio", 5, "clean", "soapy", "musk", "fresh", "cotton");
        score += scoreIfPreferred(normalized, haystack, "sexy", 4, "sensual", "sexy", "amber", "musk", "vanilla",
                "night");
        score += scoreIfPreferred(normalized, haystack, "sensual", 4, "sensual", "sexy", "seductive", "amber",
                "musk", "vanilla", "skin", "night");
        score += scoreIfPreferred(normalized, haystack, "elegante", 4, "elegant", "iris", "musk", "woody",
                "sophisticated");
        score += scoreIfPreferred(normalized, haystack, "juvenil", 3, "young", "modern", "fresh", "fruity");
        score += scoreIfPreferred(normalized, haystack, "oscuro", 5, "dark", "smoky", "smoke", "incense", "oud",
                "patchouli", "leather", "black", "night", "amber");
        score += scoreIfPreferred(normalized, haystack, "lujoso", 5, "luxury", "luxurious", "premium", "elegant",
                "exclusive", "iris", "oud", "saffron", "leather", "amber", "niche", "extrait");
        score += scoreIfPreferred(normalized, haystack, "minimalista", 4, "minimal", "minimalist", "clean", "musk",
                "skin", "soft", "tea", "transparent", "light");
        score += scoreIfPreferred(normalized, haystack, "misterioso", 5, "mysterious", "mystery", "dark", "smoky",
                "incense", "oud", "patchouli", "amber", "night");
        score += scoreIfPreferred(normalized, haystack, "calido", 4, "warm", "amber", "vanilla", "spicy");
        score += scoreIfPreferred(normalized, haystack, "casual", 3, "casual", "daily", "clean", "fresh");
        score += scoreIfPreferred(normalized, haystack, "amaderado", 24, "woody", "wood", "woods", "cedar",
                "sandalwood", "vetiver", "oud", "guaiac", "spruce");
        score += scoreIfPreferred(normalized, haystack, "cuero", 12, "leather", "suede", "birch tar", "tar");
        score += scoreIfPreferred(normalized, haystack, "ahumado", 10, "smoky", "smoke", "incense", "birch tar",
                "tar", "vetiver");
        score += scoreIfPreferred(normalized, haystack, "industrial", 8, "industrial", "petrol", "gasoline",
                "metallic", "mineral", "leather", "smoky");
        score += scoreIfPreferred(normalized, haystack, "mineral", 8, "mineral", "metallic", "vetiver", "smoke");
        score += scoreIfPreferred(normalized, haystack, "fresco", 14, "fresh", "citrus", "bergamot", "lemon",
                "orange", "aquatic", "marine", "clean");
        score += scoreIfPreferred(normalized, haystack, "citrico", 10, "citrus", "bergamot", "lemon", "orange",
                "grapefruit", "mandarin", "citric");
        score += scoreIfPreferred(normalized, haystack, "salino", 10, "salty", "salt", "marine", "aquatic",
                "ocean", "mineral", "ambergris");
        score += scoreIfPreferred(normalized, haystack, "animalico", 10, "animalic", "musk", "civet", "castoreum",
                "ambergris", "skin");
        score += scoreIfPreferred(normalized, haystack, "floral", 12, "floral", "rose", "jasmine", "iris",
                "orange blossom", "peony", "gardenia");
        score += scoreIfPreferred(normalized, haystack, "especiado", 14, "spicy", "pepper", "cardamom",
                "cinnamon", "clove", "saffron", "ginger");
        score += scoreIfPreferred(normalized, haystack, "ambar", 8, "amber", "ambroxan", "labdanum", "resin");
        score += scoreIfPreferred(normalized, haystack, "almizcle", 7, "musk", "white musk", "skin");
        score += scoreIfPreferred(normalized, haystack, "oud", 8, "oud", "agarwood");
        score += scoreIfPreferred(normalized, haystack, "tabaco", 7, "tobacco");
        score += scoreIfPreferred(normalized, haystack, "incienso", 7, "incense", "smoke", "smoky");
        score += scoreIfPreferred(normalized, haystack, "cafe", 6, "coffee", "cafe");
        score += scoreIfPreferred(normalized, haystack, "iris", 6, "iris", "orris", "powdery");
        score += scoreIfPreferred(normalized, haystack, "lavanda", 6, "lavender", "aromatic");
        return score;
    }

    private int preferredFamilyMismatchPenalty(String haystack, String preferredNotes) {
        int penalty = 0;
        for (String preference : noteTokens(preferredNotes)) {
            if (isCoreFamily(preference) && !containsAny(haystack, reasonAliases(preference))) {
                penalty -= "amaderado".equals(preference) ? 28 : 18;
            }
        }
        return penalty;
    }

    private boolean isCoreFamily(String preference) {
        return List.of("fresco", "dulce", "amaderado", "floral", "especiado").contains(normalize(preference));
    }

    private int genderScore(PerfumeItem item, PerfumeProfile profile) {
        if (profile == null || item == null || !StringUtils.hasText(profile.getGenderTarget())) {
            return 0;
        }

        String target = normalize(profile.getGenderTarget());
        String gender = normalize(item.getGender());
        String haystack = itemHaystack(item);

        if ("hombre".equals(target)) {
            if (containsAny(gender, "women", "woman", "female", "mujer", "feminine")) {
                return -45;
            }
            if (containsAny(gender, "men", "man", "male", "hombre", "masculine")) {
                return 22;
            }
            if (containsAny(gender, "unisex") || containsAny(haystack, "homme", "pour homme", "men")) {
                return 7;
            }
        }

        if ("mujer".equals(target)) {
            if (containsAny(gender, "women", "woman", "female", "mujer", "feminine")) {
                return 22;
            }
            if (containsAny(gender, "unisex") || containsAny(haystack, "femme", "pour femme", "women")) {
                return 7;
            }
            if (containsAny(gender, "men", "man", "male", "hombre", "masculine")) {
                return -45;
            }
        }

        if ("unisex".equals(target) && containsAny(gender, "unisex")) {
            return 15;
        }

        return 0;
    }

    private boolean isGenderMismatch(PerfumeItem item, String target) {
        if (item == null) {
            return false;
        }

        String gender = normalize(item.getGender());
        String haystack = itemHaystack(item);

        if ("hombre".equals(target)) {
            return isFemaleGender(gender)
                    || containsAny(haystack, " for women", "for women", "pour femme", " for woman",
                            " mujer", "women's", "womens");
        }

        if ("mujer".equals(target)) {
            return isMaleGender(gender)
                    || containsAny(haystack, " for men", "for men", "pour homme", " for man",
                            " hombre", "men's", "mens");
        }

        return false;
    }

    private boolean isFemaleGender(String gender) {
        return containsAny(gender, "women", "woman", "female", "mujer", "feminine");
    }

    private boolean isMaleGender(String gender) {
        return "men".equals(gender)
                || "man".equals(gender)
                || "male".equals(gender)
                || "hombre".equals(gender)
                || "masculine".equals(gender)
                || containsAny(gender, " men ", " man ", " male ", " hombre ", " masculine ");
    }

    private int budgetScore(PerfumeItem item, PerfumeProfile profile) {
        if (profile == null || item == null || !StringUtils.hasText(profile.getBudget())) {
            return 0;
        }

        String budget = PerfumeBudgetClassifier.normalizeBudget(profile.getBudget());
        Double price = PerfumeBudgetClassifier.parsePrice(item.getPrice());
        String value = normalize(item.getPriceValue());
        String tier = PerfumeBudgetClassifier.tier(item);

        if ("economico".equals(budget)) {
            if (StringUtils.hasText(tier) && !"economico".equals(tier)) {
                return -30;
            }
            int score = containsAny(value, "good", "good_value", "okay") ? 4 : 0;
            if (price == null) {
                return score;
            }
            if (price <= 60) {
                return score + 12;
            }
            if (price <= 100) {
                return score + 4;
            }
            return score - 12;
        }

        if ("medio".equals(budget) || "economico-medio".equals(budget)) {
            if ("medio".equals(budget) && StringUtils.hasText(tier) && !"medio".equals(tier)) {
                return -18;
            }
            if (price == null) {
                return containsAny(value, "good", "okay") ? 3 : 0;
            }
            return price >= 40 && price <= 150 ? 8 : -4;
        }

        if ("premium".equals(budget)) {
            if (StringUtils.hasText(tier) && !"premium".equals(tier)) {
                return -35;
            }
            if (price == null) {
                String haystack = itemHaystack(item);
                int score = containsAny(value, "premium", "luxury") ? 4 : 0;
                if (containsAny(haystack, "premium", "luxury", "luxurious", "niche", "exclusive", "royal",
                        "extrait", "oud", "saffron", "parfum")) {
                    score += 6;
                }
                if (containsAny(value, "good_value", "good value", "cheap")) {
                    score -= 4;
                }
                return score;
            }
            if (price >= 120) {
                return 10;
            }
            if (price >= 90) {
                return 4;
            }
            return -12;
        }

        return 0;
    }

    private List<PerfumeItem> budgetCompatibleCatalog(List<PerfumeItem> catalog, PerfumeProfile profile) {
        if (profile == null || !StringUtils.hasText(profile.getBudget())) {
            return catalog;
        }
        return catalog.stream()
                .filter(item -> PerfumeBudgetClassifier.matches(item, profile.getBudget()))
                .toList();
    }

    private List<PerfumeItem> budgetCompatibleCatalog(List<PerfumeItem> catalog, PerfumeProfile profile, int limit) {
        List<PerfumeItem> strict = budgetCompatibleCatalog(catalog, profile);
        if (profile == null || limit <= 0 || strict.size() >= Math.min(limit, catalog.size())) {
            return strict;
        }

        String budget = PerfumeBudgetClassifier.normalizeBudget(profile.getBudget());
        if (!"medio".equals(budget)) {
            return strict;
        }

        List<PerfumeItem> expanded = new ArrayList<>(strict);
        for (PerfumeItem item : catalog) {
            if (expanded.size() >= limit) {
                break;
            }
            if (!expanded.contains(item) && isReasonableMediumBudgetStretch(item)) {
                expanded.add(item);
            }
        }
        return expanded;
    }

    private boolean isReasonableMediumBudgetStretch(PerfumeItem item) {
        String tier = PerfumeBudgetClassifier.tier(item);
        return "medio".equals(tier) || "economico".equals(tier);
    }

    private Double parsePrice(String price) {
        if (!StringUtils.hasText(price)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)")
                .matcher(price);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            try {
                values.add(Double.parseDouble(matcher.group(1).replace(",", ".")));
            } catch (NumberFormatException ignored) {
                // Ignore fragments that are not valid prices.
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() >= 2 && price.matches(".*\\d+\\s*-\\s*\\d+.*")) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(values.get(0));
        }
        return values.get(0);
    }

    private int scoreIfPreferred(String preferredNotes, String haystack, String preferredToken, int points,
            String... aliases) {
        if (!preferredNotes.contains(preferredToken)) {
            return 0;
        }
        return containsAny(haystack, aliases) ? points : 0;
    }

    private List<String> profileTraits(PerfumeProfile profile) {
        if (profile == null) {
            return List.of();
        }

        List<String> traits = new ArrayList<>();
        for (String note : noteTokens(profile.getPreferredNotes())) {
            String label = preferenceLabel(note);
            if (StringUtils.hasText(label) && !traits.contains(label)) {
                traits.add(label);
            }
            if (traits.size() == 2) {
                break;
            }
        }
        addIfPresent(traits, intensityLabel(profile.getIntensity()));
        addIfPresent(traits, budgetLabel(profile.getBudget()));
        addIfPresent(traits, occasionLabel(profile.getOccasion()));
        addIfPresent(traits, seasonLabel(profile.getSeason()));
        return traits.stream().limit(5).toList();
    }

    private List<String> matchingProfileNotesForReason(PerfumeProfile profile, PerfumeItem perfume) {
        if (profile == null || perfume == null || !StringUtils.hasText(profile.getPreferredNotes())) {
            return List.of();
        }

        List<String> matches = new ArrayList<>();
        Set<String> preferences = noteTokens(profile.getPreferredNotes());
        for (String perfumeNote : displayNoteTokens(perfume.getNotes())) {
            String normalizedNote = normalize(perfumeNote);
            if (preferences.stream().anyMatch(preference -> matchesPreference(normalizedNote, preference))) {
                String label = noteLabel(perfumeNote);
                if (!matches.contains(label)) {
                    matches.add(label);
                }
            }
            if (matches.size() == 3) {
                break;
            }
        }
        return matches;
    }

    private boolean matchesPreference(String normalizedNote, String preference) {
        if (normalizedNote.contains(preference)) {
            return true;
        }
        return containsAny(normalizedNote, reasonAliases(preference));
    }

    private List<String> missingCorePreferencesForReason(PerfumeProfile profile, PerfumeItem perfume) {
        if (profile == null || perfume == null) {
            return List.of();
        }
        String haystack = itemHaystack(perfume);
        List<String> missing = new ArrayList<>();
        for (String preference : noteTokens(profile.getPreferredNotes())) {
            if (isCoreFamily(preference) && !containsAny(haystack, reasonAliases(preference))) {
                String label = preferenceLabel(preference);
                if (StringUtils.hasText(label) && !missing.contains(label)) {
                    missing.add(label);
                }
            }
        }
        return missing.stream().limit(2).toList();
    }

    private Set<String> displayNoteTokens(String notes) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : valueOrDash(notes).split("[,;/|\\n]+")) {
            String trimmed = token.trim();
            if (StringUtils.hasText(trimmed) && isUsefulNoteToken(normalize(trimmed))) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private void addIfPresent(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private String preferenceLabel(String preference) {
        return switch (normalize(preference)) {
            case "fresco" -> "fresco";
            case "dulce" -> "dulce";
            case "amaderado" -> "amaderado";
            case "floral" -> "floral";
            case "especiado" -> "especiado";
            case "citrico" -> "citrico";
            case "limpio" -> "limpio";
            case "sexy", "sensual" -> "sensual";
            case "elegante" -> "elegante";
            case "juvenil" -> "juvenil";
            case "oscuro" -> "oscuro";
            case "cuero" -> "cuero";
            case "ahumado" -> "ahumado";
            case "industrial" -> "industrial";
            case "mineral", "metalico" -> normalize(preference);
            case "lujoso" -> "lujoso";
            case "minimalista" -> "minimalista";
            case "misterioso" -> "misterioso";
            case "calido" -> "calido";
            case "casual" -> "casual";
            case "salino" -> "salino";
            case "animalico" -> "animalico";
            case "ambar", "almizcle", "oud", "tabaco", "incienso", "cafe", "iris", "lavanda" -> normalize(preference);
            case "vainilla", "caramelo", "coco", "frutal", "chocolate", "miel", "gourmand", "cremoso",
                    "arroz", "canela" -> normalize(preference);
            default -> "";
        };
    }

    private String intensityLabel(String intensity) {
        return switch (normalize(intensity)) {
            case "intenso", "potente" -> "potente";
            case "suave" -> "suave";
            default -> "";
        };
    }

    private String budgetLabel(String budget) {
        return switch (normalize(budget)) {
            case "economico" -> "economico";
            case "economico-medio" -> "economico-medio";
            case "medio" -> "medio";
            case "premium" -> "premium";
            default -> "";
        };
    }

    private String occasionLabel(String occasion) {
        return switch (normalize(occasion)) {
            case "especial" -> "para noche";
            case "diario" -> "para diario";
            case "versatil" -> "versatil";
            default -> "";
        };
    }

    private String seasonLabel(String season) {
        return switch (seasonBucket(season)) {
            case "summer" -> "para verano";
            case "winter" -> "para invierno";
            case "spring" -> "para primavera";
            case "autumn" -> "para otono";
            default -> "";
        };
    }

    private boolean seasonMatchesProfile(PerfumeProfile profile, PerfumeItem perfume) {
        if (profile == null || perfume == null) {
            return false;
        }
        String requestedSeason = seasonBucket(profile.getSeason());
        String perfumeSeason = seasonBucket(perfume.getSeason());
        return StringUtils.hasText(requestedSeason) && requestedSeason.equals(perfumeSeason);
    }

    private String noteLabel(String note) {
        String normalized = normalize(note);
        return switch (normalized) {
            case "amber" -> "ambar";
            case "bergamot" -> "bergamota";
            case "vanilla" -> "vainilla";
            case "caramel" -> "caramelo";
            case "coconut" -> "coco";
            case "honey" -> "miel";
            case "chocolate", "cacao", "cocoa" -> "chocolate";
            case "cedar" -> "cedro";
            case "sandalwood" -> "sandalo";
            case "musk" -> "almizcle";
            case "rose" -> "rosa";
            case "jasmine" -> "jazmin";
            case "lemon" -> "limon";
            case "orange" -> "naranja";
            default -> note.trim();
        };
    }

    private String[] reasonAliases(String preference) {
        return switch (normalize(preference)) {
            case "fresco" -> new String[] { "fresh", "citrus", "bergamot", "lemon", "orange", "aquatic", "marine",
                    "clean" };
            case "citrico" -> new String[] { "citrus", "bergamot", "lemon", "orange", "grapefruit",
                    "mandarin", "citric" };
            case "salino" -> new String[] { "salty", "salt", "marine", "aquatic", "ocean", "mineral",
                    "ambergris" };
            case "animalico" -> new String[] { "animalic", "musk", "civet", "castoreum", "ambergris", "skin" };
            case "dulce" -> new String[] { "sweet", "vanilla", "caramel", "tonka", "honey", "chocolate", "coconut",
                    "gourmand" };
            case "gourmand" -> new String[] { "gourmand", "dessert", "sweet", "vanilla", "caramel", "tonka",
                    "praline", "honey", "chocolate" };
            case "cremoso" -> new String[] { "creamy", "milk", "lactonic", "vanilla", "sandalwood", "musk" };
            case "arroz" -> new String[] { "rice", "milk", "creamy", "vanilla", "gourmand" };
            case "canela" -> new String[] { "cinnamon", "spicy", "warm", "vanilla", "gourmand" };
            case "amaderado" -> new String[] { "woody", "wood", "woods", "cedar", "sandalwood", "vetiver", "oud",
                    "guaiac", "spruce" };
            case "cuero" -> new String[] { "leather", "suede", "birch tar", "tar" };
            case "ahumado" -> new String[] { "smoky", "smoke", "incense", "birch tar", "tar", "vetiver" };
            case "industrial" -> new String[] { "industrial", "petrol", "gasoline", "metallic", "mineral",
                    "leather", "smoky" };
            case "mineral", "metalico" -> new String[] { "mineral", "metallic", "vetiver", "smoke" };
            case "floral" -> new String[] { "floral", "rose", "jasmine", "orange blossom", "peony", "iris" };
            case "especiado" -> new String[] { "spicy", "pepper", "cardamom", "cinnamon", "clove", "saffron" };
            case "limpio" -> new String[] { "clean", "soapy", "cotton", "musk", "fresh" };
            case "sexy", "sensual" -> new String[] { "sensual", "sexy", "amber", "musk", "vanilla", "skin", "night" };
            case "elegante" -> new String[] { "elegant", "iris", "musk", "woody", "amber", "sophisticated" };
            case "juvenil" -> new String[] { "young", "modern", "fresh", "fruity", "citrus" };
            case "oscuro" -> new String[] { "dark", "smoky", "incense", "oud", "patchouli", "leather", "amber" };
            case "lujoso" -> new String[] { "luxury", "premium", "iris", "oud", "saffron", "leather", "amber" };
            case "minimalista" -> new String[] { "minimal", "clean", "musk", "skin", "soft", "tea", "transparent" };
            case "misterioso" -> new String[] { "mysterious", "dark", "smoky", "incense", "oud", "patchouli",
                    "amber" };
            case "calido" -> new String[] { "warm", "amber", "vanilla", "spicy", "tonka" };
            case "casual" -> new String[] { "casual", "daily", "clean", "fresh", "citrus" };
            case "ambar" -> new String[] { "amber", "ambroxan", "labdanum", "resin" };
            case "almizcle" -> new String[] { "musk", "white musk", "skin" };
            case "oud" -> new String[] { "oud", "agarwood" };
            case "tabaco" -> new String[] { "tobacco" };
            case "incienso" -> new String[] { "incense", "smoke", "smoky" };
            case "cafe" -> new String[] { "coffee", "cafe" };
            case "iris" -> new String[] { "iris", "orris", "powdery" };
            case "lavanda" -> new String[] { "lavender", "aromatic" };
            default -> noteAliases(preference);
        };
    }

    private String joinHuman(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " y " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " y "
                + values.get(values.size() - 1);
    }

    private String trimReason(String reason) {
        if (reason.length() <= 260) {
            return reason;
        }
        return reason.substring(0, 257).trim() + "...";
    }

    private Set<String> matchingHistoryNotes(String itemHaystack, List<PerfumeRecommendation> recommendations) {
        Set<String> matchingNotes = new LinkedHashSet<>();
        if (recommendations == null) {
            return matchingNotes;
        }

        for (PerfumeRecommendation recommendation : recommendations) {
            for (String note : noteTokens(recommendation.getNotes())) {
                if (containsAny(itemHaystack, noteAliases(note))) {
                    matchingNotes.add(note);
                }
            }
        }
        return matchingNotes;
    }

    private Set<String> noteTokens(String notes) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalize(notes).split("[,;/| .]+")) {
            if (isUsefulNoteToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isUsefulNoteToken(String token) {
        return token.length() >= 3
                && !List.of("and", "con", "los", "las", "the", "notes", "note", "top", "middle", "base",
                        "main", "accord", "accords", "perfume", "fragancia").contains(token);
    }

    private boolean hasAcceptedSeasonMatch(PerfumeItem item, PerfumeProfile profile,
            List<PerfumeRecommendation> acceptedRecommendations) {
        String itemSeason = seasonBucket(item.getSeason());
        if (!StringUtils.hasText(itemSeason)) {
            return false;
        }

        String profileSeason = profile == null ? "" : seasonBucket(profile.getSeason());
        if (StringUtils.hasText(profileSeason) && !profileSeason.equals(itemSeason)) {
            return false;
        }

        return acceptedRecommendations.stream()
                .map(recommendation -> seasonBucket(recommendation.getSeason()))
                .anyMatch(itemSeason::equals);
    }

    private String seasonBucket(String value) {
        String normalized = normalize(value);
        if (containsAny(normalized, "verano", "summer")) {
            return "summer";
        }
        if (containsAny(normalized, "invierno", "winter")) {
            return "winter";
        }
        if (containsAny(normalized, "primavera", "spring")) {
            return "spring";
        }
        if (containsAny(normalized, "otono", "autumn", "fall")) {
            return "autumn";
        }
        return "";
    }

    private boolean isSamePerfume(PerfumeItem item, PerfumeRecommendation recommendation) {
        return normalize(item.getName()).equals(normalize(recommendation.getPerfumeName()))
                && sameBrand(item.getBrand(), recommendation.getBrand());
    }

    private boolean sameBrand(String itemBrand, String recommendationBrand) {
        return StringUtils.hasText(itemBrand)
                && StringUtils.hasText(recommendationBrand)
                && normalize(itemBrand).equals(normalize(recommendationBrand));
    }

    private String itemHaystack(PerfumeItem item) {
        return normalize(item.getName() + " " + item.getBrand() + " " + item.getNotes() + " "
                + item.getDescription() + " " + item.getSeason() + " " + item.getGender() + " "
                + item.getLongevity() + " " + item.getSillage() + " " + item.getOilType() + " "
                + item.getPriceValue() + " " + item.getPrice());
    }

    private String[] noteAliases(String note) {
        return switch (note) {
            case "vainilla" -> new String[] { "vainilla", "vanilla" };
            case "caramelo" -> new String[] { "caramelo", "caramel", "toffee", "praline" };
            case "coco" -> new String[] { "coco", "coconut" };
            case "fruta", "frutas", "frutal" -> new String[] { "fruit", "fruity", "pear", "peach", "apple",
                    "berries", "apricot", "strawberry" };
            case "fresa", "fresas" -> new String[] { "strawberry", "berries", "red fruits" };
            case "chocolate" -> new String[] { "chocolate", "cacao", "cocoa" };
            case "miel" -> new String[] { "miel", "honey" };
            case "gourmand" -> new String[] { "gourmand", "dessert", "sweet", "vanilla", "caramel", "tonka",
                    "praline", "honey", "chocolate" };
            case "cremoso" -> new String[] { "creamy", "milk", "lactonic", "vanilla", "sandalwood", "musk" };
            case "arroz" -> new String[] { "rice", "milk", "creamy", "vanilla", "gourmand" };
            case "canela" -> new String[] { "cinnamon", "spicy", "warm", "vanilla", "gourmand" };
            case "ambar" -> new String[] { "ambar", "amber", "ambroxan", "labdanum", "resin" };
            case "almizcle" -> new String[] { "almizcle", "musk", "white musk", "skin" };
            case "cafe" -> new String[] { "cafe", "coffee" };
            case "iris" -> new String[] { "iris", "orris", "powdery" };
            case "lavanda" -> new String[] { "lavanda", "lavender", "aromatic" };
            case "rosa" -> new String[] { "rosa", "rose" };
            case "incienso" -> new String[] { "incienso", "incense", "smoke", "smoky" };
            case "pachuli" -> new String[] { "pachuli", "patchouli" };
            case "oud" -> new String[] { "oud", "agarwood" };
            case "cuero" -> new String[] { "cuero", "leather", "suede", "birch tar", "tar" };
            case "ahumado" -> new String[] { "humo", "ahumado", "smoky", "smoke", "incense", "birch tar", "tar" };
            case "industrial" -> new String[] { "industrial", "petrol", "gasoline", "metallic", "mineral",
                    "leather", "smoky" };
            case "mineral", "metalico" -> new String[] { "mineral", "metallic", "vetiver", "smoke" };
            case "citricos", "citrico", "citrica" -> new String[] { "citrus", "bergamot", "lemon", "orange",
                    "citric" };
            case "salino" -> new String[] { "salty", "salt", "marine", "aquatic", "ocean", "mineral",
                    "ambergris" };
            case "animalico" -> new String[] { "animalic", "musk", "civet", "castoreum", "ambergris", "skin" };
            case "empalagoso", "empalagosa", "pesado", "pesada" -> new String[] { "sweet", "gourmand", "sugar",
                    "caramel", "vanilla", "honey", "tonka", "chocolate" };
            default -> new String[] { note };
        };
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
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ü", "u")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ü", "u");
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
