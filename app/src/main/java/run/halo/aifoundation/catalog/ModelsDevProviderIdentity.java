package run.halo.aifoundation.catalog;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;

/** Reviewed identities, not host suffixes, name heuristics or catalog-controlled destinations. */
final class ModelsDevProviderIdentity {

    private static final List<Identity> IDENTITIES = List.of(
        new Identity("openai", "https://api.openai.com/v1", "openai"),
        new Identity("deepseek", "https://api.deepseek.com", "deepseek"),
        new Identity("deepseek", "https://api.deepseek.com/v1", "deepseek"),
        new Identity("kimi", "https://api.moonshot.cn/v1", "moonshotai-cn"),
        new Identity("kimi", "https://api.moonshot.ai/v1", "moonshotai"),
        new Identity("dashscope", "https://dashscope.aliyuncs.com/compatible-mode/v1", "alibaba-cn"),
        new Identity("dashscope", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "alibaba"),
        new Identity("siliconflow", "https://api.siliconflow.cn/v1", "siliconflow-cn"),
        new Identity("siliconflow", "https://api.siliconflow.com/v1", "siliconflow"),
        new Identity("zhipuai", "https://open.bigmodel.cn/api/paas/v4", "zhipuai"),
        new Identity("zhipuai", "https://api.z.ai/api/paas/v4", "zai"),
        new Identity("doubao", "https://ark.cn-beijing.volces.com/api/v3", "volcengine"),
        new Identity("openrouter", "https://openrouter.ai/api/v1", "openrouter"),
        new Identity("aihubmix", "https://aihubmix.com/v1", "aihubmix"),
        new Identity("tokenhub", "https://tokenhub.tencentmaas.com/v1", "tencent-tokenhub")
    );

    private ModelsDevProviderIdentity() {
    }

    static String resolve(AiProvider provider, AiProviderType type) {
        var spec = provider.getSpec();
        // Endpoint overrides can route the same model ID to a different deployment.
        if (spec.getChatEndpointPath() != null && !spec.getChatEndpointPath().isBlank()) {
            return null;
        }
        var endpoint = endpoint(provider, type);
        return IDENTITIES.stream()
            .filter(item -> item.type().equals(type.getProviderType()))
            .filter(item -> item.endpoint().equals(endpoint))
            .map(Identity::catalogId).findFirst().orElse(null);
    }

    static String endpoint(AiProvider provider, AiProviderType type) {
        var url = provider.getSpec().getBaseUrl();
        return normalize(url == null || url.isBlank() ? type.getDefaultBaseUrl() : url);
    }

    static String normalize(String url) {
        if (url == null) {
            return "";
        }
        try {
            var uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return "";
            }
            return "https://" + uri.getHost().toLowerCase(Locale.ROOT)
                + uri.getRawPath().replaceAll("/+$", "");
        } catch (IllegalArgumentException failure) {
            return "";
        }
    }

    private record Identity(String type, String endpoint, String catalogId) {
    }
}
