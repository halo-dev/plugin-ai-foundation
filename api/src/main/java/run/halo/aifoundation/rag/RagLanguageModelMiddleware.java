package run.halo.aifoundation.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.GenerateTextNext;
import run.halo.aifoundation.chat.middleware.LanguageModelGenerateContext;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.chat.middleware.LanguageModelStreamContext;
import run.halo.aifoundation.chat.middleware.StreamTextNext;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.source.RetrievedContext;
import run.halo.aifoundation.source.RetrievedSource;
import run.halo.aifoundation.source.SourceReference;
import run.halo.aifoundation.source.SourceReferences;

/**
 * Language model middleware that performs retrieval-augmented generation with caller-owned
 * retrieval.
 */
public class RagLanguageModelMiddleware implements LanguageModelMiddleware {

    private final RagMiddlewareOptions options;

    public RagLanguageModelMiddleware(RagMiddlewareOptions options) {
        this.options = withDefaults(options);
        Objects.requireNonNull(this.options.getRetriever(), "retriever must not be null");
    }

    @Override
    public Mono<GenerateTextResult> wrapGenerate(LanguageModelGenerateContext context,
        GenerateTextNext next) {
        return prepare(context.request())
            .flatMap(prepared -> {
                if (prepared.skipModel()) {
                    return Mono.just(emptyContextResult(prepared));
                }
                return next.generate(prepared.request())
                    .map(result -> attachSources(result, prepared.sources(), prepared.warnings()));
            });
    }

    @Override
    public StreamTextResult wrapStream(LanguageModelStreamContext context, StreamTextNext next) {
        var prepared = prepare(context.request()).cache();
        var streamResult = prepared.map(value -> value.skipModel()
            ? emptyContextStream(value)
            : withSourceStream(next.stream(value.request()), value.sources(), value.warnings()))
            .cache();
        return run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares.defer(streamResult);
    }

    private Mono<PreparedRagRequest> prepare(GenerateTextRequest request) {
        var query = query(request);
        var metadata = options.getMetadata() != null ? options.getMetadata() : Map.<String, Object>of();
        var callerContext = options.getContext() != null ? options.getContext() : Map.<String, Object>of();
        var retrievalRequest = RagRetrievalRequest.builder()
            .query(query)
            .generationRequest(request)
            .messages(messages(request))
            .maxResults(options.getMaxResults())
            .minScore(options.getMinScore())
            .metadata(metadata)
            .context(callerContext)
            .options(options.getRetrieverOptions())
            .build();
        return invokeLifecycleStart("retrieval", query, metadata, callerContext)
            .then(options.getRetriever().retrieve(retrievalRequest))
            .flatMap(context -> usableSources(context)
                .flatMap(sources -> afterRetrieval(request, query, sources, metadata, callerContext)))
            .onErrorResume(error -> handleRetrievalError(request, query, metadata, callerContext,
                error));
    }

    private Mono<PreparedRagRequest> afterRetrieval(GenerateTextRequest request, String query,
        List<RetrievedSource> sources, Map<String, Object> metadata, Map<String, Object> context) {
        return invokeLifecycleFinish("retrieval", query, sources.size(), null, metadata, context)
            .then(Mono.defer(() -> {
                if (sources.isEmpty()) {
                    return emptyContextPrepared(request, List.of());
                }
                return rerank(query, sources, metadata, context)
                    .flatMap(reranked -> pack(request, query, reranked, metadata, context));
            }));
    }

    private Mono<List<RetrievedSource>> usableSources(RetrievedContext context) {
        var sources = context != null && context.getSources() != null
            ? context.getSources()
            : List.<RetrievedSource>of();
        return Mono.just(sources.stream()
            .filter(source -> source != null)
            .filter(source -> source.getUsedForContext() == null
                || Boolean.TRUE.equals(source.getUsedForContext()))
            .filter(source -> source.getContent() != null && !source.getContent().isBlank())
            .filter(source -> options.getMinScore() == null || source.getScore() == null
                || source.getScore() >= options.getMinScore())
            .limit(options.getMaxResults() != null ? options.getMaxResults() : Long.MAX_VALUE)
            .toList());
    }

