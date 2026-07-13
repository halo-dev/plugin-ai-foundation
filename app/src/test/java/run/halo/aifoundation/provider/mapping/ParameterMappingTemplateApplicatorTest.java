package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterMappingTemplateApplicatorTest {

    private final ParameterMappingTemplateRegistry registry =
        new ParameterMappingTemplateRegistry();

    @Test
    void appliesOpenAiRootAndAlternativeMaxTokenFields() {
        assertRoot("openai.max-tokens", "max_tokens", 128);
        assertRoot("openai.max-completion-tokens", "max_completion_tokens", 128);
    }

    @Test
    void appliesOllamaAndNestedParameterFamilies() {
        var ollama = apply("ollama.num-predict", 128);
        assertThat(ollama.options()).containsEntry("num_predict", 128);

        var rerank = apply("rerank.parameters.top-n", 5);
        assertThat(rerank.parameters()).containsEntry("top_n", 5);

        var image = apply("image.parameters.negative-prompt", "blurry");
        assertThat(image.parameters()).containsEntry("negative_prompt", "blurry");
    }

    @Test
    void appliesFixedNestedReasoningObjectWithoutAdministratorPaths() {
        var target = apply("reasoning.thinking-type", "enabled");
        assertThat(target.root()).containsEntry("thinking", Map.of("type", "enabled"));
    }

    @Test
    void appliesEveryLanguageScalarTemplateFamily() {
        assertRoot("chat.temperature", "temperature", 0.7);
        assertRoot("chat.top-p", "top_p", 0.9);
        assertRoot("chat.top-k", "top_k", 40);
        assertRoot("chat.min-p", "min_p", 0.05);
        assertRoot("chat.presence-penalty", "presence_penalty", 0.2);
        assertRoot("chat.frequency-penalty", "frequency_penalty", 0.3);
        assertRoot("chat.repetition-penalty", "repetition_penalty", 1.1);
        assertRoot("chat.stop", "stop", java.util.List.of("END"));
        assertRoot("chat.seed", "seed", 42);
        assertRoot("chat.logprobs", "logprobs", true);
        assertRoot("chat.top-logprobs", "top_logprobs", 3);
        assertRoot("chat.parallel-tool-calls", "parallel_tool_calls", false);
    }

    @Test
    void appliesEveryImageAdapterTemplateFamily() {
        assertRoot("image.n", "n", 2);
        assertRoot("image.size", "size", "1024x1024");
        assertRoot("image.aspect-ratio", "aspect_ratio", "1:1");
        assertRoot("image.seed", "seed", 42);
        assertThat(apply("image.response-format.openai", "BASE64").root())
            .containsEntry("response_format", "b64_json");
        assertThat(apply("image.response-format.minimax", "BASE64").root())
            .containsEntry("response_format", "base64");
        assertRoot("image.negative-prompt", "negative_prompt", "blurry");
        assertThat(apply("image.parameters.n", 2).parameters()).containsEntry("n", 2);
        assertThat(apply("image.parameters.size", "1024x768").parameters())
            .containsEntry("size", "1024*768");
        assertThat(apply("image.parameters.seed", 7).parameters()).containsEntry("seed", 7);
        assertThat(apply("image.parameters.negative-prompt", "blurry").parameters())
            .containsEntry("negative_prompt", "blurry");
        assertRoot("image.siliconflow.batch-size", "batch_size", 2);
        assertRoot("image.siliconflow.image-size", "image_size", "1024x1024");
        assertThat(apply("image.minimax.dimensions", "1024x768").root())
            .containsEntry("width", 1024)
            .containsEntry("height", 768);
    }

    private void assertRoot(String template, String field, Object value) {
        assertThat(apply(template, value).root()).containsEntry(field, value);
    }

    private ParameterMappingTarget apply(String template, Object value) {
        var target = new ParameterMappingTarget();
        registry.get(template).applicator().apply(value, target);
        return target;
    }
}
