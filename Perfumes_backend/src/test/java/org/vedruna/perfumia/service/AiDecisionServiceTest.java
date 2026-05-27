package org.vedruna.perfumia.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;

class AiDecisionServiceTest {

    private AiDecisionService aiDecisionService;

    @BeforeEach
    void setUp() {
        aiDecisionService = new AiDecisionService();
    }

    @Test
    void detectsAcceptanceWithAcepto() {
        assertThat(aiDecisionService.isAcceptance("acepto")).isTrue();
    }

    @Test
    void detectsAcceptanceWithMeGusta() {
        assertThat(aiDecisionService.isAcceptance("me gusta")).isTrue();
    }

    @Test
    void detectsRejectionWithNoMeConvence() {
        assertThat(aiDecisionService.isRejection("no me convence")).isTrue();
    }

    @Test
    void detectsRejectionWithOtro() {
        assertThat(aiDecisionService.isRejection("otro")).isTrue();
    }

    @Test
    void detectsGreetingWithHola() {
        assertThat(aiDecisionService.isGreeting("hola")).isTrue();
    }

    @Test
    void detectsGreetingWithBuenas() {
        assertThat(aiDecisionService.isGreeting("buenas")).isTrue();
    }

    @Test
    void detectsAnotherPerfumeRequest() {
        assertThat(aiDecisionService.wantsAnotherPerfume("quiero otro perfume")).isTrue();
    }

    @Test
    void detectsRecommendationRequestWithAccents() {
        assertThat(aiDecisionService.wantsSearchNow("que me recomiendas")).isTrue();
        assertThat(aiDecisionService.wantsSearchNow("recomiendame")).isTrue();
    }

    @Test
    void doesNotDetectAcceptanceInNeutralText() {
        assertThat(aiDecisionService.isAcceptance("busco algo fresco para verano")).isFalse();
    }

    @Test
    void doesNotDetectRejectionInNeutralText() {
        assertThat(aiDecisionService.isRejection("busco algo fresco para verano")).isFalse();
    }

    @Test
    void updatesProfileWithMasculineGender() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero un perfume para hombre");

