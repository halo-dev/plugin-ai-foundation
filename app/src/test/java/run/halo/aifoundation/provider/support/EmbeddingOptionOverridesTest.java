package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingOptions;

class EmbeddingOptionOverridesTest {

    @Test
    void keepsDefaultsWhenRequestIsMissing() {
        var overrides = EmbeddingOptionOverrides.from(null, TestOptions.class);

        assertThat(overrides.modelOr("default-model")).isEqualTo("default-model");
        assertThat(overrides.dimensionsOr(1024)).isEqualTo(1024);
        assertThat(overrides.textOr(TestOptions::apiKey, "default-key"))
            .isEqualTo("default-key");
        assertThat(overrides.valueOr(TestOptions::format, "float"))
            .isEqualTo("float");
        assertThat(overrides.booleanOr(TestOptions::enabled, true)).isTrue();
        assertThat(overrides.providerValue(TestOptions::format)).isNull();
    }

    @Test
    void appliesStandardValuesFromAnotherOptionsType() {
        var request = new StandardOptions("request-model", 512);
        var overrides = EmbeddingOptionOverrides.from(request, TestOptions.class);

        assertThat(overrides.modelOr("default-model")).isEqualTo("request-model");
        assertThat(overrides.dimensionsOr(1024)).isEqualTo(512);
        assertThat(overrides.textOr(TestOptions::apiKey, "default-key"))
            .isEqualTo("default-key");
    }

    @Test
    void appliesProviderValuesAndRejectsBlankText() {
        var request = new TestOptions(" ", null, "request-key", "base64", false);
        var overrides = EmbeddingOptionOverrides.from(request, TestOptions.class);

        assertThat(overrides.modelOr("default-model")).isEqualTo("default-model");
        assertThat(overrides.dimensionsOr(1024)).isEqualTo(1024);
        assertThat(overrides.textOr(TestOptions::apiKey, "default-key"))
            .isEqualTo("request-key");
        assertThat(overrides.valueOr(TestOptions::format, "float"))
            .isEqualTo("base64");
        assertThat(overrides.booleanOr(TestOptions::enabled, true)).isFalse();
    }

    private record StandardOptions(String model, Integer dimensions) implements EmbeddingOptions {

        @Override
        public String getModel() {
            return model;
        }

        @Override
        public Integer getDimensions() {
            return dimensions;
        }
    }

    private record TestOptions(String model, Integer dimensions, String apiKey, String format,
                               boolean enabled) implements EmbeddingOptions {

        @Override
        public String getModel() {
            return model;
        }

        @Override
        public Integer getDimensions() {
            return dimensions;
        }
    }
}
