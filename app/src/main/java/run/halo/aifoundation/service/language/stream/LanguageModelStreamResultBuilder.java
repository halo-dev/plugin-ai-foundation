package run.halo.aifoundation.service.language.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.chat.GenerationRequestMetadata;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.GenerationStep;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;
import run.halo.aifoundation.service.language.structured.StructuredOutput;

public final class LanguageModelStreamResultBuilder {
    private final LinkedHashMap<Integer, StepBuild> steps = new LinkedHashMap<>();
    private Integer currentStepIndex = 0;
    private FinishReason finishReason = FinishReason.UNKNOWN;
    private String rawFinishReason;
    private LanguageModelUsage usage;
    private GenerationRequestMetadata request;
    private GenerationResponseMetadata response;
    private Map<String, Object> providerMetadata = Map.of();
    private String errorText;
    private String errorType;

    public void accept(TextStreamPart part) {
        if (part == null || part.getType() == null) {
            return;
        }
        switch (part.getType()) {
            case PartType.START_STEP -> acceptStartStep(part);
            case PartType.TEXT_DELTA -> step(currentStepIndex).text.append(part.getDelta());
            case PartType.REASONING_DELTA -> acceptReasoningDelta(part);
            case PartType.TOOL_CALL -> acceptToolCall(part);
            case PartType.TOOL_APPROVAL_REQUEST -> acceptToolApprovalRequest(part);
            case PartType.TOOL_RESULT -> acceptToolResult(part);
            case PartType.TOOL_ERROR -> acceptToolError(part);
            case PartType.SOURCE -> acceptSource(part);
            case PartType.FILE -> acceptFile(part);
            case PartType.FINISH_STEP -> acceptFinishStep(part);
            case PartType.FINISH -> acceptFinish(part);
            case PartType.ERROR -> acceptError(part);
            default -> {
            }
        }
    }

    private void acceptStartStep(TextStreamPart part) {
        currentStepIndex = part.getStepIndex() != null ? part.getStepIndex() : 0;
        step(currentStepIndex);
    }

    private void acceptReasoningDelta(TextStreamPart part) {
        step(currentStepIndex).reasoning.add(ReasoningPart.builder()
            .text(part.getDelta())
            .signature(part.getSignature())
            .providerMetadata(part.getProviderMetadata())
            .build());
    }

    private void acceptToolCall(TextStreamPart part) {
        step(currentStepIndex).toolCalls.add(ToolCall.builder()
            .toolCallId(part.getToolCallId())
            .toolName(part.getToolName())
            .input(part.getInput())
            .providerMetadata(part.getProviderMetadata())
            .build());
    }

    private void acceptToolApprovalRequest(TextStreamPart part) {
        step(currentStepIndex).toolApprovalRequests.add(ToolApprovalRequest.builder()
            .approvalId(part.getApprovalId())
            .toolCallId(part.getToolCallId())
            .toolName(part.getToolName())
            .input(part.getInput())
            .stepIndex(part.getStepIndex())
            .providerMetadata(part.getProviderMetadata())
            .build());
    }

    private void acceptToolResult(TextStreamPart part) {
        step(currentStepIndex).toolResults.add(ToolResult.builder()
            .toolCallId(part.getToolCallId())
            .toolName(part.getToolName())
            .result(part.getResult())
            .providerMetadata(part.getProviderMetadata())
            .build());
    }

    private void acceptToolError(TextStreamPart part) {
        step(currentStepIndex).toolErrors.add(ToolError.builder()
            .toolCallId(part.getToolCallId())
            .toolName(part.getToolName())
            .errorText(part.getErrorText())
            .providerMetadata(part.getProviderMetadata())
            .build());
    }

    private void acceptSource(TextStreamPart part) {
        step(currentStepIndex).content.add(GenerationContentPart.source(
            part.getId(), part.getUrl(), part.getTitle(), part.getProviderMetadata()));
    }

    private void acceptFile(TextStreamPart part) {
        step(currentStepIndex).content.add(GenerationContentPart.file(
            part.getId(), part.getUrl(), part.getTitle(), part.getMediaType(),
            part.getData(), part.getProviderMetadata()));
    }

    private void acceptFinishStep(TextStreamPart part) {
        var step = step(part.getStepIndex() != null ? part.getStepIndex() : currentStepIndex);
        step.finishReason = part.getFinishReason();
        step.rawFinishReason = part.getRawFinishReason();
        step.usage = part.getUsage();
        step.warnings.addAll(nullSafe(part.getWarnings()));
        step.request = part.getRequest();
        step.response = part.getResponse();
        step.providerMetadata = part.getProviderMetadata();
    }

    private void acceptFinish(TextStreamPart part) {
        finishReason = part.getFinishReason();
        rawFinishReason = part.getRawFinishReason();
        usage = part.getUsage();
    }

