package run.halo.aifoundation.provider.dashscope;

import run.halo.aifoundation.provider.support.ProviderUris;

import java.util.List;

/** Resolves the distinct compatible and native endpoint families exposed by Model Studio. */
public final class DashScopeEndpointResolver {

    private static final List<String> KNOWN_SUFFIXES = List.of(
        "/compatible-mode/v1", "/compatible-api/v1", "/apps/anthropic", "/api/v1");

    private final String endpointRoot;

    public DashScopeEndpointResolver(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            throw new IllegalArgumentException("DashScope base URL is required");
        }
        var normalized = ProviderUris.withoutTrailingSlashes(configuredBaseUrl.trim());
        this.endpointRoot = stripKnownSuffix(normalized);
    }

    public String compatibleBaseUrl() {
        return endpointRoot + "/compatible-mode/v1";
    }

    public String compatibleApiBaseUrl() {
        return endpointRoot + "/compatible-api/v1";
    }

    public String nativeBaseUrl() {
        return endpointRoot + "/api/v1";
    }

    public String messagesBaseUrl() {
        return endpointRoot + "/apps/anthropic";
    }

    public String modelCatalogUrl() {
        return nativeBaseUrl() + "/models";
    }

    private String stripKnownSuffix(String value) {
        for (var suffix : KNOWN_SUFFIXES) {
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

}