        assertThat(profile.getGenderTarget()).isEqualTo("hombre");
    }

    @Test
    void updatesProfileWithFeminineGender() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "busco algo para mujer");

        assertThat(profile.getGenderTarget()).isEqualTo("mujer");
    }

    @Test
    void updatesProfileWithUnisexGender() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo unisex");

        assertThat(profile.getGenderTarget()).isEqualTo("unisex");
    }

    @Test
    void updatesProfileWithSummerSeason() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "lo quiero para verano");

        assertThat(profile.getSeason()).isEqualTo("verano");
    }

    @Test
    void updatesProfileWithWinterSeason() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "lo quiero para invierno");

        assertThat(profile.getSeason()).isEqualTo("invierno");
    }

    @Test
    void updatesProfileWithStrongIntensity() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo potente y duradero");

        assertThat(profile.getIntensity()).isEqualTo("intenso");
    }

    @Test
    void updatesProfileWithStrongIntensityFromCommonTypos() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero potecia y que dure varias horas");

        assertThat(profile.getIntensity()).isEqualTo("intenso");
    }

    @Test
    void updatesProfileWithSoftIntensity() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo suave y discreto");

        assertThat(profile.getIntensity()).isEqualTo("suave");
    }

    @Test
    void updatesProfileWithSpecialOccasion() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "lo quiero para una fiesta de noche");

        assertThat(profile.getOccasion()).isEqualTo("especial");
    }

    @Test
    void updatesProfileWithDailyOccasion() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "lo quiero para diario y clase");

        assertThat(profile.getOccasion()).isEqualTo("diario");
    }

    @Test
    void updatesProfileWithEconomicBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "busco algo barato y economico");

        assertThat(profile.getBudget()).isEqualTo("economico");
    }

    @Test
    void updatesProfileWithMediumBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "busco algo de precio medio");

        assertThat(profile.getBudget()).isEqualTo("medio");
    }

    @Test
    void updatesProfileWithNumericMediumBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero gastar menos de 80 euros");

        assertThat(profile.getBudget()).isEqualTo("medio");
    }

    @Test
    void updatesProfileWithNumericEconomicBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "mi presupuesto maximo son 45 euros");

        assertThat(profile.getBudget()).isEqualTo("economico");
    }

    @Test
    void isolatedNumbersDoNotBecomeBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "tengo 50 anos y quiero algo fresco");

        assertThat(profile.getBudget()).isNull();
        assertThat(profile.getPreferredNotes()).contains("fresco");
    }

    @Test
    void updatesProfileWithPremiumBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo premium de lujo");

        assertThat(profile.getBudget()).isEqualTo("premium");
    }

    @Test
    void updatesProfileWithPremiumBudgetFromCommonTypo() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero que sea acaro y con presencia");

        assertThat(profile.getBudget()).isEqualTo("premium");
        assertThat(profile.getIntensity()).isEqualTo("intenso");
    }

    @Test
    void updatesProfileWithVanillaSweetNote() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "me gusta la vainilla y lo dulce");

        assertThat(profile.getPreferredNotes()).contains("dulce");
        assertThat(profile.getPreferredNotes()).contains("vainilla");
    }

    @Test
    void arrozConLecheBuildsGourmandProfile() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero un perfume olor a arroz con leche");

        assertThat(profile.getPreferredNotes())
                .contains("dulce")
                .contains("gourmand")
                .contains("cremoso")
                .contains("arroz")
                .contains("vainilla")
                .contains("canela");
    }

    @Test
    void updatesProfileWithSpecificSweetNotes() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "me gusta el coco, caramelo, chocolate y miel");

        assertThat(profile.getPreferredNotes())
                .contains("dulce")
                .contains("coco")
                .contains("caramelo")
                .contains("chocolate")
                .contains("miel");
    }

    @Test
    void updatesProfileWithStrawberryNoteFromRichMessage() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile,
                "quiero un perfume para un noche de verano para mujer que huela a fresas y que sea barato y suave y discreto");

        assertThat(profile.getGenderTarget()).isEqualTo("mujer");
        assertThat(profile.getSeason()).isEqualTo("verano");
        assertThat(profile.getIntensity()).isEqualTo("suave");
        assertThat(profile.getOccasion()).isEqualTo("especial");
        assertThat(profile.getBudget()).isEqualTo("economico");
        assertThat(profile.getPreferredNotes())
                .contains("dulce")
                .contains("frutal")
                .contains("fresa");
        assertThat(aiDecisionService.missingProfileFields(profile)).isEmpty();
    }

    @Test
    void allSeasonsDoesNotLeaveSeasonPending() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "lo quiero para todas las estaciones y todo el año");

        assertThat(profile.getSeason()).isEqualTo("versatil");
        assertThat(aiDecisionService.missingProfileFields(profile)).doesNotContain("la epoca del ano");
    }

    @Test
    void lawyerAndTrialsDetectProfessionalOccasion() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "soy abogado y voy a juicios en el juzgado");

        assertThat(profile.getGenderTarget()).isEqualTo("hombre");
        assertThat(profile.getOccasion()).isEqualTo("trabajo");
        assertThat(profile.getPreferredNotes())
                .contains("elegante")
                .contains("profesional");
        assertThat(aiDecisionService.missingProfileFields(profile))
                .doesNotContain("ocasion: diario, trabajo, cita, noche o versatil");
    }

    @Test
    void femaleLawyerDetectsFeminineGender() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "soy abogada y quiero presencia en un juicio");

        assertThat(profile.getGenderTarget()).isEqualTo("mujer");
        assertThat(profile.getOccasion()).isEqualTo("trabajo");
        assertThat(profile.getIntensity()).isEqualTo("intenso");
    }

    @Test
    void attractionTowardWomenDoesNotMeanFemininePerfume() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile,
                "quiero un perfume olor a arroz con leche con el que todas las mujeres se acerquen a mi");

        assertThat(profile.getGenderTarget()).isEqualTo("hombre");
        assertThat(profile.getPreferredNotes())
                .contains("dulce")
                .contains("gourmand")
                .contains("sensual");
    }

    @Test
    void seaSmellAddsMarineAndAquaticNotes() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero olor a mar, marino y acuático");

        assertThat(profile.getPreferredNotes())
                .contains("fresco")
                .contains("marino")
                .contains("acuatico");
    }

    @Test
    void mediumBudgetPhrasesDetectMediumBudget() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "tengo presupuesto medio, algo de gama media");

        assertThat(profile.getBudget()).isEqualTo("medio");
        assertThat(aiDecisionService.missingProfileFields(profile))
                .doesNotContain("presupuesto: economico, medio o premium");
    }

    @Test
    void strongPresenceDetectsIntenseIntensity() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo potente con presencia y que se note");

        assertThat(profile.getIntensity()).isEqualTo("intenso");
        assertThat(aiDecisionService.missingProfileFields(profile)).doesNotContain("intensidad: suave o potente");
    }

    @Test
    void updatesProfileWithVibeNotesWithoutDatabaseFields() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo limpio, elegante y sexy");

        assertThat(profile.getPreferredNotes())
                .contains("limpio")
                .contains("elegante")
                .contains("sexy");
    }

    @Test
    void updatesProfileWithElegantMood() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero un perfume elegante y sofisticado");

        assertThat(profile.getPreferredNotes()).contains("elegante");
    }

    @Test
    void updatesProfileWithDarkMood() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "busco algo oscuro y nocturno");

        assertThat(profile.getPreferredNotes()).contains("oscuro");
    }

    @Test
    void updatesProfileWithLuxuryMood() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo lujoso y exclusivo");

        assertThat(profile.getPreferredNotes()).contains("lujoso");
    }

    @Test
    void updatesProfileWithFreshCitrusNote() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo fresco y citrico");

        assertThat(profile.getPreferredNotes()).contains("fresco");
    }

    @Test
    void updatesProfileWithAccentedSpanishText() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile,
                "quiero algo acu\u00e1tico para reuni\u00f3n, con presupuesto econ\u00f3mico");

        assertThat(profile.getPreferredNotes()).contains("acuatico");
        assertThat(profile.getOccasion()).isEqualTo("trabajo");
        assertThat(profile.getBudget()).isEqualTo("economico");
    }

    @Test
    void updatesProfileWithWoodyNote() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "me gustan los olores a madera");

        assertThat(profile.getPreferredNotes()).contains("amaderado");
    }

    @Test
    void spanishSeaVerbDoesNotTriggerMarineNotes() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero que sea para hombre");

        assertThat(profile.getGenderTarget()).isEqualTo("hombre");
        assertThat(profile.getPreferredNotes()).isNull();
    }

    @Test
    void updatesProfileWithDislikedVanillaNote() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "no me gusta la vainilla");

        assertThat(profile.getDislikedNotes()).contains("vainilla");
        assertThat(profile.getPreferredNotes() == null ? "" : profile.getPreferredNotes()).doesNotContain("vainilla");
    }

    @Test
    void sweetButNotCloyingKeepsPreferenceAndRejectionSeparate() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "quiero algo dulce pero no empalagoso");

        assertThat(profile.getPreferredNotes()).contains("dulce");
        assertThat(profile.getDislikedNotes()).contains("empalagoso");
    }

    @Test
    void updatesProfileWithModernSpecificNotes() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "me gusta el ambar, almizcle, oud, tabaco e incienso");

        assertThat(profile.getPreferredNotes())
                .contains("ambar")
                .contains("almizcle")
                .contains("oud")
                .contains("tabaco")
                .contains("incienso");
    }

    @Test
    void updatesProfileWithDislikedSweetPerfumes() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "odio los perfumes dulces");

        assertThat(profile.getDislikedNotes()).contains("dulces");
    }

    @Test
    void updatesProfileWithAdvancedDislikedNotes() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "no me gusta la rosa, el incienso, el pachuli ni el oud");

        assertThat(profile.getDislikedNotes())
                .contains("rosa")
                .contains("incienso")
                .contains("pachuli")
                .contains("oud");
    }

    @Test
    void updatesLastSummaryWithSeveralPreferences() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile,
                "quiero un perfume unisex para invierno, potente, dulce y premium");

        assertThat(profile.getLastSummary())
                .contains("unisex")
                .contains("invierno")
                .contains("intenso")
                .contains("dulce")
                .contains("premium");
    }

    @Test
    void updatesLastSummaryWhenNewPreferenceArrives() {
        PerfumeProfile profile = new PerfumeProfile();
        aiDecisionService.updateProfileFromMessage(profile, "quiero algo fresco para verano");

        aiDecisionService.updateProfileFromMessage(profile, "mejor que sea potente");

        assertThat(profile.getLastSummary())
                .contains("verano")
                .contains("fresco")
                .contains("intenso");
    }

    @Test
    void keepsLastSummarySafeWhenMessageHasNoPreferences() {
        PerfumeProfile profile = new PerfumeProfile();

        aiDecisionService.updateProfileFromMessage(profile, "hola, no tengo claro que quiero");

        assertThat(profile.getLastSummary()).isNotNull();
        assertThat(profile.getLastSummary()).contains("-");
    }

    @Test
    void missingProfileFieldsReturnsRequiredFieldsForEmptyProfile() {
        PerfumeProfile profile = new PerfumeProfile();

        assertThat(aiDecisionService.missingProfileFields(profile))
                .containsExactly(
                        "si lo quieres para hombre, mujer o unisex",
                        "la epoca del ano",
                        "familia olfativa: fresco, dulce, amaderado, floral o especiado",
                        "intensidad: suave o potente",
                        "ocasion: diario, trabajo, cita, noche o versatil",
                        "presupuesto: economico, medio o premium");
    }

    @Test
    void missingProfileFieldsReturnsEmptyListForCompleteProfile() {
        PerfumeProfile profile = completeProfile();

        assertThat(aiDecisionService.missingProfileFields(profile)).isEmpty();
    }

    @Test
    void missingProfileFieldsReturnsBudgetWhenOnlyBudgetIsMissing() {
        PerfumeProfile profile = completeProfile();
        profile.setBudget(null);

        assertThat(aiDecisionService.missingProfileFields(profile))
                .containsExactly("presupuesto: economico, medio o premium");
    }

    @Test
    void buildSearchQueryWithCompleteProfileCreatesCoherentSearch() {
        PerfumeProfile profile = completeProfile();

        String query = aiDecisionService.buildSearchQuery(profile, "quiero algo elegante");

        assertThat(query)
                .contains("unisex")
                .contains("winter")
                .contains("intense")
                .contains("night")
                .contains("sweet")
                .contains("premium")
                .doesNotContain("quiero algo elegante");
    }

    @Test
    void buildSearchQueryIncludesAvailableProfileValues() {
        PerfumeProfile profile = completeProfile();
        profile.setSeason("verano");
        profile.setIntensity("suave");
        profile.setOccasion("diario");
        profile.setPreferredNotes("fresco");
        profile.setBudget("medio");

        String query = aiDecisionService.buildSearchQuery(profile, "para clase");

        assertThat(query)
                .contains("summer")
                .contains("soft")
                .contains("daily")
                .contains("fresh")
                .contains("mid range");
    }

    @Test
    void buildSearchQueryWithPartialProfileDoesNotBreak() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("amaderado");

        String query = aiDecisionService.buildSearchQuery(profile, "algo para empezar");

        assertThat(query)
                .isNotBlank()
                .contains("woody")
                .contains("algo para empezar");
    }

    @Test
    void buildSearchQueryWithCompleteProfileUsesCleanTranslatedTerms() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setSeason("verano");
        profile.setIntensity("intenso");
        profile.setPreferredNotes("amaderado");
        profile.setOccasion("especial");
        profile.setBudget("economico");

        String query = aiDecisionService.buildSearchQuery(profile,
                "quiero un perfume para una noche de verano para salir de fiesta y que sea potente y barato y para hombre");

        assertThat(query)
                .contains("men")
                .contains("summer")
                .contains("strong")
                .contains("night")
                .contains("woody")
                .contains("affordable")
                .doesNotContain("quiero un perfume");
    }

    private PerfumeProfile completeProfile() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("unisex");
        profile.setSeason("invierno");
        profile.setPreferredNotes("dulce");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");
        profile.setBudget("premium");
        return profile;
    }
}
