package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReasoningProviderMetadataTest {

    @Test
    void returnsOnlyTheRequestedProviderNamespace() {
        var metadata = Map.<String, Object>of("reasoningProviderMetadata", Map.of(
            "openrouter", Map.of("details", "signed"),
            "minimax", Map.of("blocks", "thinking")));

        assertThat(ReasoningProviderMetadata.values(metadata, "openrouter"))
            .containsExactly(Map.entry("details", "signed"));
        assertThat(ReasoningProviderMetadata.values(metadata, "unknown")).isEmpty();
        assertThat(ReasoningProviderMetadata.values(null, "openrouter")).isEmpty();
    }

    @Test
    void treatsMalformedMetadataAsAbsent() {
        assertThat(ReasoningProviderMetadata.values(
            Map.of("reasoningProviderMetadata", "invalid"), "openrouter")).isEmpty();
        assertThat(ReasoningProviderMetadata.values(
            Map.of("reasoningProviderMetadata", Map.of("openrouter", "invalid")),
            "openrouter")).isEmpty();
    }
}
