package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.provider.aihubmix.AiHubMixDecisionClient;
import run.halo.aifoundation.provider.support.DecisionResponseValidationException;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderDecisionClient;
import run.halo.aifoundation.provider.typesafe.TypeSafeDecisionClient;
import run.halo.aifoundation.provider.typesafe.TypeSafeProvider;
import run.halo.aifoundation.service.decision.DecisionModelImpl;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.observation.UsageStatus;

class AuditedSystemOneDecisionModelTest {

    @ParameterizedTest
    @MethodSource("invalidProtocolResponses")
    void retainsValidMetadataWhenProtocolDecodingFails(
            String provider, String body, String responseModel, Long input, Long output, UsageQuality quality) {
        var statistics = mock(UsageObservation.class);
        var context = new ModelCallContext(ModelType.DECISION, "decision-resource", "provider", provider, "jev-latest");
        var descriptor = mock(UsageCallDescriptor.class);
        var start = new UsageCallStart(
                "decision-call",
                1,
                Instant.now(),
                "plugin",
                "1",
                "stack",
                null,
                "decision.decide",
                "decision",
                "decision-resource",
                "provider",
                provider,
                "jev-latest",
                false);
        var session = new UsageCallSession(statistics, start, Clock.systemUTC());
        when(statistics.describeCall(context, "decision.decide", false, Map.of()))
                .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        var builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .build()));
        ProviderDecisionClient client = switch (provider) {
            case "typesafe" -> new TypeSafeDecisionClient("https://example.test", "key", "jev-latest", builder);
            case "aihubmix" -> new AiHubMixDecisionClient("https://example.test", "key", "jev-latest", builder);
            default -> throw new IllegalArgumentException("Unexpected provider: " + provider);
        };
        var runtime = new DecisionModelImpl(
                client, new UsageExecutionObserver(), new TypeSafeProvider().decisionCapabilities());
        var model = new AuditedDecisionModel(runtime, context, mock(CallerPluginAuditRecorder.class), statistics);
        var request = DecisionRequest.builder()
                .state("message")
                .questions(Map.of("q", DecisionQuestion.choice("Choose", Map.of("a", "A", "b", "B"))))
                .metadata(Map.of())
                .build();

        StepVerifier.create(model.decide(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DecisionResponseValidationException.class)
                        .hasCauseInstanceOf(IllegalStateException.class))
                .verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(statistics).recordExecution(eq(session), execution.capture());
        var record = execution.getValue();
        assertThat(record.status()).isEqualTo(UsageStatus.FAILED);
        assertThat(record.responseModelId()).isEqualTo(responseModel);
        assertThat(record.usage().inputTokens()).isEqualTo(input);
        assertThat(record.usage().outputTokens()).isEqualTo(output);
        assertThat(record.usage().quality()).isEqualTo(quality);
        assertThat(record.usage().accountedTotalTokens())
                .isEqualTo(input == null || output == null ? null : input + output);

        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(statistics).finishCall(eq(session), terminal.capture());
        assertThat(terminal.getValue().status()).isEqualTo(UsageStatus.FAILED);
        assertThat(terminal.getValue().responseModelId()).isEqualTo(responseModel);
        assertThat(terminal.getValue().usage()).isEqualTo(record.usage());
        assertThat(terminal.getValue().missingExecutionCount()).isEqualTo(quality == UsageQuality.MISSING ? 1 : 0);
    }

    static Stream<Arguments> invalidProtocolResponses() {
        // Missing confidence is a protocol error even though the public SDK allows optional diagnostics.
        var missingConfidence = """
            {"model":%s,"answers":{"q":{"type":"choice","choice":"a",
              "probabilities":{"a":0.8,"b":0.2}}},"usage":%s}
            """;
        var validAnswer = """
            {"model":%s,"answers":{"q":{"type":"choice","choice":"a",
              "probabilities":{"a":0.8,"b":0.2},"confidence":0.6}},"usage":%s}
            """;
        return Stream.of("typesafe", "aihubmix")
                .flatMap(provider -> Stream.of(
                        Arguments.of(
                                provider,
                                missingConfidence.formatted(
                                        "\"jev-actual\"", "{\"input_tokens\":123,\"output_tokens\":22}"),
                                "jev-actual",
                                123L,
                                22L,
                                UsageQuality.REPORTED_COMPONENTS),
                        Arguments.of(
                                provider,
                                missingConfidence.formatted("\"jev-actual\"", "{\"input_tokens\":123}"),
                                "jev-actual",
                                123L,
                                null,
                                UsageQuality.PARTIAL),
                        Arguments.of(
                                provider,
                                missingConfidence.formatted("\"jev-actual\"", "{\"output_tokens\":22}"),
                                "jev-actual",
                                null,
                                22L,
                                UsageQuality.PARTIAL),
                        Arguments.of(
                                provider,
                                missingConfidence.formatted(
                                        "\"jev-actual\"", "{\"input_tokens\":0,\"output_tokens\":0}"),
                                "jev-actual",
                                0L,
                                0L,
                                UsageQuality.REPORTED_COMPONENTS),
                        Arguments.of(
                                provider,
                                missingConfidence.formatted("\"jev-actual\"", "null"),
                                "jev-actual",
                                null,
                                null,
                                UsageQuality.MISSING),
                        Arguments.of(
                                provider,
                                missingConfidence.formatted(
                                        "\"jev-actual\"", "{\"input_tokens\":\"invalid\",\"output_tokens\":22}"),
                                "jev-actual",
                                null,
                                22L,
                                UsageQuality.PARTIAL),
                        Arguments.of(
                                provider,
                                missingConfidence.formatted(
                                        "\"jev-actual\"", "{\"input_tokens\":123,\"output_tokens\":-1}"),
                                "jev-actual",
                                123L,
                                null,
                                UsageQuality.PARTIAL),
                        Arguments.of(
                                provider,
                                validAnswer.formatted("\"jev-actual\"", "{\"input_tokens\":1.5,\"output_tokens\":22}"),
                                "jev-actual",
                                null,
                                22L,
                                UsageQuality.PARTIAL),
                        Arguments.of(
                                provider,
                                validAnswer.formatted(
                                        "\"jev-actual\"", "{\"input_tokens\":123,\"output_tokens\":2147483648}"),
                                "jev-actual",
                                123L,
                                null,
                                UsageQuality.PARTIAL),
                        Arguments.of(
                                provider,
                                validAnswer.formatted("\" \"", "{\"input_tokens\":123,\"output_tokens\":22}"),
                                null,
                                123L,
                                22L,
                                UsageQuality.REPORTED_COMPONENTS),
                        Arguments.of(
                                provider,
                                validAnswer.formatted("\"jev-actual\"", "[]"),
                                "jev-actual",
                                null,
                                null,
                                UsageQuality.MISSING)));
    }
}
