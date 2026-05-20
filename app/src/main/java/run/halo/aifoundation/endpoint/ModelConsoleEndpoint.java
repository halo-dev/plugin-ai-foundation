package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.ChatChunk;
import run.halo.aifoundation.ChatRequest;
import run.halo.aifoundation.ChunkType;
import run.halo.aifoundation.Message;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.extension.router.selector.SelectorUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConsoleEndpoint implements CustomEndpoint {

    private static final int MAX_NAME_GENERATION_ATTEMPTS = 10;

    private final ReactiveExtensionClient client;
    private final AiModelService aiModelService;
    private final ProviderClientCache providerClientCache;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/Model";
        return route()
            .GET("models", this::listModels,
                builder -> builder.operationId("ListModels")
                    .description("List all AI models.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("labelSelector")
                        .in(ParameterIn.QUERY)
                        .description("Label selector for filtering models")
                        .implementationArray(String.class))
                    .parameter(parameterBuilder()
                        .name("fieldSelector")
                        .in(ParameterIn.QUERY)
                        .description("Field selector for filtering models (e.g., spec.providerName=openai)")
                        .implementationArray(String.class))
                    .response(responseBuilder()
                        .implementationArray(AiModel.class))
            )
            .POST("models", this::createModel,
                builder -> builder.operationId("CreateModel")
                    .description("Create a new AI model.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(AiModel.class))
                    .response(responseBuilder().implementation(AiModel.class))
            )
            .PUT("models/{name}", this::updateModel,
                builder -> builder.operationId("UpdateModel")
                    .description("Update an AI model.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(AiModel.class))
                    .response(responseBuilder().implementation(AiModel.class))
            )
            .DELETE("models/{name}", this::deleteModel,
                builder -> builder.operationId("DeleteModel")
                    .description("Delete an AI model.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name")
                        .implementation(String.class)
                        .required(true))
                    .response(responseBuilder().implementation(Void.class))
            )
            .POST("models/{name}/test-chat/stream", this::testChatStream,
                builder -> builder.operationId("TestModelChatStream")
                    .description("Test chat completion with streaming response.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(false)
                        .implementation(TestChatRequest.class))
                    .response(responseBuilder()
                        .implementation(ChatChunk.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listModels(ServerRequest request) {
        var queryParams = request.queryParams();
        var fieldSelector = queryParams.getOrDefault("fieldSelector", List.of());
        var labelSelector = queryParams.getOrDefault("labelSelector", List.of());
        var listOptions = SelectorUtil.labelAndFieldSelectorToListOptions(labelSelector, fieldSelector);
        return client.listAll(AiModel.class, listOptions,
            Sort.by("metadata.creationTimestamp").descending())
            .collectList()
            .flatMap(models -> ServerResponse.ok().bodyValue(models));
    }

    private Mono<ServerResponse> createModel(ServerRequest request) {
        return request.bodyToMono(AiModel.class)
            .flatMap(model -> validateModel(model)
                .then(Mono.defer(() -> checkModelUniqueness(model, null)))
                .then(Mono.defer(() -> {
                    if (model.getMetadata() == null) {
                        model.setMetadata(new Metadata());
                    }
                    var providerName = model.getSpec().getProviderName();
                    var modelId = model.getSpec().getModelId();
                    return createWithGeneratedName(model, providerName, modelId, 0);
                }))
            )
            .flatMap(created -> ServerResponse.ok().bodyValue(created));
    }

    private Mono<ServerResponse> updateModel(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(AiModel.class)
            .flatMap(model -> validateModel(model)
                .then(Mono.defer(() -> checkModelUniqueness(model, name)))
                .then(client.fetch(AiModel.class, name)
                    .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Model not found: " + name)))
                    .flatMap(existing -> {
                        existing.setSpec(model.getSpec());
                        return client.update(existing);
                    })
                )
            )
            .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> deleteModel(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AiModel.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: " + name)))
            .flatMap(client::delete)
            .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> testChatStream(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testChatStream: modelName={}", modelName);

        return request.bodyToMono(TestChatRequest.class)
            .defaultIfEmpty(new TestChatRequest())
            .flatMap(body -> validateTestChatRequest(body).then(Mono.defer(() -> {
                var chatRequest = ChatRequest.builder()
                    .messages(body.getMessages())
                    .temperature(body.getTemperature())
                    .maxTokens(body.getMaxTokens())
                    .topP(body.getTopP())
                    .providerOptions(body.getProviderOptions())
                    .build();
                Flux<ChatChunk> flux = aiModelService.languageModel(modelName)
                    .flatMapMany(languageModel -> languageModel.streamChat(chatRequest))
                    .onErrorResume(e -> {
                        log.error("Stream chat failed for model: {}", modelName, e);
                        return Flux.just(ChatChunk.builder()
                            .type(ChunkType.ERROR)
                            .content("Chat test failed: " + e.getMessage())
                            .build());
                    });
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(flux, ChatChunk.class);
            })));
    }

    private Mono<Void> validateTestChatRequest(TestChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "messages must not be empty"));
        }
        for (var message : request.getMessages()) {
            if (message == null || message.getRole() == null || message.getRole().isBlank()
                || message.getContent() == null || message.getContent().isBlank()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "messages must include non-empty role and content"));
            }
        }
        return Mono.empty();
    }

    private Mono<AiModel> createWithGeneratedName(AiModel model, String providerName, String modelId,
        int attempt) {
        if (attempt >= MAX_NAME_GENERATION_ATTEMPTS) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                "Could not generate a unique model resource name"));
        }
        var name = AiModelNameGenerator.generate(providerName, modelId, attempt);
        model.getMetadata().setName(name);
        return client.fetch(AiModel.class, name)
            .flatMap(existing -> createWithGeneratedName(model, providerName, modelId, attempt + 1))
            .switchIfEmpty(Mono.defer(() -> client.create(model)));
    }

    private Mono<Void> validateModel(AiModel model) {
        if (model.getSpec() == null) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model spec is required"));
        }
        var providerName = model.getSpec().getProviderName();
        var modelId = model.getSpec().getModelId();
        var modelType = model.getSpec().getModelType();

        if (providerName == null || providerName.isBlank()) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerName is required"));
        }
        if (modelId == null || modelId.isBlank()) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelId is required"));
        }
        if (modelType == null) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelType is required"));
        }
        normalizeProfileDefaults(model.getSpec());

        return client.fetch(AiProvider.class, providerName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Provider not found: " + providerName)))
            .flatMap(provider -> {
                var providerType = provider.getSpec().getProviderType();
                var type = providerClientCache.getProviderTypeMap().get(providerType);
                if (type == null) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported provider type: " + providerType));
                }
                if (!type.getSupportedModelTypes().contains(modelType)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Model type '" + modelType.getValue()
                            + "' is not supported by provider type '" + providerType
                            + "'. Supported model types: " + type.getSupportedModelTypes()));
                }
                var unsupportedFeatures = model.getSpec().getFeatures().stream()
                    .filter(feature -> !type.getSupportedFeatures().contains(feature))
                    .toList();
                if (!unsupportedFeatures.isEmpty()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Model features " + unsupportedFeatures
                            + " are not supported by provider type '" + providerType
                            + "'. Supported features: " + type.getSupportedFeatures()));
                }
                applyDefaultAdapterType(model, type);
                var adapterType = model.getSpec().getAdapterType();
                if (adapterType == null) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "adapterType is required and no supported default could be recommended"));
                }
                var supportedTypes = type.getSupportedAdapterTypes();
                if (!supportedTypes.contains(adapterType)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Adapter type '" + adapterType.getValue() + "' is not supported by provider type '"
                            + providerType + "'. Supported types: " + supportedTypes));
                }
                return Mono.empty();
            });
    }

    private void normalizeProfileDefaults(AiModel.AiModelSpec spec) {
        if (spec.getFeatures() == null) {
            spec.setFeatures(List.of());
        }
        if (spec.getDiscoverySource() == null) {
            spec.setDiscoverySource(DiscoverySource.MANUAL);
        }
        if (spec.getDiscoveryConfidence() == null) {
            spec.setDiscoveryConfidence(DiscoveryConfidence.HIGH);
        }
    }

    private void applyDefaultAdapterType(AiModel model, AiProviderType providerType) {
        var spec = model.getSpec();
        var adapterType = spec.getAdapterType();
        if (adapterType != null) {
            return;
        }
        providerType.recommendAdapterType(spec.getModelType()).ifPresent(spec::setAdapterType);
    }

    private Mono<Void> checkModelUniqueness(AiModel model, String excludeName) {
        var providerName = model.getSpec().getProviderName();
        var modelId = model.getSpec().getModelId();
        var listOptions = ListOptions.builder()
            .fieldQuery(Queries.and(
                Queries.equal("spec.providerName", providerName),
                Queries.equal("spec.modelId", modelId)
            ))
            .build();
        return client.listAll(AiModel.class, listOptions, Sort.unsorted())
            .filter(existing -> excludeName == null
                || !excludeName.equals(existing.getMetadata().getName()))
            .next()
            .flatMap(existing -> Mono.<Void>error(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A model with providerName='" + providerName
                    + "' and modelId='" + modelId + "' already exists")))
            .switchIfEmpty(Mono.empty());
    }

    @Data
    static class TestChatRequest {
        private List<Message> messages;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Map<String, Object> providerOptions;
    }
}
