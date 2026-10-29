package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.decision.DecisionAnswer;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionQuestionType;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.decision.DecisionUsage;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.typesafe.TypeSafeProvider;
import run.halo.aifoundation.service.decision.DecisionModelImpl;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageUnitKind;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

class AuditedDecisionModelTest {
    @ParameterizedTest
    @MethodSource("invalidResponseUsage")
    void recordsFailedValidationWithReportedUsageAndActualModel(DecisionUsage usage,
        Long input, Long output, Long total, UsageQuality quality) {
        var statistics = mock(UsageStatisticsService.class);
        var context = new ModelCallContext(ModelType.DECISION,
            "decision-resource", "provider", "typesafe", "jev-latest");
        var descriptor = mock(UsageCallDescriptor.class);
        var metadata = Map.<String, Object>of("feature", "moderation");
        var start = new UsageCallStart("decision-call", 1, Instant.now(), "plugin", "1",
            "stack", null, "decision.decide", "decision", "decision-resource",
            "provider", "typesafe", "jev-latest", false);
        var session = new UsageCallSession(statistics, start, Clock.systemUTC());
        when(statistics.describeCall(context, "decision.decide", false, metadata))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        var response = new DecisionResponse("jev-actual", Map.of("wrong-id",
            DecisionAnswer.builder().type(DecisionQuestionType.YES_NO).probability(0.2).build()),
            usage);
        var runtime = new DecisionModelImpl(ignored -> Mono.just(response),
            new UsageExecutionObserver(), new TypeSafeProvider().decisionCapabilities());
        var model = new AuditedDecisionModel(runtime, context,
            mock(CallerPluginAuditRecorder.class), statistics);

        StepVerifier.create(model.decide(DecisionRequest.builder().state("comment")
                .questions(Map.of("spam", DecisionQuestion.yesNo("Is it spam?")))
                .metadata(metadata).build()))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision response question IDs do not match the request"))
            .verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(statistics).recordExecution(eq(session), execution.capture());
        var record = execution.getValue();
        assertThat(record.status()).isEqualTo(UsageStatus.FAILED);
        assertThat(record.requestModelId()).isEqualTo("jev-latest");
        assertThat(record.responseModelId()).isEqualTo("jev-actual");
        assertThat(record.usage().inputTokens()).isEqualTo(input);
        assertThat(record.usage().outputTokens()).isEqualTo(output);
        assertThat(record.usage().accountedTotalTokens()).isEqualTo(total);
        assertThat(record.usage().quality()).isEqualTo(quality);

        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(statistics).finishCall(eq(session), terminal.capture());
        assertThat(terminal.getValue().status()).isEqualTo(UsageStatus.FAILED);
        assertThat(terminal.getValue().responseModelId()).isEqualTo("jev-actual");
        assertThat(terminal.getValue().attemptCount()).isEqualTo(1);
        assertThat(terminal.getValue().missingExecutionCount())
            .isEqualTo(quality == UsageQuality.MISSING ? 1 : 0);
        assertThat(terminal.getValue().usage()).isEqualTo(record.usage());
    }

    static Stream<Arguments> invalidResponseUsage() {
        return Stream.of(
            Arguments.of(new DecisionUsage(123, 22), 123L, 22L, 145L,
                UsageQuality.REPORTED_COMPONENTS),
            Arguments.of(new DecisionUsage(123, null), 123L, null, null, UsageQuality.PARTIAL),
            Arguments.of(new DecisionUsage(null, 22), null, 22L, null, UsageQuality.PARTIAL),
            Arguments.of(new DecisionUsage(0, 0), 0L, 0L, 0L, UsageQuality.REPORTED_COMPONENTS),
            Arguments.of(new DecisionUsage(null, null), null, null, null, UsageQuality.MISSING),
            Arguments.of(null, null, null, null, UsageQuality.MISSING));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recordsDecisionExecutionWithoutInventingMissingUsage(boolean hasUsage) {
        var statistics = mock(UsageStatisticsService.class);
        var audit = mock(CallerPluginAuditRecorder.class);
        var context = new ModelCallContext(ModelType.DECISION,
            "decision-resource", "provider", "typesafe", "jev-latest");
        var descriptor = mock(UsageCallDescriptor.class);
        var metadata = Map.<String, Object>of("feature", "moderation");
        var start = new UsageCallStart("decision-call", 1, Instant.now(), "plugin", "1",
            "stack", null, "decision.decide", "decision", "decision-resource",
            "provider", "typesafe", "jev-latest", false);
        var session = new UsageCallSession(statistics, start, Clock.systemUTC());
        when(statistics.describeCall(context, "decision.decide", false, metadata))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        var response = new DecisionResponse("jev-actual", Map.of("spam",
            DecisionAnswer.builder().type(DecisionQuestionType.YES_NO).probability(0.2).build()),
            hasUsage ? new DecisionUsage(12, 3) : null);
        var runtime = new DecisionModelImpl(ignored -> Mono.just(response),
            new UsageExecutionObserver(), new TypeSafeProvider().decisionCapabilities());
        var model = new AuditedDecisionModel(runtime, context, audit, statistics);

        assertThat(model.decide(DecisionRequest.builder().state("comment")
            .questions(Map.of("spam", DecisionQuestion.yesNo("Is it spam?")))
            .metadata(metadata).build()).block()).isSameAs(response);

        verify(audit).recordModelInvocation(context, "decision.decide");
        var record = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(statistics).recordExecution(eq(session), record.capture());
        assertThat(record.getValue().unitKind()).isEqualTo(UsageUnitKind.DECISION);
        assertThat(record.getValue().status()).isEqualTo(UsageStatus.SUCCEEDED);
        assertThat(record.getValue().responseModelId()).isEqualTo("jev-actual");
        assertThat(record.getValue().usage().quality()).isEqualTo(hasUsage
            ? UsageQuality.REPORTED_COMPONENTS : UsageQuality.MISSING);
        assertThat(record.getValue().usage().accountedTotalTokens())
            .isEqualTo(hasUsage ? 15L : null);
        verify(statistics).finishCall(eq(session), any());
    }
}
