package run.halo.aifoundation.provider.gitee;

import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

final class GiteeResponsesProfile implements ResponsesProfile {

    @Override
    public String providerType() {
        return "gitee-moark";
    }

    @Override
    public String adapterType() {
        return "gitee-responses";
    }
}
