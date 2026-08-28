package run.halo.aifoundation.provider.support;

import java.util.Map;
import lombok.Value;

/**
 * Provider capability flags used by the reranking runtime.
 */
@Value
public class RerankingModelProviderOptions {

    Map<String, Object> nativeOptions;

    public RerankingModelProviderOptions(Map<String, Object> nativeOptions) {
        this.nativeOptions = nativeOptions == null ? Map.of() : Map.copyOf(nativeOptions);
    }

    public static RerankingModelProviderOptions defaults() {
        return new RerankingModelProviderOptions(Map.of());
    }

    public RerankingModelProviderOptions withNativeOptions(Map<String, Object> nativeOptions) {
        return new RerankingModelProviderOptions(nativeOptions);
    }
}
