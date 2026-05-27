package org.vedruna.perfumia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptBuilderServiceTest {

    private PromptBuilderService promptBuilderService;

    @BeforeEach
    void setUp() {
        promptBuilderService = new PromptBuilderService();
    }

    @Test
    void nextQuestionReturnsGenderQuestion() {
        String question = promptBuilderService.nextQuestion("si lo quieres para hombre, mujer o unisex", "");

        assertThat(question).isEqualTo("Me falta saber para quien lo orientamos: hombre, mujer o unisex?");
    }

    @Test
    void nextQuestionReturnsSeasonQuestion() {
        String question = promptBuilderService.nextQuestion("la epoca del ano", "");

        assertThat(question).isEqualTo("Bien, eso ya me da una pista. En que momento lo imaginas mas: verano, invierno, primavera u otono?");
    }

    @Test
    void nextQuestionReturnsIntensityQuestion() {
        String question = promptBuilderService.nextQuestion("intensidad: suave o potente", "");

        assertThat(question).isEqualTo("Con eso ya voy viendo el estilo. Quieres que sea suave y discreto, o mas potente y duradero?");
    }

    @Test
    void nextQuestionReturnsOccasionQuestion() {
        String question = promptBuilderService.nextQuestion("ocasion: diario, trabajo, cita, noche o versatil", "");

        assertThat(question).isEqualTo("Me falta el uso principal: diario, trabajo, cita, noche o algo versatil?");
    }

    @Test
    void nextQuestionReturnsBudgetQuestion() {
        String question = promptBuilderService.nextQuestion("presupuesto: economico, medio o premium", "");

        assertThat(question).isEqualTo("Me falta el presupuesto para no salirme de rango: economico, medio o premium?");
    }

    @Test
    void nextQuestionReturnsGenericQuestionWhenFieldDoesNotExist() {
        String question = promptBuilderService.nextQuestion("campo desconocido", "");

        assertThat(question).isEqualTo("Dame una pista mas de como quieres sentirlo y sigo afinando contigo.");
    }

    @Test
    void nextQuestionRefinesSweetPreferenceWhenUserMentionsSweetNotes() {
        String question = promptBuilderService.nextQuestion(
                "familia olfativa: fresco, dulce, amaderado, floral o especiado",
                "me gustan los dulces");

        assertThat(question)
                .contains("vainilla")
                .contains("caramelo")
                .contains("coco");
    }

    @Test
    void nextQuestionGuidesUserWithVibeExamples() {
        String question = promptBuilderService.nextQuestion(
                "familia olfativa: fresco, dulce, amaderado, floral o especiado",
                "quiero algo elegante");

        assertThat(question)
                .contains("limpio")
                .contains("sexy")
                .contains("elegante");
    }

    @Test
    void looksLikeQuestionDetectsQuestionMark() {
        assertThat(promptBuilderService.looksLikeQuestion("me explicas esto?")).isTrue();
    }

    @Test
    void looksLikeQuestionDetectsClarificationText() {
        assertThat(promptBuilderService.looksLikeQuestion("que significa fresco")).isTrue();
    }

    @Test
    void answerClarificationQuestionAnswersBudgetQuestions() {
        String answer = promptBuilderService.answerClarificationQuestion("de cuanto precio puede ser el medio",
                List.of("presupuesto: economico, medio o premium"));

        assertThat(answer).isEqualTo("Claro. Para orientar el recomendador: economico seria hasta 50 euros, medio entre 50 y 120 euros, y premium mas de 120 euros. Con cual te quedas: economico, medio o premium?");
    }

    @Test
    void answerClarificationQuestionAnswersOlfactiveFamilyQuestions() {
        String answer = promptBuilderService.answerClarificationQuestion("que significa fresco",
                List.of("familia olfativa: fresco, dulce, amaderado, floral o especiado"));

        assertThat(answer).isEqualTo("Te explico rapido: fresco huele limpio/citrico, dulce recuerda vainilla o caramelo, amaderado es cedro/sandalo, floral es jazmin/rosa y especiado tira a pimienta/canela. Cual te llama mas?");
    }

    @Test
    void answerClarificationQuestionAnswersIntensityQuestions() {
        String answer = promptBuilderService.answerClarificationQuestion("que diferencia hay entre suave y potente",
                List.of("intensidad: suave o potente"));

        assertThat(answer).isEqualTo("Suave significa que se nota cerca y no invade; potente significa que dura mas y se percibe a distancia. Para tu caso, prefieres suave o potente?");
    }

    @Test
    void answerClarificationQuestionReturnsEmptyStringWhenItCannotAnswer() {
        String answer = promptBuilderService.answerClarificationQuestion("me apetece algo nuevo",
                List.of("presupuesto: economico, medio o premium"));

        assertThat(answer).isEmpty();
    }

    @Test
    void answerClarificationQuestionExplainsSillage() {
        String answer = promptBuilderService.answerClarificationQuestion("que significa que tenga estela",
                List.of("intensidad: suave o potente"));

        assertThat(answer)
                .contains("estela")
                .contains("Discreto")
                .contains("moderado");
    }
}
