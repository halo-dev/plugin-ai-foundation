package run.halo.aifoundation.service.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.chat.GenerationRequestMetadata;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.GenerationStep;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.chat.PreparedStep;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.chat.StepContext;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.language.mapping.LanguageModelChatOptionsBuilder;
import run.halo.aifoundation.service.language.mapping.LanguageModelMessageMapper;
import run.halo.aifoundation.service.language.mapping.LanguageModelRequestValidator;
import run.halo.aifoundation.service.language.mapping.LanguageModelResponseMapper;
import run.halo.aifoundation.service.language.mapping.LanguageModelToolCallMapper;
import run.halo.aifoundation.service.language.reasoning.ReasoningContentExtractor;
import run.halo.aifoundation.service.language.stream.LanguageModelStreamResultBuilder;
import run.halo.aifoundation.service.language.stream.StreamProtocolNormalizer;
import run.halo.aifoundation.service.language.structured.LanguageModelStructuredOutputHandler;
import run.halo.aifoundation.service.language.structured.StructuredOutput;
import run.halo.aifoundation.service.language.tool.LanguageModelToolExecutor;
import run.halo.aifoundation.service.language.tool.ToolApprovalResolver;
import run.halo.aifoundation.service.language.tool.ToolExecutionBatch;
import run.halo.aifoundation.service.language.tool.ToolStepCoordinator;

@Slf4j
public class LanguageModelImpl implements LanguageModel {
    private static final int DEFAULT_STEP_LIMIT = 1;
    private static final int MAX_STEP_LIMIT = 10;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final String WARNING_STRUCTURED_OUTPUT_PROMPT_GUIDANCE =
        "structured-output-prompt-guidance";
    private static final String WARNING_STRUCTURED_OUTPUT_STRICT_NOT_GUARANTEED =
        "structured-output-strict-not-guaranteed";
    private static final String WARNING_TOOL_INPUT_EXAMPLES_IGNORED =
        "tool-input-examples-ignored";
    private static final String WARNING_REASONING_RETURNED_WHILE_DISABLED =
        "reasoning-returned-while-disabled";

    private final ChatModel chatModel;
    private final String providerType;
    private final LanguageModelProviderOptions providerOptions;
    private final LanguageModelRequestValidator requestValidator;
    private final LanguageModelMessageMapper messageMapper;
    private final GenerationMessageHistoryAssembler messageHistoryAssembler;
    private final LanguageModelChatOptionsBuilder chatOptionsBuilder;
    private final LanguageModelResponseMapper responseMapper;
    private final LanguageModelToolCallMapper toolCallMapper;
    private final LanguageModelToolExecutor toolExecutor;
    private final ToolStepCoordinator toolStepCoordinator;
    private final ToolApprovalResolver approvalResolver;
    private final LanguageModelStructuredOutputHandler structuredOutputHandler;
    private final ReasoningContentExtractor reasoningExtractor;
    private final LanguageModelRuntimeSupport runtimeSupport;

    LanguageModelImpl(ChatModel chatModel, String providerType) {
        this(chatModel, providerType, LanguageModelProviderOptions.defaults());
    }

    LanguageModelImpl(ChatModel chatModel, String providerType,
        LanguageModelProviderOptions providerOptions) {
        this(chatModel, LanguageModelRuntimeComposition.create(providerType, providerOptions,
            new LanguageModelRuntimeSupport()));
    }

    LanguageModelImpl(ChatModel chatModel, LanguageModelRuntimeComposition composition) {
        this.chatModel = chatModel;
        this.providerType = composition.providerType();
        this.providerOptions = composition.providerOptions();
        this.requestValidator = composition.requestValidator();
        this.messageMapper = composition.messageMapper();
        this.messageHistoryAssembler = composition.messageHistoryAssembler();
        this.chatOptionsBuilder = composition.chatOptionsBuilder();
        this.responseMapper = composition.responseMapper();
        this.reasoningExtractor = composition.reasoningExtractor();
        this.toolCallMapper = composition.toolCallMapper();
        this.structuredOutputHandler = composition.structuredOutputHandler();
        this.toolExecutor = composition.toolExecutor();
        this.toolStepCoordinator = composition.toolStepCoordinator();
        this.approvalResolver = composition.approvalResolver();
        this.runtimeSupport = composition.runtimeSupport();
    }

    @Override
    public Mono<GenerateTextResult> generateText(String prompt) {
        return generateText(GenerateTextRequest.builder().prompt(prompt).build());
    }

