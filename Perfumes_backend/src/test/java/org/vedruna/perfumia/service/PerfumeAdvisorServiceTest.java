package org.vedruna.perfumia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedruna.perfumia.controller.dto.ChatResponseDTO;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.PerfumeProfileRepository;
import org.vedruna.perfumia.service.dto.PerfumeItem;

import com.fasterxml.jackson.databind.ObjectMapper;

class PerfumeAdvisorServiceTest {

    private PerfumeProfileRepository perfumeProfileRepository;
    private RecommendationPersistenceService recommendationPersistenceService;
    private PerfumeCatalogService perfumeCatalogService;
    private PerfumeScoringService perfumeScoringService;
    private GeminiService geminiService;
    private PerfumeAdvisorService perfumeAdvisorService;

    @BeforeEach
    void setUp() {
        perfumeProfileRepository = mock(PerfumeProfileRepository.class);
        recommendationPersistenceService = mock(RecommendationPersistenceService.class);
        perfumeCatalogService = mock(PerfumeCatalogService.class);
        perfumeScoringService = mock(PerfumeScoringService.class);
        geminiService = mock(GeminiService.class);

        perfumeAdvisorService = new PerfumeAdvisorService(
                perfumeProfileRepository,
                recommendationPersistenceService,
                new AiDecisionService(),
                perfumeScoringService,
                new PromptBuilderService(),
                perfumeCatalogService,
                geminiService,
                new ObjectMapper());

        when(perfumeProfileRepository.save(any(PerfumeProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(recommendationPersistenceService.hasPendingRecommendation(any(User.class))).thenReturn(false);
        when(recommendationPersistenceService.findRecentMessages(any(User.class))).thenReturn(List.of());
        when(recommendationPersistenceService.findRecommendations(any(User.class))).thenReturn(List.of());
        when(recommendationPersistenceService.findAcceptedRecommendations(any(User.class))).thenReturn(List.of());
        when(recommendationPersistenceService.findRejectedRecommendations(any(User.class))).thenReturn(List.of());
        when(recommendationPersistenceService.listRecommendations(any(User.class))).thenReturn(List.of());
    }

    @Test
    void dailyOdorRequestAsksForCleanStyleBeforeSearching() {
        User user = new User();
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        ChatResponseDTO response = perfumeAdvisorService.chat(user,
                "quiero un perfume para diario y pa q cuando cague no huela mi culo a mierda");

        assertThat(response.getAnswer())
                .contains("limpio")
                .contains("jabonoso")
                .contains("acuatico");
        verify(perfumeCatalogService, never()).searchPerfumes(any());
        verify(geminiService, never()).generateAnswer(any(String.class));
        verify(geminiService, never()).generateJsonAnswer(any(String.class));
    }

    @Test
    void genderAnswerDoesNotTriggerRecommendationWhenOlfactiveStyleIsStillMissing() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        profile.setOccasion("diario");
        profile.setPreferredNotes("casual");
        profile.setLastSummary("- | - | - | casual | diario | - | -");
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));

        ChatResponseDTO response = perfumeAdvisorService.chat(user, "quiero que sea para hombre");

        assertThat(profile.getGenderTarget()).isEqualTo("hombre");
        assertThat(response.getAnswer())
                .contains("diario")
                .contains("limpio/jabonoso");
        verify(perfumeCatalogService, never()).searchPerfumes(any());
        verify(geminiService, never()).generateAnswer(any(String.class));
        verify(geminiService, never()).generateJsonAnswer(any(String.class));
    }

