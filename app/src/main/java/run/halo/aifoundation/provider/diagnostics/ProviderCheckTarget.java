package run.halo.aifoundation.provider.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;

/** One independently reported item in a provider health check. */
@Schema(description = "Category of an individual provider health check")
public enum ProviderCheckTarget {
    CONFIGURATION,
    NETWORK,
    AUTHENTICATION,
    MODEL_DISCOVERY,
    CHAT,
    EMBEDDING
}
