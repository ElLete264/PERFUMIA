package org.vedruna.perfumia.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * Resuelve imagenes de perfumes sin hacer llamadas extra a APIs externas.
 */
final class PerfumeImageResolver {

    private static final String FRAGELLA_CDN = "https://d2k6fvhyk5xgx.cloudfront.net/images/";

    private static final Map<String, String> KNOWN_IMAGES = Map.ofEntries(
            Map.entry(key("Giorgio Armani", "Acqua di Gio Profondo"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/acqua-di-gio-profondo-giorgio-armani-for-men.jpg"),
            Map.entry(key("Dolce & Gabbana", "Light Blue"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/light-blue-dolce-gabbana-for-women.jpg"),
            Map.entry(key("Calvin Klein", "CK One"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/ck-one-calvin-klein-unisex.jpg"),
            Map.entry(key("Chanel", "Bleu de Chanel"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/bleu-de-chanel-chanel-for-men.jpg"),
            Map.entry(key("Lancome", "La Vie Est Belle"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/la-vie-est-belle-lancome-for-women.jpg"),
            Map.entry(key("Yves Saint Laurent", "Black Opium"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/black-opium-yves-saint-laurent-for-women.jpg"),
            Map.entry(key("Hermes", "Terre d'Hermes"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/terre-d-hermes-hermes-for-men.jpg"),
            Map.entry(key("Carolina Herrera", "Good Girl"),
                    "https://d2k6fvhyk5xgx.cloudfront.net/images/good-girl-carolina-herrera-for-women.jpg"));

    private PerfumeImageResolver() {
    }

    static String resolve(String brand, String perfumeName, String currentImageUrl) {
        if (StringUtils.hasText(currentImageUrl) && currentImageUrl.startsWith("http")) {
            return currentImageUrl;
        }

        String known = KNOWN_IMAGES.get(key(brand, perfumeName));
        if (StringUtils.hasText(known)) {
            return known;
        }

        return "";
    }

    static String resolveCatalogImage(String brand, String perfumeName, String currentImageUrl) {
        String resolved = resolve(brand, perfumeName, currentImageUrl);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (StringUtils.hasText(brand) && StringUtils.hasText(perfumeName)) {
            return FRAGELLA_CDN + slug(perfumeName + " " + brand) + ".jpg";
        }
        return "";
    }

    private static String key(String brand, String perfumeName) {
        return normalize(brand) + "::" + normalize(perfumeName);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String slug(String value) {
        return normalize(value)
                .replace("&", " and ")
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
