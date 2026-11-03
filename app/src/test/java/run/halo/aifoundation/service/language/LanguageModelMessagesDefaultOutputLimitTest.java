package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.PreparedStep;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappings;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.openai.OpenAiChatModel;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openai.OpenAiResponsesModel;
import run.halo.aifoundation.provider.openrouter.OpenRouterMessagesModel;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

class LanguageModelMessagesDefaultOutputLimitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void messagesRequiredDefaultRespectsSmallModelLimits() {
        // OpenRouter's catalog includes Messages models with output limits below 4096,
        // including UI-TARS 1.5 and Gemma 2 27B at 2048 tokens.
        for (var limit : List.of(1L, 2048L, 4000L)) {
            for (var prepared : List.of(false, true)) {
                var probe = probe(AdapterType.OPENROUTER_MESSAGES, limit, Map.of());
                assertAccepted(probe, request(null, prepared));
                assertThat(probe.bodies()).allSatisfy(body ->
                    assertThat(body).containsEntry("max_tokens", limit.intValue()));
            }
        }
    }

    @Test
    void largeAndUnknownLimitsRetainTheProtocolDefault() {
        for (var limit : java.util.Arrays.asList(null, 8192L, Long.MAX_VALUE)) {
            for (var prepared : List.of(false, true)) {
                var probe = probe(AdapterType.OPENROUTER_MESSAGES, limit, Map.of());
                assertAccepted(probe, request(null, prepared));
                assertThat(probe.bodies()).allSatisfy(body ->
                    assertThat(body).containsEntry("max_tokens", 4096));
            }
        }
    }

    @Test
    void sdkAndPreparedOutputLengthsKeepTheirPrecedenceAndValidation() {
        for (var prepared : List.of(false, true)) {
            var accepted = probe(AdapterType.OPENROUTER_MESSAGES, 2048L,
                Map.of("max_tokens", 5000));
            assertAccepted(accepted, request(1536, prepared));
            assertThat(accepted.bodies()).allSatisfy(body ->
                assertThat(body).containsEntry("max_tokens", 1536));

            var rejected = probe(AdapterType.OPENROUTER_MESSAGES, 2048L, Map.of());
            assertRejected(rejected, request(5000, prepared),
                "maxOutputTokens must not exceed model limit 2048");
        }
    }

    @Test
    void filteredSdkOutputLengthsStillUseASafeRequiredProtocolDefault() {
        var unsupported = new EffectiveParameterMappings.EffectiveMapping(
            ModelParameterMappings.Mode.UNSUPPORTED, null, null,
            EffectiveParameterMappings.Source.MODEL);
        var mappings = new RuntimeParameterMappings(new EffectiveParameterMappings(
            Map.of(ModelParameter.MAX_OUTPUT_TOKENS, unsupported)), null, "model", "provider");
        for (var prepared : List.of(false, true)) {
            var accepted = probe(AdapterType.OPENROUTER_MESSAGES, 2048L, Map.of(), options(), mappings);
            assertAccepted(accepted, request(1536, prepared));
            assertThat(accepted.bodies()).allSatisfy(body ->
                assertThat(body).containsEntry("max_tokens", 2048));
            var rejected = probe(AdapterType.OPENROUTER_MESSAGES, 2048L, Map.of(), options(), mappings);
            assertRejected(rejected, request(5000, prepared),
                "maxOutputTokens must not exceed model limit 2048");
        }
    }

    @Test
    void nativeOutputLengthsAreNeverReplacedByTheAutomaticDefault() {
        for (var prepared : List.of(false, true)) {
            var accepted = probe(AdapterType.OPENROUTER_MESSAGES, 2048L,
                Map.of("max_tokens", 1536));
            assertAccepted(accepted, request(null, prepared));
            assertThat(accepted.bodies()).allSatisfy(body ->
                assertThat(body).containsEntry("max_tokens", 1536));

            var rejected = probe(AdapterType.OPENROUTER_MESSAGES, 2048L,
                Map.of("max_tokens", 5000));
            assertRejected(rejected, request(null, prepared),
                "max_tokens must not exceed model limit 2048");

            var completionAlias = probe(AdapterType.OPENROUTER_MESSAGES, 8192L,
                Map.of("max_completion_tokens", 16384));
            assertRejected(completionAlias, request(null, prepared),
                "max_completion_tokens must not exceed model limit 8192");
        }
    }

    @Test
    void explicitTransportDefaultsArePreservedAndValidated() {
        for (var options : List.of(
            options().mutate().maxTokens(1536).build(),
            options().mutate().maxCompletionTokens(1536).build(),
            options().mutate().extraBody(Map.of("max_tokens", 1536)).build())) {
            var probe = probe(AdapterType.OPENROUTER_MESSAGES, 2048L, Map.of(), options);
            assertAccepted(probe, request(null, false));
            assertThat(probe.bodies()).allSatisfy(body ->
                assertThat(body).containsEntry("max_tokens", 1536));
        }
        var rejected = probe(AdapterType.OPENROUTER_MESSAGES, 2048L, Map.of(),
            options().mutate().maxTokens(5000).build());
        assertRejected(rejected, request(null, false),
            "max_tokens must not exceed model limit 2048");
    }

    @Test
    void chatAndResponsesDoNotGainAMessagesDefault() {
        for (var adapter : List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_RESPONSES)) {
            for (var prepared : List.of(false, true)) {
                var probe = probe(adapter, 2048L, Map.of());
                assertAccepted(probe, request(null, prepared));
                assertThat(probe.bodies()).allSatisfy(body -> assertThat(body)
                    .doesNotContainKeys("max_tokens", "max_completion_tokens", "max_output_tokens"));
            }
        }
    }

    private void assertAccepted(WireProbe probe, GenerateTextRequest request) {
        probe.model().generateText(request).onErrorResume(error -> {
            assertThat(error).isInstanceOf(ProviderHttpException.class);
            return Mono.empty();
        }).block();
        probe.model().streamText(request).fullStream().collectList().block();
        // A deliberate HTTP error is returned only after capturing the final outgoing JSON.
        assertThat(probe.bodies()).hasSize(2);
    }

    private void assertRejected(WireProbe probe, GenerateTextRequest request, String message) {
        StepVerifier.create(probe.model().generateText(request)).expectErrorMessage(message).verify();
        var errors = probe.model().streamText(request).fullStream()
            .filter(part -> PartType.ERROR.equals(part.getType())).collectList().block();
        assertThat(errors).singleElement().satisfies(error ->
            assertThat(error.getErrorText()).isEqualTo(message));
        assertThat(probe.bodies()).isEmpty();
    }

    private GenerateTextRequest request(Integer tokens, boolean prepared) {
        return GenerateTextRequest.builder().prompt("Hello").maxRetries(0)
            .maxOutputTokens(prepared ? null : tokens)
            .prepareStep(prepared ? step -> PreparedStep.builder().maxOutputTokens(tokens).build() : null)
            .build();
    }

    private WireProbe probe(AdapterType adapter, Long limit, Map<String, Object> nativeOptions) {
        return probe(adapter, limit, nativeOptions, options());
    }

    private WireProbe probe(AdapterType adapter, Long limit, Map<String, Object> nativeOptions,
        ChatCompletionsOptions options) {
        return probe(adapter, limit, nativeOptions, options, RuntimeParameterMappings.empty());
    }

    private WireProbe probe(AdapterType adapter, Long limit, Map<String, Object> nativeOptions,
        ChatCompletionsOptions options, RuntimeParameterMappings mappings) {
        var bodies = new ArrayList<Map<String, Object>>();
        var webClient = WebClient.builder().exchangeFunction(request -> {
            var output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                .then(Mono.defer(output::getBodyAsString)).flatMap(body -> Mono.fromCallable(() -> {
                    bodies.add(JSON.readValue(body, new TypeReference<>() { }));
                    return ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":{\"message\":\"wire probe captured\"}}").build();
                }));
        });
        ChatModel chat = switch (adapter) {
            case OPENROUTER_MESSAGES -> new OpenRouterMessagesModel(options, webClient);
            case OPENAI_CHAT -> new OpenAiChatModel(options, webClient);
            case OPENAI_RESPONSES -> new OpenAiResponsesModel(options, webClient);
            default -> throw new IllegalArgumentException("Unexpected test adapter");
        };
        AiProviderType provider = adapter == AdapterType.OPENROUTER_MESSAGES
            ? new OpenRouterProvider() : new OpenAiProvider();
        var constraints = new ModelParameterConstraints();
        constraints.setOutputTokens(limit);
        var context = new ModelRuntimeContext("model", "test-model", "provider", provider.getProviderType(),
            adapter, provider, mappings, nativeOptions, constraints);
        var configuration = LanguageModelRuntimeConfiguration.from(context, ModelCapabilities.empty());
        var composition = LanguageModelRuntimeComposition.create(configuration,
            new LanguageModelRuntimeSupport(), null, null);
        return new WireProbe(new LanguageModelImpl(chat, composition, context), bodies);
    }

    private ChatCompletionsOptions options() {
        return ChatCompletionsOptions.builder().baseUrl("https://wire.invalid")
            .model("test-model").maxRetries(0).build();
    }

    private record WireProbe(LanguageModelImpl model, List<Map<String, Object>> bodies) {
    }
}