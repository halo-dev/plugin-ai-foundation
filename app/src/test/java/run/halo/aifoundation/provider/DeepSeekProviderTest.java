package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

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

        var options = (DeepSeekChatOptions) providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory()
            .build(request);

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
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

        var options = (DeepSeekChatOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void toolOptions_applyRequiredToolChoice() {
        var request = GenerateTextRequest.builder()
            .prompt("Use a tool")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .inputSchema(Map.of("type", "object"))
                .build()))
            .toolChoice(ToolChoice.required())
            .build();

        var options = (DeepSeekChatOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getToolChoice()).isEqualTo("required");
    }

    @Test
    void toolOptions_applyNativeStrictToolSchemaWhenRequested() {
        var request = GenerateTextRequest.builder()
            .prompt("Use a strict tool")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .description("Read Halo test information")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("name", Map.of("type", "string")),
                    "required", List.of("name"),
                    "additionalProperties", false
                ))
                .strict(true)
                .build()))
            .build();

        var options = (DeepSeekChatOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getTools()).singleElement()
            .satisfies(tool -> assertThat(tool.getFunction().getStrict()).isTrue());
    }

    @Test
    void toolOptions_ignoreInputExamplesWhenProviderHasNoNativeExampleSupport() {
        var request = GenerateTextRequest.builder()
            .prompt("Use a tool with examples")
            .tools(List.of(ToolDefinition.builder()
                .name("halo_test_info")
                .description("Read Halo test information")
                .inputSchema(Map.of("type", "object"))
                .inputExamples(List.of(Map.of("name", "example")))
                .build()))
            .build();

        var options = (DeepSeekChatOptions) providerType.languageModelProviderOptions()
            .toolCallingChatOptionsFactory()
            .build(request, List.of(), java.util.Set.of());

        assertThat(options.getTools()).singleElement()
            .satisfies(tool -> {
                assertThat(tool.getFunction().getName()).isEqualTo("halo_test_info");
                assertThat(tool.getFunction().getParameters()).containsEntry("type", "object");
                assertThat(tool.getFunction().getParameters()).doesNotContainKey("examples");
            });
    }

    @Test
    void options_applyDeepSeekLogprobProviderOptions() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .providerOptions(Map.of("deepseek", Map.of(
                "logprobs", true,
                "topLogprobs", 3
            )))
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer")
            )))
            .build();

        var options = (DeepSeekChatOptions) providerType.languageModelProviderOptions()
            .structuredOutputChatOptionsFactory()
            .build(request);

        assertThat(options.getLogprobs()).isTrue();
        assertThat(options.getTopLogprobs()).isEqualTo(3);
    }

    @Test
    void options_rejectTypedDisabledReasoningBecauseRc1DeepSeekOptionsCannotMapIt() {
        var request = GenerateTextRequest.builder()
            .prompt("Fast")
            .reasoning(ReasoningOptions.disabled())
            .build();

        assertThatThrownBy(() -> providerType.languageModelProviderOptions()
            .reasoningControlOptions()
            .validate(providerType.getProviderType(), request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("disabled reasoning is not supported by provider type: deepseek");
    }

    @Test
    void options_rejectTypedEnabledReasoningBecauseRc1DeepSeekOptionsCannotMapIt() {
        var request = GenerateTextRequest.builder()
            .prompt("Think")
            .reasoning(ReasoningOptions.enabled())
            .build();

        assertThatThrownBy(() -> providerType.languageModelProviderOptions()
            .reasoningControlOptions()
            .validate(providerType.getProviderType(), request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("enabled reasoning is not supported by provider type: deepseek");
    }
}
