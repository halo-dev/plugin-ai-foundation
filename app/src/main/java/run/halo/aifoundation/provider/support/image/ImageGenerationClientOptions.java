package run.halo.aifoundation.provider.support.image;

import java.util.Map;

public record ImageGenerationClientOptions(
    String providerType,
    String baseUrl,
    String apiKey,
    String model,
    Map<String, String> customHeaders
) {
    public ImageGenerationClientOptions {
        customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
    }
}
