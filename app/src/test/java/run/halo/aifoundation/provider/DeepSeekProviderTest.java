package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.OutputSpec;
import run.halo.aifoundation.ToolDefinition;

class DeepSeekProviderTest {

    private final DeepSeekProvider providerType = new DeepSeekProvider();

    @Test
    void structuredOutputOptions_useDeepSeekJsonObjectResponseFormat() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        var options = (OpenAiChatOptions) providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory()
            .build(request);

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getResponseFormat().getJsonSchema()).isNull();
        assertThat(options.getExtraBody()).containsKey("thinking");
    }

    @Test
    void toolOptions_useDeepSeekJsonObjectResponseFormatWithStructuredObjectOutput() {
        var request = GenerateTextRequest.builder()
            .prompt("Use tool then generate JSON")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .inputSchema(Map.of("type", "object"))
                .build()))
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer")
            )))
            .build();

        var options = (OpenAiChatOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getResponseFormat().getJsonSchema()).isNull();
        assertThat(options.getExtraBody()).containsKey("thinking");
    }
}
