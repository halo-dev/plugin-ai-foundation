package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderHeadersTest {

    @Test
    void keepsOnlyNamedHeadersWithValues() {
        var headers = new LinkedHashMap<String, String>();

        ProviderHeaders.putIfValid(headers, "X-Valid", "value");
        ProviderHeaders.putIfValid(headers, null, "value");
        ProviderHeaders.putIfValid(headers, " ", "value");
        ProviderHeaders.putIfValid(headers, "X-Missing", null);

        assertThat(headers).containsExactlyEntriesOf(java.util.Map.of("X-Valid", "value"));
    }

    @Test
    void mergesHeadersInRequestPrecedenceOrder() {
        var requestHeaders = new LinkedHashMap<String, String>();
        requestHeaders.put("X-Request", "request");
        requestHeaders.put(" ", "ignored");
        requestHeaders.put("X-Missing", null);

        var merged = ProviderHeaders.merge(
            Map.of("X-Shared", "default", "X-Default", "default"),
            Map.of("X-Shared", "provider", "X-Provider", "provider"),
            requestHeaders
        );

        assertThat(merged).containsExactlyInAnyOrderEntriesOf(Map.of(
            "X-Shared", "provider",
            "X-Default", "default",
            "X-Provider", "provider",
            "X-Request", "request"
        ));
    }
}
