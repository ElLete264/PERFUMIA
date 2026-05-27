package org.vedruna.perfumia.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;

@Service
public class AiDecisionService {

    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)");

    /**
     * Detecta si el usuario acepta guardar la recomendacion pendiente.
     *
     * @param message mensaje escrito por el usuario.
     * @return true si el mensaje expresa aceptacion.
     */
    public boolean isAcceptance(String message) {
        String normalized = normalize(message);
        return containsAny(normalized, "acepto", "guardalo", "guardar", "me gusta", "si quiero", "vale");
    }

    /**
     * Detecta si el usuario rechaza la recomendacion pendiente.
     *
     * @param message mensaje escrito por el usuario.
     * @return true si el mensaje expresa rechazo.
     */
    public boolean isRejection(String message) {
        String normalized = normalize(message);
        return normalized.equals("no")
                || normalized.equals("nope")
                || containsAny(normalized, "no me convence", "no me gusta", "otro", "otra opcion", "descarta", "rechazo");
    }

    /**
     * Detecta saludos simples para responder sin lanzar una recomendacion.
     *
     * @param normalized mensaje ya normalizado.
     * @return true si el mensaje es un saludo.
     */
    public boolean isGreeting(String normalized) {
        return normalized.equals("hola")
                || normalized.equals("buenas")
                || normalized.equals("hey")
                || normalized.equals("holaa")
                || normalized.equals("que tal")
                || normalized.equals("que pasa");
    }

    /**
     * Detecta si el usuario pide otra alternativa de perfume.
     *
     * @param normalized mensaje ya normalizado.
     * @return true si pide otra recomendacion.
     */
    public boolean wantsAnotherPerfume(String normalized) {
        return containsAny(normalized, "quiero otro", "quiero otra", "otro perfume", "otra opcion", "otra recomendacion",
                "buscame otro", "dame otro", "recomiendame otro", "algo diferente");
    }

    /**
     * Detecta ordenes cortas en las que el usuario ya quiere que se busque en el
     * catalogo.
     *
     * @param normalized mensaje ya normalizado.
     * @return true si el usuario pide buscar o recomendar ya.
     */
    public boolean wantsSearchNow(String normalized) {
        if (containsAny(normalized,
                "dame perfumes", "dame un perfume", "recomiendame perfumes", "que me recomiendas",
                "alguna recomendacion", "quiero una recomendacion", "mostrar opciones")) {
            return true;
        }
        return normalized.equals("busca")
                || normalized.equals("buscar")
                || normalized.equals("buscame")
                || normalized.equals("recomienda")
                || normalized.equals("recomiendame")
                || normalized.equals("dale")
                || containsAny(normalized, "busca ", " buscar", "buscame ", "recomiendame ")
                || containsAny(normalized, "busca uno", "busca otro", "buscar otro", "buscame otro",
                        "dame una opcion", "dame otra opcion", "recomiendame uno", "recomiendame algo",
                        "muestrame", "muéstrame");
    }

    /**
     * Detecta negativas muy cortas que normalmente responden a una pregunta del
     * asistente, no a una recomendacion concreta.
     *
     * @param message mensaje escrito por el usuario.
     * @return true si es una negativa sin mas informacion.
     */
    public boolean isShortNegative(String message) {
        String normalized = normalize(message);
        return normalized.equals("no")
                || normalized.equals("nope")
                || normalized.equals("nop")
                || normalized.equals("ninguna")
                || normalized.equals("ninguno");
    }

    /**
     * Clasifica intenciones casuales devueltas por la IA.
     *
     * @param intent intencion devuelta por Gemini.
     * @return true si la intencion es casual o saludo.
     */
    public boolean isCasualIntent(String intent) {
        String normalized = normalize(intent);
        return "casual".equals(normalized) || "greeting".equals(normalized);
    }

    /**
     * Clasifica la intencion de pedir otro perfume devuelta por la IA.
     *
     * @param intent intencion devuelta por Gemini.
     * @return true si la intencion es another_perfume.
     */
    public boolean isAnotherPerfumeIntent(String intent) {
        return "another_perfume".equals(normalize(intent));
    }

    /**
     * Extrae preferencias basicas desde un mensaje de usuario usando reglas
     * locales. Mantiene el mismo comportamiento que tenia el orquestador antes
     * del refactor.
     *
     * @param profile perfil olfativo que se actualiza.
     * @param message mensaje escrito por el usuario.
     */
    public void updateProfileFromMessage(PerfumeProfile profile, String message) {
        String normalized = normalize(message);
        String fullNormalized = normalized;
        String previousBudget = profile.getBudget();
        if (impliesMaleWearerFromAttraction(normalized)) {
            profile.setGenderTarget("hombre");
        } else if (containsAny(normalized, "hombre", "masculino")) {
            profile.setGenderTarget("hombre");
        } else if (mentionsFemaleWearer(normalized)) {
            profile.setGenderTarget("mujer");
        } else if (containsAny(normalized, "abogada")) {
            profile.setGenderTarget("mujer");
        } else if (containsAny(normalized, "abogado", "empresario")) {
            profile.setGenderTarget("hombre");
        } else if (containsAny(normalized, "unisex", "cualquiera")) {
            profile.setGenderTarget("unisex");
        }

        if (containsAny(normalized, "todas las estaciones", "todo el ano", "todo el año", "cualquier estacion",
                "cualquier estación", "toda estacion", "all year", "year round", "versatil", "versátil")) {
            profile.setSeason("versatil");
        } else if (containsAny(normalized, "verano", "calor", "playa")) {
            profile.setSeason("verano");
        } else if (containsAny(normalized, "invierno", "frio", "navidad")) {
            profile.setSeason("invierno");
        } else if (containsAny(normalized, "primavera")) {
            profile.setSeason("primavera");
        } else if (containsAny(normalized, "otono")) {
            profile.setSeason("otono");
        }

        if (containsAny(normalized, "suave", "ligero", "discreto")) {
            profile.setIntensity("suave");
        } else if (containsAny(normalized, "intenso", "potente", "potecia", "potencia", "duradero", "duradera",
                "dure", "dura mucho", "que dure", "duracion", "larga duracion", "noche", "presencia",
                "con presencia", "que se note", "estela", "proyeccion")) {
            profile.setIntensity("intenso");
        }

        List<String> notes = new ArrayList<>();
        normalized = removeAvoidanceClauses(normalized);
        addNoteIfPresent(notes, normalized, "fresco", "salino", "salina", "salado", "salada", "atun");
        addNoteIfPresent(notes, normalized, "marino", "salino", "salina", "salado", "salada", "atun");
        addNoteIfPresent(notes, normalized, "salino", "salino", "salina", "salado", "salada", "salt", "salty",
                "atun");
        addNoteIfPresent(notes, normalized, "animalico", "animalico", "animalica", "animalic", "almizcle animal",
                "civet", "castoreum", "atun");
        addNoteIfPresent(notes, normalized, "fresco", "fresca", "limpio", "limpia", "marino", "citrico", "citrica",
                "citricos", "jabonoso", "ducha", "acuatico", "acuático", "olor a mar", "sea", "marine",
                "oceanico", "oceánico");
        addNoteIfPresent(notes, normalized, "marino", "olor a mar", "marino", "mar", "sea", "marine", "oceanico",
                "oceánico", "ocean");
        addNoteIfPresent(notes, normalized, "acuatico", "acuatico", "acuático", "acuatica", "acuática", "aquatic",
                "marine", "sea", "oceanico", "oceánico", "olor a mar");
        addNoteIfPresent(notes, normalized, "dulce", "vainilla", "caramelo", "chocolate", "miel", "coco", "fruta",
                "frutas", "frutal", "fresa", "fresas", "gourmand", "azucar", "arroz con leche", "cremoso",
                "cremosa", "lactonico", "lactonica");
        addNoteIfPresent(notes, normalized, "gourmand", "gourmand", "arroz con leche", "postre", "reposteria");
        addNoteIfPresent(notes, normalized, "cremoso", "cremoso", "cremosa", "lactonico", "lactonica",
                "arroz con leche", "leche");
        addNoteIfPresent(notes, normalized, "arroz", "arroz", "arroz con leche", "rice");
        addNoteIfPresent(notes, normalized, "vainilla", "avainillado", "vanilla", "arroz con leche");
        addNoteIfPresent(notes, normalized, "caramelo", "toffee");
        addNoteIfPresent(notes, normalized, "coco", "coconut");
        addNoteIfPresent(notes, normalized, "frutal", "fruta", "frutas", "fruity", "fresa", "fresas", "strawberry",
                "melocoton", "pera", "manzana");
        addNoteIfPresent(notes, normalized, "fresa", "fresas", "strawberry");
        addNoteIfPresent(notes, normalized, "chocolate", "cacao");
        addNoteIfPresent(notes, normalized, "miel", "honey");
        addNoteIfPresent(notes, normalized, "limpio", "limpia", "jabonoso", "ducha", "ropa limpia");
        addNoteIfPresent(notes, normalized, "sexy", "atractivo", "atractiva", "atraer", "atraiga", "acerquen",
                "ligar", "seducir", "seductor", "seductora");
        addNoteIfPresent(notes, normalized, "sensual", "sexy", "seductor", "seductora", "atraer", "atraiga",
                "acerquen", "ligar", "seducir");
        addNoteIfPresent(notes, normalized, "elegante", "formal", "sofisticado", "sofisticada", "abogado",
                "juicio", "juzgado", "oficina", "empresa", "empresario", "reunion", "reunión", "profesional");
        addNoteIfPresent(notes, normalized, "profesional", "abogado", "juicio", "juzgado", "oficina", "empresa",
                "empresario", "reunion", "reunión", "profesional", "corporativo");
        addNoteIfPresent(notes, normalized, "lujoso", "empresario", "lujo", "premium", "exclusivo", "exclusiva");
        addNoteIfPresent(notes, normalized, "juvenil", "joven", "moderno", "moderna");
        addNoteIfPresent(notes, normalized, "oscuro", "oscura", "dark", "nocturno", "nocturna");
        addNoteIfPresent(notes, normalized, "lujoso", "lujosa", "lujo", "premium", "exclusivo", "exclusiva");
        addNoteIfPresent(notes, normalized, "minimalista", "simple", "sencillo", "sencilla", "sobrio", "sobria");
        addNoteIfPresent(notes, normalized, "misterioso", "misteriosa", "enigmatico", "enigmatica");
        addNoteIfPresent(notes, normalized, "calido", "calida", "warm", "acogedor");
        addNoteIfPresent(notes, normalized, "casual", "informal", "diario");
        addNoteIfPresent(notes, normalized, "amaderado", "madera", "cedro", "sandalo", "vetiver",
                "gasolina", "petroleo", "tubo de escape", "escape");
        addNoteIfPresent(notes, normalized, "cuero", "cuero", "leather", "gasolina", "petroleo",
                "tubo de escape");
        addNoteIfPresent(notes, normalized, "ahumado", "humo", "ahumado", "ahumada", "smoky", "smoke",
                "tubo de escape", "escape", "gasolina");
        addNoteIfPresent(notes, normalized, "industrial", "industrial", "gasolina", "petroleo",
                "tubo de escape", "escape", "metalico", "metalica");
        addNoteIfPresent(notes, normalized, "mineral", "mineral", "metalico", "metalica", "asfalto");
        addNoteIfPresent(notes, normalized, "floral", "flores", "jazmin", "rosa", "azahar", "peonia");
        addNoteIfPresent(notes, normalized, "especiado", "pimienta", "canela", "cardamomo", "clavo");
        addNoteIfPresent(notes, normalized, "canela", "canela", "cinnamon", "arroz con leche");
        addNoteIfPresent(notes, normalized, "citrico", "citrico", "citrica", "citricos", "bergamota", "limon",
                "naranja", "mandarina", "pomelo", "grapefruit");
        addNoteIfPresent(notes, normalized, "ambar", "ambar", "amber", "ambroxan");
        addNoteIfPresent(notes, normalized, "almizcle", "almizcle", "musk", "musgo blanco", "white musk");
        addNoteIfPresent(notes, normalized, "oud", "oud", "agarwood");
        addNoteIfPresent(notes, normalized, "tabaco", "tabaco", "tobacco");
        addNoteIfPresent(notes, normalized, "incienso", "incienso", "incense");
        addNoteIfPresent(notes, normalized, "cafe", "cafe", "coffee");
        addNoteIfPresent(notes, normalized, "iris", "iris");
        addNoteIfPresent(notes, normalized, "lavanda", "lavanda", "lavender");
        if (!notes.isEmpty()) {
            profile.setPreferredNotes(mergeValues(profile.getPreferredNotes(), notes));
        }
        normalized = fullNormalized;

        if (containsAny(normalized, "abogado", "juicio", "juzgado", "oficina", "empresa", "empresario", "reunion",
                "reunión", "trabajo", "profesional", "corporativo", "despacho")) {
            profile.setOccasion("trabajo");
        } else if (containsAny(normalized, "diario", "clase", "universidad")) {
            profile.setOccasion("diario");
        } else if (containsAny(normalized, "cita", "fiesta", "evento", "noche")) {
            profile.setOccasion("especial");
        } else if (containsAny(normalized, "me da igual", "cualquiera", "todo")) {
            profile.setOccasion("versatil");
        }

        if (containsAny(normalized, "economico/medio", "económico/medio", "economico medio", "económico medio",
                "medio economico", "medio económico", "calidad precio")) {
            profile.setBudget("economico-medio");
        } else if (containsAny(normalized, "barato", "economico", "económico", "poco", "menos de 50", "50")) {
            profile.setBudget("economico");
        } else if (containsAny(normalized, "presupuesto medio", "gama media", "precio medio", "medio", "normal",
                "100", "150")) {
            profile.setBudget("medio");
        } else if (containsAny(normalized, "caro", "acaro", "premium", "lujo", "da igual el precio")) {
            profile.setBudget("premium");
        }

        String detectedBudget = detectBudget(normalized);
        if (StringUtils.hasText(detectedBudget)) {
            profile.setBudget(detectedBudget);
        } else if (!looksLikeBudgetText(normalized)) {
            profile.setBudget(previousBudget);
        }

        updateDislikedNotesFromMessage(profile, normalized);

        profile.setLastSummary(String.join(" | ", List.of(
                valueOrDash(profile.getGenderTarget()),
                valueOrDash(profile.getSeason()),
                valueOrDash(profile.getIntensity()),
                valueOrDash(profile.getPreferredNotes()),
                valueOrDash(profile.getOccasion()),
                valueOrDash(profile.getBudget()),
                valueOrDash(profile.getDislikedNotes()))));
    }

    /**
     * Devuelve los datos importantes que aun faltan para poder recomendar un
     * perfume. Se mantiene el mismo orden de preguntas que usaba el orquestador.
     *
     * @param profile perfil olfativo actual.
     * @return lista de campos faltantes.
     */
    public List<String> missingProfileFields(PerfumeProfile profile) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(profile.getGenderTarget())) {
            missing.add("si lo quieres para hombre, mujer o unisex");
        }
        if (!StringUtils.hasText(profile.getSeason())) {
            missing.add("la epoca del ano");
        }
        if (!StringUtils.hasText(profile.getPreferredNotes())) {
            missing.add("familia olfativa: fresco, dulce, amaderado, floral o especiado");
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

    /**
     * Construye la busqueda que se envia al catalogo de perfumes a partir del
     * perfil y el ultimo mensaje.
     *
     * @param profile perfil olfativo actual.
     * @param message ultimo mensaje del usuario.
     * @return texto de busqueda para el catalogo.
     */
    public String buildSearchQuery(PerfumeProfile profile, String message) {
        String rawQuery = String.join(" ", List.of(
                translateGender(profile.getGenderTarget()),
                translateSeason(profile.getSeason()),
                translateIntensity(profile.getIntensity()),
                translateOccasion(profile.getOccasion()),
                translateNotes(profile.getPreferredNotes()),
                translateBudget(profile.getBudget()),
                shouldKeepRawMessage(profile) ? valueOrDash(message) : ""));

        return rawQuery.replace("-", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean shouldKeepRawMessage(PerfumeProfile profile) {
        int filled = 0;
        if (StringUtils.hasText(profile.getGenderTarget())) {
            filled++;
        }
        if (StringUtils.hasText(profile.getSeason())) {
            filled++;
        }
        if (StringUtils.hasText(profile.getIntensity())) {
            filled++;
        }
        if (StringUtils.hasText(profile.getPreferredNotes())) {
            filled++;
        }
        if (StringUtils.hasText(profile.getOccasion())) {
            filled++;
        }
        if (StringUtils.hasText(profile.getBudget())) {
            filled++;
        }
        return filled < 3;
    }

    private String detectBudget(String normalizedMessage) {
        String numericBudget = numericBudget(normalizedMessage);
        if (StringUtils.hasText(numericBudget)) {
            return numericBudget;
        }

        if (containsAny(normalizedMessage, "economico/medio", "economico medio", "medio economico",
                "calidad precio", "buena relacion calidad precio", "calidad-precio")) {
            return "economico-medio";
        }
        if (containsAny(normalizedMessage, "no muy caro", "sin ser caro", "precio normal", "normalito")) {
            return "medio";
        }
        if (containsAny(normalizedMessage, "barato", "economico", "low cost", "asequible", "poco presupuesto")) {
            return "economico";
        }
        if (containsAny(normalizedMessage, "presupuesto medio", "gama media", "precio medio", "rango medio",
                "medio")) {
            return "medio";
        }
        if (containsAny(normalizedMessage, "sin limite", "sin limite de precio", "da igual el precio",
                "lo que cueste", "caro", "acaro", "premium", "lujo", "nicho", "exclusivo")) {
            return "premium";
        }
        return "";
    }

    private String numericBudget(String normalizedMessage) {
        if (!looksLikeBudgetText(normalizedMessage)) {
            return "";
        }

        List<Double> values = extractNumbers(normalizedMessage);
        if (values.isEmpty()) {
            return "";
        }

        double reference = values.size() >= 2 && containsAny(normalizedMessage, "entre", "rango", "de ")
                ? values.stream().mapToDouble(Double::doubleValue).average().orElse(values.get(0))
                : values.get(0);

        if (containsAny(normalizedMessage, "menos de", "hasta", "maximo", "max", "tope", "por debajo",
                "no mas de")) {
            reference = values.stream().mapToDouble(Double::doubleValue).max().orElse(reference);
        }

        return budgetFromAmount(reference);
    }

    private boolean looksLikeBudgetText(String normalizedMessage) {
        return containsAny(normalizedMessage, "euro", "eur", "\u20ac", "presupuesto", "precio", "gastar", "cuesta",
                "cueste", "menos de", "hasta", "maximo", "tope", "rango", "barato", "caro");
    }

    private List<Double> extractNumbers(String normalizedMessage) {
        List<Double> values = new ArrayList<>();
        Matcher matcher = PRICE_PATTERN.matcher(normalizedMessage);
        while (matcher.find()) {
            try {
                values.add(Double.parseDouble(matcher.group(1).replace(",", ".")));
            } catch (NumberFormatException ignored) {
                // Ignore isolated non-price numbers.
            }
        }
        return values;
    }

    private String budgetFromAmount(double amount) {
        if (amount <= 60) {
            return "economico";
        }
        if (amount <= 150) {
            return "medio";
        }
        return "premium";
    }

    private String removeAvoidanceClauses(String normalizedMessage) {
        if (!StringUtils.hasText(normalizedMessage)) {
            return "";
        }
        return normalizedMessage
                .replaceAll("\\b(no me gusta(?:n)?|odio|evitar|nada de|sin|que no (?:lleve|tenga|sea)|no (?:lleve|lleva|tenga|sea|quiero))\\b[^,.;]*(?=,|\\.|;|$)",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void updateDislikedNotesFromMessage(PerfumeProfile profile, String normalizedMessage) {
        boolean hasAvoidance = containsAny(normalizedMessage,
                "no me gusta", "odio", "evitar", "sin ", "no lleve", "no lleva", "que no tenga", "no tenga",
                "no sea", "que no sea", "nada de", "no quiero", "no empalagoso", "no pesado");
        if (!hasAvoidance) {
            return;
        }

        String cleaned = dislikedClause(normalizedMessage)
                .replace("no me gusta", " ")
                .replace("odio", " ")
                .replace("evitar", " ")
                .replace("algo que no lleve", " ")
                .replace("que no lleve", " ")
                .replace("no lleve", " ")
                .replace("no lleva", " ")
                .replace("que no tenga", " ")
                .replace("no tenga", " ")
                .replace("que no sea", " ")
                .replace("no sea", " ")
                .replace("nada de", " ")
                .replace("no quiero", " ")
                .replace("sin", " ")
                .replace("perfume", " ")
                .replace("colonia", " ")
                .replace("olor", " ")
                .replace("notas", " ");

        List<String> ignored = List.of("algo", "quiero", "lleve", "lleva", "tenga", "tiene", "sea", "mucho", "mucha",
                "para", "los", "las", "con", "del", "una", "uno", "unos", "unas", "olor", "olores", "perfumes",
                "colonias");
        List<String> disliked = new ArrayList<>();
        for (String token : cleaned.split("[, .;:]+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 3 && !ignored.contains(trimmed)) {
                disliked.add(trimmed);
            }
        }

        if (disliked.isEmpty()) {
            profile.setDislikedNotes(normalizedMessage);
            return;
        }

        List<String> merged = new ArrayList<>();
        if (StringUtils.hasText(profile.getDislikedNotes())) {
            for (String token : profile.getDislikedNotes().split("[, ]+")) {
                if (StringUtils.hasText(token) && !merged.contains(token.trim())) {
                    merged.add(token.trim());
                }
            }
        }
        for (String token : disliked) {
            if (!merged.contains(token)) {
                merged.add(token);
            }
        }
        profile.setDislikedNotes(String.join(", ", merged));
    }

    private String dislikedClause(String normalizedMessage) {
        List<String> markers = List.of("no me gusta", "odio", "evitar", "algo que no lleve", "que no lleve",
                "no lleve", "no lleva", "que no tenga", "no tenga", "que no sea", "no sea", "nada de",
                "no quiero", "sin ", "no ");
        int bestIndex = -1;
        String bestMarker = "";
        for (String marker : markers) {
            int index = normalizedMessage.indexOf(marker);
            if (index >= 0 && (bestIndex < 0 || index < bestIndex)) {
                bestIndex = index;
                bestMarker = marker;
            }
        }
        if (bestIndex < 0) {
            return normalizedMessage;
        }

        String clause = normalizedMessage.substring(bestIndex + bestMarker.length());
        for (String stop : List.of(" pero con ", " pero quiero ", " aunque con ", " y con ", " con ")) {
            int index = clause.indexOf(stop);
            if (index > 0) {
                clause = clause.substring(0, index);
            }
        }
        return clause;
    }

    private boolean impliesMaleWearerFromAttraction(String normalized) {
        if (containsAny(normalized, "para mujer", "para mujeres", "de mujer", "femenino", "femenina")) {
            return false;
        }
        return containsAny(normalized, "mujeres", "chicas", "tias")
                && containsAny(normalized, "acerquen", "se acerquen", "atraer", "atraiga", "gustar", "ligar",
                        "seducir", "conquistar", "se fijen");
    }

    private boolean mentionsFemaleWearer(String normalized) {
        return normalized.equals("mujer")
                || normalized.equals("femenino")
                || normalized.equals("femenina")
                || containsAny(normalized, "para mujer", "para una mujer", "para mujeres", "soy mujer",
                        "femenino", "femenina");
    }

    private void addNoteIfPresent(List<String> notes, String text, String note, String... aliases) {
        if ((containsNoteTerm(text, note) || containsAnyNoteTerm(text, aliases)) && !notes.contains(note)) {
            notes.add(note);
        }
    }

    private boolean containsAnyNoteTerm(String text, String... terms) {
        for (String term : terms) {
            if (containsNoteTerm(text, term)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNoteTerm(String text, String term) {
        if (!StringUtils.hasText(term)) {
            return false;
        }
        if ("sea".equals(term)) {
            return false;
        }
        if (term.length() <= 3 && term.chars().allMatch(Character::isLetterOrDigit)) {
            for (String token : text.split("[^a-z0-9]+")) {
                if (term.equals(token)) {
                    return true;
                }
            }
            return false;
        }
        return text.contains(term);
    }

    private String mergeValues(String existingValues, List<String> newValues) {
        List<String> merged = new ArrayList<>();
        if (StringUtils.hasText(existingValues)) {
            for (String token : existingValues.split(",")) {
                String trimmed = token.trim();
                if (StringUtils.hasText(trimmed) && !merged.contains(trimmed)) {
                    merged.add(trimmed);
                }
            }
        }
        for (String value : newValues) {
            String trimmed = value.trim();
            if (StringUtils.hasText(trimmed) && !merged.contains(trimmed)) {
                merged.add(trimmed);
            }
        }
        return String.join(", ", merged);
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

    private String translateSeason(String season) {
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
            return "versatile all year all seasons";
        }
        return valueOrDash(season);
    }

    private String translateGender(String gender) {
        String normalized = normalize(gender);
        if ("hombre".equals(normalized)) {
            return "men male masculine";
        }
        if ("mujer".equals(normalized)) {
            return "women female feminine";
        }
        if ("unisex".equals(normalized)) {
            return "unisex";
        }
        return valueOrDash(gender);
    }

    private String translateIntensity(String intensity) {
        String normalized = normalize(intensity);
        if ("intenso".equals(normalized)) {
            return "intense long lasting strong";
        }
        if ("suave".equals(normalized)) {
            return "soft light subtle";
        }
        return valueOrDash(intensity);
    }

    private String translateOccasion(String occasion) {
        String normalized = normalize(occasion);
        if ("especial".equals(normalized)) {
            return "night evening date special occasion";
        }
        if ("diario".equals(normalized)) {
            return "daily office casual";
        }
        if ("trabajo".equals(normalized) || "profesional".equals(normalized)) {
            return "work office professional formal business";
        }
        if ("versatil".equals(normalized)) {
            return "versatile all year";
        }
        return valueOrDash(occasion);
    }

    private String translateNotes(String notes) {
        String normalized = normalize(notes);
        List<String> translated = new ArrayList<>();
        if (normalized.contains("floral")) {
            translated.add("floral jasmine rose orange blossom");
        }
        if (normalized.contains("dulce")) {
            translated.add("sweet vanilla caramel tonka amber gourmand honey coconut chocolate fruity");
        }
        if (normalized.contains("gourmand")) {
            translated.add("gourmand dessert sweet vanilla tonka caramel amber");
        }
        if (normalized.contains("cremoso") || normalized.contains("lactonico")) {
            translated.add("creamy lactonic milk vanilla sandalwood musk");
        }
        if (normalized.contains("arroz")) {
            translated.add("rice milk creamy vanilla gourmand");
        }
        if (normalized.contains("canela")) {
            translated.add("cinnamon spicy warm vanilla gourmand");
        }
        if (normalized.contains("amaderado")) {
            translated.add("woody cedar sandalwood vetiver");
        }
        if (normalized.contains("cuero")) {
            translated.add("leather birch tar suede");
        }
        if (normalized.contains("ahumado")) {
            translated.add("smoky smoke incense birch tar vetiver");
        }
        if (normalized.contains("industrial")) {
            translated.add("industrial petrol gasoline metallic mineral leather smoky");
        }
        if (normalized.contains("mineral") || normalized.contains("metalico")) {
            translated.add("mineral metallic vetiver smoke");
        }
        if (normalized.contains("fresco")) {
            translated.add("fresh citrus aquatic clean soapy");
        }
        if (normalized.contains("citrico")) {
            translated.add("citrus bergamot lemon orange grapefruit fresh");
        }
        if (normalized.contains("marino") || normalized.contains("acuatico")) {
            translated.add("marine aquatic ocean sea fresh ozonic");
        }
        if (normalized.contains("salino")) {
            translated.add("salty marine aquatic ocean mineral");
        }
        if (normalized.contains("animalico")) {
            translated.add("animalic musk civet castoreum ambergris salty");
        }
        if (normalized.contains("especiado")) {
            translated.add("spicy cinnamon pepper cardamom");
        }
        if (normalized.contains("vainilla")) {
            translated.add("vanilla");
        }
        if (normalized.contains("caramelo")) {
            translated.add("caramel toffee");
        }
        if (normalized.contains("coco")) {
            translated.add("coconut");
        }
        if (normalized.contains("frutal")) {
            translated.add("fruity pear peach apple strawberry berries");
        }
        if (normalized.contains("fresa")) {
            translated.add("strawberry red fruits berries fruity sweet");
        }
        if (normalized.contains("chocolate")) {
            translated.add("chocolate cacao");
        }
        if (normalized.contains("miel")) {
            translated.add("honey");
        }
        if (normalized.contains("limpio")) {
            translated.add("clean fresh soapy musk");
        }
        if (normalized.contains("sexy")) {
            translated.add("sexy sensual amber musk vanilla night");
        }
        if (normalized.contains("sensual")) {
            translated.add("sensual seductive amber musk vanilla skin");
        }
        if (normalized.contains("elegante")) {
            translated.add("elegant sophisticated iris musk woods");
        }
        if (normalized.contains("juvenil")) {
            translated.add("young modern fresh fruity");
        }
        if (normalized.contains("oscuro")) {
            translated.add("dark smoky incense oud patchouli leather amber night");
        }
        if (normalized.contains("lujoso")) {
            translated.add("luxury elegant premium iris oud saffron leather amber");
        }
        if (normalized.contains("profesional")) {
            translated.add("professional elegant formal clean office business");
        }
        if (normalized.contains("minimalista")) {
            translated.add("minimal clean musk skin soft tea transparent");
        }
        if (normalized.contains("misterioso")) {
            translated.add("mysterious dark smoky incense oud patchouli amber night");
        }
        if (normalized.contains("calido")) {
            translated.add("warm amber vanilla spicy");
        }
        if (normalized.contains("casual")) {
            translated.add("casual daily clean fresh");
        }
        if (normalized.contains("ambar")) {
            translated.add("amber warm resinous vanilla labdanum");
        }
        if (normalized.contains("almizcle")) {
            translated.add("musk clean skin white musk");
        }
        if (normalized.contains("oud")) {
            translated.add("oud agarwood woody dark smoky");
        }
        if (normalized.contains("tabaco")) {
            translated.add("tobacco honey vanilla warm spicy");
        }
        if (normalized.contains("incienso")) {
            translated.add("incense smoky resinous dark");
        }
        if (normalized.contains("cafe")) {
            translated.add("coffee gourmand vanilla tonka");
        }
        if (normalized.contains("iris")) {
            translated.add("iris powdery elegant floral");
        }
        if (normalized.contains("lavanda")) {
            translated.add("lavender aromatic clean fresh");
        }
        return translated.isEmpty() ? valueOrDash(notes) : String.join(" ", translated);
    }

    private String translateBudget(String budget) {
        String normalized = normalize(budget);
        if ("economico".equals(normalized)) {
            return "affordable cheap";
        }
        if ("medio".equals(normalized) || "economico-medio".equals(normalized)) {
            return "affordable mid range";
        }
        if ("premium".equals(normalized)) {
            return "premium luxury";
        }
        return valueOrDash(budget);
    }
}