    private Mono<List<RetrievedSource>> rerank(String query, List<RetrievedSource> sources,
        Map<String, Object> metadata, Map<String, Object> context) {
        if (options.getReranker() == null) {
            return Mono.just(sources);
        }
        var request = RagSourceRerankRequest.builder()
            .query(query)
            .sources(sources)
            .topN(options.getMaxResults())
            .metadata(metadata)
            .context(context)
            .build();
        return invokeLifecycleStart("rerank", query, metadata, context)
            .then(options.getReranker().rerank(request))
            .flatMap(reranked -> invokeLifecycleFinish("rerank", query, reranked.size(), null,
                metadata, context).thenReturn(reranked))
            .onErrorResume(error -> handleRerankError(query, sources, metadata, context, error));
    }

    private Mono<List<RetrievedSource>> handleRerankError(String query, List<RetrievedSource> sources,
        Map<String, Object> metadata, Map<String, Object> context, Throwable error) {
        return invokeError("rerank", query, error, metadata, context)
            .then(Mono.defer(() -> {
                if (options.getRerankFailurePolicy() == RagFailurePolicy.USE_RETRIEVED_ORDER) {
                    return Mono.just(sources);
                }
                return Mono.error(error);
            }));
    }

    private Mono<PreparedRagRequest> pack(GenerateTextRequest request, String query,
        List<RetrievedSource> sources, Map<String, Object> metadata, Map<String, Object> context) {
        var packed = packSources(sources);
        return invokeLifecycleFinish("context", query, sources.size(), packed.length(), metadata,
                context)
            .thenReturn(new PreparedRagRequest(injectContext(request, packed), publicSources(sources),
                List.of(), false, null));
    }

    private Mono<PreparedRagRequest> emptyContextPrepared(GenerateTextRequest request,
        List<GenerationWarning> warnings) {
        if (options.getEmptyContextPolicy() == RagEmptyContextPolicy.CONTINUE_WITHOUT_CONTEXT) {
            return Mono.just(new PreparedRagRequest(request, List.of(), warnings, false, null));
        }
        return Mono.just(new PreparedRagRequest(request, List.of(), warnings, true,
            options.getEmptyContextText()));
    }

    private Mono<PreparedRagRequest> handleRetrievalError(GenerateTextRequest request, String query,
        Map<String, Object> metadata, Map<String, Object> context, Throwable error) {
        return invokeError("retrieval", query, error, metadata, context)
            .then(Mono.defer(() -> {
                if (options.getRetrievalFailurePolicy() == RagFailurePolicy.USE_EMPTY_CONTEXT) {
                    return emptyContextPrepared(request, List.of(warning(
                        "rag-retrieval-fallback-empty",
                        "Retrieval failed and was treated as empty context.")));
                }
                if (options.getRetrievalFailurePolicy()
                    == RagFailurePolicy.CONTINUE_WITHOUT_CONTEXT) {
                    return Mono.just(new PreparedRagRequest(request, List.of(), List.of(warning(
                        "rag-retrieval-fallback-continue",
                        "Retrieval failed and generation continued without context.")), false,
                        null));
                }
                return Mono.error(error);
            }));
    }

    private GenerateTextRequest injectContext(GenerateTextRequest request, String packedContext) {
        var placement = options.getPromptPlacement();
        if (placement == RagPromptPlacement.SYSTEM) {
            request.setSystem(appendBlock(request.getSystem(), packedContext));
            return request;
        }
        if (placement == RagPromptPlacement.NEW_USER_MESSAGE) {
            var messages = new ArrayList<>(messages(request));
            messages.add(ModelMessage.user(packedContext));
            request.setPrompt(null);
            request.setMessages(List.copyOf(messages));
            return request;
        }
        injectIntoLastUserMessage(request, packedContext);
        return request;
    }

    private void injectIntoLastUserMessage(GenerateTextRequest request, String packedContext) {
        var messages = messages(request);
        if (messages.isEmpty() && request.getPrompt() != null) {
            request.setPrompt(appendBlock(packedContext, request.getPrompt()));
            return;
        }
        var mutable = new ArrayList<>(messages);
        for (int i = mutable.size() - 1; i >= 0; i--) {
            var message = mutable.get(i);
            if (message.getRole() == ModelMessageRole.USER) {
                mutable.set(i, withPrependedText(message, packedContext));
                request.setMessages(List.copyOf(mutable));
                request.setPrompt(null);
                return;
            }
        }
        mutable.add(ModelMessage.user(packedContext));
        request.setMessages(List.copyOf(mutable));
        request.setPrompt(null);
    }

