package run.halo.aifoundation.provider.support.openai;

import java.util.Map;

public record OpenAiCompatibleImageOptions(String providerType, String baseUrl,
                                           String endpointPath, String apiKey, String model,
                                           Map<String, String> customHeaders) {

    public OpenAiCompatibleImageOptions {
        customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
    }
}
