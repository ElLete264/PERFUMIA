package org.vedruna.perfumia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;
import org.vedruna.perfumia.persistance.model.PerfumeRecommendation;
import org.vedruna.perfumia.service.dto.PerfumeItem;

class PerfumeScoringServiceTest {

    private PerfumeScoringService perfumeScoringService;

    @BeforeEach
    void setUp() {
        perfumeScoringService = new PerfumeScoringService();
    }

    @Test
    void scoreAddsPointsWhenPerfumeMatchesWinter() {
        PerfumeProfile profile = profileWithSeason("invierno");
        PerfumeItem item = perfume("Amber Night", "warm amber vanilla musk", "winter");

        int score = perfumeScoringService.score(item, profile, "busco algo para frio");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsWhenPerfumeMatchesSummer() {
        PerfumeProfile profile = profileWithSeason("verano");
        PerfumeItem item = perfume("Fresh Citrus", "fresh citrus aquatic clean", "summer");

        int score = perfumeScoringService.score(item, profile, "busco algo para calor");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsWhenIntensityIsStrong() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setIntensity("intenso");
        PerfumeItem item = perfume("Deep Amber", "intense amber musk vanilla", "winter");

        int score = perfumeScoringService.score(item, profile, "quiero algo potente");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsWhenIntensityIsSoft() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setIntensity("suave");
        PerfumeItem item = perfume("Clean Skin", "soft light fresh clean", "spring");

        int score = perfumeScoringService.score(item, profile, "quiero algo discreto");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsWhenOccasionIsSpecialNight() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setOccasion("especial");
        PerfumeItem item = perfume("Evening Date", "night evening date amber musk", "winter");

        int score = perfumeScoringService.score(item, profile, "para noche o fiesta");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsWhenOccasionIsDaily() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setOccasion("diario");
        PerfumeItem item = perfume("Office Fresh", "daily office clean fresh", "spring");

        int score = perfumeScoringService.score(item, profile, "para clase o trabajo");

        assertThat(score).isPositive();
    }

    @Test
    void containsDislikedNoteReturnsTrueWhenPerfumeContainsRejectedNote() {
        PerfumeItem item = perfume("Vanilla Smoke", "vanilla amber woods", "winter");

        boolean result = perfumeScoringService.containsDislikedNote(item, "vainilla, vanilla");

        assertThat(result).isTrue();
    }

    @Test
    void containsDislikedNoteReturnsFalseWhenPerfumeDoesNotContainRejectedNotes() {
        PerfumeItem item = perfume("Ocean Clean", "marine citrus fresh", "summer");

        boolean result = perfumeScoringService.containsDislikedNote(item, "vainilla");

        assertThat(result).isFalse();
    }

    @Test
    void containsDislikedNoteDetectsSpanishOudAndPatchouliAgainstEnglishNotes() {
        PerfumeItem item = perfume("Dark Woods", "oud incense patchouli amber", "winter");

        boolean result = perfumeScoringService.containsDislikedNote(item, "oud, incienso, pachuli");

        assertThat(result).isTrue();
    }

    @Test
    void scoreAddsPointsForSpecificSweetNotes() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("dulce, coco, caramelo");
        PerfumeItem item = perfume("Coconut Caramel", "sweet coconut caramel vanilla", "summer");

        int score = perfumeScoringService.score(item, profile, "busco algo dulce");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsForModernSpecificNotes() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("ambar, almizcle, oud, tabaco, incienso");
        PerfumeItem item = perfume("Amber Smoke", "amber musk oud tobacco incense", "winter");

        int score = perfumeScoringService.score(item, profile, "quiero algo ambarado con oud");

        assertThat(score).isPositive();
    }

    @Test
    void budgetScoreParsesPriceRangesCoherently() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setBudget("premium");
        PerfumeItem premiumRange = perfume("Niche Amber", "amber oud", "winter",
                "Maison Test", "unisex", "Long Lasting", "Strong", "120-250 euros", "premium");
        PerfumeItem cheap = perfume("Cheap Fresh", "fresh citrus", "summer",
                "Maison Test", "unisex", "Moderate", "Moderate", "35 euros", "good_value");