    private void acceptError(TextStreamPart part) {
        errorText = part.getErrorText();
        if (part.getStepIndex() != null) {
            currentStepIndex = part.getStepIndex();
        }
        if (part.getUsage() != null) {
            usage = part.getUsage();
        }
        if (part.getResponse() != null) {
            response = part.getResponse();
        }
        if (part.getProviderMetadata() != null) {
            providerMetadata = part.getProviderMetadata();
            var exceptionType = part.getProviderMetadata().get("exceptionType");
            if (exceptionType != null) {
                errorType = exceptionType.toString();
            }
        }
    }

    public String errorText() {
        return errorText;
    }

    public String errorType() {
        return errorType;
    }

    public Integer currentStepIndex() {
        return currentStepIndex;
    }

    public LanguageModelUsage usage() {
        return usage;
    }

    public GenerationResponseMetadata response() {
        return response;
    }

    public String errorValidationPath() {
        if (providerMetadata == null) {
            return null;
        }
        var validationPath = providerMetadata.get("validationPath");
        return validationPath != null ? validationPath.toString() : null;
    }

    public String finalStepText() {
        if (steps.isEmpty()) {
            return "";
        }
        return steps.values().stream()
            .reduce((first, second) -> second)
            .map(step -> step.text.toString())
            .orElse("");
    }

    public GenerateTextResult build(StructuredOutput structuredOutput) {
        var generationSteps = generationSteps(structuredOutput);
        var finalStep = finalStep(generationSteps);
        var reasoning = finalStepReasoning(finalStep);
        var reasoningText = reasoningText(reasoning);
        var toolCalls = flatten(generationSteps, GenerationStep::getToolCalls);
        var toolApprovalRequests = flatten(generationSteps, GenerationStep::getToolApprovalRequests);
        var toolResults = flatten(generationSteps, GenerationStep::getToolResults);
        var toolErrors = flatten(generationSteps, GenerationStep::getToolErrors);
        return GenerateTextResult.builder()
            .text(text(generationSteps))
            .output(structuredOutput != null ? structuredOutput.output() : null)
            .outputText(structuredOutput != null ? structuredOutput.outputText() : null)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(flatten(generationSteps, GenerationStep::getContent))
            .reasoning(reasoning)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(finalStep != null ? finalStep.getUsage() : null)
            .totalUsage(usage)
            .warnings(flatten(generationSteps, GenerationStep::getWarnings))
            .request(finalStep != null ? finalStep.getRequest() : request)
            .response(finalStep != null ? finalStep.getResponse() : response)
            .steps(generationSteps)
            .responseMessages(responseMessages(generationSteps))
            .toolCalls(toolCalls)
            .toolApprovalRequests(toolApprovalRequests)
            .toolResults(toolResults)
            .toolErrors(toolErrors)
            .providerMetadata(finalStep != null ? finalStep.getProviderMetadata() : providerMetadata)
            .build();
    }

    private List<GenerationStep> generationSteps(StructuredOutput structuredOutput) {
        return steps.entrySet().stream()
            .map(entry -> entry.getValue().build(entry.getKey(), structuredOutput,
                isFinalStep(entry.getKey())))
            .toList();
    }

    private GenerationStep finalStep(List<GenerationStep> generationSteps) {
        return generationSteps.isEmpty() ? null : generationSteps.get(generationSteps.size() - 1);
    }

    private String text(List<GenerationStep> generationSteps) {
        return generationSteps.stream()
            .map(GenerationStep::getText)
            .filter(LanguageModelStreamResultBuilder::hasText)
            .collect(Collectors.joining());
    }

    private List<ReasoningPart> finalStepReasoning(GenerationStep finalStep) {
        return finalStep != null ? nullSafe(finalStep.getReasoning()) : List.of();
    }

    private String reasoningText(List<ReasoningPart> reasoning) {
        return reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(LanguageModelStreamResultBuilder::hasText)
            .collect(Collectors.joining());
    }

    private static <T> List<T> flatten(List<GenerationStep> generationSteps,
        Function<GenerationStep, List<T>> values) {
        return generationSteps.stream()
            .flatMap(step -> nullSafe(values.apply(step)).stream())
            .toList();
    }

    private StepBuild step(Integer stepIndex) {
        var index = stepIndex != null ? stepIndex : 0;
        return steps.computeIfAbsent(index, ignored -> new StepBuild());
    }

    private boolean isFinalStep(Integer stepIndex) {
        return !steps.isEmpty() && stepIndex.equals(steps.keySet().stream()
            .reduce((first, second) -> second)
            .orElse(stepIndex));
    }

