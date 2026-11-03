package run.halo.aifoundation.provider.zhipu;

import run.halo.aifoundation.provider.support.ProviderUris;

/** Resolves the Chat Completions and Messages endpoint families used by Zhipu and Z.AI. */
public final class ZhiPuEndpointResolver {

    private final String endpointRoot;

    public ZhiPuEndpointResolver(String configuredBaseUrl) {
        this.endpointRoot = ProviderUris.withoutTrailingPath(configuredBaseUrl, "/api/paas/v4");
    }

    public String messagesBaseUrl() {
        return endpointRoot + "/api/anthropic/v1";
    }
}