package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingResponseMetadata;
import run.halo.aifoundation.embedding.EmbeddingUsage;
import run.halo.aifoundation.embedding.EmbeddingUtils;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.extension.AiModel;
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
    private static final String CONSOLE_EXTERNAL_TEST_TOOL_NAME = "halo_external_test_info";
    private static final String CONSOLE_REPAIR_TEST_TOOL_NAME = "halo_repair_test_info";

    private final ReactiveExtensionClient client;
    private final AiModelService aiModelService;
    private final ModelConsoleModelValidator modelValidator;

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
                    .parameter(parameterBuilder()
                        .name("enableTestToolApproval")
                        .in(ParameterIn.QUERY)
                        .description("Whether the console-only halo_test_info tool should require "
                            + "caller approval before execution.")
                        .implementation(Boolean.class)
                        .required(false))
                    .parameter(parameterBuilder()
                        .name("enableToolCallRepair")
                        .in(ParameterIn.QUERY)
                        .description("Whether to inject a console-only repairable tool and "
                            + "deterministic tool-call repair callback.")
                        .implementation(Boolean.class)
                        .required(false))
                    .requestBody(requestBodyBuilder()
                        .required(false)
                        .implementation(GenerateTextRequest.class))
                    .response(responseBuilder()
                        .description("Server-Sent Events using X-Halo-AI-Stream-Protocol: text-v1. "
                            + "Each data event contains a TextStreamPart JSON object. The stream can "
                            + "include message lifecycle, step lifecycle, text, tool call, tool result, "
                            + "tool approval request, tool error, finish, sanitized raw diagnostic, "
                            + "and error parts, then ends with data: [DONE].")
                        .implementation(TextStreamPart.class))
            )
            .POST("models/{name}/test-embedding", this::testEmbedding,
                builder -> builder.operationId("TestModelEmbedding")
                    .description("Test embedding generation with embedding settings and diagnostics.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestEmbeddingRequest.class))
                    .response(responseBuilder().implementation(TestEmbeddingResponse.class))
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
                var chatRequest = withConsoleTestTool(body, ConsoleTestToolOptions.from(request));
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

    private Mono<ServerResponse> testEmbedding(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testEmbedding: modelName={}", modelName);
        return request.bodyToMono(TestEmbeddingRequest.class)
            .flatMap(body -> validateTestEmbeddingRequest(body)
                .then(aiModelService.embeddingModel(modelName))
                .flatMap(model -> model.embed(EmbeddingRequest.builder()
                        .inputs(body.getInputs())
                        .dimensions(body.getDimensions())
                        .maxBatchSize(body.getMaxBatchSize())
                        .maxParallelCalls(body.getMaxParallelCalls())
                        .maxRetries(body.getMaxRetries())
                        .providerOptions(body.getProviderOptions())
                        .metadata(Map.of("source", "console-test"))
                        .build())
                    .map(TestEmbeddingResponse::from)))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private static boolean consoleTestToolEnabled(ServerRequest request) {
        return request.queryParam("enableTestTool")
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private static boolean consoleTestToolApprovalEnabled(ServerRequest request) {
        return request.queryParam("enableTestToolApproval")
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private static boolean consoleExternalTestToolEnabled(ServerRequest request) {
        return request.queryParam("enableExternalTestTool")
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private static boolean consoleToolCallRepairEnabled(ServerRequest request) {
        return request.queryParam("enableToolCallRepair")
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private GenerateTextRequest withConsoleTestTool(GenerateTextRequest request,
        ConsoleTestToolOptions options) {
        if (!options.hasAnyEnabled()) {
            return request;
        }
        var tools = new ArrayList<ToolDefinition>();
        if (request.getTools() != null) {
            tools.addAll(request.getTools());
        }
        tools.removeIf(tool -> CONSOLE_TEST_TOOL_NAME.equals(tool.getName())
            || CONSOLE_EXTERNAL_TEST_TOOL_NAME.equals(tool.getName())
            || CONSOLE_REPAIR_TEST_TOOL_NAME.equals(tool.getName()));
        if (options.basicEnabled() || options.approvalEnabled()) {
            tools.add(consoleTestTool(options.approvalEnabled()));
        }
        if (options.externalEnabled()) {
            tools.add(consoleExternalTestTool());
        }
        if (options.repairEnabled()) {
            tools.add(consoleRepairTestTool());
            request.setToolCallRepair(context -> {
                if (!CONSOLE_REPAIR_TEST_TOOL_NAME.equals(context.getToolCall().getToolName())) {
                    return Mono.just(ToolCallRepairResult.unrepaired());
                }
                var repairedQuery = repairedConsoleQuery(context.getToolCall().getInput());
                if (repairedQuery == null || repairedQuery.isBlank()) {
                    return Mono.just(ToolCallRepairResult.unrepaired());
                }
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of(
                        "query", repairedQuery,
                        "repairSource", "console-test"
                    ))
                    .build()));
            });
        }
        request.setTools(List.copyOf(tools));
        request.setStopWhen(StopCondition.stepCountIs(2));
        return request;
    }

    private ToolDefinition consoleTestTool(boolean approvalEnabled) {
        return ToolDefinition.builder()
            .name(CONSOLE_TEST_TOOL_NAME)
            .description("Halo 控制台模型测试工具。用于验证工具调用链路；当用户要求测试工具、"
                + "回显输入、获取 Halo 测试信息时调用。")
            .needsApproval(approvalEnabled)
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

    private ToolDefinition consoleRepairTestTool() {
        return ToolDefinition.builder()
            .name(CONSOLE_REPAIR_TEST_TOOL_NAME)
            .description("Halo 控制台工具调用修复测试工具。用于验证模型输出错误参数后，"
                + "后台 repair callback 将参数修复为 query 并继续执行工具。")
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "需要测试工具处理的文本"
                    ),
                    "repairSource", Map.of(
                        "type", "string",
                        "description", "修复来源"
                    )
                ),
                "required", List.of("query")
            ))
            .executor(context -> Mono.just(Map.of(
                "tool", CONSOLE_REPAIR_TEST_TOOL_NAME,
                "message", "Halo console repair test tool executed successfully.",
                "query", context.getInput() != null
                    ? context.getInput().getOrDefault("query", "")
                    : "",
                "repairSource", context.getInput() != null
                    ? context.getInput().getOrDefault("repairSource", "")
                    : "",
                "nextAction", "Answer the user using this repaired tool result."
            )))
            .build();
    }

    private String repairedConsoleQuery(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        for (var key : List.of("query", "text", "message", "prompt", "q")) {
            var value = input.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return input.values().stream()
            .filter(value -> value != null && !String.valueOf(value).isBlank())
            .map(String::valueOf)
            .findFirst()
            .orElse(null);
    }

    private ToolDefinition consoleExternalTestTool() {
        return ToolDefinition.builder()
            .name(CONSOLE_EXTERNAL_TEST_TOOL_NAME)
            .description("Halo 控制台外部工具测试工具。用于验证工具调用返回后由调用方在外部执行，"
                + "再把结果或错误追加到消息历史中继续生成。")
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "希望外部系统查询或处理的文本"
                    )
                )
            ))
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

    private Mono<Void> validateTestEmbeddingRequest(TestEmbeddingRequest request) {
        if (request == null || request.getInputs() == null || request.getInputs().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At least one input is required"));
        }
        if (request.getInputs().size() > 20) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At most 20 inputs can be tested at once"));
        }
        for (var input : request.getInputs()) {
            if (input == null || input.isBlank()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Embedding inputs must not be blank"));
            }
        }
        return Mono.empty();
    }

    private boolean isSupportedTestChatPart(run.halo.aifoundation.message.ModelMessageRole role,
        run.halo.aifoundation.message.ModelMessagePart part) {
        if (PartType.isText(part.getType())) {
            return part.getText() != null && !part.getText().isBlank();
        }
        if (role == run.halo.aifoundation.message.ModelMessageRole.ASSISTANT) {
            return isSupportedAssistantTestChatPart(part);
        }
        if (role == run.halo.aifoundation.message.ModelMessageRole.TOOL) {
            return isSupportedToolTestChatPart(part);
        }
        return false;
    }

    private boolean isSupportedAssistantTestChatPart(
        run.halo.aifoundation.message.ModelMessagePart part) {
        return (PartType.isReasoning(part.getType())
                && (part.getText() != null && !part.getText().isBlank()))
            || (PartType.TOOL_CALL.equals(part.getType())
                && hasText(part.getToolCallId())
                && hasText(part.getToolName()))
            || (PartType.TOOL_APPROVAL_REQUEST.equals(part.getType())
                && hasText(part.getApprovalId())
                && hasText(part.getToolCallId())
                && hasText(part.getToolName()));
    }

    private boolean isSupportedToolTestChatPart(
        run.halo.aifoundation.message.ModelMessagePart part) {
        return (PartType.TOOL_RESULT.equals(part.getType())
                && hasText(part.getToolCallId())
                && hasText(part.getToolName()))
            || (PartType.TOOL_ERROR.equals(part.getType())
                && hasText(part.getToolCallId())
                && hasText(part.getToolName())
                && hasText(part.getErrorText()))
            || (PartType.TOOL_APPROVAL_RESPONSE.equals(part.getType())
                && hasText(part.getApprovalId())
                && part.getApproved() != null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ConsoleTestToolOptions(
        boolean basicEnabled,
        boolean approvalEnabled,
        boolean externalEnabled,
        boolean repairEnabled
    ) {
        static ConsoleTestToolOptions from(ServerRequest request) {
            return new ConsoleTestToolOptions(
                consoleTestToolEnabled(request),
                consoleTestToolApprovalEnabled(request),
                consoleExternalTestToolEnabled(request),
                consoleToolCallRepairEnabled(request)
            );
        }

        boolean hasAnyEnabled() {
            return basicEnabled || approvalEnabled || externalEnabled || repairEnabled;
        }
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
        return modelValidator.validate(model);
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestEmbeddingRequest {
        private List<String> inputs;
        private Integer dimensions;
        private Integer maxBatchSize;
        private Integer maxParallelCalls;
        private Integer maxRetries;
        private Map<String, Map<String, Object>> providerOptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestEmbeddingResponse {
        private int embeddingsCount;
        private List<EmbeddingPreview> embeddings;
        private Double firstPairSimilarity;
        private EmbeddingUsage usage;
        private EmbeddingResponseMetadata response;
        private List<EmbeddingWarning> warnings;
        private Map<String, Object> providerMetadata;

        static TestEmbeddingResponse from(run.halo.aifoundation.embedding.EmbeddingResponse response) {
            var vectors = response.getEmbeddings() != null
                ? response.getEmbeddings()
                : List.<float[]>of();
            return TestEmbeddingResponse.builder()
                .embeddingsCount(vectors.size())
                .embeddings(previews(vectors))
                .firstPairSimilarity(similarity(vectors))
                .usage(response.getUsage())
                .response(response.getResponse())
                .warnings(response.getWarnings() != null ? response.getWarnings() : List.of())
                .providerMetadata(response.getProviderMetadata())
                .build();
        }

        private static List<EmbeddingPreview> previews(List<float[]> vectors) {
            var result = new ArrayList<EmbeddingPreview>();
            for (int i = 0; i < vectors.size(); i++) {
                var vector = vectors.get(i);
                result.add(EmbeddingPreview.builder()
                    .index(i)
                    .dimensions(vector != null ? vector.length : 0)
                    .preview(preview(vector))
                    .build());
            }
            return List.copyOf(result);
        }

        private static List<Float> preview(float[] vector) {
            if (vector == null) {
                return List.of();
            }
            var size = Math.min(vector.length, 8);
            var values = new ArrayList<Float>();
            for (int i = 0; i < size; i++) {
                values.add(vector[i]);
            }
            return List.copyOf(values);
        }

        private static Double similarity(List<float[]> vectors) {
            if (vectors.size() < 2) {
                return null;
            }
            return EmbeddingUtils.cosineSimilarity(vectors.get(0), vectors.get(1));
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingPreview {
        private int index;
        private int dimensions;
        private List<Float> preview;
    }

}
