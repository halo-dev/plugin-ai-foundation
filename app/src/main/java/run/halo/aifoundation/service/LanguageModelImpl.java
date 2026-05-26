package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
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
import run.halo.aifoundation.TextStreamPart;
import run.halo.aifoundation.ToolCall;
import run.halo.aifoundation.ToolChoice;
import run.halo.aifoundation.ToolDefinition;
import run.halo.aifoundation.ToolError;
import run.halo.aifoundation.ToolResult;
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

    private final ChatModel chatModel;
    private final String providerType;

    public LanguageModelImpl(ChatModel chatModel, String providerType) {
        this.chatModel = chatModel;
        this.providerType = providerType;
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
            return generateText(request)
                .flatMapMany(this::resultToStreamParts)
                .onErrorResume(e -> Flux.just(TextStreamPart.error(safeErrorMessage(e))));
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
            var finished = new AtomicBoolean(false);

            var stream = chatModel.stream(prompt)
                .concatMap(response -> mapStreamResponse(response, textId, finished))
                .concatWith(Flux.defer(() -> {
                    if (finished.get()) {
                        return Flux.empty();
                    }
                    finished.set(true);
                    var finishReason = FinishReason.UNKNOWN;
                    return Flux.just(
                        TextStreamPart.textEnd(textId),
                        TextStreamPart.finishStep(0, finishReason, null, null, List.of(), null,
                            null, Map.of()),
                        TextStreamPart.finish(finishReason, null, null)
                    );
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
            var response = chatModel.call(new Prompt(messages, buildChatOptions(request)));
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
                .content(stepContent)
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
        var usage = steps.isEmpty() ? null : steps.get(steps.size() - 1).getUsage();
        return GenerateTextResult.builder()
            .text(text)
            .content(allContent)
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
        }
        var choice = request.getToolChoice();
        if (choice != null && choice.getType() == ToolChoice.Type.TOOL
            && !names.contains(choice.getToolName())) {
            throw new IllegalArgumentException("toolChoice references unknown tool: "
                + choice.getToolName());
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

    private String toolCallingUnsupportedMessage() {
        return "Tool calling is not supported by provider type: " + providerType;
    }

    private ChatOptions buildChatOptions(GenerateTextRequest request) {
        if ("deepseek".equals(providerType)) {
            return buildDeepSeekChatOptions(request);
        }
        if (hasTools(request)
            && (request.getToolChoice() == null
            || request.getToolChoice().getType() != ToolChoice.Type.NONE)) {
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
                builder.toolNames(Set.of(request.getToolChoice().getToolName()));
            }
            return builder.build();
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

    private ChatOptions buildDeepSeekChatOptions(GenerateTextRequest request) {
        var builder = OpenAiChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stop(request.getStopSequences());
        if (hasTools(request)
            && (request.getToolChoice() == null
            || request.getToolChoice().getType() != ToolChoice.Type.NONE)) {
            builder
                .internalToolExecutionEnabled(false)
                .toolCallbacks(toolCallbacks(request))
                .extraBody(deepSeekToolExtraBody());
            if (request.getToolChoice() != null
                && request.getToolChoice().getType() == ToolChoice.Type.TOOL) {
                builder.toolNames(Set.of(request.getToolChoice().getToolName()));
            }
        }
        return builder.build();
    }

    private Map<String, Object> deepSeekToolExtraBody() {
        return Map.of("thinking", Map.of("type", "disabled"));
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
            .toolCalls(toolCalls)
            .build();
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

    private StepSnapshot mapStep(ChatResponse response, int stepIndex) {
        var result = response.getResult();
        var output = result != null ? result.getOutput() : null;
        var text = output != null && output.getText() != null ? output.getText() : "";
        var rawFinishReason = result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
        var toolCalls = output != null ? mapToolCalls(output.getToolCalls()) : List.<ToolCall>of();
        var content = new ArrayList<GenerationContentPart>();
        if (hasText(text)) {
            content.add(GenerationContentPart.text(text));
        }
        toolCalls.stream().map(GenerationContentPart::toolCall).forEach(content::add);
        return new StepSnapshot(
            stepIndex,
            text,
            output != null ? output : new AssistantMessage(text),
            content,
            mapFinishReason(rawFinishReason),
            rawFinishReason,
            mapUsage(response),
            toolCalls,
            mapWarnings(response),
            mapRequestMetadata(response),
            mapResponseMetadata(response, text),
            mapMetadata(response)
        );
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
                var value = tool.getExecutor().execute(toolCall.getInput()).block();
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

    private Flux<TextStreamPart> resultToStreamParts(GenerateTextResult result) {
        var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        var textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
        var parts = new ArrayList<TextStreamPart>();
        parts.add(TextStreamPart.start(messageId));
        for (var step : result.getSteps()) {
            parts.add(TextStreamPart.startStep(step.getStepIndex()));
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
        var rawFinishReason = result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
        var finishReason = mapFinishReason(rawFinishReason);
        var usage = mapUsage(response);
        var providerMetadata = mapMetadata(response);
        var warnings = mapWarnings(response);
        var requestMetadata = mapRequestMetadata(response);
        var responseMetadata = mapResponseMetadata(response, text);
        var content = contentParts(text);
        var step = GenerationStep.builder()
            .stepIndex(0)
            .text(text)
            .content(content)
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
            .content(content)
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
        AtomicBoolean finished) {
        var parts = new ArrayList<TextStreamPart>();
        var text = extractText(response);
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
            parts.add(TextStreamPart.textEnd(textId));
            parts.add(TextStreamPart.finishStep(0, finishReason, rawFinishReason, usage,
                mapWarnings(response), mapRequestMetadata(response),
                mapResponseMetadata(response, extractText(response)), mapMetadata(response)));
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

    private List<GenerationContentPart> contentParts(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        return List.of(GenerationContentPart.text(text));
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

    private GenerationResponseMetadata mapResponseMetadata(ChatResponse response, String text) {
        var metadata = response.getMetadata();
        return GenerationResponseMetadata.builder()
            .id(metadata != null ? metadata.getId() : null)
            .model(metadata != null ? metadata.getModel() : null)
            .messages(hasText(text) ? List.of(ModelMessage.assistant(text)) : List.of())
            .metadata(mapMetadata(response))
            .build();
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

    private record StepSnapshot(
        int stepIndex,
        String text,
        AssistantMessage output,
        List<GenerationContentPart> content,
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
