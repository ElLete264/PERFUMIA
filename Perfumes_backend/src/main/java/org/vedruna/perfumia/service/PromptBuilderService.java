package org.vedruna.perfumia.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class PromptBuilderService {

    public String nextQuestion(String missingField, String message) {
        String normalized = normalize(message);
        if (missingField.startsWith("si lo quieres")) {
            return "Me falta saber para quien lo orientamos: hombre, mujer o unisex?";
        }
        if (missingField.startsWith("la epoca")) {
            return "Bien, eso ya me da una pista. En que momento lo imaginas mas: verano, invierno, primavera u otono?";
        }
        if (missingField.startsWith("familia olfativa")) {
            if (containsAny(normalized, "dulce", "vainilla", "caramelo", "coco", "chocolate", "miel", "fruta")) {
                return "Perfecto, vamos por una linea dulce. Para que no se quede generico: te tira mas vainilla, caramelo, coco, frutas, chocolate o miel?";
            }
            if (containsAny(normalized, "limpio", "elegante", "sexy", "juvenil", "calido", "casual")) {
                return "Te entiendo mas por sensacion que por nota, y eso tambien sirve. Lo quieres mas limpio, sexy, elegante, juvenil, calido o casual?";
            }
            if (containsAny(normalized, "nunca", "no se", "no lo se", "no he usado", "ni idea")) {
                return "Sin problema, te guio. Piensa en sensaciones: limpio como ducha, dulce como vainilla, amaderado como madera seca, floral como jazmin o especiado como canela. Cual te llama mas?";
            }
            return "Me falta la direccion del olor: lo quieres fresco, dulce, amaderado, floral o especiado?";
        }
        if (missingField.startsWith("intensidad")) {
            return "Con eso ya voy viendo el estilo. Quieres que sea suave y discreto, o mas potente y duradero?";
        }
        if (missingField.startsWith("ocasion")) {
            return "Me falta el uso principal: diario, trabajo, cita, noche o algo versatil?";
        }
        if (missingField.startsWith("presupuesto")) {
            return "Me falta el presupuesto para no salirme de rango: economico, medio o premium?";
        }
        return "Dame una pista mas de como quieres sentirlo y sigo afinando contigo.";
    }

    public String answerClarificationQuestion(String message, List<String> missingFields) {
        if (missingFields.isEmpty() || !looksLikeQuestion(message)) {
            return "";
        }

        String currentMissingField = missingFields.get(0);
        String normalized = normalize(message);

        if (currentMissingField.startsWith("presupuesto")
                && containsAny(normalized, "precio", "presupuesto", "cuanto", "rango", "abanico", "medio",
                        "economico", "premium")) {
            return "Claro. Para orientar el recomendador: economico seria hasta 50 euros, medio entre 50 y 120 euros, y premium mas de 120 euros. Con cual te quedas: economico, medio o premium?";
        }

        if (currentMissingField.startsWith("familia olfativa")
                && containsAny(normalized, "empalagoso", "cargado", "pesado", "dulce")) {
            return "Dentro de lo dulce hay grados: vainilla y miel suelen ser calidos, caramelo/chocolate mas golosos, coco/frutas mas faciles. Si te molestan los empalagosos, puedo buscar un dulce mas limpio. Que prefieres?";
        }

        if (currentMissingField.startsWith("familia olfativa")
                && containsAny(normalized, "huele", "oler", "familia", "fresco", "dulce", "amaderado", "floral",
                        "especiado", "nunca", "no se", "no lo se")) {
            return "Te explico rapido: fresco huele limpio/citrico, dulce recuerda vainilla o caramelo, amaderado es cedro/sandalo, floral es jazmin/rosa y especiado tira a pimienta/canela. Cual te llama mas?";
        }

        if (containsAny(normalized, "estela", "proyecta", "proyeccion", "se note", "discreto")) {
            return "La estela es cuanto se nota el perfume alrededor. Discreto se queda cerca, moderado se percibe normal y con estela deja mas rastro. Como lo prefieres?";
        }

        if (currentMissingField.startsWith("intensidad")
                && containsAny(normalized, "suave", "potente", "intenso", "duradero", "dura", "proyecta")) {
            return "Suave significa que se nota cerca y no invade; potente significa que dura mas y se percibe a distancia. Para tu caso, prefieres suave o potente?";
        }

        if (currentMissingField.startsWith("ocasion")
                && containsAny(normalized, "ocasion", "diario", "trabajo", "cita", "noche", "versatil")) {
            return "La ocasion ayuda a no pasarnos ni quedarnos cortos: diario/trabajo suele ser mas limpio, cita o noche puede ser mas intenso, y versatil vale para casi todo. Para que lo quieres principalmente?";
        }

        if (currentMissingField.startsWith("la epoca")
                && containsAny(normalized, "epoca", "estacion", "verano", "invierno", "primavera", "otono",
                        "calor", "frio")) {
            return "La epoca cambia bastante: en verano suelen ir mejor perfumes frescos y ligeros; en invierno, mas dulces, especiados o amaderados. Para que estacion lo buscamos?";
        }

        return "";
    }

    public boolean looksLikeQuestion(String message) {
        String normalized = normalize(message);
        return message.contains("?") || containsAny(normalized,
                "que", "cual", "cuanto", "como", "dime", "explica", "no entiendo", "no se", "no lo se",
                "diferencia", "significa");
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
                .replace("ã±", "n");
        return normalized
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ü", "u");
    }
}
