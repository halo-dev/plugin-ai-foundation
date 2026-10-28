package run.halo.aifoundation.service.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.decision.DecisionAnswer;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionQuestionType;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.exception.DecisionCancelledException;
import run.halo.aifoundation.exception.DecisionTimeoutException;
import run.halo.aifoundation.provider.support.ProviderDecisionClient;
import run.halo.aifoundation.provider.typesafe.TypeSafeProvider;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;

class DecisionModelTest {
    private final ProviderDecisionClient client = mock(ProviderDecisionClient.class);
    private final DecisionModelImpl model = new DecisionModelImpl(client,
        new UsageExecutionObserver(), new TypeSafeProvider().decisionCapabilities());

    @Test
    void validatesBeforeCallingProvider() {
        var badType = DecisionQuestion.choice("Route?", Map.of("only", "only"));
        var tooManyLevels = DecisionQuestion.score("Score?", java.util.Collections.nCopies(11, "level"));
        for (var request : List.of(
            DecisionRequest.builder().state(" ").questions(Map.of("q", DecisionQuestion.yesNo("?"))).build(),
            DecisionRequest.builder().state("text").questions(Map.of()).build(),
            DecisionRequest.builder().state("text").questions(Map.of("q", badType)).build(),
            DecisionRequest.builder().state("text").questions(Map.of("q", tooManyLevels)).build(),
            DecisionRequest.builder().state(new Object()).questions(Map.of("q", DecisionQuestion.yesNo("?"))).build())) {
            StepVerifier.create(model.decide(request)).expectError(IllegalArgumentException.class).verify();
        }
        verifyNoInteractions(client);
    }

    @Test
    void rejectsMismatchedQuestionIdsTypesAndInvalidProbabilities() {
        var valid = DecisionAnswer.builder().type(DecisionQuestionType.YES_NO).probability(0.5).build();
        for (var answer : List.of(
            Map.of("wrong", valid),
            Map.of("q", DecisionAnswer.builder().type(DecisionQuestionType.CHOICE).choice("a").build()),
            Map.of("q", DecisionAnswer.builder().type(DecisionQuestionType.YES_NO).probability(1.1).build()),
            Map.of("q", DecisionAnswer.builder().type(DecisionQuestionType.YES_NO).probability(Double.NaN).build()))) {
            when(client.decide(any())).thenReturn(Mono.just(new DecisionResponse("jev", answer, null)));
            StepVerifier.create(model.decide(request())).expectError(IllegalStateException.class).verify();
        }
    }

    @Test
    void rejectsInvalidDistributionAndUnknownChoice() {
        var request = request();
        request.setQuestions(Map.of("q", DecisionQuestion.choice("Choose", Map.of("a", "A", "b", "B"))));
        for (var answer : List.of(
            DecisionAnswer.builder().type(DecisionQuestionType.CHOICE).choice("a")
                .probabilities(Map.of("a", 0.1, "b", 0.1)).confidence(0.2).build(),
            DecisionAnswer.builder().type(DecisionQuestionType.CHOICE).choice("unknown")
                .probabilities(Map.of("a", 0.8, "b", 0.2)).confidence(0.6).build())) {
            when(client.decide(any())).thenReturn(Mono.just(new DecisionResponse("jev", Map.of("q", answer), null)));
            StepVerifier.create(model.decide(request)).expectError(IllegalStateException.class).verify();
        }
    }

    @Test
    void honoursCancellationAndTimeout() {
        var cancelled = request();
        cancelled.setCancellationToken(() -> true);
        StepVerifier.create(model.decide(cancelled)).expectError(DecisionCancelledException.class).verify();
        verifyNoInteractions(client);
        when(client.decide(any())).thenReturn(Mono.never());
        var timed = request();
        timed.setTimeouts(GenerationTimeouts.builder().totalTimeout(Duration.ofSeconds(1)).build());
        StepVerifier.withVirtualTime(() -> model.decide(timed)).thenAwait(Duration.ofSeconds(1))
            .expectError(DecisionTimeoutException.class).verify();
    }

    @Test
    void capabilitySnapshotsCannotMutateRuntimeLimits() {
        var capability = model.capabilities();
        capability.setMaxScoreLevels(100);
        assertThat(model.capabilities().getMaxScoreLevels()).isEqualTo(10);
    }

    private static DecisionRequest request() {
        return DecisionRequest.builder().state("message")
            .questions(Map.of("q", DecisionQuestion.yesNo("Is it urgent?"))).build();
    }
}