    private List<ModelMessage> responseMessages(List<GenerationStep> steps) {
        return nullSafe(steps).stream()
            .flatMap(step -> nullSafe(step.getResponseMessages()).stream())
            .toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class StepBuild {
        private final StringBuilder text = new StringBuilder();
        private final ArrayList<ReasoningPart> reasoning = new ArrayList<>();
        private final ArrayList<ToolCall> toolCalls = new ArrayList<>();
        private final ArrayList<ToolApprovalRequest> toolApprovalRequests = new ArrayList<>();
        private final ArrayList<ToolResult> toolResults = new ArrayList<>();
        private final ArrayList<ToolError> toolErrors = new ArrayList<>();
        private final ArrayList<GenerationContentPart> content = new ArrayList<>();
        private final ArrayList<GenerationWarning> warnings = new ArrayList<>();
        private FinishReason finishReason = FinishReason.UNKNOWN;
        private String rawFinishReason;
        private LanguageModelUsage usage;
        private GenerationRequestMetadata request;
        private GenerationResponseMetadata response;
        private Map<String, Object> providerMetadata = Map.of();

        GenerationStep build(Integer stepIndex, StructuredOutput structuredOutput, boolean finalStep) {
            var content = new ArrayList<GenerationContentPart>();
            reasoning.stream().map(GenerationContentPart::reasoning).forEach(content::add);
            if (hasText(text.toString())) {
                content.add(GenerationContentPart.text(text.toString()));
            }
            content.addAll(this.content);
            toolCalls.stream().map(GenerationContentPart::toolCall).forEach(content::add);
            toolApprovalRequests.stream()
                .map(GenerationContentPart::toolApprovalRequest)
                .forEach(content::add);
            toolResults.stream().map(GenerationContentPart::toolResult).forEach(content::add);
            toolErrors.stream().map(GenerationContentPart::toolError).forEach(content::add);
            var reasoningText = reasoning.stream()
                .map(ReasoningPart::getText)
                .filter(LanguageModelStreamResultBuilder::hasText)
                .collect(Collectors.joining());
            return GenerationStep.builder()
                .stepIndex(stepIndex)
                .text(text.toString())
                .output(finalStep && structuredOutput != null ? structuredOutput.output() : null)
                .outputText(finalStep && structuredOutput != null ? structuredOutput.outputText() : null)
                .reasoningText(hasText(reasoningText) ? reasoningText : null)
                .content(content)
                .reasoning(reasoning)
                .finishReason(finishReason)
                .rawFinishReason(rawFinishReason)
                .usage(usage)
                .toolCalls(toolCalls)
                .toolApprovalRequests(toolApprovalRequests)
                .toolResults(toolResults)
                .toolErrors(toolErrors)
                .responseMessages(responseMessages(response, text.toString(), reasoning,
                    toolCalls, toolApprovalRequests, toolResults, toolErrors))
                .warnings(warnings)
                .request(request)
                .response(appendToolMessages(response, toolResults, toolErrors))
                .providerMetadata(providerMetadata)
                .build();
        }

        private List<ModelMessage> responseMessages(GenerationResponseMetadata response,
            String text, List<ReasoningPart> reasoning, List<ToolCall> toolCalls,
            List<ToolApprovalRequest> approvals, List<ToolResult> results,
            List<ToolError> errors) {
            var messages = new ArrayList<ModelMessage>();
            var toolMessage = toolMessage(results, errors);
            var responseMessages = response != null && response.getMessages() != null
                ? List.copyOf(response.getMessages())
                : List.<ModelMessage>of();
            if (toolMessage != null && (nullSafe(toolCalls).isEmpty()
                && nullSafe(approvals).isEmpty())) {
                messages.add(toolMessage);
                messages.addAll(responseMessages);
                return messages;
            }
            if (!responseMessages.isEmpty()) {
                messages.addAll(responseMessages);
            }
            if (toolMessage != null && responseMessages.stream()
                .noneMatch(message -> message.getRole() == run.halo.aifoundation.message.ModelMessageRole.TOOL)) {
                messages.add(toolMessage);
            }
            return messages;
        }

        private ModelMessage toolMessage(List<ToolResult> results, List<ToolError> errors) {
            if ((results == null || results.isEmpty()) && (errors == null || errors.isEmpty())) {
                return null;
            }
            var parts = new ArrayList<ModelMessagePart>();
            nullSafe(results).stream().map(ModelMessagePart::toolResult).forEach(parts::add);
            nullSafe(errors).stream().map(ModelMessagePart::toolError).forEach(parts::add);
            return ModelMessage.tool(parts);
        }

        private GenerationResponseMetadata appendToolMessages(GenerationResponseMetadata response,
            List<ToolResult> results, List<ToolError> errors) {
            if ((results == null || results.isEmpty()) && (errors == null || errors.isEmpty())) {
                return response;
            }
            var parts = new ArrayList<run.halo.aifoundation.message.ModelMessagePart>();
            nullSafe(results).stream()
                .map(run.halo.aifoundation.message.ModelMessagePart::toolResult)
                .forEach(parts::add);
            nullSafe(errors).stream()
                .map(run.halo.aifoundation.message.ModelMessagePart::toolError)
                .forEach(parts::add);
            var messages = new ArrayList<run.halo.aifoundation.message.ModelMessage>();
            if (response != null && response.getMessages() != null) {
                messages.addAll(response.getMessages());
            }
            messages.add(run.halo.aifoundation.message.ModelMessage.tool(parts));
            if (response == null) {
                return GenerationResponseMetadata.builder().messages(messages).build();
            }
            return GenerationResponseMetadata.builder()
                .id(response.getId())
                .model(response.getModel())
                .timestamp(response.getTimestamp())
                .messages(messages)
                .headers(response.getHeaders())
                .body(response.getBody())
                .metadata(response.getMetadata())
                .build();
        }
    }
}
