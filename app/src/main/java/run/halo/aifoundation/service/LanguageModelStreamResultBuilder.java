package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.chat.GenerationRequestMetadata;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.GenerationStep;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

final class LanguageModelStreamResultBuilder {
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

    void accept(TextStreamPart part) {
        if (part == null || part.getType() == null) {
            return;
        }
        switch (part.getType()) {
            case PartType.START_STEP -> {
                currentStepIndex = part.getStepIndex() != null ? part.getStepIndex() : 0;
                step(currentStepIndex);
            }
            case PartType.TEXT_DELTA -> step(currentStepIndex).text.append(part.getDelta());
            case PartType.REASONING_DELTA -> step(currentStepIndex).reasoning.add(
                ReasoningPart.builder()
                    .text(part.getDelta())
                    .signature(part.getSignature())
                    .providerMetadata(part.getProviderMetadata())
                    .build());
            case PartType.TOOL_CALL -> step(currentStepIndex).toolCalls.add(ToolCall.builder()
                .toolCallId(part.getToolCallId())
                .toolName(part.getToolName())
                .input(part.getInput())
                .providerMetadata(part.getProviderMetadata())
                .build());
            case PartType.TOOL_RESULT -> step(currentStepIndex).toolResults.add(ToolResult.builder()
                .toolCallId(part.getToolCallId())
                .toolName(part.getToolName())
                .result(part.getResult())
                .providerMetadata(part.getProviderMetadata())
                .build());
            case PartType.TOOL_ERROR -> step(currentStepIndex).toolErrors.add(ToolError.builder()
                .toolCallId(part.getToolCallId())
                .toolName(part.getToolName())
                .errorText(part.getErrorText())
                .providerMetadata(part.getProviderMetadata())
                .build());
            case PartType.SOURCE -> step(currentStepIndex).content.add(GenerationContentPart.source(
                part.getId(), part.getUrl(), part.getTitle(), part.getProviderMetadata()));
            case PartType.FILE -> step(currentStepIndex).content.add(GenerationContentPart.file(
                part.getId(), part.getUrl(), part.getTitle(), part.getMediaType(),
                part.getData(), part.getProviderMetadata()));
            case PartType.FINISH_STEP -> {
                var step = step(part.getStepIndex() != null ? part.getStepIndex() : currentStepIndex);
                step.finishReason = part.getFinishReason();
                step.rawFinishReason = part.getRawFinishReason();
                step.usage = part.getUsage();
                step.warnings.addAll(nullSafe(part.getWarnings()));
                step.request = part.getRequest();
                step.response = part.getResponse();
                step.providerMetadata = part.getProviderMetadata();
            }
            case PartType.FINISH -> {
                finishReason = part.getFinishReason();
                rawFinishReason = part.getRawFinishReason();
                usage = part.getUsage();
            }
            case PartType.ERROR -> {
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
            default -> {
            }
        }
    }

    String errorText() {
        return errorText;
    }

    String errorType() {
        return errorType;
    }

    Integer currentStepIndex() {
        return currentStepIndex;
    }

    LanguageModelUsage usage() {
        return usage;
    }

    GenerationResponseMetadata response() {
        return response;
    }

    String errorValidationPath() {
        if (providerMetadata == null) {
            return null;
        }
        var validationPath = providerMetadata.get("validationPath");
        return validationPath != null ? validationPath.toString() : null;
    }

    String finalStepText() {
        if (steps.isEmpty()) {
            return "";
        }
        return steps.values().stream()
            .reduce((first, second) -> second)
            .map(step -> step.text.toString())
            .orElse("");
    }

    GenerateTextResult build(StructuredOutput structuredOutput) {
        var generationSteps = steps.entrySet().stream()
            .map(entry -> entry.getValue().build(entry.getKey(), structuredOutput,
                isFinalStep(entry.getKey())))
            .toList();
        var content = generationSteps.stream()
            .flatMap(step -> nullSafe(step.getContent()).stream())
            .toList();
        var warnings = generationSteps.stream()
            .flatMap(step -> nullSafe(step.getWarnings()).stream())
            .toList();
        var toolCalls = generationSteps.stream()
            .flatMap(step -> nullSafe(step.getToolCalls()).stream())
            .toList();
        var toolResults = generationSteps.stream()
            .flatMap(step -> nullSafe(step.getToolResults()).stream())
            .toList();
        var toolErrors = generationSteps.stream()
            .flatMap(step -> nullSafe(step.getToolErrors()).stream())
            .toList();
        var finalStep = generationSteps.isEmpty() ? null : generationSteps.get(generationSteps.size() - 1);
        var text = generationSteps.stream()
            .map(GenerationStep::getText)
            .filter(LanguageModelStreamResultBuilder::hasText)
            .collect(Collectors.joining());
        var reasoning = finalStep != null ? nullSafe(finalStep.getReasoning()) : List.<ReasoningPart>of();
        var reasoningText = reasoning.stream()
            .map(ReasoningPart::getText)
            .filter(LanguageModelStreamResultBuilder::hasText)
            .collect(Collectors.joining());
        return GenerateTextResult.builder()
            .text(text)
            .output(structuredOutput != null ? structuredOutput.output() : null)
            .outputText(structuredOutput != null ? structuredOutput.outputText() : null)
            .reasoningText(hasText(reasoningText) ? reasoningText : null)
            .content(content)
            .reasoning(reasoning)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(finalStep != null ? finalStep.getUsage() : null)
            .totalUsage(usage)
            .warnings(warnings)
            .request(finalStep != null ? finalStep.getRequest() : request)
            .response(finalStep != null ? finalStep.getResponse() : response)
            .steps(generationSteps)
            .toolCalls(toolCalls)
            .toolResults(toolResults)
            .toolErrors(toolErrors)
            .providerMetadata(finalStep != null ? finalStep.getProviderMetadata() : providerMetadata)
            .build();
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
                .toolResults(toolResults)
                .toolErrors(toolErrors)
                .warnings(warnings)
                .request(request)
                .response(response)
                .providerMetadata(providerMetadata)
                .build();
        }
    }
}