        assertThat(perfumeScoringService.score(premiumRange, profile, "quiero algo premium"))
                .isGreaterThan(perfumeScoringService.score(cheap, profile, "quiero algo premium"));
    }

    @Test
    void chooseTopPerfumesFiltersPremiumBudgetBeforeSorting() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setBudget("premium");
        profile.setPreferredNotes("dulce, frutal, fresa");
        profile.setOccasion("especial");

        PerfumeItem designerMen = perfume("Bleu de Chanel", "citrus incense cedar sandalwood", "winter",
                "Chanel", "men", "Long Lasting", "Moderate", "90-140 euros", "mid_range");
        PerfumeItem designerWomen = perfume("Good Girl", "jasmine tonka cacao almond", "winter",
                "Carolina Herrera", "women", "Long Lasting", "Strong", "70-140 euros", "mid_range");
        PerfumeItem premiumUnisex = perfume("Mukhallat", "strawberry vanilla almond musk", "winter",
                "Montale", "unisex", "Long Lasting", "Strong", "120-180 euros", "premium");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(designerMen, designerWomen, premiumUnisex), profile,
                "hombre fresa noche premium", 3);

        assertThat(result).containsExactly(premiumUnisex);
    }

    @Test
    void chooseTopPerfumesKeepsMediumBudgetAwayFromPremiumWhenMediumExists() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setBudget("medio");
        profile.setPreferredNotes("amaderado");

        PerfumeItem premium = perfume("Royal Oud", "woody oud amber", "winter",
                "Creed", "unisex", "Long Lasting", "Strong", "250 euros", "premium");
        PerfumeItem medium = perfume("Terre d'Hermes", "orange vetiver cedar", "winter",
                "Hermes", "men", "Long Lasting", "Moderate", "80-120 euros", "mid_range");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(premium, medium), profile, "presupuesto medio amaderado", 3);

        assertThat(result).containsExactly(medium);
    }

    @Test
    void chooseTopPerfumesCompletesMediumBudgetWithGoodValueWhenOnlyTwoMediumOptionsExist() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setSeason("verano");
        profile.setOccasion("trabajo");
        profile.setBudget("medio");
        profile.setPreferredNotes("fresco, citrico");

        PerfumeItem mediumCitrus = perfume("Office Citrus", "fresh citrus bergamot clean office", "summer",
                "Dior", "men", "Moderate", "Moderate", "85 euros", "mid_range");
        PerfumeItem mediumAquatic = perfume("Aqua Homme", "aquatic citrus fresh musk professional", "summer",
                "Giorgio Armani", "men", "Moderate", "Moderate", "95 euros", "okay");
        PerfumeItem goodValueFresh = perfume("Good Value Fresh", "fresh aquatic citrus clean daily", "summer",
                "Nautica", "men", "Moderate", "Moderate", "35 euros", "good_value");
        PerfumeItem premiumFresh = perfume("Royal Summer", "fresh citrus musk summer", "summer",
                "Creed", "men", "Long Lasting", "Moderate", "220 euros", "premium");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(mediumCitrus, mediumAquatic, goodValueFresh, premiumFresh), profile,
                "verano fresco hombre presupuesto medio trabajo", 3);

        assertThat(result)
                .hasSize(3)
                .contains(mediumCitrus, mediumAquatic, goodValueFresh)
                .doesNotContain(premiumFresh);
    }

    @Test
    void chooseTopPerfumesDiversifiesBrandsWhenCompatibleAlternativesExist() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setSeason("versatil");
        profile.setOccasion("diario");
        profile.setBudget("medio");
        profile.setPreferredNotes("amaderado");

        PerfumeItem cleanVetiver = perfume("Clean White Vetiver", "woody vetiver cedar clean daily all year",
                "all year", "Clean", "men", "Moderate", "Moderate", "80 euros", "good_value");
        PerfumeItem cleanCedar = perfume("Clean Cedar", "woody cedar sandalwood clean everyday", "all year",
                "Clean", "men", "Moderate", "Moderate", "82 euros", "okay");
        PerfumeItem cleanMoss = perfume("Clean Moss", "woody moss vetiver clean daily", "all year",
                "Clean", "men", "Moderate", "Moderate", "78 euros", "okay");
        PerfumeItem terre = perfume("Terre d'Hermes", "woody vetiver orange cedar daily signature", "all year",
                "Hermes", "men", "Moderate", "Moderate", "95 euros", "mid_range");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(cleanVetiver, cleanCedar, cleanMoss, terre), profile,
                "hombre diario todas las estaciones amaderado precio medio", 3);

        assertThat(result)
                .hasSize(3)
                .contains(terre);
        assertThat(result.stream().filter(item -> "Clean".equals(item.getBrand())).count())
                .isLessThanOrEqualTo(2);
    }

    @Test
    void chooseTopPerfumesFiltersFragellaCatalogByPremiumBudget() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setBudget("premium");
        profile.setPreferredNotes("dulce, frutal, fresa");
        profile.setOccasion("especial");

        PerfumeItem fragellaDesigner = PerfumeItem.builder()
                .name("Designer Sweet")
                .brand("Chanel")
                .description("sweet designer fragrance")
                .notes("tonka, citrus, woods")
                .season("winter")
                .source("fragella")
                .gender("men")
                .price("95-140 euros")
                .priceValue("mid_range")
                .longevity("Long Lasting")
                .sillage("Moderate")
                .build();
        PerfumeItem fragellaPremium = PerfumeItem.builder()
                .name("Premium Strawberry Musk")
                .brand("Montale")
                .description("strawberry vanilla musk niche fragrance")
                .notes("strawberry, vanilla, musk, almond")
                .season("winter")
                .source("fragella")
                .gender("unisex")
                .price("140-190 euros")
                .priceValue("premium")
                .longevity("Long Lasting")
                .sillage("Strong")
                .build();

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(fragellaDesigner, fragellaPremium), profile,
                "hombre fresa noche premium", 3);

        assertThat(result).containsExactly(fragellaPremium);
    }

    @Test
    void scoreAddsPointsForCleanElegantVibe() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("limpio, elegante");
        PerfumeItem item = perfume("Clean Iris", "clean soapy musk iris elegant", "spring");

        int score = perfumeScoringService.score(item, profile, "quiero algo elegante");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsForElegantMood() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("elegante");
        PerfumeItem item = perfume("Elegant Iris", "sophisticated iris musk woody", "spring");

        int score = perfumeScoringService.score(item, profile, "quiero algo elegante");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsForCleanMood() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("limpio");
        PerfumeItem item = perfume("White Shirt", "clean soapy musk cotton fresh", "spring");

        int score = perfumeScoringService.score(item, profile, "quiero algo limpio");

        assertThat(score).isPositive();
    }

    @Test
    void scoreAddsPointsForMysteriousMood() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("misterioso");
        PerfumeItem item = perfume("Midnight Mystery", "dark smoky incense oud amber night", "winter");

        int score = perfumeScoringService.score(item, profile, "quiero algo misterioso");

        assertThat(score).isPositive();
    }

    @Test
    void scoreDoesNotBreakWithNullOrEmptyFields() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem item = PerfumeItem.builder()
                .name("")
                .brand(null)
                .description("")
                .notes(null)
                .season("")
                .build();

        int score = perfumeScoringService.score(item, profile, "");

        assertThat(score).isZero();
    }

    @Test
    void choosePerfumeReturnsNullWhenCatalogIsEmpty() {
        PerfumeItem result = perfumeScoringService.choosePerfume(List.of(), new PerfumeProfile(), "busco algo");

        assertThat(result).isNull();
    }

    @Test
    void choosePerfumeReturnsBestScoredPerfumeForProfile() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("invierno");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");

        PerfumeItem lowScore = perfume("Fresh Day", "fresh citrus clean", "summer");
        PerfumeItem bestScore = perfume("Amber Night", "winter night intense amber vanilla musk", "winter");

        PerfumeItem result = perfumeScoringService.choosePerfume(List.of(lowScore, bestScore), profile,
                "quiero algo para una noche especial");

        assertThat(result).isEqualTo(bestScore);
    }

    @Test
    void choosePerfumeReturnsNullWhenCatalogIsNull() {
        PerfumeItem result = perfumeScoringService.choosePerfume(null, new PerfumeProfile(), "busco algo");

        assertThat(result).isNull();
    }

    @Test
    void chooseTopPerfumesReturnsEmptyListWhenCatalogIsNull() {
        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(null, new PerfumeProfile(), "busco algo", 3);

        assertThat(result).isEmpty();
    }

    @Test
    void chooseTopPerfumesReturnsEmptyListWhenCatalogIsEmpty() {
        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(List.of(), new PerfumeProfile(),
                "busco algo", 3);

        assertThat(result).isEmpty();
    }

    @Test
    void chooseTopPerfumesReturnsEmptyListWhenLimitIsNotPositive() {
        PerfumeItem item = perfume("Fresh Day", "fresh citrus clean", "summer");

        assertThat(perfumeScoringService.chooseTopPerfumes(List.of(item), new PerfumeProfile(), "busco algo", 0))
                .isEmpty();
        assertThat(perfumeScoringService.chooseTopPerfumes(List.of(item), new PerfumeProfile(), "busco algo", -1))
                .isEmpty();
    }

    @Test
    void chooseTopPerfumesReturnsAtMostThreePerfumes() {
        PerfumeProfile profile = new PerfumeProfile();
        List<PerfumeItem> catalog = List.of(
                perfume("One", "fresh clean", "spring"),
                perfume("Two", "amber vanilla", "winter"),
                perfume("Three", "citrus aquatic", "summer"),
                perfume("Four", "rose musk", "spring"));

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(catalog, profile, "busco algo", 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void chooseTopPerfumesSortsByScoreDescending() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("invierno");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");

        PerfumeItem lowScore = perfume("Summer Fresh", "fresh citrus clean", "summer");
        PerfumeItem mediumScore = perfume("Soft Winter", "winter soft amber", "winter");
        PerfumeItem bestScore = perfume("Amber Night", "winter night intense amber vanilla musk", "winter");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(lowScore, bestScore, mediumScore), profile, "quiero algo para noche especial", 3);

        assertThat(result).containsExactly(bestScore, mediumScore);
    }

    @Test
    void chooseTopPerfumesPrefersSummerOverSpringWhenUserAsksForSummer() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("verano");
        profile.setPreferredNotes("especiado");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");

        PerfumeItem springSpicy = perfume("Spring Spice", "cardamom pepper fresh", "spring");
        PerfumeItem summerSpicy = perfume("Summer Spice", "summer cardamom pepper aquatic", "summer");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(springSpicy, summerSpicy), profile, "quiero algo unisex para verano especiado de noche", 2);

        assertThat(result).containsExactly(summerSpicy);
    }

    @Test
    void chooseTopPerfumesFiltersWinterAndFallWhenUserAsksForSummer() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("verano");
        profile.setPreferredNotes("dulce");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");
        profile.setBudget("economico");

        PerfumeItem winterSweet = perfume("Vanilla & Caramel", "caramel rose vanilla tonka bean", "winter");
        PerfumeItem fallSweet = perfume("Passion Fruit", "toffee guaiac wood spruce", "fall");
        PerfumeItem summerOption = perfume("Summer Coconut", "coconut citrus clean", "summer");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(winterSweet, fallSweet, summerOption), profile,
                "quiero unisex verano dulce noche economico", 3);

        assertThat(result).containsExactly(summerOption);
    }

    @Test
    void chooseTopPerfumesPrefersWoodyStrongMenOverWeakTropicalUnisex() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setSeason("verano");
        profile.setPreferredNotes("amaderado");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");
        profile.setBudget("economico");

        PerfumeItem weakTropical = perfume("Pink Jungle", "coconut guava mango passionfruit pineapple", "summer",
                "Fresh Line", "unisex", "Weak", "Soft", "42.00", "okay");
        PerfumeItem woodyMen = perfume("Cedar Night", "woody cedar vetiver citrus", "summer",
                "Maison Test", "men", "Long Lasting", "Strong", "55.00", "good_value");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(weakTropical, woodyMen), profile,
                "quiero un perfume para una noche de verano para salir de fiesta potente barato hombre amaderado", 2);

        assertThat(result).containsExactly(woodyMen, weakTropical);
    }

    @Test
    void scoreStrongRequestPenalizesWeakAndSoftPerfume() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setIntensity("intenso");

        PerfumeItem weak = perfume("Soft Projection", "summer citrus", "summer",
                "Test", "unisex", "Weak", "Soft", "40.00", "okay");
        PerfumeItem strong = perfume("Power Woods", "cedar amber", "summer",
                "Test", "unisex", "Long Lasting", "Strong", "40.00", "okay");

        assertThat(perfumeScoringService.score(strong, profile, "quiero algo potente"))
                .isGreaterThan(perfumeScoringService.score(weak, profile, "quiero algo potente"));
    }

    @Test
    void scoreMaleRequestPrefersMenOverUnisexAndPenalizesWomen() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");

        PerfumeItem men = perfume("Men Woods", "cedar", "summer", "Test", "men", "", "", "", "");
        PerfumeItem unisex = perfume("Unisex Woods", "cedar", "summer", "Test", "unisex", "", "", "", "");
        PerfumeItem women = perfume("Women Woods", "cedar", "summer", "Test", "women", "", "", "", "");

        assertThat(perfumeScoringService.score(men, profile, "para hombre"))
                .isGreaterThan(perfumeScoringService.score(unisex, profile, "para hombre"));
        assertThat(perfumeScoringService.score(unisex, profile, "para hombre"))
                .isGreaterThan(perfumeScoringService.score(women, profile, "para hombre"));
    }

    @Test
    void chooseTopPerfumesFiltersWomenWhenUserAsksForMen() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setSeason("verano");
        profile.setPreferredNotes("fresco");
        profile.setIntensity("suave");
        profile.setOccasion("diario");
        profile.setBudget("medio");

        PerfumeItem womenCitrus = perfume("Very Verino for women", "bergamot lime grapefruit mandarin orange",
                "summer", "Roberto Verino", "women", "Long Lasting", "Moderate", "49.00", "okay");
        PerfumeItem unisexLemon = perfume("Fresh Sugar Lemon", "lemon yuzu mandarin orange",
                "summer", "Fresh", "unisex", "Moderate", "Moderate", "74.99", "okay");
        PerfumeItem menCitrus = perfume("Citrus Homme", "lemon bergamot fresh clean",
                "summer", "Maison Test", "men", "Moderate", "Moderate", "65.00", "good_value");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(womenCitrus, unisexLemon, menCitrus), profile,
                "quiero algo fresco de limon para hombre en verano", 3);

        assertThat(result)
                .containsExactly(menCitrus, unisexLemon)
                .doesNotContain(womenCitrus);
    }

    @Test
    void chooseTopPerfumesFiltersForWomenNameEvenWhenGenderIsMissing() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setGenderTarget("hombre");
        profile.setSeason("verano");
        profile.setPreferredNotes("fresco");

        PerfumeItem namedForWomen = perfume("VV for women", "lime bergamot grapefruit", "summer");
        PerfumeItem unisex = perfume("Lemon Splash", "lime bergamot grapefruit", "summer",
                "Maison Test", "unisex", "Moderate", "Moderate", "40.00", "okay");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(namedForWomen, unisex), profile, "para hombre fresco verano", 2);

        assertThat(result).containsExactly(unisex);
    }

    @Test
    void scoreWoodyRequestPenalizesPerfumeWithoutWoodyClues() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("amaderado");

        PerfumeItem fruity = perfume("Fruit Splash", "mango pineapple coconut", "summer");
        PerfumeItem woody = perfume("Cedar Vetiver", "woody cedar vetiver", "summer");

        assertThat(perfumeScoringService.score(woody, profile, "que sea amaderado"))
                .isGreaterThan(perfumeScoringService.score(fruity, profile, "que sea amaderado"));
    }

    @Test
    void choosePerfumeStillReturnsBestPerfumeAfterAddingTopThreeSupport() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("invierno");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");

        PerfumeItem lowScore = perfume("Summer Fresh", "fresh citrus clean", "summer");
        PerfumeItem bestScore = perfume("Amber Night", "winter night intense amber vanilla musk", "winter");

        PerfumeItem result = perfumeScoringService.choosePerfume(List.of(lowScore, bestScore), profile,
                "quiero algo para noche especial");

        assertThat(result).isEqualTo(bestScore);
    }

    @Test
    void chooseTopPerfumesWithHistoryBoostsAcceptedNotes() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem neutral = perfume("Neutral Air", "green tea citrus", "spring");
        PerfumeItem similarToAccepted = perfume("Amber Vanilla", "vanilla amber musk", "winter");
        PerfumeRecommendation accepted = recommendation("Old Favorite", "Other Brand", "vanilla amber", "winter",
                true);

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(neutral, similarToAccepted), profile, "busco algo", List.of(accepted), List.of(), 2);

        assertThat(result).containsExactly(similarToAccepted, neutral);
    }

    @Test
    void chooseTopPerfumesWithHistoryBoostsAcceptedBrandSlightly() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem otherBrand = perfume("Soft Musk", "soft musk tea", "spring", "Other Brand");
        PerfumeItem acceptedBrand = perfume("Quiet Musk", "soft musk tea", "spring", "Maison Test");
        PerfumeRecommendation accepted = recommendation("Previous One", "Maison Test", "iris", "spring", true);

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(otherBrand, acceptedBrand), profile, "busco algo", List.of(accepted), List.of(), 2);

        assertThat(result).containsExactly(acceptedBrand, otherBrand);
    }

    @Test
    void chooseTopPerfumesWithHistoryPenalizesRejectedNotes() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem rejectedStyle = perfume("Dark Oud", "oud incense amber", "winter");
        PerfumeItem saferOption = perfume("Clean Citrus", "clean citrus musk", "summer");
        PerfumeRecommendation rejected = recommendation("Old Oud", "Other Brand", "oud incense", "winter", null);

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(rejectedStyle, saferOption), profile, "busco algo", List.of(), List.of(rejected), 2);

        assertThat(result).containsExactly(saferOption, rejectedStyle);
    }

    @Test
    void chooseTopPerfumesWithHistoryStronglyPenalizesSameRejectedPerfume() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("invierno");
        profile.setIntensity("intenso");
        profile.setOccasion("especial");
        PerfumeItem rejectedAgain = perfume("Amber Night", "winter night intense amber vanilla musk", "winter",
                "Maison Test");
        PerfumeItem alternative = perfume("Soft Winter", "winter soft amber", "winter", "Other Brand");
        PerfumeRecommendation rejected = recommendation("Amber Night", "Maison Test", "amber vanilla", "winter",
                null);

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(rejectedAgain, alternative), profile, "quiero algo para noche especial", List.of(),
                List.of(rejected), 2);

        assertThat(result).containsExactly(alternative, rejectedAgain);
    }

    @Test
    void historyDoesNotOverrideCurrentSeason() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("verano");
        PerfumeItem winterFromHistory = perfume("Amber Memory", "winter amber vanilla", "winter");
        PerfumeItem summerMatch = perfume("Summer Citrus", "summer citrus aquatic", "summer");
        PerfumeRecommendation acceptedWinter = recommendation("Winter Love", "Other Brand", "amber vanilla",
                "winter", true);

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(winterFromHistory, summerMatch), profile, "quiero algo para verano", List.of(acceptedWinter),
                List.of(), 2);

        assertThat(result).containsExactly(summerMatch);
    }

    @Test
    void chooseTopPerfumesWithNullHistoryDoesNotBreak() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem item = perfume("Clean Musk", "clean musk", "spring");

        List<PerfumeItem> result = perfumeScoringService.chooseTopPerfumes(
                List.of(item), profile, "busco algo", null, null, 3);

        assertThat(result).containsExactly(item);
    }

    @Test
    void sameInputWithHistoryProducesSameOrder() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem first = perfume("Amber Vanilla", "vanilla amber musk", "winter");
        PerfumeItem second = perfume("Clean Citrus", "clean citrus musk", "summer");
        PerfumeItem third = perfume("Dark Oud", "oud incense amber", "winter");
        PerfumeRecommendation accepted = recommendation("Old Favorite", "Other Brand", "vanilla amber", "winter",
                true);
        PerfumeRecommendation rejected = recommendation("Old Oud", "Other Brand", "oud incense", "winter", null);
        List<PerfumeItem> catalog = List.of(first, second, third);

        List<PerfumeItem> firstRun = perfumeScoringService.chooseTopPerfumes(
                catalog, profile, "busco algo", List.of(accepted), List.of(rejected), 3);
        List<PerfumeItem> secondRun = perfumeScoringService.chooseTopPerfumes(
                catalog, profile, "busco algo", List.of(accepted), List.of(rejected), 3);

        assertThat(secondRun).containsExactlyElementsOf(firstRun);
    }

    @Test
    void recommendationReasonIncludesOccasionWhenPresent() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setOccasion("especial");
        PerfumeItem item = perfume("Amber Night", "amber vanilla musk", "winter");

        String reason = perfumeScoringService.buildRecommendationReason(profile, item);

        assertThat(reason).contains("para noche");
    }

    @Test
    void recommendationReasonIncludesIntensityWhenPresent() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setIntensity("intenso");
        PerfumeItem item = perfume("Amber Night", "amber vanilla musk", "winter");

        String reason = perfumeScoringService.buildRecommendationReason(profile, item);

        assertThat(reason).contains("potente");
    }

    @Test
    void recommendationReasonIncludesBudgetWhenPresent() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setBudget("premium");
        PerfumeItem item = perfume("Iris Luxe", "iris amber musk", "winter");

        String reason = perfumeScoringService.buildRecommendationReason(profile, item);

        assertThat(reason).contains("premium");
    }

    @Test
    void recommendationReasonMentionsMatchingNotesWhenPresent() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setPreferredNotes("fresco, calido");
        PerfumeItem item = perfume("Bright Amber", "amber, bergamot, musk", "summer");

        String reason = perfumeScoringService.buildRecommendationReason(profile, item);

        assertThat(reason)
                .contains("ambar")
                .contains("bergamota");
    }

    @Test
    void recommendationReasonDoesNotBreakWithNullFields() {
        PerfumeProfile profile = new PerfumeProfile();
        PerfumeItem item = PerfumeItem.builder().build();

        String reason = perfumeScoringService.buildRecommendationReason(profile, item);

        assertThat(reason)
                .isNotBlank()
                .contains("Encaja contigo");
    }

    @Test
    void recommendationReasonDoesNotSayMismatchedSeasonReinforcesRequestedSeason() {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason("verano");
        PerfumeItem item = perfume("Vanilla Night", "vanilla amber", "winter");

        String reason = perfumeScoringService.buildRecommendationReason(profile, item);

        assertThat(reason)
                .doesNotContain("refuerza la temporada")
                .contains("lo marca mas como invierno");
    }

    private PerfumeProfile profileWithSeason(String season) {
        PerfumeProfile profile = new PerfumeProfile();
        profile.setSeason(season);
        return profile;
    }

    private PerfumeItem perfume(String name, String notes, String season) {
        return perfume(name, notes, season, "Test Brand");
    }

    private PerfumeItem perfume(String name, String notes, String season, String brand) {
        return PerfumeItem.builder()
                .name(name)
                .brand(brand)
                .description(notes)
                .notes(notes)
                .season(season)
                .source("test")
                .build();
    }

    private PerfumeItem perfume(String name, String notes, String season, String brand, String gender,
            String longevity, String sillage, String price, String priceValue) {
        return PerfumeItem.builder()
                .name(name)
                .brand(brand)
                .description(notes)
                .notes(notes)
                .season(season)
                .gender(gender)
                .longevity(longevity)
                .sillage(sillage)
                .price(price)
                .priceValue(priceValue)
                .source("test")
                .build();
    }

    private PerfumeRecommendation recommendation(String name, String brand, String notes, String season,
            Boolean accepted) {
        PerfumeRecommendation recommendation = new PerfumeRecommendation();
        recommendation.setPerfumeName(name);
        recommendation.setBrand(brand);
        recommendation.setDescription(notes);
        recommendation.setNotes(notes);
        recommendation.setSeason(season);
        recommendation.setAccepted(accepted);
        return recommendation;
    }
}
