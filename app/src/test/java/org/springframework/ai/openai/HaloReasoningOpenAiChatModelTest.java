package org.springframework.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;

class HaloReasoningOpenAiChatModelTest {

    @Test
    void createRequestCopiesAssistantReasoningContent() {
        var openAiApi = OpenAiApi.builder()
            .apiKey("test-key")
            .build();
        var model = new HaloReasoningOpenAiChatModel(openAiApi,
            OpenAiChatOptions.builder().model("deepseek-reasoner").build());

        var request = model.createRequest(new Prompt(List.of(
            new UserMessage("Need weather"),
            AssistantMessage.builder()
                .content("")
                .properties(Map.of("reasoningContent", "Need to call weather tool."))
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function",
                    "weather", "{}")))
                .build()
        ), OpenAiChatOptions.builder().model("deepseek-reasoner").build()), false);

        assertThat(request.messages())
            .filteredOn(message -> message.role() == OpenAiApi.ChatCompletionMessage.Role.ASSISTANT)
            .singleElement()
            .satisfies(message -> assertThat(message.reasoningContent())
                .isEqualTo("Need to call weather tool."));
    }
}
