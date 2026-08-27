package run.halo.aifoundation.provider.protocol.chatcompletions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsModel;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.contract.ProviderContractSource;

@ProviderContractSource(
    provider = "chat-completions-wire",
    officialDocumentation = "https://platform.openai.com/docs/api-reference/chat",
    retrievedAt = "2026-08-24"
)
class ChatCompletionsProfileTest {

    @Test
    @SuppressWarnings("unchecked")
    void providerProfileOwnsRequestAndResponseMetadataPolicy() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://provider.example/v1")
            .apiKey("test-key")
            .model("model-a")
            .build();
        var profile = new ChatCompletionsProfile() {
            @Override
            public String providerType() {
                return "fixture-provider";
            }

            @Override
            public String adapterType() {
                return "fixture-chat";
            }

            @Override
            public void customizeRequest(Map<String, Object> body, Prompt prompt,
                ChatCompletionsOptions requestOptions, boolean stream) {
                body.put("provider_hint", "strict");
            }

            @Override
            public Map<String, Object> normalizeProviderMetadata(Map<String, Object> metadata) {
                var normalized = new LinkedHashMap<>(metadata);
                normalized.put("normalizedBy", providerType());
                return Map.copyOf(normalized);
            }
        };
        var model = new ChatCompletionsModel(options, WebClient.builder(), profile);
        var prompt = new Prompt(List.of(new UserMessage("hello")), options);

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);
        var response = (ChatResponse) ReflectionTestUtils.invokeMethod(model, "chatResponse", """
            {"id":"response-1","model":"model-a","provider_request_id":"request-1",
             "choices":[{"message":{"role":"assistant","content":"hello"},
             "finish_reason":"stop"}]}
            """, options);

        assertThat(body).containsEntry("provider_hint", "strict");
        assertThat((Object) response.getMetadata().get("provider_request_id"))
            .isEqualTo("request-1");
        assertThat((Object) response.getMetadata().get("normalizedBy"))
            .isEqualTo("fixture-provider");
    }

    @Test
    void standardProfileDefaultsToAppendableToolArgumentDeltas() {
        var profile = new StandardChatCompletionsProfile("provider", "provider-chat");

        assertThat(profile.toolInputStreamDialect()
            .normalizeArguments(0, "{", "\"name\":\"Halo\"}").delta())
            .isEqualTo("\"name\":\"Halo\"}");
    }
}
