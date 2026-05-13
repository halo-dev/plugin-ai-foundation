package run.halo.aifoundation.provider;

import run.halo.aifoundation.extension.AiProvider;

public class ProviderAdapterFactory {

    public static ProviderAdapter create(AiProvider provider, String apiKey) {
        var providerType = provider.getSpec().getProviderType();
        if (providerType == null) {
            throw new IllegalArgumentException(
                "Provider type is null for: " + provider.getMetadata().getName());
        }
        return switch (providerType.toLowerCase()) {
            case "openai" -> new OpenAiAdapter(provider, apiKey);
            case "aihubmix" -> new AiHubMixAdapter(provider, apiKey);
            case "deepseek" -> new DeepSeekAdapter(provider, apiKey);
            case "siliconflow" -> new SiliconFlowAdapter(provider, apiKey);
            case "doubao" -> new DouBaoAdapter(provider, apiKey);
            case "ernie" -> new ErnieAdapter(provider, apiKey);
            case "zhipuai" -> new ZhiPuAdapter(provider, apiKey);
            case "ollama" -> new OllamaAdapter(provider, apiKey);
            case "openailike" -> new OpenAiLikeAdapter(provider, apiKey);
            default -> throw new IllegalArgumentException(
                "Unsupported provider type: " + providerType);
        };
    }
}
