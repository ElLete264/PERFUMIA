package org.vedruna.perfumia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.util.StringUtils;
import org.vedruna.perfumia.service.dto.PerfumeItem;

final class PerfumeBudgetClassifier {

    static final String ECONOMICO = "economico";
    static final String ECONOMICO_MEDIO = "economico-medio";
    static final String MEDIO = "medio";
    static final String PREMIUM = "premium";
    static final String UNKNOWN = "";

    private PerfumeBudgetClassifier() {
    }

    static boolean matches(PerfumeItem item, String requestedBudget) {
        String requested = normalizeBudget(requestedBudget);
        if (!StringUtils.hasText(requested)) {
            return true;
        }

        String tier = tier(item);
        if (!StringUtils.hasText(tier)) {
            return false;
        }

        if (ECONOMICO.equals(requested)) {
            return ECONOMICO.equals(tier);
        }
        if (ECONOMICO_MEDIO.equals(requested)) {
            return ECONOMICO.equals(tier) || MEDIO.equals(tier);
        }
        if (MEDIO.equals(requested)) {
            return MEDIO.equals(tier);
        }
        if (PREMIUM.equals(requested)) {
            return PREMIUM.equals(tier);
        }
        return true;
    }

    static String tier(PerfumeItem item) {
        if (item == null) {
            return UNKNOWN;
        }
        return tier(item.getBrand(), item.getName(), item.getPrice(), item.getPriceValue(), item.getDescription(),
                item.getNotes(), item.getOilType());
    }

    static String tier(String brand, String name, String price, String priceValue, String description, String notes,
            String oilType) {
        Double parsedPrice = parsePrice(price);
        String value = normalize(priceValue);
        if (parsedPrice != null) {
            if (containsAny(value, "premium", "luxury", "niche", "exclusive", "expensive", "very expensive")
                    && parsedPrice >= 120) {
                return PREMIUM;
            }
            if (containsAny(value, "cheap", "low cost", "affordable", "good value", "good_value")
                    && parsedPrice <= 60) {
                return ECONOMICO;
            }
            return tierFromPrice(parsedPrice);
        }

        if (containsAny(value, "cheap", "low cost", "affordable", "good value", "good_value")) {
            return ECONOMICO;
        }
        if (containsAny(value, "premium", "luxury", "niche", "exclusive", "expensive", "very expensive")) {
            return PREMIUM;
        }
        if (containsAny(value, "mid", "moderate", "okay")) {
            return MEDIO;
        }

        String text = normalize(brand + " " + name + " " + description + " " + notes + " " + oilType);
        if (containsAny(text, "creed", "tom ford", "maison francis kurkdjian", "kilian", "by kilian",
                "amouage", "parfums de marly", "xerjoff", "byredo", "le labo", "diptyque", "initio",
                "nishane", "montale", "roja", "clive christian", "frederic malle", "frédéric malle",
                "penhaligon", "maison crivelli", "tiziana terenzi", "niche", "extrait", "exclusive",
                "luxury", "luxurious", "premium")) {
            return PREMIUM;
        }
        if (containsAny(text, "chanel", "dior", "yves saint laurent", "giorgio armani", "armani",
                "dolce", "gabbana", "hermes", "carolina herrera", "lancome", "paco rabanne", "rabanne",
                "valentino", "givenchy", "jean paul gaultier", "prada", "versace", "burberry")) {
            return MEDIO;
        }
        if (containsAny(text, "calvin klein", "ck one", "zara", "fresh line", "sweet essentials",
                "somethin special")) {
            return ECONOMICO;
        }

        return UNKNOWN;
    }

    static String normalizeBudget(String budget) {
        String normalized = normalize(budget);
        if (containsAny(normalized, "economico medio", "economico-medio", "medio economico", "calidad precio")) {
            return ECONOMICO_MEDIO;
        }
        if (containsAny(normalized, "barato", "economico", "asequible", "cheap", "affordable")) {
            return ECONOMICO;
        }
        if (containsAny(normalized, "medio", "gama media", "mid range", "normal")) {
            return MEDIO;
        }
        if (containsAny(normalized, "premium", "lujo", "luxury", "nicho", "niche", "sin limite", "caro")) {
            return PREMIUM;
        }
        return normalized;
    }

    private static String tierFromPrice(double price) {
        if (price <= 60) {
            return ECONOMICO;
        }
        if (price <= 150) {
            return MEDIO;
        }
        return PREMIUM;
    }

    static Double parsePrice(String price) {
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

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
