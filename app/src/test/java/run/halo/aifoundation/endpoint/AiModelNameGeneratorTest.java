package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import run.halo.app.infra.ValidationUtils;

class AiModelNameGeneratorTest {

    @Test
    void generate_normalizesUnsafeCharactersAndCase() {
        var name = AiModelNameGenerator.generate("OpenAI-Prod", "GPT/4.1 Mini");

        assertThat(name).startsWith("openai-prod-gpt-4-1-mini-");
        assertThat(name).doesNotContain("/", ".", " ");
        assertThat(name).matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
        assertThat(ValidationUtils.NAME_PATTERN.matcher(name).matches()).isTrue();
    }

    @Test
    void generate_supportsOllamaModelTags() {
        var qwen = AiModelNameGenerator.generate("ollama-local", "qwen2.5-coder:7b");
        var llama = AiModelNameGenerator.generate("ollama-local", "llama3.1:8b");

        assertThat(qwen).startsWith("ollama-local-qwen2-5-coder-7b-");
        assertThat(llama).startsWith("ollama-local-llama3-1-8b-");
        assertThat(qwen).doesNotContain(".", ":");
        assertThat(llama).doesNotContain(".", ":");
        assertThat(ValidationUtils.NAME_PATTERN.matcher(qwen).matches()).isTrue();
        assertThat(ValidationUtils.NAME_PATTERN.matcher(llama).matches()).isTrue();
    }

    @Test
    void generate_distinguishesRawValuesWithSameNormalizedPrefix() {
        var dotted = AiModelNameGenerator.generate("openai-prod", "qwen2.5:7b");
        var dashed = AiModelNameGenerator.generate("openai-prod", "qwen2-5-7b");
        var slashed = AiModelNameGenerator.generate("openai-prod", "qwen2_5/7b");

        assertThat(dotted).startsWith("openai-prod-qwen2-5-7b-");
        assertThat(dashed).startsWith("openai-prod-qwen2-5-7b-");
        assertThat(slashed).startsWith("openai-prod-qwen2-5-7b-");
        assertThat(dotted).isNotEqualTo(dashed);
        assertThat(dotted).isNotEqualTo(slashed);
        assertThat(dashed).isNotEqualTo(slashed);
    }

    @Test
    void generate_usesDifferentSuffixForCollisionAttempts() {
        var first = AiModelNameGenerator.generate("openai-prod", "gpt-4", 0);
        var second = AiModelNameGenerator.generate("openai-prod", "gpt-4", 1);

        assertThat(first).startsWith("openai-prod-gpt-4-");
        assertThat(second).startsWith("openai-prod-gpt-4-");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generate_truncatesLongNamesWithinDnsLabelLimit() {
        var name = AiModelNameGenerator.generate(
            "very-long-provider-name-that-keeps-going",
            "very-long-model-id-that-keeps-going-and-going-and-going:latest");

        assertThat(name).hasSizeLessThanOrEqualTo(63);
        assertThat(name).matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    }
}
