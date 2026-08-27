package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderUrisTest {

    @Test
    void removesAllTrailingSlashes() {
        assertThat(ProviderUris.withoutTrailingSlashes("https://example.com/v1///"))
            .isEqualTo("https://example.com/v1");
    }

    @Test
    void preservesOtherValues() {
        assertThat(ProviderUris.withoutTrailingSlashes("https://example.com/v1"))
            .isEqualTo("https://example.com/v1");
        assertThat(ProviderUris.withoutTrailingSlashes(""))
            .isEmpty();
        assertThat(ProviderUris.withoutTrailingSlashes(null))
            .isNull();
    }
}