    private ModelMessage withPrependedText(ModelMessage message, String text) {
        var content = new ArrayList<ModelMessagePart>();
        content.add(ModelMessagePart.text(text));
        if (message.getContent() != null) {
            content.addAll(message.getContent());
        }
        return new ModelMessage(message.getRole(), List.copyOf(content));
    }

    private String packSources(List<RetrievedSource> sources) {
        var max = options.getMaxContextCharacters() != null
            ? options.getMaxContextCharacters()
            : Integer.MAX_VALUE;
        var builder = new StringBuilder();
        builder.append(options.getContextHeader()).append("\n\n");
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            var title = source.getTitle() != null ? source.getTitle() : source.getId();
            var block = "[" + (i + 1) + "] " + (title != null ? title : "Source") + "\n"
                + source.getContent() + "\n\n";
            if (builder.length() + block.length() > max) {
                var remaining = max - builder.length();
                if (remaining > 0) {
                    builder.append(block, 0, Math.min(remaining, block.length()));
                }
                break;
            }
            builder.append(block);
        }
        return builder.toString().trim();
    }

    private GenerateTextResult attachSources(GenerateTextResult result, List<SourceReference> sources,
        List<GenerationWarning> warnings) {
        result.setSources(mergeSources(result.getSources(), sources));
        if (!warnings.isEmpty()) {
            var merged = new ArrayList<GenerationWarning>();
            if (result.getWarnings() != null) {
                merged.addAll(result.getWarnings());
            }
            merged.addAll(warnings);
            result.setWarnings(List.copyOf(merged));
        }
        return result;
    }

    private StreamTextResult withSourceStream(StreamTextResult delegate, List<SourceReference> sources,
        List<GenerationWarning> warnings) {
        var sourceParts = Flux.fromIterable(sources)
            .map(SourceReferences::toContentPart)
            .map(TextStreamPart::source);
        var fullStream = delegate.fullStream()
            .switchOnFirst((signal, flux) -> {
                if (signal.hasValue() && PartType.START.equals(signal.get().getType())) {
                    return Flux.concat(Mono.just(signal.get()), sourceParts, flux.skip(1));
                }
                return Flux.concat(sourceParts, flux);
            });
        return new StreamTextResult(fullStream, delegate.textStream(), delegate.partialOutputStream(),
            delegate.elementStream(), delegate.output(),
            delegate.result().map(result -> attachSources(result, sources, warnings)));
    }

    private StreamTextResult emptyContextStream(PreparedRagRequest prepared) {
        var text = prepared.emptyText();
        var textId = "rag-empty-context";
        var result = Mono.just(emptyContextResult(prepared));
        var fullStream = Flux.just(
            TextStreamPart.start("rag-empty-context"),
            TextStreamPart.textStart(textId),
            TextStreamPart.textDelta(textId, text),
            TextStreamPart.textEnd(textId),
            TextStreamPart.finish(FinishReason.STOP, "stop", null)
        );
        return new StreamTextResult(fullStream, Flux.just(text), Flux.empty(), Flux.empty(),
            Mono.empty(), result);
    }

    private GenerateTextResult emptyContextResult(PreparedRagRequest prepared) {
        return GenerateTextResult.builder()
            .text(prepared.emptyText())
            .finishReason(FinishReason.STOP)
            .sources(prepared.sources())
            .warnings(prepared.warnings())
            .providerMetadata(Map.of("rag", Map.of("emptyContext", true)))
            .build();
    }

    private List<SourceReference> publicSources(List<RetrievedSource> sources) {
        return SourceReferences.fromRetrievedSources(sources.stream()
            .filter(source -> source.getVisible() == null || Boolean.TRUE.equals(source.getVisible()))
            .toList());
    }

    private List<SourceReference> mergeSources(List<SourceReference> existing,
        List<SourceReference> added) {
        var merged = new ArrayList<SourceReference>();
        if (existing != null) {
            merged.addAll(existing);
        }
        merged.addAll(added);
        return List.copyOf(merged);
    }

    private List<ModelMessage> messages(GenerateTextRequest request) {
        return request.getMessages() != null ? request.getMessages() : List.of();
    }

    private String query(GenerateTextRequest request) {
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            return request.getPrompt();
        }
        var messages = messages(request);
        for (int i = messages.size() - 1; i >= 0; i--) {
            var message = messages.get(i);
            if (message.getRole() == ModelMessageRole.USER && message.getContent() != null) {
                for (var part : message.getContent()) {
                    if (PartType.TEXT.equals(part.getType()) && part.getText() != null
                        && !part.getText().isBlank()) {
                        return part.getText();
                    }
                }
            }
        }
        return "";
    }

    private String appendBlock(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n\n" + second;
    }

    private Mono<Void> invokeLifecycleStart(String stage, String query,
        Map<String, Object> metadata, Map<String, Object> context) {
        if (options.getLifecycle() == null) {
            return Mono.empty();
        }
        var event = event(stage, query, null, null, null, null, metadata, context);
        return switch (stage) {
            case "retrieval" -> options.getLifecycle().onRetrievalStart(event);
            case "rerank" -> options.getLifecycle().onRerankStart(event);
            default -> Mono.empty();
        };
    }

    private Mono<Void> invokeLifecycleFinish(String stage, String query, Integer sourceCount,
        Integer contextCharacters, Map<String, Object> metadata, Map<String, Object> context) {
        if (options.getLifecycle() == null) {
            return Mono.empty();
        }
        var event = event(stage, query, sourceCount, contextCharacters, null, null, metadata,
            context);
        return switch (stage) {
            case "retrieval" -> options.getLifecycle().onRetrievalFinish(event);
            case "rerank" -> options.getLifecycle().onRerankFinish(event);
            case "context" -> options.getLifecycle().onContextPacked(event);
            default -> Mono.empty();
        };
    }

    private Mono<Void> invokeError(String stage, String query, Throwable error,
        Map<String, Object> metadata, Map<String, Object> context) {
        if (options.getLifecycle() == null) {
            return Mono.empty();
        }
        return options.getLifecycle().onError(event(stage, query, null, null, null, error,
            metadata, context));
    }

    private RagLifecycleEvent event(String stage, String query, Integer sourceCount,
        Integer contextCharacters, String warningCode, Throwable error, Map<String, Object> metadata,
        Map<String, Object> context) {
        return RagLifecycleEvent.builder()
            .stage(stage)
            .query(query)
            .sourceCount(sourceCount)
            .contextCharacters(contextCharacters)
            .warningCode(warningCode)
            .errorType(error != null ? error.getClass().getSimpleName() : null)
            .errorMessage(error != null ? error.getMessage() : null)
            .metadata(metadata)
            .context(context)
            .build();
    }

    private GenerationWarning warning(String code, String message) {
        return GenerationWarning.builder()
            .code(code)
            .message(message)
            .providerMetadata(Map.of("source", "rag"))
            .build();
    }

    private RagMiddlewareOptions withDefaults(RagMiddlewareOptions options) {
        var defaults = RagMiddlewareOptions.defaults(options.getRetriever());
        return defaults.toBuilder()
            .reranker(options.getReranker())
            .maxResults(options.getMaxResults() != null ? options.getMaxResults()
                : defaults.getMaxResults())
            .minScore(options.getMinScore())
            .maxContextCharacters(options.getMaxContextCharacters() != null
                ? options.getMaxContextCharacters()
                : defaults.getMaxContextCharacters())
            .promptPlacement(options.getPromptPlacement() != null
                ? options.getPromptPlacement()
                : defaults.getPromptPlacement())
            .emptyContextPolicy(options.getEmptyContextPolicy() != null
                ? options.getEmptyContextPolicy()
                : defaults.getEmptyContextPolicy())
            .retrievalFailurePolicy(options.getRetrievalFailurePolicy() != null
                ? options.getRetrievalFailurePolicy()
                : defaults.getRetrievalFailurePolicy())
            .rerankFailurePolicy(options.getRerankFailurePolicy() != null
                ? options.getRerankFailurePolicy()
                : defaults.getRerankFailurePolicy())
            .emptyContextText(options.getEmptyContextText() != null
                ? options.getEmptyContextText()
                : defaults.getEmptyContextText())
            .contextHeader(options.getContextHeader() != null
                ? options.getContextHeader()
                : defaults.getContextHeader())
            .lifecycle(options.getLifecycle())
            .metadata(options.getMetadata())
            .context(options.getContext())
            .retrieverOptions(options.getRetrieverOptions())
            .build();
    }

    private record PreparedRagRequest(
        GenerateTextRequest request,
        List<SourceReference> sources,
        List<GenerationWarning> warnings,
        boolean skipModel,
        String emptyText
    ) {
    }
}
