package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.FinishReason;
import run.halo.aifoundation.GenerationContentPart;
import run.halo.aifoundation.GenerationRequestMetadata;
import run.halo.aifoundation.GenerationResponseMetadata;
import run.halo.aifoundation.GenerationStep;
import run.halo.aifoundation.GenerationWarning;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.GenerateTextResult;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.LanguageModelUsage;
import run.halo.aifoundation.ModelMessage;
import run.halo.aifoundation.ModelMessagePart;
import run.halo.aifoundation.ModelMessageRole;
import run.halo.aifoundation.PartType;
import run.halo.aifoundation.OutputSpec;
import run.halo.aifoundation.OutputType;
import run.halo.aifoundation.ReasoningPart;
import run.halo.aifoundation.StructuredOutputValidationException;
import run.halo.aifoundation.TextStreamPart;
import run.halo.aifoundation.ToolCall;
import run.halo.aifoundation.ToolChoice;
import run.halo.aifoundation.ToolDefinition;
import run.halo.aifoundation.ToolError;
import run.halo.aifoundation.ToolResult;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class LanguageModelImpl implements LanguageModel {
    private static final int DEFAULT_MAX_STEPS = 1;
    private static final int MAX_STEPS_LIMIT = 10;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {
    };

    private final ChatModel chatModel;
    private final String providerType;
    private final LanguageModelProviderOptions providerOptions;

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
    }

    @Override
    public Mono<GenerateTextResult> generateText(String prompt) {
        return generateText(GenerateTextRequest.builder().prompt(prompt).build());
    }

    @Override
    public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
        return Mono.fromCallable(() -> generateTextBlocking(request))
            .subscribeOn(Schedulers.boundedElastic())
            ;
    }

    @Override
    public Flux<TextStreamPart> streamText(GenerateTextRequest request) {
        if (hasTools(request)) {
            return streamTextWithTools(request);
        }
        if (hasStructuredOutput(request)) {
            return streamStructuredText(request);
        }
        return Flux.defer(() -> {
            Prompt prompt;
            try {
                prompt = buildPrompt(request);
            } catch (RuntimeException e) {
                return Flux.just(TextStreamPart.error(e.getMessage()));
            }

            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
            var reasoningId = "rsn_" + UUID.randomUUID().toString().replace("-", "");
            var finished = new AtomicBoolean(false);
            var reasoningStarted = new AtomicBoolean(false);

            var stream = chatModel.stream(prompt)
                .concatMap(response -> mapStreamResponse(response, textId, reasoningId, finished,
                    reasoningStarted))
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
                    parts.addAll(List.of(TextStreamPart.textEnd(textId),
                        TextStreamPart.finishStep(0, finishReason, null, null, List.of(), null,
                            null, Map.of()),
                        TextStreamPart.finish(finishReason, null, null)
                    ));
                    return Flux.fromIterable(parts);
                }))
                .onErrorResume(e -> {
                    log.error("[{}] Streaming error", providerType, e);
                    return Flux.just(TextStreamPart.error(safeErrorMessage(e)));
                });

            return Flux.concat(Flux.just(TextStreamPart.start(messageId),
                TextStreamPart.startStep(0),
                TextStreamPart.textStart(textId)), stream);
        });
    }

    private Flux<TextStreamPart> streamTextWithTools(GenerateTextRequest request) {
        return Flux.defer(() -> {
            List<org.springframework.ai.chat.messages.Message> messages;
            try {
                validateRequest(request);
                assertToolCallingSupported(request);
                messages = buildMessages(request);
            } catch (RuntimeException e) {
                return Flux.just(TextStreamPart.error(safeErrorMessage(e)));
            }
            var totalUsage = new UsageAccumulator();
            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            return Flux.concat(Flux.just(TextStreamPart.start(messageId)),
                streamToolStep(request, messages, 0, totalUsage));
        });
    }

    private Flux<TextStreamPart> streamStructuredText(GenerateTextRequest request) {
        return Flux.defer(() -> {
            List<org.springframework.ai.chat.messages.Message> messages;
            try {
                validateRequest(request);
                messages = buildMessages(request);
            } catch (RuntimeException e) {
                return Flux.just(TextStreamPart.error(safeErrorMessage(e)));
            }
            var accumulator = new StreamStepAccumulator(0);
            var totalUsage = new UsageAccumulator();
            var prompt = new Prompt(messages, buildChatOptions(request));
            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var stream = chatModel.stream(prompt)
                .concatMap(response -> mapToolStreamResponse(request, response, accumulator))
                .onErrorResume(e -> {
                    log.error("[{}] Structured streaming error", providerType, e);
                    accumulator.failed = true;
                    return Flux.just(TextStreamPart.error(safeErrorMessage(e)));
                });
            return Flux.concat(Flux.just(TextStreamPart.start(messageId),
                    TextStreamPart.startStep(0)),
                stream,
                Flux.defer(() -> completeStructuredSingleStep(request, accumulator, totalUsage)));
        });
    }

    private Flux<TextStreamPart> completeStructuredSingleStep(GenerateTextRequest request,
        StreamStepAccumulator accumulator, UsageAccumulator totalUsage) {
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
            var rawDiagnostic = sanitizedRawDiagnostic(accumulator.lastResponse);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
        }
        totalUsage.add(accumulator.usage());
        try {
            validateFinalStructuredOutput(request, accumulator);
        } catch (StructuredOutputValidationException e) {
            parts.add(TextStreamPart.error(e.getMessage()));
            return Flux.fromIterable(parts);
        }
        parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
            accumulator.rawFinishReason, accumulator.usage(), accumulator.warnings(),
            accumulator.request(), accumulator.response(), accumulator.providerMetadata()));
        parts.add(TextStreamPart.finish(accumulator.finishReason(), accumulator.rawFinishReason,
            totalUsage.usage()));
        return Flux.fromIterable(parts);
    }

    private Flux<TextStreamPart> streamToolStep(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages, int stepIndex,
        UsageAccumulator totalUsage) {
        return Flux.defer(() -> {
            var accumulator = new StreamStepAccumulator(stepIndex);
            var prompt = new Prompt(messages, buildChatOptions(request));
            var stream = chatModel.stream(prompt)
                .concatMap(response -> mapToolStreamResponse(request, response, accumulator))
                .onErrorResume(e -> {
                    log.error("[{}] Tool streaming error", providerType, e);
                    accumulator.failed = true;
                    return Flux.just(TextStreamPart.error(safeErrorMessage(e)));
                });
            return Flux.concat(Flux.just(TextStreamPart.startStep(stepIndex)), stream,
                Flux.defer(() -> completeToolStreamStep(request, messages, accumulator,
                    totalUsage)));
        });
    }

    private Flux<TextStreamPart> mapToolStreamResponse(GenerateTextRequest request, ChatResponse response,
        StreamStepAccumulator accumulator) {
        var parts = new ArrayList<TextStreamPart>();
        accumulator.accept(response);
        var reasoning = extractReasoning(response);
        if (hasText(reasoning)) {
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
        var text = extractText(response);
        if (hasText(text)) {
            if (!accumulator.textStarted) {
                accumulator.textStarted = true;
                parts.add(TextStreamPart.textStart(accumulator.textId));
            }
            parts.add(TextStreamPart.textDelta(accumulator.textId, text));
        }
        return Flux.fromIterable(parts);
    }

    private Flux<TextStreamPart> completeToolStreamStep(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages,
        StreamStepAccumulator accumulator, UsageAccumulator totalUsage) {
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
            var rawDiagnostic = sanitizedRawDiagnostic(accumulator.lastResponse);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
        }

        var toolCalls = accumulator.toolCalls();
        toolCalls.forEach(toolCall -> parts.add(TextStreamPart.toolCall(toolCall)));
        var canContinue = accumulator.stepIndex + 1 < maxSteps(request);
        var toolResults = canContinue
            ? executeToolCalls(toolCalls, request)
            : maxStepsReached(toolCalls);
        toolResults.results().forEach(result -> parts.add(TextStreamPart.toolResult(result)));
        toolResults.errors().forEach(error -> parts.add(TextStreamPart.toolError(error)));

        var warnings = new ArrayList<>(accumulator.warnings());
        warnings.addAll(toolResults.warnings());
        totalUsage.add(accumulator.usage());
        if (toolCalls.isEmpty()
            || !toolResults.errors().isEmpty()
            || toolResults.results().isEmpty()
            || !canContinue) {
            if (hasStructuredOutput(request) && toolCalls.isEmpty() && toolResults.errors().isEmpty()) {
                try {
                    validateFinalStructuredOutput(request, accumulator);
                } catch (StructuredOutputValidationException e) {
                    parts.add(TextStreamPart.error(e.getMessage()));
                    return Flux.fromIterable(parts);
                }
            } else if (hasStructuredOutput(request) && (!toolCalls.isEmpty() || !canContinue)) {
                parts.add(TextStreamPart.error("Structured output validation failed: final structured output was not produced"));
                return Flux.fromIterable(parts);
            }
            parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
                accumulator.rawFinishReason, accumulator.usage(), warnings, accumulator.request(),
                accumulator.response(), accumulator.providerMetadata()));
            parts.add(TextStreamPart.finish(accumulator.finishReason(), accumulator.rawFinishReason,
                totalUsage.usage()));
            return Flux.fromIterable(parts);
        }

        parts.add(TextStreamPart.finishStep(accumulator.stepIndex, accumulator.finishReason(),
            accumulator.rawFinishReason, accumulator.usage(), warnings, accumulator.request(),
            accumulator.response(), accumulator.providerMetadata()));

        messages.add(accumulator.output());
        messages.add(toolResponseMessage(toolResults.results()));
        return Flux.concat(Flux.fromIterable(parts),
            streamToolStep(request, messages, accumulator.stepIndex + 1, totalUsage));
    }

    private GenerateTextResult generateTextBlocking(GenerateTextRequest request) {
        validateRequest(request);
        assertToolCallingSupported(request);
        var messages = buildMessages(request);
        var steps = new ArrayList<GenerationStep>();
        var allContent = new ArrayList<GenerationContentPart>();
        var allWarnings = new ArrayList<GenerationWarning>();
        LanguageModelUsage totalUsage = null;
        var finalFinishReason = FinishReason.UNKNOWN;
        String finalRawFinishReason = null;
        Map<String, Object> finalProviderMetadata = Map.of();
        GenerationRequestMetadata finalRequestMetadata = null;
        GenerationResponseMetadata finalResponseMetadata = null;

        for (var stepIndex = 0; stepIndex < maxSteps(request); stepIndex++) {
            var response = callProvider(request, messages);
            var step = mapStep(response, stepIndex);
            var canContinue = stepIndex + 1 < maxSteps(request);
            var toolResults = canContinue
                ? executeToolCalls(step.toolCalls(), request)
                : maxStepsReached(step.toolCalls());
            var stepContent = new ArrayList<>(step.content());
            toolResults.results().stream()
                .map(GenerationContentPart::toolResult)
                .forEach(stepContent::add);
            toolResults.errors().stream()
                .map(GenerationContentPart::toolError)
                .forEach(stepContent::add);

            var stepWarnings = new ArrayList<>(step.warnings());
            stepWarnings.addAll(toolResults.warnings());
            var generationStep = GenerationStep.builder()
                .stepIndex(stepIndex)
                .text(step.text())
                .reasoningText(step.reasoningText())
                .content(stepContent)
                .reasoning(step.reasoning())
                .finishReason(step.finishReason())
                .rawFinishReason(step.rawFinishReason())
                .usage(step.usage())
                .toolCalls(step.toolCalls())
                .toolResults(toolResults.results())
                .toolErrors(toolResults.errors())
                .warnings(stepWarnings)
                .request(step.request())
                .response(step.response())
                .providerMetadata(step.providerMetadata())
                .build();
            steps.add(generationStep);
            allContent.addAll(stepContent);
            allWarnings.addAll(stepWarnings);
            totalUsage = addUsage(totalUsage, step.usage());
            finalFinishReason = step.finishReason();
            finalRawFinishReason = step.rawFinishReason();
            finalProviderMetadata = step.providerMetadata();
            finalRequestMetadata = step.request();
            finalResponseMetadata = step.response();

            if (step.toolCalls().isEmpty()
                || !toolResults.errors().isEmpty()
                || toolResults.results().isEmpty()
                || !canContinue) {
                break;
            }
            messages.add(step.output());
            messages.add(toolResponseMessage(toolResults.results()));
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
            structuredOutput = parseStructuredOutput(request.getOutput(), outputSourceText);
            if (!steps.isEmpty()) {
                var finalStep = steps.get(steps.size() - 1);
                finalStep.setOutput(structuredOutput.output());
                finalStep.setOutputText(structuredOutput.outputText());
            }
        }
        return GenerateTextResult.builder()
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
            .providerMetadata(finalProviderMetadata)
            .build();
    }

    private Prompt buildPrompt(GenerateTextRequest request) {
        validateRequest(request);
        return new Prompt(buildMessages(request), buildChatOptions(request));
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessages(
        GenerateTextRequest request) {
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        if (hasText(request.getSystem())) {
            messages.add(new SystemMessage(request.getSystem().trim()));
        }
        var outputInstruction = structuredOutputInstruction(request.getOutput());
        if (hasText(outputInstruction)) {
            messages.add(new SystemMessage(outputInstruction));
        }
        if (hasText(request.getPrompt())) {
            messages.add(new UserMessage(request.getPrompt().trim()));
        } else {
            request.getMessages().stream()
                .map(this::convertMessage)
                .forEach(messages::add);
        }
        return messages;
    }

    private void validateRequest(GenerateTextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.getSystem() != null && request.getSystem().isBlank()) {
            throw new IllegalArgumentException("system must not be blank");
        }
        var hasPrompt = hasText(request.getPrompt());
        var hasMessages = request.getMessages() != null && !request.getMessages().isEmpty();
        if (hasPrompt == hasMessages) {
            throw new IllegalArgumentException("exactly one of prompt or messages must be provided");
        }
        if (request.getPrompt() != null && !hasPrompt) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (hasMessages) {
            for (var message : request.getMessages()) {
                validateMessage(message);
            }
        }
        validateOutput(request.getOutput());
        validateTools(request);
    }

    private void validateMessage(ModelMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("messages must not contain null items");
        }
        if (message.getRole() == null) {
            throw new IllegalArgumentException("message role must not be null");
        }
        if (message.getContent() == null || message.getContent().isEmpty()) {
            throw new IllegalArgumentException("message content must not be empty");
        }
        for (var part : message.getContent()) {
            validatePart(message.getRole(), part);
        }
    }

    private void validatePart(ModelMessageRole role, ModelMessagePart part) {
        if (part == null) {
            throw new IllegalArgumentException("message content must not contain null parts");
        }
        if (PartType.isText(part.getType()) && !hasText(part.getText())) {
            throw new IllegalArgumentException("text content part must not be blank");
        }
        if (PartType.isText(part.getType())) {
            return;
        }
        if (PartType.isReasoning(part.getType())) {
            if (role != ModelMessageRole.ASSISTANT) {
                throw new IllegalArgumentException("reasoning content part is only supported for assistant messages");
            }
            if (!supportsReasoningHistory()) {
                throw new IllegalArgumentException("reasoning content is not supported by provider type: "
                    + providerType);
            }
            if (!hasText(part.getText()) && (part.getProviderOptions() == null
                || part.getProviderOptions().isEmpty())) {
                throw new IllegalArgumentException("reasoning content part must include text or provider metadata");
            }
            return;
        }
        if (role == ModelMessageRole.TOOL && PartType.isToolResponse(part.getType())) {
            if (!hasText(part.getToolCallId()) || !hasText(part.getToolName())) {
                throw new IllegalArgumentException("tool content part must include toolCallId and toolName");
            }
            return;
        }
        throw new IllegalArgumentException("unsupported content part type: " + part.getType());
    }

    private void validateTools(GenerateTextRequest request) {
        if (request.getMaxSteps() != null && request.getMaxSteps() < 1) {
            throw new IllegalArgumentException("maxSteps must be greater than or equal to 1");
        }
        if (request.getMaxSteps() != null && request.getMaxSteps() > MAX_STEPS_LIMIT) {
            throw new IllegalArgumentException("maxSteps must be less than or equal to "
                + MAX_STEPS_LIMIT);
        }
        var tools = request.getTools();
        if (tools == null || tools.isEmpty()) {
            if (request.getToolChoice() != null
                && request.getToolChoice().getType() == ToolChoice.Type.TOOL) {
                throw new IllegalArgumentException("toolChoice toolName requires tools");
            }
            return;
        }
        var names = new HashSet<String>();
        for (var tool : tools) {
            if (tool == null) {
                throw new IllegalArgumentException("tools must not contain null items");
            }
            if (!hasText(tool.getName()) || !tool.getName().matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("tool name must contain only letters, numbers, '_' or '-'");
            }
            if (!names.add(tool.getName())) {
                throw new IllegalArgumentException("duplicate tool name: " + tool.getName());
            }
            if (tool.getInputSchema() != null && !(tool.getInputSchema() instanceof Map)) {
                throw new IllegalArgumentException("tool inputSchema must be a JSON object");
            }
            validateSchemaObject(tool.getInputSchema(), "tool inputSchema");
            validateSchemaObject(tool.getOutputSchema(), "tool outputSchema");
        }
        var choice = request.getToolChoice();
        if (choice != null && choice.getType() == ToolChoice.Type.TOOL
            && !names.contains(choice.getToolName())) {
            throw new IllegalArgumentException("toolChoice references unknown tool: "
                + choice.getToolName());
        }
    }

    private void validateOutput(OutputSpec output) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return;
        }
        switch (output.getType()) {
            case OBJECT -> validateRequiredSchemaObject(output.getSchema(), "output schema");
            case ARRAY -> validateRequiredSchemaObject(output.getElementSchema(), "output elementSchema");
            case CHOICE -> {
                if (output.getChoices() == null || output.getChoices().isEmpty()) {
                    throw new IllegalArgumentException("output choices must not be empty");
                }
                if (output.getChoices().stream().anyMatch(choice -> !hasText(choice))) {
                    throw new IllegalArgumentException("output choices must not contain blank values");
                }
            }
            case JSON -> {
            }
            default -> {
            }
        }
    }

    private void validateSchemaObject(Map<String, Object> schema, String name) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
    }

    private void validateRequiredSchemaObject(Map<String, Object> schema, String name) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
    }

    private void assertToolCallingSupported(GenerateTextRequest request) {
        if (hasTools(request) && !supportsToolCalling()) {
            throw new IllegalArgumentException(toolCallingUnsupportedMessage());
        }
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

    private ChatOptions buildChatOptions(GenerateTextRequest request) {
        if (hasTools(request)
            && (request.getToolChoice() == null
            || request.getToolChoice().getType() != ToolChoice.Type.NONE)) {
            var toolNames = toolNames(request);
            if (providerOptions.toolCallingChatOptionsFactory() != null) {
                return providerOptions.toolCallingChatOptionsFactory()
                    .build(request, toolCallbacks(request), toolNames);
            }
            var builder = DefaultToolCallingChatOptions.builder()
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxOutputTokens())
                .topP(request.getTopP())
                .topK(request.getTopK())
                .presencePenalty(request.getPresencePenalty())
                .frequencyPenalty(request.getFrequencyPenalty())
                .stopSequences(request.getStopSequences())
                .internalToolExecutionEnabled(false)
                .toolCallbacks(toolCallbacks(request));
            if (request.getToolChoice() != null
                && request.getToolChoice().getType() == ToolChoice.Type.TOOL) {
                builder.toolNames(toolNames);
            }
            return builder.build();
        }
        if (hasStructuredOutput(request)
            && providerOptions.structuredOutputChatOptionsFactory() != null) {
            return providerOptions.structuredOutputChatOptionsFactory().build(request);
        }
        return ChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .build();
    }

    private ChatResponse callProvider(GenerateTextRequest request,
        List<org.springframework.ai.chat.messages.Message> messages) {
        var prompt = new Prompt(messages, buildChatOptions(request));
        if (shouldUseReasoningAwareStreamCall(request)) {
            var responses = chatModel.stream(prompt).collectList().block();
            return aggregateStreamResponses(responses);
        }
        return chatModel.call(prompt);
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
            var reasoningContent = firstText(output.getMetadata(), "reasoningContent",
                "reasoning_content");
            if (hasText(reasoningContent)) {
                reasoning.append(reasoningContent);
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
        var properties = hasText(reasoning.toString())
            ? Map.<String, Object>of("reasoningContent", reasoning.toString())
            : Map.<String, Object>of();
        var output = AssistantMessage.builder()
            .content(text.toString())
            .properties(properties)
            .toolCalls(toolCalls)
            .build();
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        return new ChatResponse(List.of(new Generation(output, generationMetadata)),
            lastResponse.getMetadata());
    }

    private org.springframework.ai.chat.messages.Message convertMessage(ModelMessage message) {
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(textContent(message));
            case ASSISTANT -> assistantMessage(message);
            case USER -> new UserMessage(textContent(message));
            case TOOL -> toolResponseMessage(message);
        };
    }

    private String textContent(ModelMessage message) {
        return message.getContent().stream()
            .filter(part -> PartType.isText(part.getType()))
            .map(ModelMessagePart::getText)
            .reduce("", String::concat);
    }

    private AssistantMessage assistantMessage(ModelMessage message) {
        var toolCalls = message.getContent().stream()
            .filter(part -> PartType.isToolCall(part.getType()))
            .map(part -> new AssistantMessage.ToolCall(part.getToolCallId(), "function",
                part.getToolName(), writeJson(part.getInput())))
            .toList();
        return AssistantMessage.builder()
            .content(textContent(message))
            .properties(assistantProperties(message))
            .toolCalls(toolCalls)
            .build();
    }

    private Map<String, Object> assistantProperties(ModelMessage message) {
        var reasoningContent = reasoningContent(message.getContent());
        if (!hasText(reasoningContent)) {
            return Map.of();
        }
        return Map.of("reasoningContent", reasoningContent);
    }

    private ToolResponseMessage toolResponseMessage(ModelMessage message) {
        var responses = message.getContent().stream()
            .filter(part -> PartType.isToolResponse(part.getType()))
            .map(part -> new ToolResponseMessage.ToolResponse(part.getToolCallId(),
                part.getToolName(), PartType.isToolError(part.getType())
                ? part.getErrorText()
                : writeJson(part.getResult())))
            .toList();
        return ToolResponseMessage.builder().responses(responses).build();
    }

    private ToolResponseMessage toolResponseMessage(List<ToolResult> results) {
        var responses = results.stream()
            .map(result -> new ToolResponseMessage.ToolResponse(result.getToolCallId(),
                result.getToolName(), writeJson(result.getResult())))
            .toList();
        return ToolResponseMessage.builder().responses(responses).build();
    }

    private List<ToolCallback> toolCallbacks(GenerateTextRequest request) {
        return request.getTools().stream()
            .map(tool -> FunctionToolCallback
                .builder(tool.getName(), (Function<Map<String, Object>, Object>) input -> Map.of())
                .description(tool.getDescription())
                .inputSchema(writeJson(defaultInputSchema(tool)))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .build())
            .map(ToolCallback.class::cast)
            .toList();
    }

    private Map<String, Object> defaultInputSchema(ToolDefinition tool) {
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            return tool.getInputSchema();
        }
        return Map.of("type", "object", "properties", Map.of());
    }

    private Set<String> toolNames(GenerateTextRequest request) {
        if (request.getToolChoice() != null
            && request.getToolChoice().getType() == ToolChoice.Type.TOOL) {
            return Set.of(request.getToolChoice().getToolName());
        }
        return Set.of();
    }

    private StepSnapshot mapStep(ChatResponse response, int stepIndex) {
        var result = response.getResult();
        var output = result != null ? result.getOutput() : null;
        var text = output != null && output.getText() != null ? output.getText() : "";
        var reasoning = mapReasoning(output);
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var rawFinishReason = result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
        var toolCalls = output != null ? mapToolCalls(output.getToolCalls()) : List.<ToolCall>of();
        var content = new ArrayList<GenerationContentPart>();
        reasoning.stream().map(GenerationContentPart::reasoning).forEach(content::add);
        if (hasText(text)) {
            content.add(GenerationContentPart.text(text));
        }
        toolCalls.stream().map(GenerationContentPart::toolCall).forEach(content::add);
        return new StepSnapshot(
            stepIndex,
            text,
            hasText(reasoningText) ? reasoningText : null,
            output != null ? output : new AssistantMessage(text),
            content,
            reasoning,
            mapFinishReason(rawFinishReason),
            rawFinishReason,
            mapUsage(response),
            toolCalls,
            mapWarnings(response),
            mapRequestMetadata(response),
            mapResponseMetadata(response, text, reasoning, toolCalls),
            mapMetadata(response)
        );
    }

    private List<ReasoningPart> mapReasoning(AssistantMessage output) {
        if (output == null || output.getMetadata() == null || output.getMetadata().isEmpty()) {
            return List.of();
        }
        var reasoningContent = firstText(output.getMetadata(), "reasoningContent",
            "reasoning_content");
        if (!hasText(reasoningContent)) {
            return List.of();
        }
        return List.of(ReasoningPart.builder()
            .text(reasoningContent)
            .providerMetadata(reasoningProviderMetadata(reasoningContent, output.getMetadata()))
            .build());
    }

    @SuppressWarnings("unchecked")
    private String firstText(Map<String, Object> metadata, String... keys) {
        for (var key : keys) {
            var value = metadata.get(key);
            if (value instanceof String text && hasText(text)) {
                return text;
            }
            if (value instanceof Map<?, ?> map) {
                var nested = firstText((Map<String, Object>) sanitizeValue(map), keys);
                if (hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Map<String, Object> reasoningProviderMetadata(String reasoningContent,
        Map<String, Object> outputMetadata) {
        var providerValues = new LinkedHashMap<String, Object>();
        providerValues.put("reasoning_content", reasoningContent);
        outputMetadata.forEach((key, value) -> {
            if (key != null && key.toLowerCase().contains("reasoning")) {
                providerValues.put(key, sanitizeValue(value));
            }
        });
        return Map.of(providerType, providerValues);
    }

    private List<ToolCall> mapToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
            .map(toolCall -> ToolCall.builder()
                .toolCallId(toolCall.id())
                .toolName(toolCall.name())
                .input(parseToolInput(toolCall.arguments()))
                .rawInput(toolCall.arguments())
                .providerMetadata(Map.of("type", toolCall.type()))
                .build())
            .toList();
    }

    private ToolExecutionBatch executeToolCalls(List<ToolCall> toolCalls,
        GenerateTextRequest request) {
        if (toolCalls.isEmpty()) {
            return new ToolExecutionBatch(List.of(), List.of(), List.of());
        }
        var toolsByName = request.getTools() == null ? Map.<String, ToolDefinition>of()
            : request.getTools().stream()
                .collect(Collectors.toMap(ToolDefinition::getName, Function.identity()));
        var results = new ArrayList<ToolResult>();
        var errors = new ArrayList<ToolError>();
        var warnings = new ArrayList<GenerationWarning>();
        for (var toolCall : toolCalls) {
            var tool = toolsByName.get(toolCall.getToolName());
            if (tool == null) {
                errors.add(ToolError.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .errorText("Unknown tool: " + toolCall.getToolName())
                    .build());
                break;
            }
            if (tool.getExecutor() == null) {
                warnings.add(GenerationWarning.builder()
                    .code("tool-not-executed")
                    .message("Tool has no executor: " + tool.getName())
                    .build());
                break;
            }
            try {
                if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
                    validateJsonValue(toolCall.getInput(), tool.getInputSchema(),
                        "$." + toolCall.getToolName() + ".input");
                }
                var value = tool.getExecutor().execute(toolCall.getInput()).block();
                if (tool.getOutputSchema() != null && !tool.getOutputSchema().isEmpty()) {
                    validateJsonValue(value, tool.getOutputSchema(),
                        "$." + toolCall.getToolName() + ".output");
                }
                results.add(ToolResult.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .result(value)
                    .build());
            } catch (RuntimeException e) {
                errors.add(ToolError.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .errorText(safeErrorMessage(e))
                    .build());
                break;
            }
        }
        return new ToolExecutionBatch(results, errors, warnings);
    }

    private ToolExecutionBatch maxStepsReached(List<ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return new ToolExecutionBatch(List.of(), List.of(), List.of());
        }
        return new ToolExecutionBatch(List.of(), List.of(), List.of(GenerationWarning.builder()
            .code("max-steps-reached")
            .message("Tool calls were not executed because maxSteps was reached")
            .build()));
    }

    private Map<String, Object> parseToolInput(String arguments) {
        if (!hasText(arguments)) {
            return Map.of();
        }
        try {
            var parsed = JSON_MAPPER.readValue(arguments, MAP_TYPE);
            return parsed != null ? parsed : Map.of();
        } catch (JacksonException e) {
            return Map.of("_raw", arguments);
        }
    }

    private String structuredOutputInstruction(OutputSpec output) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return null;
        }
        var base = "Return only the requested structured output. Do not wrap it in Markdown or "
            + "explanatory prose.";
        return switch (output.getType()) {
            case OBJECT -> base + " Return a JSON object that matches this JSON Schema: "
                + writeJson(output.getSchema());
            case ARRAY -> base + " Return a JSON array. Each element must match this JSON Schema: "
                + writeJson(output.getElementSchema());
            case CHOICE -> base + " Return exactly one of these string choices: "
                + String.join(", ", output.getChoices());
            case JSON -> base + " Return valid JSON.";
            case TEXT -> null;
        };
    }

    private StructuredOutput parseStructuredOutput(OutputSpec output, String text) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return new StructuredOutput(text, text);
        }
        var outputText = structuredOutputText(output, text);
        try {
            return switch (output.getType()) {
                case JSON -> new StructuredOutput(JSON_MAPPER.readValue(outputText, OBJECT_TYPE),
                    outputText);
                case OBJECT -> {
                    var value = JSON_MAPPER.readValue(outputText, OBJECT_TYPE);
                    if (!(value instanceof Map<?, ?> map)) {
                        throw new StructuredOutputValidationException(
                            "Structured output validation failed: expected JSON object");
                    }
                    var sanitized = sanitizeValue(map);
                    validateJsonValue(sanitized, output.getSchema(), "$");
                    yield new StructuredOutput(sanitized, outputText);
                }
                case ARRAY -> {
                    var value = JSON_MAPPER.readValue(outputText, OBJECT_TYPE);
                    if (!(value instanceof List<?> list)) {
                        throw new StructuredOutputValidationException(
                            "Structured output validation failed: expected JSON array");
                    }
                    for (var i = 0; i < list.size(); i++) {
                        validateJsonValue(list.get(i), output.getElementSchema(), "$[" + i + "]");
                    }
                    yield new StructuredOutput(sanitizeValue(list), outputText);
                }
                case CHOICE -> {
                    var choice = normalizeChoice(outputText);
                    if (output.getChoices() == null || !output.getChoices().contains(choice)) {
                        throw new StructuredOutputValidationException(
                            "Structured output validation failed: expected one of "
                                + output.getChoices());
                    }
                    yield new StructuredOutput(choice, outputText);
                }
                case TEXT -> new StructuredOutput(text, text);
            };
        } catch (StructuredOutputValidationException e) {
            throw e;
        } catch (JacksonException e) {
            throw new StructuredOutputValidationException(
                "Structured output validation failed: output is not valid JSON", e);
        }
    }

    private String structuredOutputText(OutputSpec output, String text) {
        var trimmed = text != null ? text.trim() : "";
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        }
        if (output.getType() == OutputType.OBJECT
            || output.getType() == OutputType.JSON && trimmed.startsWith("{")) {
            var start = trimmed.indexOf('{');
            var end = trimmed.lastIndexOf('}');
            if (start >= 0 && end >= start) {
                return trimmed.substring(start, end + 1);
            }
        }
        if (output.getType() == OutputType.ARRAY
            || output.getType() == OutputType.JSON && trimmed.startsWith("[")) {
            var start = trimmed.indexOf('[');
            var end = trimmed.lastIndexOf(']');
            if (start >= 0 && end >= start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private String normalizeChoice(String outputText) {
        try {
            var value = JSON_MAPPER.readValue(outputText, OBJECT_TYPE);
            if (value instanceof String text) {
                return text.trim();
            }
        } catch (JacksonException ignored) {
        }
        return outputText.trim();
    }

    @SuppressWarnings("unchecked")
    private void validateJsonValue(Object value, Map<String, Object> schema, String path) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        var type = schema.get("type");
        if (type instanceof String typeName) {
            validateJsonType(value, typeName, path);
        }
        var enumValues = schema.get("enum");
        if (enumValues instanceof Collection<?> values && !values.contains(value)) {
            throw new StructuredOutputValidationException(
                "Structured output validation failed: " + path + " must be one of " + values);
        }
        if ("object".equals(type) || schema.containsKey("properties")) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new StructuredOutputValidationException(
                    "Structured output validation failed: " + path + " must be an object");
            }
            var required = schema.get("required");
            if (required instanceof Collection<?> requiredFields) {
                for (var field : requiredFields) {
                    if (!map.containsKey(field)) {
                        throw new StructuredOutputValidationException(
                            "Structured output validation failed: missing required field "
                                + path + "." + field);
                    }
                }
            }
            var properties = schema.get("properties");
            if (properties instanceof Map<?, ?> propertyMap) {
                for (var entry : propertyMap.entrySet()) {
                    var key = entry.getKey();
                    if (key == null || !map.containsKey(key)) {
                        continue;
                    }
                    if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                        validateJsonValue(map.get(key), (Map<String, Object>) sanitizeValue(propertySchema),
                            path + "." + key);
                    }
                }
            }
        }
        if ("array".equals(type) || schema.containsKey("items")) {
            if (!(value instanceof List<?> list)) {
                throw new StructuredOutputValidationException(
                    "Structured output validation failed: " + path + " must be an array");
            }
            var items = schema.get("items");
            if (items instanceof Map<?, ?> itemSchema) {
                for (var i = 0; i < list.size(); i++) {
                    validateJsonValue(list.get(i), (Map<String, Object>) sanitizeValue(itemSchema),
                        path + "[" + i + "]");
                }
            }
        }
    }

    private void validateJsonType(Object value, String type, String path) {
        var valid = switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            default -> true;
        };
        if (!valid) {
            throw new StructuredOutputValidationException(
                "Structured output validation failed: " + path + " must be " + type);
        }
    }

    private void validateFinalStructuredOutput(GenerateTextRequest request,
        StreamStepAccumulator accumulator) {
        if (!hasStructuredOutput(request)) {
            return;
        }
        parseStructuredOutput(request.getOutput(), accumulator.text.toString());
    }

    private Flux<TextStreamPart> resultToStreamParts(GenerateTextResult result) {
        var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        var textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
        var parts = new ArrayList<TextStreamPart>();
        parts.add(TextStreamPart.start(messageId));
        for (var step : result.getSteps()) {
            var reasoningId = "rsn_" + UUID.randomUUID().toString().replace("-", "");
            parts.add(TextStreamPart.startStep(step.getStepIndex()));
            if (hasText(step.getReasoningText())) {
                parts.add(TextStreamPart.reasoningStart(reasoningId));
                nullSafe(step.getReasoning()).forEach(reasoning ->
                    parts.add(TextStreamPart.reasoningDelta(reasoningId, reasoning.getText(),
                        reasoning.getProviderMetadata())));
                parts.add(TextStreamPart.reasoningEnd(reasoningId));
            }
            if (hasText(step.getText())) {
                parts.add(TextStreamPart.textStart(textId));
                parts.add(TextStreamPart.textDelta(textId, step.getText()));
                parts.add(TextStreamPart.textEnd(textId));
            }
            nullSafe(step.getToolCalls())
                .forEach(toolCall -> parts.add(TextStreamPart.toolCall(toolCall)));
            nullSafe(step.getToolResults())
                .forEach(toolResult -> parts.add(TextStreamPart.toolResult(toolResult)));
            nullSafe(step.getToolErrors())
                .forEach(toolError -> parts.add(TextStreamPart.toolError(toolError)));
            parts.add(TextStreamPart.finishStep(step.getStepIndex(), step.getFinishReason(),
                step.getRawFinishReason(), step.getUsage(), step.getWarnings(), step.getRequest(),
                step.getResponse(), step.getProviderMetadata()));
        }
        parts.add(TextStreamPart.finish(result.getFinishReason(), result.getRawFinishReason(),
            result.getTotalUsage()));
        return Flux.fromIterable(parts);
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

    private Integer sum(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private int maxSteps(GenerateTextRequest request) {
        return request.getMaxSteps() != null ? request.getMaxSteps() : DEFAULT_MAX_STEPS;
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

    private GenerateTextResult mapResult(ChatResponse response) {
        var result = response.getResult();
        var output = result != null ? result.getOutput() : null;
        var text = output != null && output.getText() != null ? output.getText() : "";
        var reasoning = mapReasoning(output);
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
        var rawFinishReason = result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
        var finishReason = mapFinishReason(rawFinishReason);
        var usage = mapUsage(response);
        var providerMetadata = mapMetadata(response);
        var warnings = mapWarnings(response);
        var requestMetadata = mapRequestMetadata(response);
        var responseMetadata = mapResponseMetadata(response, text, reasoning, List.of());
        var content = contentParts(text, reasoning);
        var step = GenerationStep.builder()
            .stepIndex(0)
            .text(text)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(content)
            .reasoning(reasoning)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(usage)
            .warnings(warnings)
            .request(requestMetadata)
            .response(responseMetadata)
            .providerMetadata(providerMetadata)
            .build();
        return GenerateTextResult.builder()
            .text(text)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(content)
            .reasoning(reasoning)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(usage)
            .totalUsage(usage)
            .warnings(warnings)
            .request(requestMetadata)
            .response(responseMetadata)
            .steps(List.of(step))
            .providerMetadata(providerMetadata)
            .build();
    }

    private Flux<TextStreamPart> mapStreamResponse(ChatResponse response, String textId,
        String reasoningId, AtomicBoolean finished, AtomicBoolean reasoningStarted) {
        var parts = new ArrayList<TextStreamPart>();
        var text = extractText(response);
        var reasoning = extractReasoning(response);
        if (hasText(reasoning)) {
            if (!reasoningStarted.get()) {
                reasoningStarted.set(true);
                parts.add(TextStreamPart.reasoningStart(reasoningId));
            }
            parts.add(TextStreamPart.reasoningDelta(reasoningId, reasoning,
                reasoningProviderMetadata(reasoning, response.getResult().getOutput().getMetadata())));
        }
        if (hasText(text)) {
            parts.add(TextStreamPart.textDelta(textId, text));
        }

        var rawFinishReason = extractFinishReason(response);
        if (isFinish(rawFinishReason)) {
            finished.set(true);
            var finishReason = mapFinishReason(rawFinishReason);
            var usage = mapUsage(response);
            var rawDiagnostic = sanitizedRawDiagnostic(response);
            if (!rawDiagnostic.isEmpty()) {
                parts.add(TextStreamPart.raw(rawDiagnostic));
            }
            if (reasoningStarted.get()) {
                parts.add(TextStreamPart.reasoningEnd(reasoningId));
            }
            parts.add(TextStreamPart.textEnd(textId));
            parts.add(TextStreamPart.finishStep(0, finishReason, rawFinishReason, usage,
                mapWarnings(response), mapRequestMetadata(response),
                mapResponseMetadata(response, extractText(response), reasoningParts(reasoning),
                    List.of()), mapMetadata(response)));
            parts.add(TextStreamPart.finish(finishReason, rawFinishReason, usage));
        }
        return Flux.fromIterable(parts);
    }

    private String extractText(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
            return "";
        }
        return result.getOutput().getText();
    }

    private String extractReasoning(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return "";
        }
        var reasoning = mapReasoning(result.getOutput());
        if (reasoning.isEmpty()) {
            return "";
        }
        return reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(this::hasText)
            .collect(Collectors.joining());
    }

    private String extractFinishReason(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getMetadata() == null) {
            return null;
        }
        return result.getMetadata().getFinishReason();
    }

    private boolean isFinish(String rawFinishReason) {
        return rawFinishReason != null && !rawFinishReason.isBlank()
            && !"null".equalsIgnoreCase(rawFinishReason);
    }

    private FinishReason mapFinishReason(String rawFinishReason) {
        if (rawFinishReason == null || rawFinishReason.isBlank()
            || "null".equalsIgnoreCase(rawFinishReason)) {
            return FinishReason.UNKNOWN;
        }
        return switch (rawFinishReason.trim().toLowerCase()) {
            case "stop" -> FinishReason.STOP;
            case "length", "max_tokens" -> FinishReason.LENGTH;
            case "content_filter", "safety" -> FinishReason.CONTENT_FILTER;
            case "tool_calls", "tool-call" -> FinishReason.TOOL_CALLS;
            case "error" -> FinishReason.ERROR;
            default -> FinishReason.OTHER;
        };
    }

    private LanguageModelUsage mapUsage(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null || metadata.getUsage() == null) {
            return null;
        }
        var usage = metadata.getUsage();
        var input = usage.getPromptTokens();
        var output = usage.getCompletionTokens();
        var total = usage.getTotalTokens();
        if (input == null && output == null && total == null && usage.getNativeUsage() == null) {
            return null;
        }
        return LanguageModelUsage.builder()
            .inputTokens(input)
            .outputTokens(output)
            .reasoningTokens(reasoningTokens(usage.getNativeUsage()))
            .totalTokens(total)
            .raw(usage.getNativeUsage())
            .build();
    }

    private Map<String, Object> mapMetadata(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        map.put("providerType", providerType);
        if (hasText(metadata.getId())) {
            map.put("id", metadata.getId());
        }
        if (hasText(metadata.getModel())) {
            map.put("model", metadata.getModel());
        }
        metadata.entrySet().forEach(entry -> map.put(entry.getKey(), sanitizeValue(entry.getValue())));
        return map;
    }

    private Integer reasoningTokens(Object nativeUsage) {
        if (nativeUsage == null) {
            return null;
        }
        try {
            var completionTokenDetails = nativeUsage.getClass()
                .getMethod("completionTokenDetails")
                .invoke(nativeUsage);
            if (completionTokenDetails == null) {
                return null;
            }
            var value = completionTokenDetails.getClass()
                .getMethod("reasoningTokens")
                .invoke(completionTokenDetails);
            return value instanceof Integer integer ? integer : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private List<GenerationContentPart> contentParts(String text, List<ReasoningPart> reasoning) {
        var content = new ArrayList<GenerationContentPart>();
        nullSafe(reasoning).stream().map(GenerationContentPart::reasoning).forEach(content::add);
        if (hasText(text)) {
            content.add(GenerationContentPart.text(text));
        }
        return content;
    }

    private List<GenerationWarning> mapWarnings(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null || !metadata.containsKey("warnings")) {
            return List.of();
        }
        var warnings = metadata.get("warnings");
        if (warnings instanceof Collection<?> collection) {
            return collection.stream()
                .map(this::mapWarning)
                .toList();
        }
        return List.of(mapWarning(warnings));
    }

    @SuppressWarnings("unchecked")
    private GenerationWarning mapWarning(Object value) {
        if (value instanceof GenerationWarning warning) {
            return warning;
        }
        if (value instanceof Map<?, ?> map) {
            var code = map.get("code");
            var message = map.get("message");
            return GenerationWarning.builder()
                .code(code != null ? code.toString() : null)
                .message(message != null ? message.toString() : null)
                .providerMetadata((Map<String, Object>) sanitizeValue(map))
                .build();
        }
        return GenerationWarning.builder()
            .message(value != null ? value.toString() : null)
            .build();
    }

    private GenerationRequestMetadata mapRequestMetadata(ChatResponse response) {
        var metadata = response.getMetadata();
        return GenerationRequestMetadata.builder()
            .model(metadata != null ? metadata.getModel() : null)
            .metadata(Map.of("providerType", providerType))
            .build();
    }

    private GenerationResponseMetadata mapResponseMetadata(ChatResponse response, String text,
        List<ReasoningPart> reasoning, List<ToolCall> toolCalls) {
        var metadata = response.getMetadata();
        return GenerationResponseMetadata.builder()
            .id(metadata != null ? metadata.getId() : null)
            .model(metadata != null ? metadata.getModel() : null)
            .messages(responseMessages(text, reasoning, toolCalls))
            .metadata(mapMetadata(response))
            .build();
    }

    private List<ModelMessage> responseMessages(String text, List<ReasoningPart> reasoning,
        List<ToolCall> toolCalls) {
        var parts = new ArrayList<ModelMessagePart>();
        nullSafe(reasoning).stream().map(ModelMessagePart::reasoning).forEach(parts::add);
        if (hasText(text)) {
            parts.add(ModelMessagePart.text(text));
        }
        nullSafe(toolCalls).stream().map(ModelMessagePart::toolCall).forEach(parts::add);
        return parts.isEmpty() ? List.of() : List.of(ModelMessage.assistant(parts));
    }

    private String reasoningContent(List<ModelMessagePart> parts) {
        return parts.stream()
            .filter(part -> PartType.isReasoning(part.getType()))
            .map(part -> {
                var metadataReasoning = reasoningContent(part.getProviderOptions());
                return hasText(metadataReasoning) ? metadataReasoning : part.getText();
            })
            .filter(this::hasText)
            .collect(Collectors.joining());
    }

    @SuppressWarnings("unchecked")
    private String reasoningContent(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        var direct = firstText(metadata, "reasoningContent", "reasoning_content");
        if (hasText(direct)) {
            return direct;
        }
        var provider = metadata.get(providerType);
        if (provider instanceof Map<?, ?> providerMap) {
            return firstText((Map<String, Object>) sanitizeValue(providerMap),
                "reasoningContent", "reasoning_content");
        }
        return null;
    }

    private List<ReasoningPart> reasoningParts(String reasoning) {
        if (!hasText(reasoning)) {
            return List.of();
        }
        return List.of(ReasoningPart.builder()
            .text(reasoning)
            .providerMetadata(Map.of(providerType, Map.of("reasoning_content", reasoning)))
            .build());
    }

    private Map<String, Object> sanitizedRawDiagnostic(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null) {
            return Map.of();
        }
        var raw = new LinkedHashMap<String, Object>();
        metadata.entrySet().forEach(entry -> {
            var key = entry.getKey();
            if (key != null && isRawDiagnosticKey(key)) {
                raw.put(key, sanitizeValue(entry.getValue()));
            }
        });
        return raw;
    }

    private boolean isRawDiagnosticKey(String key) {
        var normalized = key.toLowerCase();
        return normalized.contains("raw") || normalized.contains("native");
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sanitized = new LinkedHashMap<String, Object>();
            map.forEach((key, nestedValue) -> {
                var keyString = key != null ? key.toString() : "";
                sanitized.put(keyString, isSensitiveKey(keyString) ? "[REDACTED]"
                    : sanitizeValue(nestedValue));
            });
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        var normalized = key.toLowerCase();
        return normalized.contains("apikey")
            || normalized.contains("api_key")
            || normalized.contains("authorization")
            || normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password");
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
                    var reasoningContent = firstText(output.getMetadata(), "reasoningContent",
                        "reasoning_content");
                    if (hasText(reasoningContent)) {
                        reasoning.append(reasoningContent);
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

        AssistantMessage output() {
            var properties = hasText(reasoning.toString())
                ? Map.<String, Object>of("reasoningContent", reasoning.toString())
                : Map.<String, Object>of();
            return AssistantMessage.builder()
                .content(text.toString())
                .properties(properties)
                .toolCalls(toolCalls)
                .build();
        }

        List<ToolCall> toolCalls() {
            return mapToolCalls(toolCalls);
        }

        List<ReasoningPart> reasoningParts() {
            return LanguageModelImpl.this.reasoningParts(reasoning.toString());
        }

        FinishReason finishReason() {
            return mapFinishReason(rawFinishReason);
        }

        LanguageModelUsage usage() {
            return lastResponse != null ? mapUsage(lastResponse) : null;
        }

        List<GenerationWarning> warnings() {
            return lastResponse != null ? mapWarnings(lastResponse) : List.of();
        }

        GenerationRequestMetadata request() {
            return lastResponse != null ? mapRequestMetadata(lastResponse)
                : GenerationRequestMetadata.builder()
                    .metadata(Map.of("providerType", providerType))
                    .build();
        }

        GenerationResponseMetadata response() {
            return lastResponse != null
                ? mapResponseMetadata(lastResponse, text.toString(), reasoningParts(), toolCalls())
                : GenerationResponseMetadata.builder()
                    .messages(responseMessages(text.toString(), reasoningParts(), toolCalls()))
                    .metadata(Map.of("providerType", providerType))
                    .build();
        }

        Map<String, Object> providerMetadata() {
            return lastResponse != null ? mapMetadata(lastResponse) : Map.of("providerType", providerType);
        }
    }

    private record StructuredOutput(
        Object output,
        String outputText
    ) {
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

    private record ToolExecutionBatch(
        List<ToolResult> results,
        List<ToolError> errors,
        List<GenerationWarning> warnings
    ) {
    }
}