    @Override
    public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
        var run = new LanguageModelGenerationRun(request, providerType,
            resolvedStopCondition(request));
        return generateTextReactive(request, run)
            .transform(mono -> withTotalTimeout(mono, request))
            .onErrorResume(error -> run.error(error, null, List.of()).then(Mono.error(error)));
    }

    @Override
    public StreamTextResult streamText(GenerateTextRequest request) {
        var run = new LanguageModelGenerationRun(request, providerType,
            resolvedStopCondition(request));
        var fullStream = StreamProtocolNormalizer.normalize(
            streamTextParts(request, run)
                .transform(stream -> withTotalTimeout(stream, request))
                .onErrorResume(error -> run.error(error, null, List.of())
                    .thenMany(Flux.just(terminalErrorPart(error))))
        ).cache();
        var textStream = fullStream
            .filter(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .map(TextStreamPart::getDelta)
            .filter(this::hasText);
        var result = fullStream.collectList()
            .map(parts -> resultFromStreamParts(request, parts))
            .cache();
        var output = hasStructuredOutput(request)
            ? result.map(GenerateTextResult::getOutput)
            : Mono.empty();
        return new StreamTextResult(
            fullStream,
            textStream,
            structuredOutputHandler.partialOutputStream(request, textStream),
            structuredOutputHandler.elementStream(request, textStream),
            output,
            result
        );
    }

    private Flux<TextStreamPart> streamTextParts(GenerateTextRequest request,
        LanguageModelGenerationRun run) {
        if (hasTools(request)) {
            return streamTextWithTools(request, run);
        }
        if (hasStructuredOutput(request)) {
            return streamStructuredText(request, run);
        }
        return Flux.defer(() -> {
            Prompt prompt;
            try {
                checkCancellation(request);
                prompt = buildPrompt(request);
                run.start();
                run.stepStart(0, request, initialExecutionMessages(request), List.of(),
                    resolvedStopCondition(request));
            } catch (RuntimeException e) {
                return run.error(e, 0, List.of())
                    .thenMany(Flux.just(TextStreamPart.error(e.getMessage())));
            }

            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var streamState = new SimpleStreamState();

            var stream = chatModel.stream(prompt)
                .transform(flux -> withStepTimeout(flux, request))
                .<ChatResponse>handle((response, sink) -> {
                    try {
                        checkCancellation(request);
                        sink.next(response);
                    } catch (RuntimeException e) {
                        sink.error(e);
                    }
                })
                .concatMap(response -> mapStreamResponse(request, run, response, streamState))
                .concatWith(Flux.defer(() -> {
                    if (streamState.finished.get()) {
                        return Flux.empty();
                    }
                    streamState.finished.set(true);
                    var finishReason = FinishReason.UNKNOWN;
                    var parts = new ArrayList<TextStreamPart>();
                    if (streamState.reasoningStarted.get()) {
                        parts.add(TextStreamPart.reasoningEnd(streamState.reasoningId));
                    }
                    if (streamState.textStarted.get()) {
                        parts.add(TextStreamPart.textEnd(streamState.textId));
                    }
                    var step = GenerationStep.builder()
                        .stepIndex(0)
                        .finishReason(finishReason)
                        .warnings(run.warnings())
                        .providerMetadata(Map.of())
                        .build();
                    var result = GenerateTextResult.builder()
                        .finishReason(finishReason)
                        .warnings(run.warnings())
                        .steps(List.of(step))
                        .responseMessages(responseMessages(List.of(step)))
                        .build();
                    parts.add(TextStreamPart.finishStep(0, finishReason, null, null, run.warnings(), null,
                        null, Map.of()));
                    parts.add(TextStreamPart.finish(finishReason, null, null));
                    return run.stepFinish(0, step, List.of(step))
                        .then(run.finish(result))
                        .thenMany(Flux.fromIterable(parts));
                }))
                .onErrorResume(e -> {
                    log.error("[{}] Streaming error", providerType, e);
                    return run.error(e, 0, List.of()).thenMany(Flux.just(terminalErrorPart(e)));
                });

            return run.start()
                .then(run.stepStart(0, request, initialExecutionMessages(request), List.of(),
                    resolvedStopCondition(request)))
                .thenMany(Flux.concat(Flux.just(TextStreamPart.start(messageId),
                    TextStreamPart.startStep(0)), stream));
        });
    }

    private Flux<TextStreamPart> streamTextWithTools(GenerateTextRequest request,
        LanguageModelGenerationRun run) {
        return Flux.defer(() -> {
            List<org.springframework.ai.chat.messages.Message> messages;
            try {
                checkCancellation(request);
                validateRequest(request);
                assertToolCallingSupported(request);
                messages = buildMessages(request);
            } catch (RuntimeException e) {
                return run.error(e, null, List.of()).thenMany(Flux.just(terminalErrorPart(e)));
            }
            var executionMessages = initialExecutionMessages(request);
            var totalUsage = new UsageAccumulator();
            var steps = new ArrayList<GenerationStep>();
            var stopWhen = resolvedStopCondition(request);
            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var loop = new ToolStreamLoop(request, messages, executionMessages, totalUsage, steps,
                stopWhen, run);
            return run.start()
                .then(resumeApprovedTools(request, messages, executionMessages, run))
                .flatMapMany(resumedApprovals -> Flux.concat(Flux.just(TextStreamPart.start(messageId)),
                    Flux.fromIterable(resumedApprovals.content().stream()
                        .map(this::streamPartFromContent)
                        .toList()),
                    streamToolStep(loop, 0)));
        });
    }

    private Flux<TextStreamPart> streamStructuredText(GenerateTextRequest request,
        LanguageModelGenerationRun run) {
        return Flux.defer(() -> {
            List<org.springframework.ai.chat.messages.Message> messages;
            try {
                checkCancellation(request);
                validateRequest(request);
                messages = buildMessages(request);
            } catch (RuntimeException e) {
                return run.error(e, 0, List.of()).thenMany(Flux.just(terminalErrorPart(e)));
            }
            var accumulator = new StreamStepAccumulator(0);
            var totalUsage = new UsageAccumulator();
            var prompt = new Prompt(messages, buildChatOptions(request));
            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var stream = chatModel.stream(prompt)
                .transform(flux -> withStepTimeout(flux, request))
                .<ChatResponse>handle((response, sink) -> {
                    try {
                        checkCancellation(request);
                        sink.next(response);
                    } catch (RuntimeException e) {
                        sink.error(e);
                    }
                })
                .concatMap(response -> mapToolStreamResponse(request, response, accumulator))
                .onErrorResume(e -> {
                    log.error("[{}] Structured streaming error", providerType, e);
                    accumulator.failed = true;
                    return run.error(e, 0, List.of())
                        .thenMany(Flux.just(terminalErrorPart(e)));
                });
            return run.start()
                .then(run.stepStart(0, request, initialExecutionMessages(request), List.of(),
                    resolvedStopCondition(request)))
                .thenMany(Flux.concat(Flux.just(TextStreamPart.start(messageId),
                        TextStreamPart.startStep(0)),
                    stream,
                    Flux.defer(() -> completeStructuredSingleStep(request, run, accumulator,
                        totalUsage))));
        });
    }

    private Flux<TextStreamPart> completeStructuredSingleStep(GenerateTextRequest request,
        LanguageModelGenerationRun run, StreamStepAccumulator accumulator,
        UsageAccumulator totalUsage) {
        if (accumulator.failed) {
            return Flux.empty();
        }
        var parts = new ArrayList<TextStreamPart>();
        if (accumulator.reasoningStarted) {
            parts.add(TextStreamPart.reasoningEnd(accumulator.reasoningId));
        }
        if (accumulator.textStarted) {
            parts.add(TextStreamPart.textEnd(accumulator.textId));
        }
        if (accumulator.lastResponse != null) {
            var rawDiagnostic = responseMapper.sanitizedRawDiagnostic(accumulator.lastResponse);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
        }
        totalUsage.add(accumulator.usage());
        try {
            validateFinalStructuredOutput(request, accumulator);
        } catch (StructuredOutputValidationException e) {
            parts.add(structuredValidationErrorPart(e, accumulator));
            return Flux.fromIterable(parts);
        }
        var warnings = new ArrayList<>(accumulator.warnings());
        warnings.addAll(requestWarnings(request));
        warnings.addAll(run.warnings());
        var generationStep = streamGenerationStep(accumulator, accumulator.toolCalls(), List.of(),
            new ToolExecutionBatch(List.of(), List.of(), List.of()), warnings);
        parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
            accumulator.rawFinishReason, accumulator.usage(), warnings,
            accumulator.request(), accumulator.response(), accumulator.providerMetadata()));
        parts.add(TextStreamPart.finish(accumulator.finishReason(), accumulator.rawFinishReason,
            totalUsage.usage()));
        return run.stepFinish(accumulator.stepIndex, generationStep, List.of(generationStep))
            .then(run.finish(resultFromSteps(List.of(generationStep), totalUsage.usage())))
            .thenMany(Flux.fromIterable(parts));
    }

    private Flux<TextStreamPart> streamToolStep(ToolStreamLoop loop, int stepIndex) {
        return Flux.defer(() -> {
            PreparedInvocation prepared;
            try {
                checkCancellation(loop.request());
                prepared = prepareInvocation(loop.request(), loop.messages(), loop.executionMessages(),
                    loop.completedSteps(), null, stepIndex, loop.stopWhen());
            } catch (RuntimeException e) {
                return loop.run().error(e, stepIndex, loop.completedSteps())
                    .thenMany(Flux.just(terminalErrorPart(e)));
            }
            var accumulator = new StreamStepAccumulator(stepIndex);
            var prompt = new Prompt(prepared.messages(), buildChatOptions(prepared.request()));
            var stream = chatModel.stream(prompt)
                .transform(flux -> withStepTimeout(flux, prepared.request()))
                .<ChatResponse>handle((response, sink) -> {
                    try {
                        checkCancellation(prepared.request());
                        sink.next(response);
                    } catch (RuntimeException e) {
                        sink.error(e);
                    }
                })
                .concatMap(response -> mapToolStreamResponse(prepared.request(), response, accumulator))
                .onErrorResume(e -> {
                    log.error("[{}] Tool streaming error", providerType, e);
                    accumulator.failed = true;
                    return loop.run().error(e, stepIndex, loop.completedSteps())
                        .thenMany(Flux.just(terminalErrorPart(e)));
                });
            return loop.run().stepStart(stepIndex, prepared.request(), prepared.executionMessages(),
                    loop.completedSteps(), prepared.stopWhen())
                .thenMany(Flux.concat(Flux.just(TextStreamPart.startStep(stepIndex)), stream,
                    Flux.defer(() -> completeToolStreamStep(prepared, accumulator, loop))));
        });
    }

    private Flux<TextStreamPart> mapToolStreamResponse(GenerateTextRequest request, ChatResponse response,
        StreamStepAccumulator accumulator) {
        var parts = new ArrayList<TextStreamPart>();
        accumulator.accept(response);
        var reasoning = extractReasoning(response);
        if (hasText(reasoning)) {
            if (accumulator.textStarted) {
                accumulator.textStarted = false;
                parts.add(TextStreamPart.textEnd(accumulator.textId));
            }
            if (!accumulator.reasoningStarted) {
                accumulator.reasoningStarted = true;
                parts.add(TextStreamPart.reasoningStart(accumulator.reasoningId));
            }
            var output = response.getResult() != null ? response.getResult().getOutput() : null;
            var metadata = output != null && output.getMetadata() != null
                ? output.getMetadata()
                : Map.<String, Object>of();
            parts.add(TextStreamPart.reasoningDelta(accumulator.reasoningId, reasoning,
                reasoningProviderMetadata(reasoning, metadata)));
        }
        var text = responseMapper.extractText(response);
        if (hasText(text)) {
            if (accumulator.reasoningStarted) {
                accumulator.reasoningStarted = false;
                parts.add(TextStreamPart.reasoningEnd(accumulator.reasoningId));
            }
            if (!accumulator.textStarted) {
                accumulator.textStarted = true;
                parts.add(TextStreamPart.textStart(accumulator.textId));
            }
            parts.add(TextStreamPart.textDelta(accumulator.textId, text));
        }
        responseMapper.sourceAndFileParts(response).stream()
            .map(responseMapper::streamPart)
            .forEach(parts::add);
        return Flux.fromIterable(parts);
    }

    private Flux<TextStreamPart> completeToolStreamStep(PreparedInvocation prepared,
        StreamStepAccumulator accumulator, ToolStreamLoop loop) {
        if (accumulator.failed) {
            return Flux.empty();
        }
        var parts = new ArrayList<TextStreamPart>();
        if (accumulator.reasoningStarted) {
            parts.add(TextStreamPart.reasoningEnd(accumulator.reasoningId));
        }
        if (accumulator.textStarted) {
            parts.add(TextStreamPart.textEnd(accumulator.textId));
        }
        if (accumulator.lastResponse != null) {
            var rawDiagnostic = responseMapper.sanitizedRawDiagnostic(accumulator.lastResponse);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
        }

        var toolCalls = accumulator.toolCalls();
        var toolExecutionAllowed = accumulator.stepIndex + 1 < resolvedStepLimit(prepared.request());
        return toolStepCoordinator.resolve(new ToolStepCoordinator.ToolStepRequest(toolCalls,
                prepared.request(), accumulator.stepIndex, prepared.executionMessages(),
                accumulator.providerMetadata(), loop.run(), approvalResolver::approvalId,
                toolExecutionAllowed))
            .flatMapMany(toolStep -> completeResolvedToolStreamStep(prepared, accumulator, loop,
                parts, toolStep));
    }

    private Flux<TextStreamPart> completeResolvedToolStreamStep(PreparedInvocation prepared,
        StreamStepAccumulator accumulator, ToolStreamLoop loop, ArrayList<TextStreamPart> parts,
        ToolStepCoordinator.ToolStepResolution toolStep) {
        var request = prepared.request();
        var messages = prepared.messages();
        var executionMessages = prepared.executionMessages();
        var completedSteps = loop.completedSteps();
        var totalUsage = loop.totalUsage();
        var run = loop.run();
        var stopWhen = prepared.stopWhen();
        var toolResults = toolStep.execution();
        var recordedToolCalls = toolStep.toolCalls();
        recordedToolCalls.forEach(toolCall -> parts.add(TextStreamPart.toolCall(toolCall)));
        var approvalRequests = toolStep.approvalRequests();
        approvalRequests.forEach(approval -> parts.add(TextStreamPart.toolApprovalRequest(approval)));
        toolResults.results().forEach(result -> parts.add(TextStreamPart.toolResult(result)));
        toolResults.errors().forEach(error -> parts.add(TextStreamPart.toolError(error)));

        var warnings = new ArrayList<>(accumulator.warnings());
        warnings.addAll(toolStep.warnings());
        warnings.addAll(requestWarnings(request));
        warnings.addAll(run.warnings());
        totalUsage.add(accumulator.usage());
        var generationStep = streamGenerationStep(accumulator, recordedToolCalls, approvalRequests,
            toolResults, warnings);
        completedSteps.add(generationStep);
        var shouldContinue = stopWhen.shouldContinue(StepContext.builder()
            .stepIndex(accumulator.stepIndex)
            .step(generationStep)
            .steps(List.copyOf(completedSteps))
            .messages(List.copyOf(executionMessages))
            .tools(nullSafe(request.getTools()))
            .stopWhen(stopWhen)
            .providerOptions(request.getProviderOptions())
            .build());
        if (!toolStep.canContinue() || !shouldContinue) {
            if (hasStructuredOutput(request) && recordedToolCalls.isEmpty()
                && toolResults.errors().isEmpty()) {
                try {
                    validateFinalStructuredOutput(request, accumulator);
                } catch (StructuredOutputValidationException e) {
                    parts.add(structuredValidationErrorPart(e, accumulator));
                    return Flux.fromIterable(parts);
                }
            } else if (hasStructuredOutput(request) && (!recordedToolCalls.isEmpty()
                || !shouldContinue)) {
                parts.add(streamErrorPart(
                    "Structured output validation failed: final structured output was not produced",
                    accumulator, null));
                return Flux.fromIterable(parts);
            }
                parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
                    accumulator.rawFinishReason, accumulator.usage(), warnings, accumulator.request(),
                    generationStep.getResponse(), accumulator.providerMetadata()));
                parts.add(TextStreamPart.finish(accumulator.finishReason(), accumulator.rawFinishReason,
                    totalUsage.usage()));
            return run.stepFinish(accumulator.stepIndex, generationStep, List.copyOf(completedSteps))
                .then(run.finish(resultFromSteps(completedSteps, totalUsage.usage())))
                .thenMany(Flux.fromIterable(parts));
        }

        parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
            accumulator.rawFinishReason, accumulator.usage(), warnings, accumulator.request(),
            generationStep.getResponse(), accumulator.providerMetadata()));

        messages.add(accumulator.output(recordedToolCalls));
        messages.add(messageMapper.toolResponseMessage(toolResults.results()));
        executionMessages.addAll(nullSafe(generationStep.getResponseMessages()));
        var nextLoop = loop.next(request, messages, executionMessages, stopWhen);
        return run.stepFinish(accumulator.stepIndex, generationStep, List.copyOf(completedSteps))
            .thenMany(Flux.concat(Flux.fromIterable(parts),
                streamToolStep(nextLoop, accumulator.stepIndex + 1)));
    }

    private GenerationStep streamGenerationStep(StreamStepAccumulator accumulator,
        List<ToolCall> toolCalls, List<ToolApprovalRequest> approvalRequests,
        ToolExecutionBatch toolResults,
        List<GenerationWarning> warnings) {
        var content = new ArrayList<GenerationContentPart>();
        accumulator.reasoningParts().stream()
            .map(GenerationContentPart::reasoning)
            .forEach(content::add);
        if (hasText(accumulator.text.toString())) {
            content.add(GenerationContentPart.text(accumulator.text.toString()));
        }
        toolCalls.stream().map(GenerationContentPart::toolCall).forEach(content::add);
        approvalRequests.stream()
            .map(GenerationContentPart::toolApprovalRequest)
            .forEach(content::add);
        toolResults.results().stream().map(GenerationContentPart::toolResult).forEach(content::add);
        toolResults.errors().stream().map(GenerationContentPart::toolError).forEach(content::add);
        var responseMetadata = appendToolMessages(
            appendApprovalMessages(accumulator.response(toolCalls), approvalRequests),
            toolResults.results(), toolResults.errors());
        return GenerationStep.builder()
            .stepIndex(accumulator.stepIndex)
            .text(accumulator.text.toString())
            .reasoningText(hasText(accumulator.reasoning.toString())
                ? accumulator.reasoning.toString()
                : null)
            .content(content)
            .reasoning(accumulator.reasoningParts())
            .finishReason(accumulator.finishReason())
            .rawFinishReason(accumulator.rawFinishReason)
            .usage(accumulator.usage())
            .toolCalls(toolCalls)
            .toolApprovalRequests(approvalRequests)
            .toolResults(toolResults.results())
            .toolErrors(toolResults.errors())
            .responseMessages(responseMessages(responseMetadata))
            .warnings(warnings)
            .request(accumulator.request())
            .response(responseMetadata)
            .providerMetadata(accumulator.providerMetadata())
            .build();
    }

    private Mono<GenerateTextResult> generateTextReactive(GenerateTextRequest request,
        LanguageModelGenerationRun run) {
        return Mono.defer(() -> {
            checkCancellation(request);
            validateRequest(request);
            assertToolCallingSupported(request);
            var messages = buildMessages(request);
            var executionMessages = initialExecutionMessages(request);
            var state = new NonStreamingGenerationState();
            var stopWhen = resolvedStopCondition(request);
            return run.start()
                .then(resumeApprovedTools(request, messages, executionMessages, run))
                .flatMap(resumedApprovals -> {
                    state.allContent.addAll(resumedApprovals.content());
                    state.allToolResults.addAll(resumedApprovals.results());
                    state.allToolErrors.addAll(resumedApprovals.errors());
                    state.allResponseMessages.addAll(resumedApprovals.responseMessages());
                    state.resumedResults.addAll(resumedApprovals.results());
                    state.resumedErrors.addAll(resumedApprovals.errors());
                    return generateTextStep(request, run, messages, executionMessages, stopWhen, 0,
                        state);
                });
        });
    }

    private Mono<GenerateTextResult> generateTextStep(GenerateTextRequest baseRequest,
        LanguageModelGenerationRun run, List<org.springframework.ai.chat.messages.Message> messages,
        List<ModelMessage> executionMessages, StopCondition stopWhen, int stepIndex,
        NonStreamingGenerationState state) {
        if (stepIndex >= resolvedStepLimit(baseRequest)) {
            return finishGenerateText(baseRequest, run, state);
        }
        return Mono.defer(() -> {
            checkCancellation(baseRequest);
            var prepared = prepareInvocation(baseRequest, messages, executionMessages, state.steps, null,
                stepIndex, stopWhen);
            var stepRequest = prepared.request();
            var preparedStopWhen = prepared.stopWhen();
            return run.stepStart(stepIndex, stepRequest, prepared.executionMessages(), state.steps,
                    preparedStopWhen)
                .then(Mono.defer(() -> {
                    checkCancellation(stepRequest);
                    return callProvider(stepRequest, prepared.messages());
                }))
                .flatMap(response -> completeGenerateTextStep(baseRequest, run, prepared,
                    preparedStopWhen, stepIndex, response, state));
        });
    }

    private Mono<GenerateTextResult> completeGenerateTextStep(GenerateTextRequest baseRequest,
        LanguageModelGenerationRun run, PreparedInvocation prepared, StopCondition stopWhen,
        int stepIndex, ChatResponse response, NonStreamingGenerationState state) {
        var stepRequest = prepared.request();
        var step = mapStep(response, stepIndex, stepRequest);
        var toolExecutionAllowed = stepIndex + 1 < resolvedStepLimit(stepRequest);
        return toolStepCoordinator.resolve(new ToolStepCoordinator.ToolStepRequest(step.toolCalls(),
                stepRequest, stepIndex, prepared.executionMessages(), step.providerMetadata(), run,
                approvalResolver::approvalId, toolExecutionAllowed))
            .flatMap(toolStep -> {
                var generationStep = recordGenerateTextStep(stepRequest, prepared, stopWhen, step,
                    toolStep, state);
                var shouldContinue = stopWhen.shouldContinue(StepContext.builder()
                    .stepIndex(stepIndex)
                    .step(generationStep)
                    .steps(List.copyOf(state.steps))
                    .messages(List.copyOf(prepared.executionMessages()))
                    .tools(nullSafe(stepRequest.getTools()))
                    .stopWhen(stopWhen)
                    .providerOptions(stepRequest.getProviderOptions())
                    .build());
                return run.stepFinish(stepIndex, generationStep, List.copyOf(state.steps))
                    .then(Mono.defer(() -> {
                        if (!toolStep.canContinue() || !shouldContinue) {
                            return finishGenerateText(baseRequest, run, state);
                        }
                        checkCancellation(stepRequest);
                        var nextMessages = new ArrayList<>(prepared.messages());
                        nextMessages.add(assistantOutput(step.text(), step.reasoning(),
                            toolStep.toolCalls()));
                        nextMessages.add(messageMapper.toolResponseMessage(
                            toolStep.execution().results()));
                        var nextExecutionMessages = new ArrayList<>(prepared.executionMessages());
                        nextExecutionMessages.addAll(nullSafe(generationStep.getResponseMessages()));
                        return generateTextStep(baseRequest, run, nextMessages, nextExecutionMessages,
                            stopWhen, stepIndex + 1, state);
                    }));
            });
    }

    private GenerationStep recordGenerateTextStep(GenerateTextRequest stepRequest,
        PreparedInvocation prepared, StopCondition stopWhen, StepSnapshot step,
        ToolStepCoordinator.ToolStepResolution toolStep, NonStreamingGenerationState state) {
        var approvalRequests = toolStep.approvalRequests();
        var recordedToolCalls = toolStep.toolCalls();
        var toolResults = toolStep.execution();
        var stepContent = contentWithToolCalls(step.content(), recordedToolCalls);
        approvalRequests.stream()
            .map(GenerationContentPart::toolApprovalRequest)
            .forEach(stepContent::add);
        toolResults.results().stream()
            .map(GenerationContentPart::toolResult)
            .forEach(stepContent::add);
        toolResults.errors().stream()
            .map(GenerationContentPart::toolError)
            .forEach(stepContent::add);

        var stepWarnings = new ArrayList<>(step.warnings());
        stepWarnings.addAll(toolStep.warnings());
        stepWarnings.addAll(requestWarnings(stepRequest));
        var responseMetadata = appendToolMessages(
            appendApprovalMessages(responseWithToolCalls(step.response(), step.text(),
                step.reasoning(), recordedToolCalls), approvalRequests),
            toolResults.results(), toolResults.errors());
        var generationStep = GenerationStep.builder()
            .stepIndex(step.stepIndex())
            .text(step.text())
            .reasoningText(step.reasoningText())
            .content(stepContent)
            .reasoning(step.reasoning())
            .finishReason(step.finishReason())
            .rawFinishReason(step.rawFinishReason())
            .usage(step.usage())
            .toolCalls(recordedToolCalls)
            .toolApprovalRequests(approvalRequests)
            .toolResults(toolResults.results())
            .toolErrors(toolResults.errors())
            .responseMessages(responseMessages(responseMetadata))
            .warnings(stepWarnings)
            .request(step.request())
            .response(responseMetadata)
            .providerMetadata(step.providerMetadata())
            .build();
        state.steps.add(generationStep);
        state.allContent.addAll(stepContent);
        state.allWarnings.addAll(stepWarnings);
        state.allToolCalls.addAll(recordedToolCalls);
        state.allToolApprovalRequests.addAll(approvalRequests);
        state.allToolResults.addAll(toolResults.results());
        state.allToolErrors.addAll(toolResults.errors());
        state.allResponseMessages.addAll(nullSafe(generationStep.getResponseMessages()));
        state.totalUsage = addUsage(state.totalUsage, step.usage());
        state.finalFinishReason = step.finishReason();
        state.finalRawFinishReason = step.rawFinishReason();
        state.finalProviderMetadata = step.providerMetadata();
        state.finalRequestMetadata = step.request();
        state.finalResponseMetadata = responseMetadata;
        return generationStep;
    }

    private Mono<GenerateTextResult> finishGenerateText(GenerateTextRequest request,
        LanguageModelGenerationRun run, NonStreamingGenerationState state) {
        var text = state.allContent.stream()
            .filter(part -> PartType.isText(part.getType()))
            .map(GenerationContentPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var reasoning = state.steps.isEmpty()
            ? List.<ReasoningPart>of()
            : nullSafe(state.steps.get(state.steps.size() - 1).getReasoning());
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var usage = state.steps.isEmpty() ? null : state.steps.get(state.steps.size() - 1).getUsage();
        StructuredOutput structuredOutput = null;
        if (hasStructuredOutput(request)) {
            var outputSourceText = state.steps.isEmpty() ? text
                : state.steps.get(state.steps.size() - 1).getText();
            try {
                structuredOutput = structuredOutputHandler.parse(request.getOutput(),
                    outputSourceText);
            } catch (StructuredOutputValidationException e) {
                throw structuredOutputHandler.enrich(e, request.getOutput(), outputSourceText,
                    state.steps.isEmpty() ? null
                        : state.steps.get(state.steps.size() - 1).getStepIndex(),
                    usage, state.finalResponseMetadata);
            }
            if (!state.steps.isEmpty()) {
                var finalStep = state.steps.get(state.steps.size() - 1);
                finalStep.setOutput(structuredOutput.output());
                finalStep.setOutputText(structuredOutput.outputText());
            }
        }
        state.allWarnings.addAll(run.warnings());
        state.finalResponseMetadata = appendToolMessages(state.finalResponseMetadata,
            state.resumedResults, state.resumedErrors);
        var result = GenerateTextResult.builder()
            .text(text)
            .output(structuredOutput != null ? structuredOutput.output() : null)
            .outputText(structuredOutput != null ? structuredOutput.outputText() : null)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(state.allContent)
            .reasoning(reasoning)
            .finishReason(state.finalFinishReason)
            .rawFinishReason(state.finalRawFinishReason)
            .usage(usage)
            .totalUsage(state.totalUsage)
            .warnings(state.allWarnings)
            .request(state.finalRequestMetadata)
            .response(state.finalResponseMetadata)
            .steps(state.steps)
            .responseMessages(state.allResponseMessages)
            .toolCalls(state.allToolCalls)
            .toolApprovalRequests(state.allToolApprovalRequests)
            .toolResults(state.allToolResults)
            .toolErrors(state.allToolErrors)
            .providerMetadata(state.finalProviderMetadata)
            .build();
        return run.finish(result)
            .then(Mono.fromSupplier(() -> {
                result.setWarnings(mergeWarnings(result.getWarnings(), run.warnings()));
                return result;
            }));
    }

    private Prompt buildPrompt(GenerateTextRequest request) {
        validateRequest(request);
        return new Prompt(buildMessages(request), buildChatOptions(request));
    }

    private Mono<ApprovalResumeResult> resumeApprovedTools(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> providerMessages,
        List<ModelMessage> executionMessages, LanguageModelGenerationRun run) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return Mono.just(new ApprovalResumeResult(List.of(), List.of(), List.of(), List.of()));
        }
        var resolution = approvalResolver.resolve(request.getMessages());
        if (resolution.isEmpty()) {
            return Mono.just(new ApprovalResumeResult(List.of(), List.of(), List.of(), List.of()));
        }
        var resumedContent = new ArrayList<GenerationContentPart>();
        var resumedResults = new ArrayList<ToolResult>();
        var resumedErrors = new ArrayList<ToolError>();
        var responseMessages = new ArrayList<ModelMessage>();
        return Flux.fromIterable(resolution.approvals())
            .concatMap(approval -> {
                if (!Boolean.TRUE.equals(approval.response().approved())) {
                    resumedErrors.add(approval.deniedError());
                    return Mono.empty();
                }
                var stepIndex = approval.request().stepIndex() != null
                    ? approval.request().stepIndex()
                    : 0;
                return toolExecutor.execute(List.of(approval.toolCall()), request, stepIndex,
                        executionMessages, approval.request().providerMetadata(), run)
                    .doOnNext(batch -> {
                        resumedResults.addAll(batch.results());
                        resumedErrors.addAll(batch.errors());
                    });
            })
            .then(Mono.fromSupplier(() -> {
                if (!resumedResults.isEmpty() || !resumedErrors.isEmpty()) {
                    var parts = new ArrayList<ModelMessagePart>();
                    resumedResults.stream().map(ModelMessagePart::toolResult).forEach(parts::add);
                    resumedErrors.stream().map(ModelMessagePart::toolError).forEach(parts::add);
                    var toolMessage = ModelMessage.tool(parts);
                    executionMessages.add(toolMessage);
                    providerMessages.add(messageMapper.convert(toolMessage));
                    responseMessages.add(toolMessage);
                    resumedResults.stream()
                        .map(GenerationContentPart::toolResult)
                        .forEach(resumedContent::add);
                    resumedErrors.stream()
                        .map(GenerationContentPart::toolError)
                        .forEach(resumedContent::add);
                }
                return new ApprovalResumeResult(resumedContent, resumedResults, resumedErrors,
                    responseMessages);
            }));
    }

    private GenerationResponseMetadata appendApprovalMessages(GenerationResponseMetadata response,
        List<ToolApprovalRequest> approvals) {
        return messageHistoryAssembler.appendApprovalMessages(response, approvals);
    }

    private GenerationResponseMetadata appendToolMessages(GenerationResponseMetadata response,
        List<ToolResult> results, List<ToolError> errors) {
        return messageHistoryAssembler.appendToolMessages(response, results, errors);
    }

    private GenerationResponseMetadata responseWithToolCalls(GenerationResponseMetadata response,
        String text, List<ReasoningPart> reasoning, List<ToolCall> toolCalls) {
        return messageHistoryAssembler.withToolCalls(response, text, reasoning, toolCalls);
    }

    private AssistantMessage assistantOutput(String text, List<ReasoningPart> reasoning,
        List<ToolCall> toolCalls) {
        var messages = messageMapper.responseMessages(text, reasoning, toolCalls);
        if (messages.isEmpty()) {
            return new AssistantMessage(text != null ? text : "");
        }
        return (AssistantMessage) messageMapper.convert(messages.getFirst());
    }

    private List<GenerationContentPart> contentWithToolCalls(List<GenerationContentPart> content,
        List<ToolCall> toolCalls) {
        var parts = new ArrayList<GenerationContentPart>();
        nullSafe(content).stream()
            .filter(part -> !PartType.isToolCall(part.getType()))
            .forEach(parts::add);
        nullSafe(toolCalls).stream()
            .map(GenerationContentPart::toolCall)
            .forEach(parts::add);
        return parts;
    }

    private List<ModelMessage> responseMessages(GenerationResponseMetadata response) {
        return messageHistoryAssembler.responseMessages(response);
    }

    private GenerationResponseMetadata mapResponseMetadata(ChatResponse response, String text,
        List<ReasoningPart> reasoning, List<ToolCall> toolCalls) {
        return sanitizeResponseMessages(
            responseMapper.mapResponseMetadata(response, text, reasoning, toolCalls));
    }

    private GenerationResponseMetadata sanitizeResponseMessages(GenerationResponseMetadata response) {
        return messageHistoryAssembler.sanitize(response);
    }

    private List<ModelMessage> responseMessages(List<GenerationStep> steps) {
        return messageHistoryAssembler.responseMessages(steps);
    }

    private TextStreamPart streamPartFromContent(GenerationContentPart part) {
        if (PartType.isToolResult(part.getType())) {
            return TextStreamPart.toolResult(ToolResult.builder()
                .toolCallId(part.getToolCallId())
                .toolName(part.getToolName())
                .result(part.getResult())
                .providerMetadata(part.getMetadata())
                .build());
        }
        if (PartType.isToolError(part.getType())) {
            return TextStreamPart.toolError(ToolError.builder()
                .toolCallId(part.getToolCallId())
                .toolName(part.getToolName())
                .errorText(part.getErrorText())
                .providerMetadata(part.getMetadata())
                .build());
        }
        return TextStreamPart.raw(Map.of("ignoredPartType", part.getType()));
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessages(
        GenerateTextRequest request) {
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        if (hasText(request.getSystem())) {
            messages.add(new SystemMessage(request.getSystem().trim()));
        }
        var outputInstruction = structuredOutputHandler.instruction(request.getOutput());
        if (hasText(outputInstruction)) {
            messages.add(new SystemMessage(outputInstruction));
        }
        if (hasText(request.getPrompt())) {
            messages.add(new UserMessage(request.getPrompt().trim()));
        } else {
            request.getMessages().stream()
                .filter(this::hasProviderMessage)
                .map(messageMapper::convert)
                .forEach(messages::add);
        }
        return messages;
    }

    private boolean hasProviderMessage(ModelMessage message) {
        if (message == null || message.getContent() == null) {
            return false;
        }
        return switch (message.getRole()) {
            case TOOL -> messageMapper.hasProviderToolResponse(message);
            case ASSISTANT -> message.getContent().stream()
                .anyMatch(part -> PartType.isText(part.getType())
                    || PartType.isReasoning(part.getType())
                    || PartType.isToolCall(part.getType()));
            default -> true;
        };
    }

    private PreparedInvocation prepareInvocation(GenerateTextRequest baseRequest,
        List<org.springframework.ai.chat.messages.Message> currentMessages,
        List<ModelMessage> currentExecutionMessages, List<GenerationStep> completedSteps,
        GenerationStep completedStep, int stepIndex, StopCondition currentStopWhen) {
        var context = StepContext.builder()
            .stepIndex(stepIndex)
            .step(completedStep)
            .steps(List.copyOf(nullSafe(completedSteps)))
            .messages(List.copyOf(nullSafe(currentExecutionMessages)))
            .tools(nullSafe(baseRequest.getTools()))
            .stopWhen(currentStopWhen)
            .seed(baseRequest.getSeed())
            .maxRetries(baseRequest.getMaxRetries())
            .providerOptions(baseRequest.getProviderOptions())
            .build();
        var prepared = baseRequest.getPrepareStep() != null
            ? baseRequest.getPrepareStep().prepare(context)
            : null;
        if (prepared == null) {
            prepared = PreparedStep.empty();
        }
        var stepRequest = applyPreparedStep(baseRequest, prepared);
        validateActiveTools(baseRequest, prepared);
        requestValidator.validateTools(stepRequest);
        List<ModelMessage> executionMessages = prepared.getMessages() != null
            ? new ArrayList<>(prepared.getMessages())
            : new ArrayList<>(nullSafe(currentExecutionMessages));
        List<org.springframework.ai.chat.messages.Message> providerMessages = prepared.getMessages() != null
            ? buildMessages(stepRequest)
            : new ArrayList<>(currentMessages);
        var stopWhen = prepared.getStopWhen() != null ? prepared.getStopWhen() : currentStopWhen;
        return new PreparedInvocation(stepRequest, providerMessages, executionMessages, stopWhen);
    }

    private GenerateTextRequest applyPreparedStep(GenerateTextRequest request,
        PreparedStep prepared) {
        var tools = request.getTools();
        if (prepared.getActiveTools() != null) {
            var active = Set.copyOf(prepared.getActiveTools());
            tools = nullSafe(request.getTools()).stream()
                .filter(tool -> active.contains(tool.getName()))
                .toList();
        }
        return GenerateTextRequest.builder()
            .system(prepared.getMessages() != null ? null : request.getSystem())
            .prompt(prepared.getMessages() != null ? null : request.getPrompt())
            .messages(prepared.getMessages() != null ? prepared.getMessages() : request.getMessages())
            .maxOutputTokens(prepared.getMaxOutputTokens() != null
                ? prepared.getMaxOutputTokens()
                : request.getMaxOutputTokens())
            .temperature(prepared.getTemperature() != null
                ? prepared.getTemperature()
                : request.getTemperature())
            .topP(prepared.getTopP() != null ? prepared.getTopP() : request.getTopP())
            .topK(prepared.getTopK() != null ? prepared.getTopK() : request.getTopK())
            .presencePenalty(prepared.getPresencePenalty() != null
                ? prepared.getPresencePenalty()
                : request.getPresencePenalty())
            .frequencyPenalty(prepared.getFrequencyPenalty() != null
                ? prepared.getFrequencyPenalty()
                : request.getFrequencyPenalty())
            .stopSequences(prepared.getStopSequences() != null
                ? prepared.getStopSequences()
                : request.getStopSequences())
            .seed(prepared.getSeed() != null ? prepared.getSeed() : request.getSeed())
            .maxRetries(prepared.getMaxRetries() != null
                ? prepared.getMaxRetries()
                : request.getMaxRetries())
            .providerOptions(prepared.getProviderOptions() != null
                ? prepared.getProviderOptions()
                : request.getProviderOptions())
            .reasoning(request.getReasoning())
            .headers(request.getHeaders())
            .metadata(request.getMetadata())
            .context(request.getContext())
            .output(request.getOutput())
            .tools(tools)
            .toolChoice(prepared.getToolChoice() != null
                ? prepared.getToolChoice()
                : request.getToolChoice())
            .stopWhen(prepared.getStopWhen() != null ? prepared.getStopWhen() : request.getStopWhen())
            .prepareStep(request.getPrepareStep())
            .lifecycle(request.getLifecycle())
            .toolCallRepair(request.getToolCallRepair())
            .cancellationToken(request.getCancellationToken())
            .timeouts(request.getTimeouts())
            .build();
    }

    private void validateActiveTools(GenerateTextRequest request, PreparedStep prepared) {
        if (prepared.getActiveTools() == null) {
            return;
        }
        var names = nullSafe(request.getTools()).stream()
            .map(ToolDefinition::getName)
            .collect(Collectors.toSet());
        for (var name : prepared.getActiveTools()) {
            if (!names.contains(name)) {
                throw new IllegalArgumentException("prepareStep activeTools references unknown tool: "
                    + name);
            }
        }
    }

    private List<ModelMessage> initialExecutionMessages(GenerateTextRequest request) {
        var messages = new ArrayList<ModelMessage>();
        if (hasText(request.getSystem())) {
            messages.add(ModelMessage.system(request.getSystem().trim()));
        }
        var outputInstruction = structuredOutputHandler.instruction(request.getOutput());
        if (hasText(outputInstruction)) {
            messages.add(ModelMessage.system(outputInstruction));
        }
        if (hasText(request.getPrompt())) {
            messages.add(ModelMessage.user(request.getPrompt().trim()));
        } else if (request.getMessages() != null) {
            messages.addAll(request.getMessages());
        }
        return messages;
    }

    private void validateRequest(GenerateTextRequest request) {
        requestValidator.validate(request);
    }

    private void assertToolCallingSupported(GenerateTextRequest request) {
        chatOptionsBuilder.assertRequestSupported(request, supportsToolCalling(),
            toolCallingUnsupportedMessage());
    }

    protected boolean supportsToolCalling() {
        return true;
    }

    protected boolean supportsReasoningHistory() {
        return providerOptions.reasoningHistorySupported();
    }

    private String toolCallingUnsupportedMessage() {
        return "Tool calling is not supported by provider type: " + providerType;
    }

    private org.springframework.ai.chat.prompt.ChatOptions buildChatOptions(
        GenerateTextRequest request) {
        return chatOptionsBuilder.build(request);
    }

    private Mono<ChatResponse> callProvider(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages) {
        var call = Mono.defer(() -> {
            checkCancellation(request);
            var prompt = new Prompt(messages, buildChatOptions(request));
            if (shouldUseReasoningAwareStreamCall(request)) {
                return chatModel.stream(prompt)
                    .collectList()
                    .map(this::aggregateStreamResponses);
            }
            return Mono.fromCallable(() -> chatModel.call(prompt))
                .subscribeOn(Schedulers.boundedElastic());
        });
        call = withStepTimeout(call, request);
        var maxRetries = maxRetries(request);
        if (maxRetries > 0) {
            call = call.retryWhen(Retry.max(maxRetries).filter(this::isRetryableProviderFailure));
        }
        return call;
    }

    private int maxRetries(GenerateTextRequest request) {
        return request.getMaxRetries() != null ? request.getMaxRetries() : DEFAULT_MAX_RETRIES;
    }

    private boolean isRetryableProviderFailure(Throwable error) {
        return !(error instanceof IllegalArgumentException
            || error instanceof AiGenerationCancelledException
            || error instanceof AiGenerationTimeoutException
            || error instanceof StructuredOutputValidationException
            || error instanceof TimeoutException);
    }

    private boolean shouldUseReasoningAwareStreamCall(GenerateTextRequest request) {
        return providerOptions.streamToolCallsForReasoning() && hasTools(request);
    }

    private ChatResponse aggregateStreamResponses(List<ChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return new ChatResponse(List.of());
        }
        var aggregation = new StreamResponseAggregation();
        for (var response : responses) {
            aggregation.accept(response);
        }
        if (!aggregation.hasOutput()) {
            return responses.get(responses.size() - 1);
        }
        return aggregation.toResponse();
    }

    private StepSnapshot mapStep(ChatResponse response, int stepIndex,
        GenerateTextRequest request) {
        var result = response.getResult();
        var output = result != null ? result.getOutput() : null;
        var originalText = output != null && output.getText() != null ? output.getText() : "";
        var extraction = reasoningExtractor.extract(originalText,
            output != null ? output.getMetadata() : Map.of());
        var text = extraction.text();
        var reasoning = extraction.reasoning();
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var rawFinishReason = rawFinishReason(result);
        var toolCalls = output != null ? toolCallMapper.mapToolCalls(output.getToolCalls())
            : List.<ToolCall>of();
        var content = stepContent(response, text, reasoning, toolCalls);
        var warnings = stepWarnings(response, request, reasoning);
        return new StepSnapshot(
            stepIndex,
            text,
            hasText(reasoningText) ? reasoningText : null,
            output != null ? output : new AssistantMessage(text),
            content,
            reasoning,
            responseMapper.mapFinishReason(rawFinishReason),
            rawFinishReason,
            responseMapper.mapUsage(response),
            toolCalls,
            warnings,
            responseMapper.mapRequestMetadata(response),
            mapResponseMetadata(response, text, reasoning, toolCalls),
            responseMapper.mapMetadata(response)
        );
    }

    private String rawFinishReason(Generation result) {
        return result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
    }

    private List<GenerationContentPart> stepContent(ChatResponse response, String text,
        List<ReasoningPart> reasoning, List<ToolCall> toolCalls) {
        var content = new ArrayList<GenerationContentPart>();
        reasoning.stream().map(GenerationContentPart::reasoning).forEach(content::add);
        if (hasText(text)) {
            content.add(GenerationContentPart.text(text));
        }
        content.addAll(responseMapper.sourceAndFileParts(response));
        toolCalls.stream().map(GenerationContentPart::toolCall).forEach(content::add);
        return content;
    }

    private List<GenerationWarning> stepWarnings(ChatResponse response, GenerateTextRequest request,
        List<ReasoningPart> reasoning) {
        var warnings = new ArrayList<>(responseMapper.mapWarnings(response));
        warnings.addAll(reasoningReturnedWhileDisabledWarnings(request, reasoning));
        return warnings;
    }

    private Map<String, Object> reasoningProviderMetadata(String reasoningContent,
        Map<String, Object> outputMetadata) {
        return reasoningExtractor.extract("", outputMetadata).reasoning().stream()
            .findFirst()
            .map(ReasoningPart::getProviderMetadata)
            .orElse(Map.of());
    }

    private void validateFinalStructuredOutput(GenerateTextRequest request,
        StreamStepAccumulator accumulator) {
        if (!hasStructuredOutput(request)) {
            return;
        }
        structuredOutputHandler.parse(request.getOutput(), accumulator.text.toString());
    }

    private GenerateTextResult resultFromStreamParts(GenerateTextRequest request,
        List<TextStreamPart> parts) {
        var builder = new LanguageModelStreamResultBuilder();
        for (var part : parts) {
            builder.accept(part);
        }
        if (builder.errorText() != null) {
            if ("cancelled".equals(builder.errorType())) {
                throw new AiGenerationCancelledException(builder.errorText());
            }
            if ("timeout".equals(builder.errorType())) {
                throw new AiGenerationTimeoutException("stream", builder.errorText());
            }
            if (hasStructuredOutput(request)) {
                throw new StructuredOutputValidationException(builder.errorText(), null,
                    request.getOutput().getType(), builder.finalStepText(),
                    builder.errorValidationPath(),
                    builder.currentStepIndex(), builder.usage(), builder.response());
            }
            throw new IllegalStateException(builder.errorText());
        }
        StructuredOutput structuredOutput = null;
        if (hasStructuredOutput(request)) {
            try {
                structuredOutput = structuredOutputHandler.parse(request.getOutput(),
                    builder.finalStepText());
            } catch (StructuredOutputValidationException e) {
                throw structuredOutputHandler.enrich(e, request.getOutput(), builder.finalStepText(),
                    builder.currentStepIndex(), builder.usage(), builder.response());
            }
        }
        return builder.build(structuredOutput);
    }

    private TextStreamPart structuredValidationErrorPart(StructuredOutputValidationException error,
        StreamStepAccumulator accumulator) {
        return streamErrorPart(error.getMessage(), accumulator, error.getValidationPath());
    }

    private TextStreamPart streamErrorPart(String message, StreamStepAccumulator accumulator,
        String validationPath) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.putAll(accumulator.providerMetadata());
        if (validationPath != null) {
            metadata.put("validationPath", validationPath);
        }
        return TextStreamPart.builder()
            .type(PartType.ERROR)
            .errorText(message)
            .stepIndex(accumulator.stepIndex)
            .usage(accumulator.usage())
            .response(accumulator.response())
            .providerMetadata(metadata)
            .build();
    }

    private GenerateTextResult resultFromSteps(List<GenerationStep> steps,
        LanguageModelUsage totalUsage) {
        var safeSteps = List.copyOf(nullSafe(steps));
        var finalStep = safeSteps.isEmpty() ? null : safeSteps.get(safeSteps.size() - 1);
        var content = safeSteps.stream()
            .flatMap(step -> nullSafe(step.getContent()).stream())
            .toList();
        var warnings = safeSteps.stream()
            .flatMap(step -> nullSafe(step.getWarnings()).stream())
            .toList();
        var toolCalls = safeSteps.stream()
            .flatMap(step -> nullSafe(step.getToolCalls()).stream())
            .toList();
        var toolApprovalRequests = safeSteps.stream()
            .flatMap(step -> nullSafe(step.getToolApprovalRequests()).stream())
            .toList();
        var toolResults = safeSteps.stream()
            .flatMap(step -> nullSafe(step.getToolResults()).stream())
            .toList();
        var toolErrors = safeSteps.stream()
            .flatMap(step -> nullSafe(step.getToolErrors()).stream())
            .toList();
        var text = safeSteps.stream()
            .map(GenerationStep::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var reasoning = finalStep != null ? nullSafe(finalStep.getReasoning()) : List.<ReasoningPart>of();
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        return GenerateTextResult.builder()
            .text(text)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(content)
            .reasoning(reasoning)
            .finishReason(finalStep != null ? finalStep.getFinishReason() : FinishReason.UNKNOWN)
            .rawFinishReason(finalStep != null ? finalStep.getRawFinishReason() : null)
            .usage(finalStep != null ? finalStep.getUsage() : null)
            .totalUsage(totalUsage)
            .warnings(warnings)
            .request(finalStep != null ? finalStep.getRequest() : null)
            .response(finalStep != null ? finalStep.getResponse() : null)
            .steps(safeSteps)
            .responseMessages(responseMessages(safeSteps))
            .toolCalls(toolCalls)
            .toolApprovalRequests(toolApprovalRequests)
            .toolResults(toolResults)
            .toolErrors(toolErrors)
            .providerMetadata(finalStep != null ? finalStep.getProviderMetadata() : Map.of())
            .build();
    }

    private LanguageModelUsage addUsage(LanguageModelUsage left, LanguageModelUsage right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return LanguageModelUsage.builder()
            .inputTokens(sum(left.getInputTokens(), right.getInputTokens()))
            .outputTokens(sum(left.getOutputTokens(), right.getOutputTokens()))
            .reasoningTokens(sum(left.getReasoningTokens(), right.getReasoningTokens()))
            .totalTokens(sum(left.getTotalTokens(), right.getTotalTokens()))
            .build();
    }

    private <T> Mono<T> withTotalTimeout(Mono<T> mono, GenerateTextRequest request) {
        var timeout = totalTimeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("total", timeout, error));
    }

    private <T> Flux<T> withTotalTimeout(Flux<T> flux, GenerateTextRequest request) {
        var timeout = totalTimeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return flux;
        }
        return flux.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("total", timeout, error));
    }

    private <T> Mono<T> withStepTimeout(Mono<T> mono, GenerateTextRequest request) {
        var timeout = stepTimeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("step", timeout, error));
    }

    private <T> Flux<T> withStepTimeout(Flux<T> flux, GenerateTextRequest request) {
        var timeout = stepTimeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return flux;
        }
        return flux.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("step", timeout, error));
    }

    private Duration totalTimeout(GenerateTextRequest request) {
        return request != null && request.getTimeouts() != null
            ? request.getTimeouts().getTotalTimeout()
            : null;
    }

    private Duration stepTimeout(GenerateTextRequest request) {
        return request != null && request.getTimeouts() != null
            ? request.getTimeouts().getStepTimeout()
            : null;
    }

    private void checkCancellation(GenerateTextRequest request) {
        runtimeSupport.checkCancellation(request);
    }

    private TextStreamPart terminalErrorPart(Throwable error) {
        var type = error instanceof AiGenerationCancelledException
            ? "cancelled"
            : error instanceof AiGenerationTimeoutException ? "timeout" : "error";
        return TextStreamPart.builder()
            .type(PartType.ERROR)
            .errorText(safeErrorMessage(error))
            .providerMetadata(Map.of("exceptionType", type))
            .build();
    }

    private Integer sum(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private int resolvedStepLimit(GenerateTextRequest request) {
        return request.getStopWhen() != null ? MAX_STEP_LIMIT : DEFAULT_STEP_LIMIT;
    }

    private StopCondition resolvedStopCondition(GenerateTextRequest request) {
        if (request.getStopWhen() != null) {
            return request.getStopWhen();
        }
        return StopCondition.stepCountIs(DEFAULT_STEP_LIMIT);
    }

    private boolean hasTools(GenerateTextRequest request) {
        return request != null && request.getTools() != null && !request.getTools().isEmpty();
    }

    private boolean hasStructuredOutput(GenerateTextRequest request) {
        return request != null
            && request.getOutput() != null
            && request.getOutput().getType() != null
            && request.getOutput().getType() != OutputType.TEXT;
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private Flux<TextStreamPart> mapStreamResponse(GenerateTextRequest request,
        LanguageModelGenerationRun run, ChatResponse response, SimpleStreamState state) {
        var parts = new ArrayList<TextStreamPart>();
        var text = responseMapper.extractText(response);
        var reasoning = extractReasoning(response);
        if (hasText(reasoning)) {
            if (state.textStarted.get()) {
                state.textStarted.set(false);
                parts.add(TextStreamPart.textEnd(state.textId));
            }
            if (!state.reasoningStarted.get()) {
                state.reasoningStarted.set(true);
                parts.add(TextStreamPart.reasoningStart(state.reasoningId));
            }
            parts.add(TextStreamPart.reasoningDelta(state.reasoningId, reasoning,
                reasoningProviderMetadata(reasoning, response.getResult().getOutput().getMetadata())));
        }
        if (hasText(text)) {
            if (state.reasoningStarted.get()) {
                state.reasoningStarted.set(false);
                parts.add(TextStreamPart.reasoningEnd(state.reasoningId));
            }
            if (!state.textStarted.get()) {
                state.textStarted.set(true);
                parts.add(TextStreamPart.textStart(state.textId));
            }
            parts.add(TextStreamPart.textDelta(state.textId, text));
        }
        responseMapper.sourceAndFileParts(response).stream()
            .map(responseMapper::streamPart)
            .forEach(parts::add);

        var rawFinishReason = responseMapper.extractFinishReason(response);
        if (responseMapper.isFinish(rawFinishReason)) {
            state.finished.set(true);
            var finishReason = responseMapper.mapFinishReason(rawFinishReason);
            var usage = responseMapper.mapUsage(response);
            var rawDiagnostic = responseMapper.sanitizedRawDiagnostic(response);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
            if (state.reasoningStarted.get()) {
                state.reasoningStarted.set(false);
                parts.add(TextStreamPart.reasoningEnd(state.reasoningId));
            }
            if (state.textStarted.get()) {
                state.textStarted.set(false);
                parts.add(TextStreamPart.textEnd(state.textId));
            }
            var reasoningParts = responseMapper.reasoningParts(reasoning);
            var warnings = mergeWarnings(
                mergeWarnings(responseMapper.mapWarnings(response), requestWarnings(request)),
                mergeWarnings(run.warnings(),
                    reasoningReturnedWhileDisabledWarnings(request, reasoningParts)));
            parts.add(TextStreamPart.finishStep(0, finishReason, rawFinishReason, usage,
                warnings, responseMapper.mapRequestMetadata(response),
                mapResponseMetadata(response, responseMapper.extractText(response), reasoningParts,
                    List.of()), responseMapper.mapMetadata(response)));
            var step = GenerationStep.builder()
                .stepIndex(0)
                .text(text)
                .reasoningText(hasText(reasoning) ? reasoning : null)
                .reasoning(reasoningParts)
                .finishReason(finishReason)
                .rawFinishReason(rawFinishReason)
                .usage(usage)
                .warnings(warnings)
                .request(responseMapper.mapRequestMetadata(response))
                .response(mapResponseMetadata(response, responseMapper.extractText(response), reasoningParts,
                    List.of()))
                .providerMetadata(responseMapper.mapMetadata(response))
                .build();
            step.setResponseMessages(responseMessages(step.getResponse()));
            run.stepFinish(0, step, List.of(step));
            run.finish(GenerateTextResult.builder()
                .text(text)
                .reasoningText(hasText(reasoning) ? reasoning : null)
                .finishReason(finishReason)
                .rawFinishReason(rawFinishReason)
                .usage(usage)
                .totalUsage(usage)
                .warnings(step.getWarnings())
                .request(step.getRequest())
                .response(step.getResponse())
                .steps(List.of(step))
                .responseMessages(responseMessages(List.of(step)))
                .providerMetadata(step.getProviderMetadata())
                .build());
            parts.add(TextStreamPart.finish(finishReason, rawFinishReason, usage));
        }
        return Flux.fromIterable(parts);
    }

    private String extractReasoning(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return "";
        }
        var reasoning = reasoningExtractor.extract("", result.getOutput().getMetadata()).reasoning();
        if (reasoning.isEmpty()) {
            return "";
        }
        return reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
    }

    private List<GenerationWarning> requestWarnings(GenerateTextRequest request) {
        var warnings = new ArrayList<GenerationWarning>();
        if (hasStructuredOutput(request)
            && providerOptions.structuredOutputChatOptionsFactory() == null) {
            warnings.add(warning(WARNING_STRUCTURED_OUTPUT_PROMPT_GUIDANCE,
                "Structured output is guided by prompt instructions because the provider adapter "
                    + "does not expose native structured output options."));
        }
        if (hasStructuredOutput(request)
            && Boolean.TRUE.equals(request.getOutput().getStrict())
            && providerOptions.structuredOutputChatOptionsFactory() == null) {
            warnings.add(warning(WARNING_STRUCTURED_OUTPUT_STRICT_NOT_GUARANTEED,
                "Strict structured output was requested, but provider-native strict schema "
                    + "enforcement is not available for this adapter."));
        }
        if (request != null && request.getTools() != null
            && providerOptions.toolCallingChatOptionsFactory() == null
            && request.getTools().stream()
                .anyMatch(tool -> tool != null && tool.getInputExamples() != null
                    && !tool.getInputExamples().isEmpty())) {
            warnings.add(warning(WARNING_TOOL_INPUT_EXAMPLES_IGNORED,
                "Tool input examples are ignored by the default tool adapter."));
        }
        return warnings;
    }

    private List<GenerationWarning> reasoningReturnedWhileDisabledWarnings(
        GenerateTextRequest request, List<ReasoningPart> reasoning) {
        if (request == null
            || request.getReasoning() == null
            || request.getReasoning().getMode() != ReasoningOptions.Mode.DISABLED
            || reasoning == null
            || reasoning.isEmpty()) {
            return List.of();
        }
        return List.of(warning(WARNING_REASONING_RETURNED_WHILE_DISABLED,
            "Reasoning content was returned even though reasoning was explicitly disabled."));
    }

    private List<GenerationWarning> mergeWarnings(List<GenerationWarning> left,
        List<GenerationWarning> right) {
        if ((left == null || left.isEmpty()) && (right == null || right.isEmpty())) {
            return List.of();
        }
        var warnings = new ArrayList<GenerationWarning>();
        warnings.addAll(nullSafe(left));
        warnings.addAll(nullSafe(right));
        return warnings;
    }

    private GenerationWarning warning(String code, String message) {
        return GenerationWarning.builder()
            .code(code)
            .message(message)
            .providerMetadata(Map.of())
            .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeErrorMessage(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private final class UsageAccumulator {
        private LanguageModelUsage usage;

        void add(LanguageModelUsage next) {
            usage = addUsage(usage, next);
        }

        LanguageModelUsage usage() {
            return usage;
        }
    }

    private static final class SimpleStreamState {
        private final String textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
        private final String reasoningId = "rsn_" + UUID.randomUUID().toString().replace("-", "");
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean textStarted = new AtomicBoolean(false);
        private final AtomicBoolean reasoningStarted = new AtomicBoolean(false);
    }

    private final class StreamResponseAggregation {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        private ChatResponse lastResponse;
        private String finishReason;

        void accept(ChatResponse response) {
            var result = response.getResult();
            if (result == null || result.getOutput() == null) {
                return;
            }
            lastResponse = response;
            var output = result.getOutput();
            appendText(output);
            appendReasoning(output);
            replaceToolCalls(output);
            rememberFinishReason(result);
        }

        boolean hasOutput() {
            return lastResponse != null;
        }

        ChatResponse toResponse() {
            var extraction = reasoningExtractor.extract(text.toString(), reasoningMetadata());
            var output = AssistantMessage.builder()
                .content(extraction.text())
                .properties(reasoningProperties(extraction.reasoningText()))
                .toolCalls(toolCalls)
                .build();
            var generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
            return new ChatResponse(List.of(new Generation(output, generationMetadata)),
                lastResponse.getMetadata());
        }

        private void appendText(AssistantMessage output) {
            if (hasText(output.getText())) {
                text.append(output.getText());
            }
        }

        private void appendReasoning(AssistantMessage output) {
            var extraction = reasoningExtractor.extract("", output.getMetadata());
            if (hasText(extraction.reasoningText())) {
                reasoning.append(extraction.reasoningText());
            }
        }

        private void replaceToolCalls(AssistantMessage output) {
            if (output.getToolCalls() == null || output.getToolCalls().isEmpty()) {
                return;
            }
            toolCalls.clear();
            toolCalls.addAll(output.getToolCalls());
        }

        private void rememberFinishReason(Generation result) {
            if (result.getMetadata() != null && hasText(result.getMetadata().getFinishReason())) {
                finishReason = result.getMetadata().getFinishReason();
            }
        }

        private Map<String, Object> reasoningMetadata() {
            return hasText(reasoning.toString())
                ? Map.of("reasoning", reasoning.toString())
                : Map.of();
        }

        private Map<String, Object> reasoningProperties(String reasoningText) {
            return hasText(reasoningText)
                ? Map.of("reasoningContent", reasoningText)
                : Map.of();
        }
    }

    private record ToolStreamLoop(
        GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages,
        List<ModelMessage> executionMessages,
        UsageAccumulator totalUsage,
        List<GenerationStep> completedSteps,
        StopCondition stopWhen,
        LanguageModelGenerationRun run
    ) {
        ToolStreamLoop next(GenerateTextRequest request,
            List<org.springframework.ai.chat.messages.Message> messages,
            List<ModelMessage> executionMessages, StopCondition stopWhen) {
            return new ToolStreamLoop(request, messages, executionMessages, totalUsage,
                completedSteps, stopWhen, run);
        }
    }

    private final class StreamStepAccumulator {
        private final int stepIndex;
        private final String textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
        private final String reasoningId = "rsn_" + UUID.randomUUID().toString().replace("-", "");
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final ArrayList<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        private ChatResponse lastResponse;
        private String rawFinishReason;
        private boolean textStarted;
        private boolean reasoningStarted;
        private boolean failed;

        StreamStepAccumulator(int stepIndex) {
            this.stepIndex = stepIndex;
        }

        void accept(ChatResponse response) {
            if (response == null) {
                return;
            }
            lastResponse = response;
            var result = response.getResult();
            if (result == null) {
                return;
            }
            var output = result.getOutput();
            if (output != null) {
                if (hasText(output.getText())) {
                    text.append(output.getText());
                }
                if (output.getMetadata() != null) {
                    var extraction = reasoningExtractor.extract("", output.getMetadata());
                    if (hasText(extraction.reasoningText())) {
                        reasoning.append(extraction.reasoningText());
                    }
                }
                if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                    toolCalls.clear();
                    toolCalls.addAll(output.getToolCalls());
                }
            }
            if (result.getMetadata() != null && hasText(result.getMetadata().getFinishReason())) {
                rawFinishReason = result.getMetadata().getFinishReason();
            }
        }

        AssistantMessage output(List<ToolCall> toolCalls) {
            return assistantOutput(text.toString(), reasoningParts(), toolCalls);
        }

        List<ToolCall> toolCalls() {
            return toolCallMapper.mapToolCalls(toolCalls);
        }

        List<ReasoningPart> reasoningParts() {
            return responseMapper.reasoningParts(reasoning.toString());
        }

        FinishReason finishReason() {
            return responseMapper.mapFinishReason(rawFinishReason);
        }

        LanguageModelUsage usage() {
            return lastResponse != null ? responseMapper.mapUsage(lastResponse) : null;
        }

        List<GenerationWarning> warnings() {
            return lastResponse != null ? responseMapper.mapWarnings(lastResponse) : List.of();
        }

        GenerationRequestMetadata request() {
            return lastResponse != null ? responseMapper.mapRequestMetadata(lastResponse)
                : GenerationRequestMetadata.builder()
                    .metadata(Map.of("providerType", providerType))
                    .build();
        }

        GenerationResponseMetadata response() {
            return response(toolCalls());
        }

        GenerationResponseMetadata response(List<ToolCall> toolCalls) {
            return lastResponse != null
                ? mapResponseMetadata(lastResponse, text.toString(), reasoningParts(), toolCalls)
                : sanitizeResponseMessages(GenerationResponseMetadata.builder()
                    .messages(messageMapper.responseMessages(text.toString(), reasoningParts(),
                        toolCalls))
                    .metadata(Map.of("providerType", providerType))
                    .build());
        }

        Map<String, Object> providerMetadata() {
            return lastResponse != null ? responseMapper.mapMetadata(lastResponse) : Map.of();
        }
    }

    private record StepSnapshot(
        int stepIndex,
        String text,
        String reasoningText,
        AssistantMessage output,
        List<GenerationContentPart> content,
        List<ReasoningPart> reasoning,
        FinishReason finishReason,
        String rawFinishReason,
        LanguageModelUsage usage,
        List<ToolCall> toolCalls,
        List<GenerationWarning> warnings,
        GenerationRequestMetadata request,
        GenerationResponseMetadata response,
        Map<String, Object> providerMetadata
    ) {
    }

    private record PreparedInvocation(
        GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages,
        List<ModelMessage> executionMessages,
        StopCondition stopWhen
    ) {
    }

    private static final class NonStreamingGenerationState {
        private final ArrayList<GenerationStep> steps = new ArrayList<>();
        private final ArrayList<GenerationContentPart> allContent = new ArrayList<>();
        private final ArrayList<GenerationWarning> allWarnings = new ArrayList<>();
        private final ArrayList<ToolCall> allToolCalls = new ArrayList<>();
        private final ArrayList<ToolApprovalRequest> allToolApprovalRequests = new ArrayList<>();
        private final ArrayList<ToolResult> allToolResults = new ArrayList<>();
        private final ArrayList<ToolError> allToolErrors = new ArrayList<>();
        private final ArrayList<ModelMessage> allResponseMessages = new ArrayList<>();
        private final ArrayList<ToolResult> resumedResults = new ArrayList<>();
        private final ArrayList<ToolError> resumedErrors = new ArrayList<>();
        private LanguageModelUsage totalUsage;
        private FinishReason finalFinishReason = FinishReason.UNKNOWN;
        private String finalRawFinishReason;
        private Map<String, Object> finalProviderMetadata = Map.of();
        private GenerationRequestMetadata finalRequestMetadata;
        private GenerationResponseMetadata finalResponseMetadata;
    }

    private record ApprovalResumeResult(
        List<GenerationContentPart> content,
        List<ToolResult> results,
        List<ToolError> errors,
        List<ModelMessage> responseMessages
    ) {
    }
}
