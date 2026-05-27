package org.vedruna.perfumia.service;

import java.util.Locale;

import org.springframework.util.StringUtils;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.service.dto.PerfumeItem;

/**
 * Calcula un precio orientativo defendible sin inventar precios exactos ni hacer
 * llamadas externas adicionales.
 */
final class PerfumePriceEstimator {

    private PerfumePriceEstimator() {
    }

    static String estimate(PerfumeProfile profile, PerfumeItem perfume) {
        String budget = normalize(profile != null ? profile.getBudget() : "");

        if (perfume != null && StringUtils.hasText(perfume.getPrice())) {
            return withBudgetMismatchNote(formatPrice(perfume.getPrice()),
                    PerfumeBudgetClassifier.tier(perfume),
                    budget);
        }

        String knownEstimate = knownBrandEstimate(
                perfume != null ? perfume.getBrand() : "",
                perfume != null ? perfume.getName() : "");
        if (StringUtils.hasText(knownEstimate)) {
            return withBudgetMismatchNote(knownEstimate,
                    PerfumeBudgetClassifier.tier(
                            perfume != null ? perfume.getBrand() : "",
                            perfume != null ? perfume.getName() : "",
                            "",
                            "",
                            perfume != null ? perfume.getDescription() : "",
                            perfume != null ? perfume.getNotes() : "",
                            perfume != null ? perfume.getOilType() : ""),
                    budget);
        }

        if (budget.contains("econom")) {
            return "Aprox. 20-50 euros";
        }
        if (budget.contains("medio")) {
            return "Aprox. 50-100 euros";
        }
        if (budget.contains("premium") || budget.contains("lujo")) {
            return "Aprox. 120-250+ euros";
        }
        return defaultEstimate(perfume != null ? perfume.getBrand() : "", perfume != null ? perfume.getName() : "");
    }

    static String estimate(PerfumeRecommendation recommendation) {
        if (recommendation == null) {
            return "";
        }
        String knownEstimate = knownBrandEstimate(recommendation.getBrand(), recommendation.getPerfumeName());
        return StringUtils.hasText(knownEstimate)
                ? knownEstimate
                : defaultEstimate(recommendation.getBrand(), recommendation.getPerfumeName());
    }

    private static String knownBrandEstimate(String brand, String perfumeName) {
        String text = normalize(brand + " " + perfumeName);

        if (containsAny(text, "creed", "tom ford", "maison francis kurkdjian", "kilian", "amouage",
                "parfums de marly", "xerjoff", "byredo", "le labo", "diptyque")) {
            return "Aprox. 120-250+ euros";
        }
        if (containsAny(text, "chanel", "dior", "yves saint laurent", "giorgio armani", "armani",
                "dolce", "gabbana", "hermes", "carolina herrera", "lancome", "paco rabanne", "valentino",
                "givenchy", "jean paul gaultier", "prada")) {
            return "Aprox. 70-140 euros";
        }
        if (containsAny(text, "calvin klein", "zara", "fresh line", "sweet essentials", "somethin special")) {
            return "Aprox. 20-60 euros";
        }
        if (containsAny(text, "premium", "luxury", "royal", "exclusive", "niche", "extrait")) {
            return "Aprox. 120-250+ euros";
        }

        return "";
    }

    private static String defaultEstimate(String brand, String perfumeName) {
        String text = normalize(brand + " " + perfumeName);
        return StringUtils.hasText(text) ? "Aprox. 40-90 euros" : "";
    }

    private static String withBudgetMismatchNote(String estimate, String tier, String requestedBudget) {
        if (!StringUtils.hasText(estimate) || !StringUtils.hasText(tier) || !StringUtils.hasText(requestedBudget)) {
            return estimate;
        }
        if ((requestedBudget.contains("premium") || requestedBudget.contains("lujo"))
                && !PerfumeBudgetClassifier.PREMIUM.equals(tier)) {
            return estimate + " (rango " + humanTier(tier) + "; no premium)";
        }
        if (requestedBudget.contains("medio") && PerfumeBudgetClassifier.PREMIUM.equals(tier)) {
            return estimate + " (por encima de gama media)";
        }
        if (requestedBudget.contains("econom") && !PerfumeBudgetClassifier.ECONOMICO.equals(tier)) {
            return estimate + " (por encima de economico)";
        }
        return estimate;
    }

    private static String humanTier(String tier) {
        return switch (tier) {
            case PerfumeBudgetClassifier.ECONOMICO -> "economico";
            case PerfumeBudgetClassifier.MEDIO -> "medio";
            case PerfumeBudgetClassifier.PREMIUM -> "premium";
            default -> "desconocido";
        };
    }

    private static String formatPrice(String price) {
        String clean = price.trim();
        if (!StringUtils.hasText(clean)) {
            return "";
        }
        if (clean.contains("\u20ac") || clean.toLowerCase(Locale.ROOT).contains("eur")
                || clean.toLowerCase(Locale.ROOT).contains("euro")) {
            return "Aprox. " + clean;
        }
        return "Aprox. " + clean.replace(".", ",") + " euros";
    }

    private static Double parsePrice(String price) {
        return PerfumeBudgetClassifier.parsePrice(price);
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
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
