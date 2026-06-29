package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.rag.RagEmptyContextPolicy;
import run.halo.aifoundation.rag.RagFailurePolicy;
import run.halo.aifoundation.rag.RagLanguageModelMiddleware;
import run.halo.aifoundation.rag.RagLifecycle;
import run.halo.aifoundation.rag.RagLifecycleEvent;
import run.halo.aifoundation.rag.RagMiddlewareOptions;
import run.halo.aifoundation.rag.RagPromptPlacement;
import run.halo.aifoundation.rag.RagSourceRerankRequest;
import run.halo.aifoundation.rag.RagSourceReranker;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankResponseMetadata;
import run.halo.aifoundation.rerank.RerankResult;
import run.halo.aifoundation.rerank.RerankUsage;
import run.halo.aifoundation.rerank.RerankWarning;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.source.RetrievedContext;
import run.halo.aifoundation.source.RetrievedSource;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.ui.InvalidUIMessageException;
import run.halo.aifoundation.ui.UIMessageCancellation;
import run.halo.aifoundation.ui.UIMessageCancellations;
import run.halo.aifoundation.ui.UIMessageChatHandlers;
import run.halo.aifoundation.ui.UIMessageChatRequest;
import run.halo.aifoundation.ui.UIMessageChatTrigger;
import run.halo.aifoundation.ui.UIMessageChunk;
import run.halo.aifoundation.ui.UIMessageChunks;
import run.halo.aifoundation.ui.UIMessageStream;
import run.halo.aifoundation.ui.UIMessageStreamResponse;
import run.halo.aifoundation.ui.UIMessageTransportCodec;
import run.halo.aifoundation.extension.AiModel;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.extension.router.selector.SelectorUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConsoleEndpoint implements CustomEndpoint {

    private static final int MAX_NAME_GENERATION_ATTEMPTS = 10;
    private static final String CONSOLE_TEST_TOOL_NAME = "halo_test_info";
    private static final String CONSOLE_EXTERNAL_TEST_TOOL_NAME = "halo_external_test_info";
    private static final String CONSOLE_AGENT_PAGE_CONTEXT_TOOL_NAME = "get_current_page_context";
    private static final String CONSOLE_AGENT_ECHO_TOOL_NAME = "halo_agent_test_action";
    private static final String CONSOLE_REPAIR_TEST_TOOL_NAME = "halo_repair_test_info";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

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
            .POST("models/{name}/test-chat/ui-message/stream", this::testUiMessageChatStream,
                builder -> builder.operationId("TestModelUiMessageChatStream")
                    .description("Test text generation with Halo UI Message stream response.")
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
                        .name("enableExternalTestTool")
                        .in(ParameterIn.QUERY)
                        .description("Whether to inject the console-only halo_external_test_info "
                            + "tool that must be executed by the workbench caller.")
                        .implementation(Boolean.class)
                        .required(false))
                    .parameter(parameterBuilder()
                        .name("enableToolCallRepair")
                        .in(ParameterIn.QUERY)
                        .description("Whether to inject a console-only repairable tool and "
                            + "deterministic tool-call repair callback.")
                        .implementation(Boolean.class)
                        .required(false))
                    .parameter(parameterBuilder()
                        .name("enableAgentTestTools")
                        .in(ParameterIn.QUERY)
                        .description("Whether to inject console-only browser Agent test tools "
                            + "that are executed by the workbench frontend.")
                        .implementation(Boolean.class)
                        .required(false))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestUiMessageChatRequest.class))
                    .response(responseBuilder()
                        .description("Server-Sent Events using the Halo UI Message stream protocol. "
                            + "Each data event contains a UIMessageChunk JSON object and the stream "
                            + "ends with data: [DONE].")
                        .implementation(UIMessageChunk.class))
            )
            .POST("models/{name}/test-completion/stream", this::testCompletionStream,
                builder -> builder.operationId("TestModelCompletionStream")
                    .description("Test prompt completion with plain text stream response.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestCompletionStreamRequest.class))
                    .response(responseBuilder()
                        .description("Plain text stream containing generated text deltas.")
                        .implementation(String.class))
            )
            .POST("models/{name}/test-object/stream", this::testObjectStream,
                builder -> builder.operationId("TestModelObjectStream")
                    .description("Test structured object generation with plain JSON text stream response.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestObjectStreamRequest.class))
                    .response(responseBuilder()
                        .description("Plain text stream containing generated JSON text deltas. "
                            + "The final generated value is validated as structured output.")
                        .implementation(String.class))
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
            .POST("models/{name}/test-rerank", this::testRerank,
                builder -> builder.operationId("TestModelRerank")
                    .description("Test reranking with candidate documents and diagnostics.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestRerankRequest.class))
                    .response(responseBuilder().implementation(TestRerankResponse.class))
            )
            .POST("models/{name}/test-image-generation", this::testImageGeneration,
                builder -> builder.operationId("TestModelImageGeneration")
                    .description("Test image generation with prompt, optional input images, and diagnostics.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Image generation model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestImageGenerationRequest.class))
                    .response(responseBuilder().implementation(TestImageGenerationResponse.class))
            )
            .POST("models/{name}/test-rag/ui-message/stream", this::testRagUiMessageStream,
                builder -> builder.operationId("TestModelRagUiMessageStream")
                    .description("Test single-query RAG with manual sources and Halo UI Message "
                        + "stream response.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Language model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(TestRagRequest.class))
                    .response(responseBuilder()
                        .description("Server-Sent Events using the Halo UI Message stream protocol.")
                        .implementation(UIMessageChunk.class))
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

    private Mono<ServerResponse> testUiMessageChatStream(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testUiMessageChatStream: modelName={}", modelName);

        return request.bodyToMono(TestUiMessageChatRequest.class)
            .flatMap(body -> validateTestUiMessageChatRequest(body).then(Mono.defer(() -> {
                var cancellation = UIMessageCancellations.create();
                return aiModelService.languageModel(modelName)
                    .map(languageModel -> UIMessageChatHandlers.<Map<String, Object>>streamText(
                        options -> options
                            .model(languageModel)
                            .chatRequest(toUiMessageChatRequest(body))
                            .metadataSupplier(() -> new LinkedHashMap<>())
                            .serializer(ModelConsoleEndpoint::writeJson)
                            .request(builder -> applyConsoleGenerationOptions(builder, body,
                                ConsoleTestToolOptions.from(request)))
                            .cancellationToken(cancellation.token())
                            .onError(ModelConsoleEndpoint::safeMessage)
                            .onFinish(finish -> log.debug(
                                "UI message chat test finished: modelName={}, messages={}, "
                                    + "aborted={}, errorText={}",
                                modelName, finish.messages().size(), finish.terminal().aborted(),
                                finish.terminal().errorText()
                            ))
                    ))
                    .flatMap(chat -> uiMessageStreamResponse(chat.response(), cancellation));
            })))
            .onErrorResume(error -> {
                if (error instanceof ResponseStatusException) {
                    return Mono.error(error);
                }
                log.error("UI message stream chat failed for model: {}, error={}",
                    modelName, safeMessage(error), error);
                return uiMessageStreamResponse(new UIMessageStreamResponse(new UIMessageStream(
                    Flux.just(UIMessageChunks.error(safeMessage(error), null,
                        Map.of("exceptionType", "error")))
                ), ModelConsoleEndpoint::writeJson), UIMessageCancellations.create());
            });
    }

    private Mono<ServerResponse> uiMessageStreamResponse(UIMessageStreamResponse response,
        UIMessageCancellation cancellation) {
        Flux<ServerSentEvent<Object>> flux = cancellation.cancelWhenSubscriberCancels(
                response.stream())
            .onErrorResume(error -> {
                log.error("UI message stream failed: {}", safeMessage(error), error);
                return Flux.just(UIMessageChunks.error(safeMessage(error), null,
                    Map.of("exceptionType", "error")));
            })
            .map(chunk -> ServerSentEvent.builder((Object) writeJson(chunk)).build())
            .concatWith(Mono.just(ServerSentEvent.builder((Object) UIMessageStreamResponse.DONE_MARKER)
                .build()));
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .headers(headers -> response.headers().forEach(headers::set))
            .body(flux, ServerSentEvent.class);
    }

    private Mono<ServerResponse> testCompletionStream(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testCompletionStream: modelName={}", modelName);

        return request.bodyToMono(TestCompletionStreamRequest.class)
            .flatMap(body -> validateTestCompletionRequest(body).then(Mono.defer(() -> {
                var generation = completionRequest(body).build();
                return aiModelService.languageModel(modelName)
                    .map(languageModel -> languageModel.streamText(generation))
                    .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(result.textStream(), String.class));
            })));
    }

    private Mono<ServerResponse> testObjectStream(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testObjectStream: modelName={}", modelName);

        return request.bodyToMono(TestObjectStreamRequest.class)
            .flatMap(body -> validateTestObjectRequest(body).then(Mono.defer(() -> {
                var generation = objectRequest(body).build();
                return aiModelService.languageModel(modelName)
                    .map(languageModel -> languageModel.streamText(generation))
                    .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(result.textStream()
                            .concatWith(result.result().then(Mono.empty())), String.class));
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

    private Mono<ServerResponse> testRerank(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testRerank: modelName={}", modelName);
        return request.bodyToMono(TestRerankRequest.class)
            .flatMap(body -> validateTestRerankRequest(body)
                .then(aiModelService.rerankingModel(modelName))
                .flatMap(model -> model.rerank(RerankRequest.builder()
                        .query(body.getQuery())
                        .documents(toRerankDocuments(body.getDocuments()))
                        .topN(body.getTopN())
                        .providerOptions(body.getProviderOptions())
                        .metadata(Map.of("source", "console-test"))
                        .build())
                    .map(TestRerankResponse::from)))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> testImageGeneration(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testImageGeneration: modelName={}", modelName);
        return request.bodyToMono(TestImageGenerationRequest.class)
            .flatMap(body -> validateTestImageGenerationRequest(body)
                .then(aiModelService.imageGenerationModel(modelName))
                .flatMap(model -> model.generateImage(toGenerateImageRequest(body)))
                .map(TestImageGenerationResponse::from))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> testRagUiMessageStream(ServerRequest request) {
        var modelName = request.pathVariable("name");
        log.info("testRagUiMessageStream: modelName={}", modelName);
        return request.bodyToMono(TestRagRequest.class)
            .flatMap(body -> validateTestRagRequest(body).then(Mono.defer(() -> {
                var cancellation = UIMessageCancellations.create();
                var diagnostics = new RagTestDiagnostics(toRetrievedSources(body));
                return aiModelService.languageModel(modelName)
                    .flatMap(languageModel -> testRagReranker(body, diagnostics)
                        .map(optionalReranker -> {
                            var reranker = optionalReranker.orElse(null);
                            var generation = ragGenerationRequest(body, cancellation, diagnostics,
                                reranker);
                            var result = languageModel.streamText(generation);
                            var chunks = Flux.concat(
                                Flux.just(UIMessageChunks.data("rag-input", diagnostics.input())),
                                result.toUIMessageStream().chunks(),
                                Flux.defer(() -> Flux.just(UIMessageChunks.data("rag-diagnostics",
                                    diagnostics.snapshot())))
                            );
                            return new UIMessageStreamResponse(new UIMessageStream(chunks));
                        }))
                    .flatMap(response -> uiMessageStreamResponse(response, cancellation));
            })));
    }

    private GenerateTextRequest ragGenerationRequest(TestRagRequest body,
        UIMessageCancellation cancellation, RagTestDiagnostics diagnostics,
        RagSourceReranker reranker) {
        var ragOptions = body.getRagOptions() != null ? body.getRagOptions() : new TestRagOptions();
        var retrievedSources = diagnostics.retrievedSources(body.getTopN(),
            ragOptions.getMinScore(), reranker != null);
        return GenerateTextRequest.builder()
            .prompt(body.getQuery())
            .system(body.getSystem())
            .temperature(body.getTemperature())
            .topP(body.getTopP())
            .topK(body.getTopK())
            .presencePenalty(body.getPresencePenalty())
            .frequencyPenalty(body.getFrequencyPenalty())
            .stopSequences(body.getStopSequences())
            .maxOutputTokens(body.getMaxOutputTokens())
            .seed(body.getSeed())
            .maxRetries(body.getMaxRetries())
            .providerOptions(body.getProviderOptions())
            .reasoning(body.getReasoning())
            .headers(body.getHeaders())
            .metadata(Map.of("source", "console-rag-test"))
            .context(body.getContext())
            .cancellationToken(cancellation.token())
            .middleware(new RagLanguageModelMiddleware(RagMiddlewareOptions.builder()
                .retriever(retrievalRequest -> Mono.just(RetrievedContext.builder()
                    .query(retrievalRequest.getQuery())
                    .sources(retrievedSources)
                    .metadata(Map.of("source", "console-rag-test"))
                    .build()))
                .reranker(reranker)
                .maxResults(null)
                .minScore(ragOptions.getMinScore())
                .maxContextCharacters(ragOptions.getMaxContextCharacters())
                .promptPlacement(ragOptions.getPromptPlacement())
                .emptyContextPolicy(ragOptions.getEmptyContextPolicy())
                .retrievalFailurePolicy(ragOptions.getRetrievalFailurePolicy())
                .rerankFailurePolicy(ragOptions.getRerankFailurePolicy())
                .emptyContextText(ragOptions.getEmptyContextText())
                .contextHeader(ragOptions.getContextHeader())
                .metadata(Map.of("source", "console-rag-test"))
                .context(body.getContext())
                .retrieverOptions(ragOptions.getRetrieverOptions())
                .lifecycle(diagnostics.lifecycle())
                .build()))
            .build();
    }

    private Mono<Optional<RagSourceReranker>> testRagReranker(TestRagRequest body,
        RagTestDiagnostics diagnostics) {
        if (body.getRerankModelName() == null || body.getRerankModelName().isBlank()) {
            return Mono.just(Optional.empty());
        }
        return aiModelService.rerankingModel(body.getRerankModelName())
            .map(model -> new ConsoleRagSourceReranker(model, body.getTopN(),
                body.getRerankProviderOptions(), diagnostics))
            .map(Optional::<RagSourceReranker>of);
    }

    private Mono<Void> validateTestRagRequest(TestRagRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "request body is required"));
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "query must not be blank"));
        }
        if (request.getSources() == null || request.getSources().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At least one source is required"));
        }
        if (request.getSources().size() > 50) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At most 50 sources can be tested at once"));
        }
        if (request.getTopN() != null && request.getTopN() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "topN must be positive"));
        }
        for (var source : request.getSources()) {
            if (source == null || source.getContent() == null || source.getContent().isBlank()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "RAG source content must not be blank"));
            }
        }
        return Mono.empty();
    }

    private List<RetrievedSource> toRetrievedSources(TestRagRequest request) {
        var sources = new ArrayList<RetrievedSource>();
        for (int i = 0; i < request.getSources().size(); i++) {
            var source = request.getSources().get(i);
            var metadata = new LinkedHashMap<String, Object>();
            if (source.getMetadata() != null) {
                metadata.putAll(source.getMetadata());
            }
            metadata.put("originalIndex", i);
            sources.add(RetrievedSource.builder()
                .id(hasText(source.getId()) ? source.getId() : String.valueOf(i))
                .sourceType(source.getSourceType())
                .title(source.getTitle())
                .url(source.getUrl())
                .content(source.getContent())
                .score(source.getScore())
                .metadata(Map.copyOf(metadata))
                .usedForContext(source.getUsedForContext())
                .visible(source.getVisible())
                .build());
        }
        return List.copyOf(sources);
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

    private static boolean consoleAgentTestToolsEnabled(ServerRequest request) {
        return request.queryParam("enableAgentTestTools")
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
            || CONSOLE_AGENT_PAGE_CONTEXT_TOOL_NAME.equals(tool.getName())
            || CONSOLE_AGENT_ECHO_TOOL_NAME.equals(tool.getName())
            || CONSOLE_REPAIR_TEST_TOOL_NAME.equals(tool.getName()));
        if (options.basicEnabled() || options.approvalEnabled()) {
            tools.add(consoleTestTool(options.approvalEnabled()));
        }
        if (options.externalEnabled()) {
            tools.add(consoleExternalTestTool());
        }
        if (options.agentEnabled()) {
            tools.add(consoleAgentPageContextTool());
            tools.add(consoleAgentTestActionTool());
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

    private void applyConsoleGenerationOptions(
        GenerateTextRequest.GenerateTextRequestBuilder builder,
        TestUiMessageChatRequest request, ConsoleTestToolOptions options) {
        var generation = GenerateTextRequest.builder()
            .system(request.getSystem())
            .temperature(request.getTemperature())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .maxOutputTokens(request.getMaxOutputTokens())
            .seed(request.getSeed())
            .maxRetries(request.getMaxRetries())
            .providerOptions(request.getProviderOptions())
            .reasoning(request.getReasoning())
            .headers(request.getHeaders())
            .metadata(request.getMetadata())
            .context(request.getContext())
            .output(request.getOutput())
            .toolChoice(request.getToolChoice())
            .build();
        generation = withConsoleTestTool(generation, options);
        builder.system(generation.getSystem())
            .temperature(generation.getTemperature())
            .topP(generation.getTopP())
            .topK(generation.getTopK())
            .presencePenalty(generation.getPresencePenalty())
            .frequencyPenalty(generation.getFrequencyPenalty())
            .stopSequences(generation.getStopSequences())
            .maxOutputTokens(generation.getMaxOutputTokens())
            .seed(generation.getSeed())
            .maxRetries(generation.getMaxRetries())
            .providerOptions(generation.getProviderOptions())
            .reasoning(generation.getReasoning())
            .headers(generation.getHeaders())
            .metadata(generation.getMetadata())
            .context(generation.getContext())
            .output(generation.getOutput())
            .tools(generation.getTools())
            .toolChoice(generation.getToolChoice())
            .stopWhen(generation.getStopWhen())
            .toolCallRepair(generation.getToolCallRepair());
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

    private ToolDefinition consoleAgentPageContextTool() {
        return ToolDefinition.builder()
            .name(CONSOLE_AGENT_PAGE_CONTEXT_TOOL_NAME)
            .description("Halo 控制台 Agent 浏览器工具测试：读取当前后台测试工作台页面上下文，"
                + "包括页面标题、地址、选中模型、测试模式和最近用户消息。该工具由前端执行，"
                + "用于验证客户端 Agent 工具调用与自动续跑。")
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of()
            ))
            .build();
    }

    private ToolDefinition consoleAgentTestActionTool() {
        return ToolDefinition.builder()
            .name(CONSOLE_AGENT_ECHO_TOOL_NAME)
            .description("Halo 控制台 Agent 浏览器工具测试：执行一个中性的前端测试动作并回显输入，"
                + "不会修改真实业务数据。该工具由前端执行，用于验证工具结果回传后模型继续生成。")
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "message", Map.of(
                        "type", "string",
                        "description", "需要前端测试工具回显的内容"
                    )
                ),
                "required", List.of("message")
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

    private Mono<Void> validateTestUiMessageChatRequest(TestUiMessageChatRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "request body is required"));
        }
        if (request.getId() == null || request.getId().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "id must not be blank"));
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "messages must not be empty"));
        }
        var trigger = parseUiMessageChatTrigger(request.getTrigger());
        if (trigger == UIMessageChatTrigger.REGENERATE_MESSAGE
            && (request.getMessageId() == null || request.getMessageId().isBlank())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "messageId must be set for regenerate-message requests"));
        }
        for (var message : request.getMessages()) {
            validateConsoleUiMessage(message);
        }
        return Mono.empty();
    }

    private Mono<Void> validateTestCompletionRequest(TestCompletionStreamRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "request body is required"));
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "prompt must not be blank"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateTestObjectRequest(TestObjectStreamRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "request body is required"));
        }
        if (request.getInput() == null || request.getInput().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "input must not be blank"));
        }
        try {
            objectOutput(request);
        } catch (ResponseStatusException e) {
            return Mono.error(e);
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

    private Mono<Void> validateTestRerankRequest(TestRerankRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "request body is required"));
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "query must not be blank"));
        }
        if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At least one document is required"));
        }
        if (request.getDocuments().size() > 50) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At most 50 documents can be tested at once"));
        }
        if (request.getTopN() != null && request.getTopN() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "topN must be positive"));
        }
        for (var document : request.getDocuments()) {
            if (document == null || document.isBlank()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rerank documents must not be blank"));
            }
        }
        return Mono.empty();
    }

    private Mono<Void> validateTestImageGenerationRequest(TestImageGenerationRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "request body is required"));
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "prompt must not be blank"));
        }
        if (request.getN() != null && request.getN() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "n must be positive"));
        }
        if (request.getWidth() != null && request.getWidth() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "width must be positive"));
        }
        if (request.getHeight() != null && request.getHeight() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "height must be positive"));
        }
        if (request.getMaxRetries() != null && request.getMaxRetries() < 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "maxRetries must not be negative"));
        }
        if (request.getMaxParallelCalls() != null && request.getMaxParallelCalls() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "maxParallelCalls must be positive"));
        }
        if (request.getImages() != null && request.getImages().size() > 8) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At most 8 input images can be tested at once"));
        }
        if (request.getImages() != null) {
            for (var image : request.getImages()) {
                if (!hasMediaPayload(image)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "input images must set exactly one of url or data"));
                }
            }
        }
        if (request.getMask() != null && !hasMediaPayload(request.getMask())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "mask must set exactly one of url or data"));
        }
        return Mono.empty();
    }

    private static List<RerankDocument> toRerankDocuments(List<String> documents) {
        var result = new ArrayList<RerankDocument>();
        for (int i = 0; i < documents.size(); i++) {
            result.add(RerankDocument.builder()
                .id(String.valueOf(i))
                .text(documents.get(i))
                .build());
        }
        return List.copyOf(result);
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String emptyToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private static boolean hasMediaPayload(TestMediaContent media) {
        return media != null && hasText(media.getUrl()) != hasText(media.getData());
    }

    private GenerateTextRequest.GenerateTextRequestBuilder completionRequest(
        TestCompletionStreamRequest request) {
        return GenerateTextRequest.builder()
            .prompt(request.getPrompt())
            .system(request.getSystem())
            .temperature(request.getTemperature())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .maxOutputTokens(request.getMaxOutputTokens())
            .seed(request.getSeed())
            .maxRetries(request.getMaxRetries())
            .providerOptions(request.getProviderOptions())
            .reasoning(request.getReasoning())
            .headers(request.getHeaders())
            .metadata(request.getMetadata())
            .context(request.getContext());
    }

    private GenerateTextRequest.GenerateTextRequestBuilder objectRequest(
        TestObjectStreamRequest request) {
        return completionRequest(request)
            .prompt(request.getInput())
            .output(objectOutput(request));
    }

    private GenerateImageRequest toGenerateImageRequest(TestImageGenerationRequest request) {
        var builder = GenerateImageRequest.builder()
            .prompt(request.getPrompt())
            .images(toDataContents(request.getImages()))
            .mask(toDataContent(request.getMask()))
            .n(request.getN())
            .aspectRatio(request.getAspectRatio())
            .seed(request.getSeed())
            .responseFormat(request.getResponseFormat())
            .providerOptions(request.getProviderOptions())
            .headers(request.getHeaders())
            .maxRetries(request.getMaxRetries())
            .maxParallelCalls(request.getMaxParallelCalls())
            .metadata(Map.of("source", "console-image-generation-test"));
        applyImageSize(builder, request);
        return builder.build();
    }

    private static void applyImageSize(GenerateImageRequest.GenerateImageRequestBuilder builder,
        TestImageGenerationRequest request) {
        if (hasText(request.getSize())) {
            var size = request.getSize().trim();
            if (size.matches("\\d+")) {
                builder.size(Integer.parseInt(size));
            } else {
                builder.size(size);
            }
            return;
        }
        if (request.getWidth() != null && request.getHeight() != null) {
            builder.size(request.getWidth(), request.getHeight());
        } else if (request.getWidth() != null) {
            builder.size(request.getWidth());
        }
    }

    private static List<DataContent> toDataContents(List<TestMediaContent> media) {
        if (media == null || media.isEmpty()) {
            return null;
        }
        return media.stream()
            .map(ModelConsoleEndpoint::toDataContent)
            .toList();
    }

    private static DataContent toDataContent(TestMediaContent media) {
        if (media == null) {
            return null;
        }
        if (hasText(media.getUrl())) {
            return DataContent.url(media.getUrl().trim(), emptyToNull(media.getMediaType()),
                emptyToNull(media.getFilename()));
        }
        if (hasText(media.getData())) {
            var data = media.getData().trim();
            if (data.regionMatches(true, 0, "data:", 0, "data:".length())) {
                return DataContent.dataUrl(data, emptyToNull(media.getFilename()));
            }
            return DataContent.data(data, emptyToNull(media.getMediaType()),
                emptyToNull(media.getFilename()));
        }
        return null;
    }

    private run.halo.aifoundation.schema.OutputSpec objectOutput(
        TestObjectStreamRequest request) {
        if (request.getOutput() != null && !request.getOutput().isEmpty()) {
            return outputSpecFromMap(request.getOutput());
        }
        if (request.getSchema() == null || request.getSchema().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "schema or output must be provided");
        }
        return run.halo.aifoundation.schema.OutputSpec.object(request.getSchema());
    }

    private run.halo.aifoundation.schema.OutputSpec outputSpecFromMap(Map<String, Object> output) {
        var type = outputType(output.get("type"));
        if (type != run.halo.aifoundation.schema.OutputType.OBJECT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "output.type must be object");
        }
        return run.halo.aifoundation.schema.OutputSpec.builder()
            .type(type)
            .name(stringValue(output.get("name")))
            .description(stringValue(output.get("description")))
            .schema(objectMapValue(output.get("schema")))
            .strict(booleanValue(output.get("strict")))
            .providerOptions(providerOptionsValue(output.get("providerOptions")))
            .build();
    }

    private run.halo.aifoundation.schema.OutputType outputType(Object value) {
        if (value == null) {
            return run.halo.aifoundation.schema.OutputType.OBJECT;
        }
        var normalized = String.valueOf(value).trim().replace('-', '_').toUpperCase();
        try {
            return run.halo.aifoundation.schema.OutputType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "unsupported output.type: " + value, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMapValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schema must be an object");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> providerOptionsValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Map<String, Object>>) map;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "providerOptions must be an object");
    }

    private static Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static UIMessageChatRequest<Map<String, Object>> toUiMessageChatRequest(
        TestUiMessageChatRequest request) {
        return UIMessageTransportCodec.chatRequestFromMap(toUiMessageChatRequestMap(request));
    }

    private static void validateConsoleUiMessage(TestUiMessage message) {
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "messages must not contain null values");
        }
        if (message.getId() == null || message.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "message id must not be blank");
        }
        if (message.getRole() == null || message.getRole().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "message role must not be blank");
        }
        if (message.getParts() == null || message.getParts().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "message parts must not be empty");
        }
        try {
            UIMessageTransportCodec.messageFromMap(toUiMessageMap(message));
        } catch (InvalidUIMessageException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private static UIMessageChatTrigger parseUiMessageChatTrigger(String trigger) {
        try {
            return UIMessageChatTrigger.fromValue(trigger != null ? trigger : "submit-message");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private static Map<String, Object> toUiMessageChatRequestMap(
        TestUiMessageChatRequest request) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", request.getId());
        map.put("messages", request.getMessages().stream()
            .map(ModelConsoleEndpoint::toUiMessageMap)
            .toList());
        map.put("trigger", request.getTrigger() != null ? request.getTrigger() : "submit-message");
        if (request.getMessageId() != null) {
            map.put("messageId", request.getMessageId());
        }
        return map;
    }

    private static Map<String, Object> toUiMessageMap(TestUiMessage message) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", message.getId());
        map.put("role", message.getRole());
        map.put("parts", message.getParts());
        if (message.getMetadata() != null) {
            map.put("metadata", message.getMetadata());
        }
        return map;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static String writeJson(UIMessageChunk chunk) {
        try {
            return JSON_MAPPER.writeValueAsString(UIMessageTransportCodec.chunkToMap(chunk));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to serialize UI message chunk", e);
        }
    }

    private static String safeMessage(Throwable error) {
        if (error instanceof InvalidUIMessageException invalid) {
            return invalid.getMessage() + ": " + invalid.issues();
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private record ConsoleTestToolOptions(
        boolean basicEnabled,
        boolean approvalEnabled,
        boolean externalEnabled,
        boolean agentEnabled,
        boolean repairEnabled
    ) {
        static ConsoleTestToolOptions from(ServerRequest request) {
            return new ConsoleTestToolOptions(
                consoleTestToolEnabled(request),
                consoleTestToolApprovalEnabled(request),
                consoleExternalTestToolEnabled(request),
                consoleAgentTestToolsEnabled(request),
                consoleToolCallRepairEnabled(request)
            );
        }

        boolean hasAnyEnabled() {
            return basicEnabled || approvalEnabled || externalEnabled || agentEnabled
                || repairEnabled;
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
    public static class TestRerankRequest {
        private String query;
        private List<String> documents;
        private Integer topN;
        private Map<String, Map<String, Object>> providerOptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestImageGenerationRequest {
        private String prompt;
        private List<TestMediaContent> images;
        private TestMediaContent mask;
        private Integer n;
        private String size;
        private Integer width;
        private Integer height;
        private String aspectRatio;
        private Integer seed;
        private ImageResponseFormat responseFormat;
        private Integer maxRetries;
        private Integer maxParallelCalls;
        private Map<String, Map<String, Object>> providerOptions;
        private Map<String, String> headers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestMediaContent {
        private String url;
        private String data;
        private String mediaType;
        private String filename;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRagRequest {
        private String query;
        private String system;
        private List<TestRagSource> sources;
        private String rerankModelName;
        private Integer topN;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private List<String> stopSequences;
        private Integer maxOutputTokens;
        private Integer seed;
        private Integer maxRetries;
        private Map<String, Map<String, Object>> providerOptions;
        private Map<String, Map<String, Object>> rerankProviderOptions;
        private run.halo.aifoundation.chat.ReasoningOptions reasoning;
        private Map<String, String> headers;
        private Map<String, Object> context;
        private TestRagOptions ragOptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRagSource {
        private String id;
        private String sourceType;
        private String title;
        private String url;
        private String content;
        private Double score;
        private Map<String, Object> metadata;
        private Boolean usedForContext;
        private Boolean visible;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRagOptions {
        private Double minScore;
        private Integer maxContextCharacters;
        private RagPromptPlacement promptPlacement;
        private RagEmptyContextPolicy emptyContextPolicy;
        private RagFailurePolicy retrievalFailurePolicy;
        private RagFailurePolicy rerankFailurePolicy;
        private String emptyContextText;
        private String contextHeader;
        private Map<String, Object> retrieverOptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCompletionStreamRequest {
        private String prompt;
        private String system;
        private Integer maxOutputTokens;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private List<String> stopSequences;
        private Integer seed;
        private Integer maxRetries;
        private Map<String, Map<String, Object>> providerOptions;
        private run.halo.aifoundation.chat.ReasoningOptions reasoning;
        private Map<String, String> headers;
        private Map<String, Object> metadata;
        private Map<String, Object> context;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestObjectStreamRequest extends TestCompletionStreamRequest {
        private String input;
        private Map<String, Object> schema;
        private Map<String, Object> output;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestUiMessageChatRequest {
        private String id;
        private List<TestUiMessage> messages;
        private String trigger = "submit-message";
        private String messageId;
        private String system;
        private Integer maxOutputTokens;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private List<String> stopSequences;
        private Integer seed;
        private Integer maxRetries;
        private Map<String, Map<String, Object>> providerOptions;
        private run.halo.aifoundation.chat.ReasoningOptions reasoning;
        private Map<String, String> headers;
        private Map<String, Object> metadata;
        private Map<String, Object> context;
        private run.halo.aifoundation.schema.OutputSpec output;
        private run.halo.aifoundation.tool.ToolChoice toolChoice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestUiMessage {
        private String id;
        private String role;
        private List<Map<String, Object>> parts;
        private Map<String, Object> metadata;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRerankResponse {
        private String query;
        private int resultsCount;
        private List<RerankPreview> results;
        private RerankUsage usage;
        private RerankResponseMetadata response;
        private List<RerankWarning> warnings;
        private Map<String, Object> providerMetadata;

        static TestRerankResponse from(RerankResponse response) {
            var results = response.getResults() != null
                ? response.getResults()
                : List.<run.halo.aifoundation.rerank.RerankResult>of();
            return TestRerankResponse.builder()
                .query(response.getQuery())
                .resultsCount(results.size())
                .results(results.stream()
                    .map(result -> RerankPreview.builder()
                        .index(result.getIndex())
                        .documentId(result.getDocument() != null
                            ? result.getDocument().getId()
                            : null)
                        .text(result.getDocument() != null
                            ? result.getDocument().getText()
                            : null)
                        .score(result.getScore())
                        .providerMetadata(result.getProviderMetadata())
                        .build())
                    .toList())
                .usage(response.getUsage())
                .response(response.getResponse())
                .warnings(response.getWarnings() != null ? response.getWarnings() : List.of())
                .providerMetadata(response.getProviderMetadata())
                .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestImageGenerationResponse {
        private int imagesCount;
        private List<TestGeneratedFile> images;
        private ImageUsage usage;
        private List<ImageGenerationWarning> warnings;
        private List<GenerationResponseMetadata> responses;
        private Map<String, Object> providerMetadata;

        static TestImageGenerationResponse from(GenerateImageResult result) {
            var images = result.getImages() != null
                ? result.getImages()
                : List.<GeneratedFile>of();
            var previews = new ArrayList<TestGeneratedFile>();
            for (int i = 0; i < images.size(); i++) {
                previews.add(TestGeneratedFile.from(i, images.get(i)));
            }
            return TestImageGenerationResponse.builder()
                .imagesCount(images.size())
                .images(List.copyOf(previews))
                .usage(result.getUsage())
                .warnings(result.getWarnings() != null ? result.getWarnings() : List.of())
                .responses(result.getResponses() != null ? result.getResponses() : List.of())
                .providerMetadata(result.getProviderMetadata())
                .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestGeneratedFile {
        private int index;
        private String id;
        private String url;
        private String base64;
        private String mediaType;
        private String filename;
        private String title;
        private Map<String, Object> metadata;

        static TestGeneratedFile from(int index, GeneratedFile file) {
            return TestGeneratedFile.builder()
                .index(index)
                .id(file.getId())
                .url(file.getUrl())
                .base64(file.getBase64())
                .mediaType(file.getMediaType())
                .filename(file.getFilename())
                .title(file.getTitle())
                .metadata(file.getMetadata())
                .build();
        }
    }

    private static class ConsoleRagSourceReranker implements RagSourceReranker {

        private final RerankingModel model;
        private final Integer topN;
        private final Map<String, Map<String, Object>> providerOptions;
        private final RagTestDiagnostics diagnostics;

        ConsoleRagSourceReranker(RerankingModel model, Integer topN,
            Map<String, Map<String, Object>> providerOptions, RagTestDiagnostics diagnostics) {
            this.model = model;
            this.topN = topN;
            this.providerOptions = providerOptions;
            this.diagnostics = diagnostics;
        }

        @Override
        public Mono<List<RetrievedSource>> rerank(RagSourceRerankRequest request) {
            var documents = request.getSources().stream()
                .map(source -> RerankDocument.builder()
                    .id(source.getId())
                    .text(source.getContent())
                    .metadata(source.getMetadata())
                    .build())
                .toList();
            return model.rerank(RerankRequest.builder()
                    .query(request.getQuery())
                    .documents(documents)
                    .topN(topN != null ? topN : request.getTopN())
                    .providerOptions(providerOptions)
                    .metadata(request.getMetadata())
                    .context(request.getContext())
                    .build())
                .map(response -> response.getResults() != null ? response.getResults() : List.<RerankResult>of())
                .map(results -> results.stream()
                    .filter(result -> result.getIndex() >= 0
                        && result.getIndex() < request.getSources().size())
                    .map(result -> withRerankMetadata(request.getSources().get(result.getIndex()),
                        result))
                    .toList())
                .doOnNext(diagnostics::finalSources);
        }

        private RetrievedSource withRerankMetadata(RetrievedSource source, RerankResult result) {
            var metadata = new LinkedHashMap<String, Object>();
            if (source.getMetadata() != null) {
                metadata.putAll(source.getMetadata());
            }
            metadata.put("rerankScore", result.getScore());
            if (result.getProviderMetadata() != null && !result.getProviderMetadata().isEmpty()) {
                metadata.put("rerankProviderMetadata", result.getProviderMetadata());
            }
            return RetrievedSource.builder()
                .id(source.getId())
                .sourceType(source.getSourceType())
                .title(source.getTitle())
                .url(source.getUrl())
                .content(source.getContent())
                .score(source.getScore())
                .metadata(Map.copyOf(metadata))
                .usedForContext(source.getUsedForContext())
                .visible(source.getVisible())
                .build();
        }
    }

    private static class RagTestDiagnostics {

        private final List<RetrievedSource> originalSources;
        private final AtomicReference<List<RetrievedSource>> finalSources =
            new AtomicReference<>(List.of());
        private final CopyOnWriteArrayList<Map<String, Object>> events = new CopyOnWriteArrayList<>();

        RagTestDiagnostics(List<RetrievedSource> originalSources) {
            this.originalSources = originalSources;
        }

        List<RetrievedSource> originalSources() {
            return originalSources;
        }

        List<RetrievedSource> retrievedSources(Integer topN, Double minScore, boolean willRerank) {
            var sources = originalSources.stream()
                .filter(source -> source.getUsedForContext() == null
                    || Boolean.TRUE.equals(source.getUsedForContext()))
                .filter(source -> source.getContent() != null && !source.getContent().isBlank())
                .filter(source -> minScore == null || source.getScore() == null
                    || source.getScore() >= minScore)
                .limit(!willRerank && topN != null ? topN : Long.MAX_VALUE)
                .toList();
            if (!willRerank) {
                finalSources(sources);
            }
            return sources;
        }

        void finalSources(List<RetrievedSource> sources) {
            finalSources.set(sources != null ? List.copyOf(sources) : List.of());
        }

        RagLifecycle lifecycle() {
            return new RagLifecycle() {
                @Override
                public Mono<Void> onRetrievalStart(RagLifecycleEvent event) {
                    return record("retrieval-start", event);
                }

                @Override
                public Mono<Void> onRetrievalFinish(RagLifecycleEvent event) {
                    return record("retrieval-finish", event);
                }

                @Override
                public Mono<Void> onRerankStart(RagLifecycleEvent event) {
                    return record("rerank-start", event);
                }

                @Override
                public Mono<Void> onRerankFinish(RagLifecycleEvent event) {
                    return record("rerank-finish", event);
                }

                @Override
                public Mono<Void> onContextPacked(RagLifecycleEvent event) {
                    return record("context-packed", event);
                }

                @Override
                public Mono<Void> onError(RagLifecycleEvent event) {
                    return record("error", event);
                }
            };
        }

        Map<String, Object> input() {
            return Map.of(
                "sources", previews(originalSources),
                "sourceCount", originalSources.size()
            );
        }

        Map<String, Object> snapshot() {
            var finalItems = finalSources.get();
            return Map.of(
                "sources", previews(finalItems.isEmpty() ? originalSources : finalItems),
                "originalSources", previews(originalSources),
                "finalSources", previews(finalItems),
                "sourceCount", originalSources.size(),
                "events", List.copyOf(events)
            );
        }

        private Mono<Void> record(String type, RagLifecycleEvent event) {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("type", type);
            payload.put("stage", event.getStage());
            payload.put("query", event.getQuery());
            putIfPresent(payload, "sourceCount", event.getSourceCount());
            putIfPresent(payload, "contextCharacters", event.getContextCharacters());
            putIfPresent(payload, "warningCode", event.getWarningCode());
            putIfPresent(payload, "errorType", event.getErrorType());
            putIfPresent(payload, "errorMessage", event.getErrorMessage());
            putIfPresent(payload, "metadata", event.getMetadata());
            putIfPresent(payload, "context", event.getContext());
            events.add(Map.copyOf(payload));
            return Mono.empty();
        }

        private static List<Map<String, Object>> previews(List<RetrievedSource> sources) {
            return sources.stream()
                .map(RagTestDiagnostics::preview)
                .toList();
        }

        private static Map<String, Object> preview(RetrievedSource source) {
            var payload = new LinkedHashMap<String, Object>();
            putIfPresent(payload, "id", source.getId());
            putIfPresent(payload, "sourceType", source.getSourceType());
            putIfPresent(payload, "title", source.getTitle());
            putIfPresent(payload, "url", source.getUrl());
            putIfPresent(payload, "score", source.getScore());
            putIfPresent(payload, "metadata", source.getMetadata());
            putIfPresent(payload, "usedForContext", source.getUsedForContext());
            putIfPresent(payload, "visible", source.getVisible());
            return Map.copyOf(payload);
        }

        private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
            if (value != null) {
                payload.put(key, value);
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankPreview {
        private int index;
        private String documentId;
        private String text;
        private Double score;
        private Map<String, Object> providerMetadata;
    }

}