    @Test
    void geminiAnswersBeforeLocalAdvisorWhenConfigured() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateJsonAnswer(any(String.class))).thenReturn("""
                {
                  "intent": "perfume_chat",
                  "perfumeRelated": true,
                  "answer": "Te lo traduzco a algo ponible: cuero ahumado, madera oscura y un punto industrial, no tubo de escape literal. Para rematarlo en Fragella, lo quieres para noche/citas o para diario?",
                  "readyToSearch": false,
                  "searchQuery": "",
                  "profile": {
                    "genderTarget": "hombre",
                    "season": "",
                    "preferredNotes": "amaderado, cuero, ahumado, industrial",
                    "intensity": "intenso",
                    "occasion": "",
                    "budget": "economico",
                    "dislikedNotes": ""
                  }
                }
                """);

        ChatResponseDTO response = perfumeAdvisorService.chat(user,
                "quiero un perfume olor a gasolina para atraer a mujeres que sea potente y barato");

        assertThat(response.getAnswer())
                .contains("Te lo traduzco")
                .contains("noche/citas")
                .doesNotContain("Me falta");
        assertThat(profile.getPreferredNotes()).contains("cuero").contains("ahumado");
        verify(geminiService).generateJsonAnswer(any(String.class));
        verify(perfumeCatalogService, never()).searchPerfumes(any());
    }

    @Test
    void geminiContinuesWhenGreetingIncludesPerfumePreference() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateJsonAnswer(any(String.class))).thenReturn("""
                {
                  "intent": "greeting",
                  "perfumeRelated": false,
                  "answer": "Hola, soy PerfumIA. Cuéntame un poco qué buscas: algo fresco para diario, algo más intenso, o todavía no lo tienes claro?",
                  "readyToSearch": false,
                  "searchQuery": "",
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
                """);
        when(geminiService.generateAnswer(any(String.class)))
                .thenReturn("Perfecto, si te gustan las fresas iría por algo frutal y dulce, pero sin hacerlo empalagoso. Para orientarlo bien, lo quieres para diario, para salir o para algo más especial?");

        ChatResponseDTO response = perfumeAdvisorService.chat(user,
                "hoola no entiendo de perfumes pero me gusta el olor a fresas");

        assertThat(response.getAnswer())
                .contains("fresas")
                .contains("diario")
                .doesNotContain("Cuéntame un poco qué buscas");
        assertThat(profile.getPreferredNotes())
                .contains("frutal")
                .contains("fresa");
        verify(geminiService).generateJsonAnswer(any(String.class));
        verify(geminiService).generateAnswer(any(String.class));
        verify(perfumeCatalogService, never()).searchPerfumes(any());
    }

    @Test
    void geminiSearchesWhenOnlyDefaultableFieldsAreMissing() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        profile.setGenderTarget("hombre");
        profile.setOccasion("diario");
        profile.setBudget("economico");
        profile.setPreferredNotes("limpio");
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateJsonAnswer(any(String.class))).thenReturn("""
                {
                  "intent": "perfume_chat",
                  "perfumeRelated": true,
                  "answer": "Perfecto, un aroma jabonoso y limpio es una excelente eleccion para el dia a dia. Con estos datos, ya podemos buscar algo que te encaje.",
                  "readyToSearch": false,
                  "searchQuery": "",
                  "profile": {
                    "genderTarget": "",
                    "season": "",
                    "preferredNotes": "limpio",
                    "intensity": "",
                    "occasion": "",
                    "budget": "",
                    "dislikedNotes": ""
                  }
                }
                """);
        PerfumeItem perfume = PerfumeItem.builder()
                .name("Clean Soap")
                .brand("Test Brand")
                .notes("clean musk soap citrus")
                .season("summer")
                .gender("men")
                .build();
        when(perfumeCatalogService.searchPerfumes(any(String.class))).thenReturn(List.of(perfume));
        when(perfumeScoringService.chooseTopPerfumes(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(perfume));

        ChatResponseDTO response = perfumeAdvisorService.chat(user, "jabonoso");

        assertThat(response.getAnswer()).contains("He encontrado");
        assertThat(profile.getSeason()).isEqualTo("versatil");
        assertThat(profile.getIntensity()).isEqualTo("suave");
        verify(perfumeCatalogService, atLeastOnce()).searchPerfumes(any(String.class));
    }

    @Test
    void geminiProfileValuesAreCanonicalizedBeforeSearch() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateJsonAnswer(any(String.class))).thenReturn("""
                {
                  "intent": "recommend_now",
                  "perfumeRelated": true,
                  "answer": "Ya tengo un perfil limpio, citrico y con presencia para citas.",
                  "readyToSearch": true,
                  "searchQuery": "masculine clean citrus amber date under 80",
                  "profile": {
                    "genderTarget": "masculino",
                    "season": "todo el ano",
                    "preferredNotes": "jabonoso, citrico, ambroxan",
                    "intensity": "potente",
                    "occasion": "citas",
                    "budget": "menos de 80 euros",
                    "dislikedNotes": "empalagoso"
                  }
                }
                """);
        PerfumeItem perfume = PerfumeItem.builder()
                .name("Clean Amber")
                .brand("Test Brand")
                .notes("clean citrus amber musk")
                .season("all year")
                .gender("men")
                .build();
        when(perfumeCatalogService.searchPerfumes(any(String.class))).thenReturn(List.of(perfume));
        when(perfumeScoringService.chooseTopPerfumes(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(perfume));

        ChatResponseDTO response = perfumeAdvisorService.chat(user,
                "buscame algo masculino jabonoso y citrico para citas por menos de 80 euros");

        assertThat(response.getAnswer()).contains("He encontrado");
        assertThat(profile.getGenderTarget()).isEqualTo("hombre");
        assertThat(profile.getSeason()).isEqualTo("versatil");
        assertThat(profile.getIntensity()).isEqualTo("intenso");
        assertThat(profile.getOccasion()).isEqualTo("especial");
        assertThat(profile.getBudget()).isEqualTo("medio");
        assertThat(profile.getPreferredNotes())
                .contains("limpio")
                .contains("citrico")
                .contains("ambar");
        assertThat(profile.getDislikedNotes()).contains("empalagoso");
        verify(perfumeCatalogService, atLeastOnce()).searchPerfumes(any(String.class));
    }

    @Test
    void searchRequestAsksOnlyForMissingOccasionForMarineAnimalicProfile() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        profile.setGenderTarget("mujer");
        profile.setBudget("economico");
        profile.setPreferredNotes("marino, salino, animalico");
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateJsonAnswer(any(String.class))).thenReturn("""
                {
                  "intent": "perfume_chat",
                  "perfumeRelated": true,
                  "answer": "Con esa direccion marina y animalica ya puedo buscar en Fragella.",
                  "readyToSearch": false,
                  "searchQuery": "",
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
                """);
        PerfumeItem perfume = PerfumeItem.builder()
                .name("Salty Musk")
                .brand("Test Brand")
                .notes("marine salty animalic musk")
                .season("all year")
                .gender("women")
                .build();
        when(perfumeCatalogService.searchPerfumes(any(String.class))).thenReturn(List.of(perfume));
        when(perfumeScoringService.chooseTopPerfumes(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(perfume));

        ChatResponseDTO response = perfumeAdvisorService.chat(user, "pero busca o necesitas algo mas");

        assertThat(response.getAnswer())
                .contains("diario")
                .contains("trabajo");
        verify(perfumeCatalogService, never()).searchPerfumes(any(String.class));
    }

    @Test
    void completeMarineAnimalicProfileSearchesWithDefaults() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        profile.setGenderTarget("mujer");
        profile.setOccasion("diario");
        profile.setBudget("economico");
        profile.setPreferredNotes("marino, salino, animalico");
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateJsonAnswer(any(String.class))).thenReturn("""
                {
                  "intent": "perfume_chat",
                  "perfumeRelated": true,
                  "answer": "Con esa direccion marina y animalica ya puedo buscar en Fragella.",
                  "readyToSearch": false,
                  "searchQuery": "",
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
                """);
        PerfumeItem perfume = PerfumeItem.builder()
                .name("Salty Musk")
                .brand("Test Brand")
                .notes("marine salty animalic musk")
                .season("all year")
                .gender("women")
                .build();
        when(perfumeCatalogService.searchPerfumes(any(String.class))).thenReturn(List.of(perfume));
        when(perfumeScoringService.chooseTopPerfumes(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(perfume));

        ChatResponseDTO response = perfumeAdvisorService.chat(user, "diario");

        assertThat(response.getAnswer()).contains("He encontrado");
        assertThat(profile.getSeason()).isEqualTo("versatil");
        assertThat(profile.getIntensity()).isEqualTo("suave");
        verify(perfumeCatalogService, atLeastOnce()).searchPerfumes(any(String.class));
    }

    @Test
    void localCatalogAnswerDoesNotClaimFragellaAndCanReturnThreeOptions() {
        User user = new User();
        PerfumeProfile profile = new PerfumeProfile();
        profile.setUser(user);
        profile.setGenderTarget("hombre");
        profile.setOccasion("diario");
        profile.setBudget("economico");
        profile.setPreferredNotes("dulce, frutal, fresa");
        profile.setSeason("versatil");
        profile.setIntensity("suave");
        when(perfumeProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(geminiService.isConfigured()).thenReturn(false);

        PerfumeItem first = PerfumeItem.builder()
                .name("CK One")
                .brand("Calvin Klein")
                .source("local")
                .notes("bergamota, te verde, almizcle")
                .gender("unisex")
                .price("25-55 euros")
                .priceValue("good_value")
                .build();
        PerfumeItem second = PerfumeItem.builder()
                .name("Nautica Voyage")
                .brand("Nautica")
                .source("local")
                .notes("manzana verde, loto, almizcle, cedro")
                .gender("men")
                .price("20-45 euros")
                .priceValue("good_value")
                .build();
        PerfumeItem third = PerfumeItem.builder()
                .name("Bade'e Al Oud Sublime")
                .brand("Lattafa")
                .source("local")
                .notes("manzana, lichi, frutas rojas, vainilla")
                .gender("unisex")
                .price("25-45 euros")
                .priceValue("good_value")
                .build();

        when(perfumeCatalogService.searchPerfumes(any(String.class))).thenReturn(List.of(first, second, third));
        when(perfumeScoringService.containsDislikedNote(any(), any())).thenReturn(false);
        when(perfumeScoringService.chooseTopPerfumes(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(first, second, third));

        ChatResponseDTO response = perfumeAdvisorService.chat(user, "busca ya");

        assertThat(response.getAnswer())
                .contains("catalogo local")
                .contains("3 opciones")
                .doesNotContain("He filtrado Fragella");
    }
}
