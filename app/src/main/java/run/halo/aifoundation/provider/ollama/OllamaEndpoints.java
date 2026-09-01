package run.halo.aifoundation.provider.ollama;

/** Canonical Ollama native and OpenAI-compatible endpoint resolution. */
final class OllamaEndpoints {

    private OllamaEndpoints() {
    }

    static String nativeUrl(String baseUrl, String resource) {
        var base = trim(baseUrl);
        var path = resource.startsWith("/") ? resource : "/" + resource;
        return base.endsWith("/api") ? base + path : base + "/api" + path;
    }

    static String openAiBaseUrl(String baseUrl) {
        var base = nativeBaseUrl(baseUrl);
        return base.endsWith("/v1") ? base : base + "/v1";
    }

    static String nativeBaseUrl(String baseUrl) {
        var base = trim(baseUrl);
        return base.endsWith("/api") ? base.substring(0, base.length() - 4) : base;
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ollama base URL must not be blank");
        }
        var result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
