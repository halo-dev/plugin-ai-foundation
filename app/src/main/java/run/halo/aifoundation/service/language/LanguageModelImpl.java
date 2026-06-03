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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

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

    public LanguageModelImpl(ChatModel chatModel, String providerType) {
        this(chatModel, providerType, LanguageModelProviderOptions.defaults());
    }

    public LanguageModelImpl(ChatModel chatModel, String providerType,
        LanguageModelProviderOptions providerOptions) {
        this.chatModel = chatModel;
        this.providerType = providerType;
        this.providerOptions = providerOptions != null
            ? providerOptions
            : LanguageModelProviderOptions.defaults();
        this.requestValidator = new LanguageModelRequestValidator(providerType,
            this.providerOptions.reasoningHistorySupported());
        this.messageMapper = new LanguageModelMessageMapper(providerType);
        this.messageHistoryAssembler = new GenerationMessageHistoryAssembler(providerType,
            this.providerOptions.reasoningHistorySupported(), messageMapper);
        this.chatOptionsBuilder = new LanguageModelChatOptionsBuilder(providerType,
            this.providerOptions, this::writeJson);
        this.responseMapper = new LanguageModelResponseMapper(providerType, messageMapper);
        this.reasoningExtractor =
            new ReasoningContentExtractor(providerType, this.responseMapper::sanitizeValue);
        this.toolCallMapper = new LanguageModelToolCallMapper();
        this.structuredOutputHandler =
            new LanguageModelStructuredOutputHandler(responseMapper, this::writeJson);
        this.toolExecutor = new LanguageModelToolExecutor(structuredOutputHandler::validateJsonValue,
            this::checkCancellation, this::withToolTimeout);
        this.toolStepCoordinator = new ToolStepCoordinator(toolExecutor);
        this.approvalResolver = new ToolApprovalResolver();
    }

    @Override
    public Mono<GenerateTextResult> generateText(String prompt) {
        return generateText(GenerateTextRequest.builder().prompt(prompt).build());
    }

    @Override
    public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
        var run = new LanguageModelGenerationRun(request, providerType,
            resolvedStopCondition(request));
        return Mono.fromCallable(() -> generateTextBlocking(request, run))
            .subscribeOn(Schedulers.boundedElastic())
            .transform(mono -> withTotalTimeout(mono, request))
            .doOnError(error -> run.error(error, null, List.of()));
    }

    @Override
    public StreamTextResult streamText(GenerateTextRequest request) {
        var run = new LanguageModelGenerationRun(request, providerType,
            resolvedStopCondition(request));
        var fullStream = StreamProtocolNormalizer.normalize(
            streamTextParts(request, run)
                .transform(stream -> withTotalTimeout(stream, request))
                .onErrorResume(error -> {
                    run.error(error, null, List.of());
                    return Flux.just(terminalErrorPart(error));
                })
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
                run.error(e, 0, List.of());
                return Flux.just(TextStreamPart.error(e.getMessage()));
            }

            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
            var reasoningId = "rsn_" + UUID.randomUUID().toString().replace("-", "");
            var finished = new AtomicBoolean(false);
            var textStarted = new AtomicBoolean(false);
            var reasoningStarted = new AtomicBoolean(false);

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
                .concatMap(response -> mapStreamResponse(request, run, response, textId, reasoningId,
                    finished, textStarted, reasoningStarted))
                .concatWith(Flux.defer(() -> {
                    if (finished.get()) {
                        return Flux.empty();
                    }
                    finished.set(true);
                    var finishReason = FinishReason.UNKNOWN;
                    var parts = new ArrayList<TextStreamPart>();
                    if (reasoningStarted.get()) {
                        parts.add(TextStreamPart.reasoningEnd(reasoningId));
                    }
                    if (textStarted.get()) {
                        parts.add(TextStreamPart.textEnd(textId));
                    }
                    var step = GenerationStep.builder()
                        .stepIndex(0)
                        .finishReason(finishReason)
                        .warnings(run.warnings())
                        .providerMetadata(Map.of())
                        .build();
                    run.stepFinish(0, step, List.of(step));
                    var result = GenerateTextResult.builder()
                        .finishReason(finishReason)
                        .warnings(run.warnings())
                        .steps(List.of(step))
                        .responseMessages(responseMessages(List.of(step)))
                        .build();
                    run.finish(result);
                    parts.add(TextStreamPart.finishStep(0, finishReason, null, null, run.warnings(), null,
                        null, Map.of()));
                    parts.add(TextStreamPart.finish(finishReason, null, null));
                    return Flux.fromIterable(parts);
                }))
                .onErrorResume(e -> {
                    log.error("[{}] Streaming error", providerType, e);
                    run.error(e, 0, List.of());
                    return Flux.just(terminalErrorPart(e));
                });

            return Flux.concat(Flux.just(TextStreamPart.start(messageId),
                TextStreamPart.startStep(0)), stream);
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
                run.start();
            } catch (RuntimeException e) {
                run.error(e, null, List.of());
                return Flux.just(terminalErrorPart(e));
            }
            var executionMessages = initialExecutionMessages(request);
            var resumedApprovals = resumeApprovedTools(request, messages, executionMessages, run);
            var totalUsage = new UsageAccumulator();
            var steps = new ArrayList<GenerationStep>();
            var stopWhen = resolvedStopCondition(request);
            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            return Flux.concat(Flux.just(TextStreamPart.start(messageId)),
                Flux.fromIterable(resumedApprovals.content().stream()
                    .map(this::streamPartFromContent)
                    .toList()),
                streamToolStep(request, messages, executionMessages, 0, totalUsage, steps,
                    stopWhen, run));
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
                run.start();
                run.stepStart(0, request, initialExecutionMessages(request), List.of(),
                    resolvedStopCondition(request));
            } catch (RuntimeException e) {
                run.error(e, 0, List.of());
                return Flux.just(terminalErrorPart(e));
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
                    run.error(e, 0, List.of());
                    return Flux.just(terminalErrorPart(e));
                });
            return Flux.concat(Flux.just(TextStreamPart.start(messageId),
                    TextStreamPart.startStep(0)),
                stream,
                Flux.defer(() -> completeStructuredSingleStep(request, run, accumulator, totalUsage)));
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
        run.stepFinish(accumulator.stepIndex, generationStep, List.of(generationStep));
        parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
            accumulator.rawFinishReason, accumulator.usage(), warnings,
            accumulator.request(), accumulator.response(), accumulator.providerMetadata()));
        parts.add(TextStreamPart.finish(accumulator.finishReason(), accumulator.rawFinishReason,
            totalUsage.usage()));
        run.finish(resultFromSteps(List.of(generationStep), totalUsage.usage()));
        return Flux.fromIterable(parts);
    }

    private Flux<TextStreamPart> streamToolStep(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages,
        List<ModelMessage> executionMessages, int stepIndex, UsageAccumulator totalUsage,
        List<GenerationStep> completedSteps, StopCondition stopWhen, LanguageModelGenerationRun run) {
        return Flux.defer(() -> {
            PreparedInvocation prepared;
            try {
                checkCancellation(request);
                prepared = prepareInvocation(request, messages, executionMessages, completedSteps,
                    null, stepIndex, stopWhen);
                run.stepStart(stepIndex, prepared.request(), prepared.executionMessages(),
                    completedSteps, prepared.stopWhen());
            } catch (RuntimeException e) {
                run.error(e, stepIndex, completedSteps);
                return Flux.just(terminalErrorPart(e));
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
                    run.error(e, stepIndex, completedSteps);
                    return Flux.just(terminalErrorPart(e));
                });
            return Flux.concat(Flux.just(TextStreamPart.startStep(stepIndex)), stream,
                Flux.defer(() -> completeToolStreamStep(prepared.request(), prepared.messages(),
                    accumulator, prepared.executionMessages(), totalUsage, completedSteps,
                    prepared.stopWhen(), run)));
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

    private Flux<TextStreamPart> completeToolStreamStep(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages,
        StreamStepAccumulator accumulator, List<ModelMessage> executionMessages,
        UsageAccumulator totalUsage, List<GenerationStep> completedSteps, StopCondition stopWhen,
        LanguageModelGenerationRun run) {
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
        var toolExecutionAllowed = accumulator.stepIndex + 1 < resolvedStepLimit(request);
        var toolStep = toolStepCoordinator.resolve(toolCalls, request, accumulator.stepIndex,
            executionMessages, accumulator.providerMetadata(), run, approvalResolver::approvalId,
            toolExecutionAllowed);
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
        run.stepFinish(accumulator.stepIndex, generationStep, List.copyOf(completedSteps));
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
            run.finish(resultFromSteps(completedSteps, totalUsage.usage()));
            return Flux.fromIterable(parts);
        }

        parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
            accumulator.rawFinishReason, accumulator.usage(), warnings, accumulator.request(),
            generationStep.getResponse(), accumulator.providerMetadata()));

        messages.add(accumulator.output(recordedToolCalls));
        messages.add(messageMapper.toolResponseMessage(toolResults.results()));
        executionMessages.addAll(nullSafe(generationStep.getResponseMessages()));
        return Flux.concat(Flux.fromIterable(parts),
            streamToolStep(request, messages, executionMessages, accumulator.stepIndex + 1,
                totalUsage, completedSteps, stopWhen, run));
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

    private GenerateTextResult generateTextBlocking(GenerateTextRequest request,
        LanguageModelGenerationRun run) {
        checkCancellation(request);
        validateRequest(request);
        assertToolCallingSupported(request);
        run.start();
        var messages = buildMessages(request);
        var executionMessages = initialExecutionMessages(request);
        var resumedApprovals = resumeApprovedTools(request, messages, executionMessages, run);
        var steps = new ArrayList<GenerationStep>();
        var allContent = new ArrayList<GenerationContentPart>();
        var allWarnings = new ArrayList<GenerationWarning>();
        var allToolCalls = new ArrayList<ToolCall>();
        var allToolApprovalRequests = new ArrayList<ToolApprovalRequest>();
        var allToolResults = new ArrayList<ToolResult>();
        var allToolErrors = new ArrayList<ToolError>();
        var allResponseMessages = new ArrayList<ModelMessage>();
        allContent.addAll(resumedApprovals.content());
        allToolResults.addAll(resumedApprovals.results());
        allToolErrors.addAll(resumedApprovals.errors());
        allResponseMessages.addAll(resumedApprovals.responseMessages());
        LanguageModelUsage totalUsage = null;
        var finalFinishReason = FinishReason.UNKNOWN;
        String finalRawFinishReason = null;
        Map<String, Object> finalProviderMetadata = Map.of();
        GenerationRequestMetadata finalRequestMetadata = null;
        GenerationResponseMetadata finalResponseMetadata = null;
        var stopWhen = resolvedStopCondition(request);

        for (var stepIndex = 0; stepIndex < resolvedStepLimit(request); stepIndex++) {
            checkCancellation(request);
            var prepared = prepareInvocation(request, messages, executionMessages, steps, null,
                stepIndex, stopWhen);
            var stepRequest = prepared.request();
            stopWhen = prepared.stopWhen();
            run.stepStart(stepIndex, stepRequest, prepared.executionMessages(), steps, stopWhen);
            checkCancellation(stepRequest);
            var response = callProvider(stepRequest, prepared.messages());
            var step = mapStep(response, stepIndex, stepRequest);
            var toolExecutionAllowed = stepIndex + 1 < resolvedStepLimit(stepRequest);
            var toolStep = toolStepCoordinator.resolve(step.toolCalls(), stepRequest, stepIndex,
                prepared.executionMessages(), step.providerMetadata(), run,
                approvalResolver::approvalId, toolExecutionAllowed);
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
                .stepIndex(stepIndex)
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
            steps.add(generationStep);
            run.stepFinish(stepIndex, generationStep, List.copyOf(steps));
            allContent.addAll(stepContent);
            allWarnings.addAll(stepWarnings);
            allToolCalls.addAll(recordedToolCalls);
            allToolApprovalRequests.addAll(approvalRequests);
            allToolResults.addAll(toolResults.results());
            allToolErrors.addAll(toolResults.errors());
            allResponseMessages.addAll(nullSafe(generationStep.getResponseMessages()));
            totalUsage = addUsage(totalUsage, step.usage());
            finalFinishReason = step.finishReason();
            finalRawFinishReason = step.rawFinishReason();
            finalProviderMetadata = step.providerMetadata();
            finalRequestMetadata = step.request();
            finalResponseMetadata = responseMetadata;

            var shouldContinue = stopWhen.shouldContinue(StepContext.builder()
                .stepIndex(stepIndex)
                .step(generationStep)
                .steps(List.copyOf(steps))
                .messages(List.copyOf(prepared.executionMessages()))
                .tools(nullSafe(stepRequest.getTools()))
                .stopWhen(stopWhen)
                .providerOptions(stepRequest.getProviderOptions())
                .build());
            if (!toolStep.canContinue() || !shouldContinue) {
                break;
            }
            checkCancellation(stepRequest);
            messages = new ArrayList<>(prepared.messages());
            messages.add(assistantOutput(step.text(), step.reasoning(), recordedToolCalls));
            messages.add(messageMapper.toolResponseMessage(toolResults.results()));
            executionMessages = new ArrayList<>(prepared.executionMessages());
            executionMessages.addAll(nullSafe(generationStep.getResponseMessages()));
        }

        var text = allContent.stream()
            .filter(part -> PartType.isText(part.getType()))
            .map(GenerationContentPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var reasoning = steps.isEmpty()
            ? List.<ReasoningPart>of()
            : nullSafe(steps.get(steps.size() - 1).getReasoning());
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var usage = steps.isEmpty() ? null : steps.get(steps.size() - 1).getUsage();
        StructuredOutput structuredOutput = null;
        if (hasStructuredOutput(request)) {
            var outputSourceText = steps.isEmpty() ? text : steps.get(steps.size() - 1).getText();
            try {
                structuredOutput = structuredOutputHandler.parse(request.getOutput(),
                    outputSourceText);
            } catch (StructuredOutputValidationException e) {
                throw structuredOutputHandler.enrich(e, request.getOutput(), outputSourceText,
                    steps.isEmpty() ? null : steps.get(steps.size() - 1).getStepIndex(),
                    usage, finalResponseMetadata);
            }
            if (!steps.isEmpty()) {
                var finalStep = steps.get(steps.size() - 1);
                finalStep.setOutput(structuredOutput.output());
                finalStep.setOutputText(structuredOutput.outputText());
            }
        }
        allWarnings.addAll(run.warnings());
        finalResponseMetadata = appendToolMessages(finalResponseMetadata,
            resumedApprovals.results(), resumedApprovals.errors());
        var result = GenerateTextResult.builder()
            .text(text)
            .output(structuredOutput != null ? structuredOutput.output() : null)
            .outputText(structuredOutput != null ? structuredOutput.outputText() : null)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(allContent)
            .reasoning(reasoning)
            .finishReason(finalFinishReason)
            .rawFinishReason(finalRawFinishReason)
            .usage(usage)
            .totalUsage(totalUsage)
            .warnings(allWarnings)
            .request(finalRequestMetadata)
            .response(finalResponseMetadata)
            .steps(steps)
            .responseMessages(allResponseMessages)
            .toolCalls(allToolCalls)
            .toolApprovalRequests(allToolApprovalRequests)
            .toolResults(allToolResults)
            .toolErrors(allToolErrors)
            .providerMetadata(finalProviderMetadata)
            .build();
        run.finish(result);
        result.setWarnings(mergeWarnings(result.getWarnings(), run.warnings()));
        return result;
    }

    private Prompt buildPrompt(GenerateTextRequest request) {
        validateRequest(request);
        return new Prompt(buildMessages(request), buildChatOptions(request));
    }

    private ApprovalResumeResult resumeApprovedTools(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> providerMessages,
        List<ModelMessage> executionMessages, LanguageModelGenerationRun run) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return new ApprovalResumeResult(List.of(), List.of(), List.of(), List.of());
        }
        var resolution = approvalResolver.resolve(request.getMessages());
        if (resolution.isEmpty()) {
            return new ApprovalResumeResult(List.of(), List.of(), List.of(), List.of());
        }
        var resumedContent = new ArrayList<GenerationContentPart>();
        var resumedResults = new ArrayList<ToolResult>();
        var resumedErrors = new ArrayList<ToolError>();
        var responseMessages = new ArrayList<ModelMessage>();
        for (var approval : resolution.approvals()) {
            if (Boolean.TRUE.equals(approval.response().approved())) {
                var stepIndex = approval.request().stepIndex() != null
                    ? approval.request().stepIndex()
                    : 0;
                var batch = toolExecutor.execute(List.of(approval.toolCall()), request, stepIndex,
                    executionMessages, approval.request().providerMetadata(), run);
                resumedResults.addAll(batch.results());
                resumedErrors.addAll(batch.errors());
            } else {
                resumedErrors.add(approval.deniedError());
            }
        }
        if (!resumedResults.isEmpty() || !resumedErrors.isEmpty()) {
            var parts = new ArrayList<ModelMessagePart>();
            resumedResults.stream().map(ModelMessagePart::toolResult).forEach(parts::add);
            resumedErrors.stream().map(ModelMessagePart::toolError).forEach(parts::add);
            var toolMessage = ModelMessage.tool(parts);
            executionMessages.add(toolMessage);
            providerMessages.add(messageMapper.convert(toolMessage));
            responseMessages.add(toolMessage);
            resumedResults.stream().map(GenerationContentPart::toolResult).forEach(resumedContent::add);
            resumedErrors.stream().map(GenerationContentPart::toolError).forEach(resumedContent::add);
        }
        return new ApprovalResumeResult(resumedContent, resumedResults, resumedErrors,
            responseMessages);
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

    private ChatResponse callProvider(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages) {
        var call = withStepTimeout(Mono.fromCallable(() -> {
            checkCancellation(request);
            var prompt = new Prompt(messages, buildChatOptions(request));
            if (shouldUseReasoningAwareStreamCall(request)) {
                var responses = chatModel.stream(prompt).collectList().block();
                return aggregateStreamResponses(responses);
            }
            return chatModel.call(prompt);
        }), request);
        var maxRetries = maxRetries(request);
        if (maxRetries > 0) {
            call = call.retryWhen(Retry.max(maxRetries).filter(this::isRetryableProviderFailure));
        }
        return call.block();
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
        var text = new StringBuilder();
        var reasoning = new StringBuilder();
        var toolCalls = new ArrayList<AssistantMessage.ToolCall>();
        ChatResponse lastResponse = null;
        String finishReason = null;
        for (var response : responses) {
            var result = response.getResult();
            if (result == null || result.getOutput() == null) {
                continue;
            }
            lastResponse = response;
            var output = result.getOutput();
            if (hasText(output.getText())) {
                text.append(output.getText());
            }
            var extraction = reasoningExtractor.extract("", output.getMetadata());
            if (hasText(extraction.reasoningText())) {
                reasoning.append(extraction.reasoningText());
            }
            if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                toolCalls.clear();
                toolCalls.addAll(output.getToolCalls());
            }
            if (result.getMetadata() != null && hasText(result.getMetadata().getFinishReason())) {
                finishReason = result.getMetadata().getFinishReason();
            }
        }
        if (lastResponse == null) {
            return responses.get(responses.size() - 1);
        }
        var extraction = reasoningExtractor.extract(text.toString(), hasText(reasoning.toString())
            ? Map.of("reasoning", reasoning.toString())
            : Map.of());
        var properties = hasText(extraction.reasoningText())
            ? Map.<String, Object>of("reasoningContent", extraction.reasoningText())
            : Map.<String, Object>of();
        var output = AssistantMessage.builder()
            .content(extraction.text())
            .properties(properties)
            .toolCalls(toolCalls)
            .build();
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        return new ChatResponse(List.of(new Generation(output, generationMetadata)),
            lastResponse.getMetadata());
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
        var rawFinishReason = result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
        var toolCalls = output != null ? toolCallMapper.mapToolCalls(output.getToolCalls())
            : List.<ToolCall>of();
        var content = new ArrayList<GenerationContentPart>();
        reasoning.stream().map(GenerationContentPart::reasoning).forEach(content::add);
        if (hasText(text)) {
            content.add(GenerationContentPart.text(text));
        }
        content.addAll(responseMapper.sourceAndFileParts(response));
        toolCalls.stream().map(GenerationContentPart::toolCall).forEach(content::add);
        var warnings = new ArrayList<>(responseMapper.mapWarnings(response));
        warnings.addAll(reasoningReturnedWhileDisabledWarnings(request, reasoning));
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

    private <T> Mono<T> withToolTimeout(Mono<T> mono, GenerateTextRequest request) {
        var timeout = toolTimeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("tool", timeout, error));
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

    private Duration toolTimeout(GenerateTextRequest request) {
        return request != null && request.getTimeouts() != null
            ? request.getTimeouts().getToolTimeout()
            : null;
    }

    private void checkCancellation(GenerateTextRequest request) {
        if (request != null && request.getCancellationToken() != null
            && request.getCancellationToken().isCancellationRequested()) {
            throw new AiGenerationCancelledException("Generation was cancelled");
        }
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

    private String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value != null ? value : Map.of());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to serialize JSON value", e);
        }
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private Flux<TextStreamPart> mapStreamResponse(GenerateTextRequest request,
        LanguageModelGenerationRun run,
        ChatResponse response, String textId, String reasoningId, AtomicBoolean finished,
        AtomicBoolean textStarted, AtomicBoolean reasoningStarted) {
        var parts = new ArrayList<TextStreamPart>();
        var text = responseMapper.extractText(response);
        var reasoning = extractReasoning(response);
        if (hasText(reasoning)) {
            if (textStarted.get()) {
                textStarted.set(false);
                parts.add(TextStreamPart.textEnd(textId));
            }
            if (!reasoningStarted.get()) {
                reasoningStarted.set(true);
                parts.add(TextStreamPart.reasoningStart(reasoningId));
            }
            parts.add(TextStreamPart.reasoningDelta(reasoningId, reasoning,
                reasoningProviderMetadata(reasoning, response.getResult().getOutput().getMetadata())));
        }
        if (hasText(text)) {
            if (reasoningStarted.get()) {
                reasoningStarted.set(false);
                parts.add(TextStreamPart.reasoningEnd(reasoningId));
            }
            if (!textStarted.get()) {
                textStarted.set(true);
                parts.add(TextStreamPart.textStart(textId));
            }
            parts.add(TextStreamPart.textDelta(textId, text));
        }
        responseMapper.sourceAndFileParts(response).stream()
            .map(responseMapper::streamPart)
            .forEach(parts::add);

        var rawFinishReason = responseMapper.extractFinishReason(response);
        if (responseMapper.isFinish(rawFinishReason)) {
            finished.set(true);
            var finishReason = responseMapper.mapFinishReason(rawFinishReason);
            var usage = responseMapper.mapUsage(response);
            var rawDiagnostic = responseMapper.sanitizedRawDiagnostic(response);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
            if (reasoningStarted.get()) {
                reasoningStarted.set(false);
                parts.add(TextStreamPart.reasoningEnd(reasoningId));
            }
            if (textStarted.get()) {
                textStarted.set(false);
                parts.add(TextStreamPart.textEnd(textId));
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

    private record ApprovalResumeResult(
        List<GenerationContentPart> content,
        List<ToolResult> results,
        List<ToolError> errors,
        List<ModelMessage> responseMessages
    ) {
    }
}
