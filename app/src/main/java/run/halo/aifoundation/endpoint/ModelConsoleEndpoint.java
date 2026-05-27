package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.PartType;
import run.halo.aifoundation.TextStreamPart;
import run.halo.aifoundation.ToolDefinition;
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
    private static final String HALO_AI_STREAM_PROTOCOL_HEADER = "X-Halo-AI-Stream-Protocol";
    private static final String HALO_AI_TEXT_STREAM_PROTOCOL = "text-v1";
    private static final String CONSOLE_TEST_TOOL_NAME = "halo_test_info";

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
                    .description("Test text generation with Halo text stream response.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .parameter(parameterBuilder()
                        .name("enableTestTool")
                        .in(ParameterIn.QUERY)
                        .description("Whether to inject the console-only halo_test_info tool for "
                            + "tool calling tests.")
                        .implementation(Boolean.class)
                        .required(false))
                    .requestBody(requestBodyBuilder()
                        .required(false)
                        .implementation(GenerateTextRequest.class))
                    .response(responseBuilder()
                        .description("Server-Sent Events using X-Halo-AI-Stream-Protocol: text-v1. "
                            + "Each data event contains a TextStreamPart JSON object. The stream can "
                            + "include message lifecycle, step lifecycle, text, tool call, tool result, "
                            + "tool error, finish, sanitized raw diagnostic, and error parts, then ends "
                            + "with data: [DONE].")
                        .implementation(TextStreamPart.class))
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

        return request.bodyToMono(GenerateTextRequest.class)
            .defaultIfEmpty(new GenerateTextRequest())
            .flatMap(body -> validateTestChatRequest(body).then(Mono.defer(() -> {
                var chatRequest = withConsoleTestTool(body, consoleTestToolEnabled(request));
                Flux<ServerSentEvent<Object>> flux = aiModelService.languageModel(modelName)
                    .flatMapMany(languageModel -> languageModel.streamText(chatRequest).fullStream())
                    .onErrorResume(e -> {
                        log.error("Stream chat failed for model: {}", modelName, e);
                        return Flux.just(TextStreamPart.error("Chat test failed: " + e.getMessage()));
                    })
                    .map(part -> ServerSentEvent.builder((Object) part).build())
                    .concatWith(Mono.just(ServerSentEvent.builder((Object) "[DONE]").build()));
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HALO_AI_STREAM_PROTOCOL_HEADER, HALO_AI_TEXT_STREAM_PROTOCOL)
                    .body(flux, ServerSentEvent.class);
            })));
    }

    private boolean consoleTestToolEnabled(ServerRequest request) {
        return request.queryParam("enableTestTool")
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private GenerateTextRequest withConsoleTestTool(GenerateTextRequest request, boolean enabled) {
        if (!enabled) {
            return request;
        }
        var tools = new ArrayList<ToolDefinition>();
        if (request.getTools() != null) {
            tools.addAll(request.getTools());
        }
        var hasTestTool = tools.stream()
            .anyMatch(tool -> CONSOLE_TEST_TOOL_NAME.equals(tool.getName()));
        if (!hasTestTool) {
            tools.add(consoleTestTool());
        }
        request.setTools(List.copyOf(tools));
        if (request.getMaxSteps() == null || request.getMaxSteps() < 2) {
            request.setMaxSteps(2);
        }
        return request;
    }

    private ToolDefinition consoleTestTool() {
        return ToolDefinition.builder()
            .name(CONSOLE_TEST_TOOL_NAME)
            .description("Halo 控制台模型测试工具。用于验证工具调用链路；当用户要求测试工具、"
                + "回显输入、获取 Halo 测试信息时调用。")
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "用户想让测试工具回显或处理的文本"
                    )
                )
            ))
            .executor(context -> Mono.just(Map.of(
                "tool", CONSOLE_TEST_TOOL_NAME,
                "message", "Halo console test tool executed successfully.",
                "query", context.getInput() != null
                    ? context.getInput().getOrDefault("query", "")
                    : "",
                "nextAction", "Answer the user using this tool result."
            )))
            .build();
    }

    private Mono<Void> validateTestChatRequest(GenerateTextRequest request) {
        var hasPrompt = request.getPrompt() != null && !request.getPrompt().isBlank();
        var hasMessages = request.getMessages() != null && !request.getMessages().isEmpty();
        if (hasPrompt == hasMessages) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "exactly one of prompt or messages must be provided"));
        }
        if (request.getPrompt() != null && request.getPrompt().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "prompt must not be blank"));
        }
        if (request.getSystem() != null && request.getSystem().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "system must not be blank"));
        }
        if (hasMessages) {
            for (var message : request.getMessages()) {
                if (message == null || message.getRole() == null
                    || message.getContent() == null || message.getContent().isEmpty()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "messages must include role and content"));
                }
                for (var part : message.getContent()) {
                    if (part == null || !isSupportedTestChatPart(message.getRole(), part)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "messages must include non-empty supported content parts"));
                    }
                }
            }
        }
        return Mono.empty();
    }

    private boolean isSupportedTestChatPart(run.halo.aifoundation.ModelMessageRole role,
        run.halo.aifoundation.ModelMessagePart part) {
        if (PartType.isText(part.getType())) {
            return part.getText() != null && !part.getText().isBlank();
        }
        return role == run.halo.aifoundation.ModelMessageRole.ASSISTANT
            && PartType.isReasoning(part.getType())
            && part.getText() != null
            && !part.getText().isBlank();
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

}
