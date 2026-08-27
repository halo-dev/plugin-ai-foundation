package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderRequestOptionsTest {

    @Test
    void readsOnlyTheRequestedProviderNamespace() {
        var options = Map.of(
            "first", Map.<String, Object>of("enabled", true),
            "second", Map.<String, Object>of("enabled", false)
        );

        assertThat(ProviderRequestOptions.get(options, "first"))
            .containsEntry("enabled", true);
        assertThat(ProviderRequestOptions.get(options, "missing")).isNull();
        assertThat(ProviderRequestOptions.get(null, "first")).isNull();
    }

    @Test
    void suppliesAnImmutableEmptyMapForMissingOptions() {
        assertThat(ProviderRequestOptions.orEmpty(null, "first")).isEmpty();
        assertThat(ProviderRequestOptions.orEmpty(Map.of(), "first")).isEmpty();
    }

    @Test
    void copiesOnlyNamedNonNullValues() {
        var source = new LinkedHashMap<String, Object>();
        source.put("valid", true);
        source.put(null, "ignored");
        source.put("missing", null);
        var target = new LinkedHashMap<String, Object>();

        ProviderRequestOptions.copyNonNullValues(target, source);

        assertThat(target).containsOnlyKeys("valid");
    }
}
