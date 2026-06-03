package run.halo.aifoundation.service.language;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolExecutor;
import run.halo.aifoundation.tool.ToolResult;

abstract class LanguageModelTestSupport {

    ChatResponse chatResponse(String text, String finishReason, Integer promptTokens,
        Integer completionTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_1")
            .model("test-model");
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(text), generationMetadata)),
            metadataBuilder.build()
        );
    }

    ChatResponse toolCallResponse(String id, String name, String arguments,
        Integer promptTokens, Integer completionTokens) {
        return multiToolCallResponse(List.of(
            new AssistantMessage.ToolCall(id, "function", name, arguments)
        ), promptTokens, completionTokens);
    }

    ChatResponse multiToolCallResponse(List<AssistantMessage.ToolCall> toolCalls,
        Integer promptTokens, Integer completionTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason("tool_calls")
            .build();
        var output = AssistantMessage.builder()
            .content("")
            .toolCalls(toolCalls)
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_tool")
            .model("test-model");
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(
            List.of(new Generation(output, generationMetadata)),
            metadataBuilder.build()
        );
    }

    ToolDefinition repairableWeatherTool(ToolExecutor executor) {
        return ToolDefinition.builder()
            .name("weather")
            .inputSchema(weatherInputSchema())
            .executor(executor)
            .build();
    }

    Map<String, Object> weatherInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("location", Map.of("type", "string")),
            "required", List.of("location")
        );
    }

    ModelMessage externalToolCallMessage(String id, String name, Map<String, Object> input) {
        return ModelMessage.assistant(List.of(ModelMessagePart.toolCall(ToolCall.builder()
            .toolCallId(id)
            .toolName(name)
            .input(input)
            .build())));
    }

    List<ModelMessage> externalToolResultMessages() {
        return List.of(
            ModelMessage.user("Weather in SF?"),
            externalToolCallMessage("call_1", "weather", Map.of("location", "SF")),
            ModelMessage.tool(List.of(ModelMessagePart.toolResult(ToolResult.builder()
                .toolCallId("call_1")
                .toolName("weather")
                .result(Map.of("temperature", 22))
                .build())))
        );
    }

    List<ModelMessage> approvalMessages(boolean approved, String reason) {
        return approvalMessages(approved, reason, 0);
    }

    List<ModelMessage> approvalMessages(boolean approved, String reason, int stepIndex) {
        var approvalRequest = ToolApprovalRequest.builder()
            .approvalId("approval_call_1")
            .toolCallId("call_1")
            .toolName("run")
            .input(Map.of("command", "rm file"))
            .stepIndex(stepIndex)
            .providerMetadata(Map.of())
            .build();
        var approvalResponse = ToolApprovalResponse.builder()
            .approvalId("approval_call_1")
            .toolCallId("call_1")
            .toolName("run")
            .approved(approved)
            .reason(reason)
            .build();
        return List.of(
            ModelMessage.user("Remove file"),
            ModelMessage.assistant(List.of(
                ModelMessagePart.toolCall(ToolCall.builder()
                    .toolCallId("call_1")
                    .toolName("run")
                    .input(Map.of("command", "rm file"))
                    .build()),
                ModelMessagePart.toolApprovalRequest(approvalRequest)
            )),
            ModelMessage.tool(List.of(ModelMessagePart.toolApprovalResponse(approvalResponse)))
        );
    }

    List<ReasoningPart> reasoningParts(String text) {
        return List.of(ReasoningPart.builder().text(text).build());
    }

    LanguageModelProviderOptions reasoningHistoryProviderOptions() {
        return new LanguageModelProviderOptions(true, true, null, null, null,
            ReasoningControlOptions.unsupported());
    }
}
