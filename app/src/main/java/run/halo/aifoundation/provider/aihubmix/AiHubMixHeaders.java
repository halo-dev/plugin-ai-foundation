package run.halo.aifoundation.provider.aihubmix;

import java.util.Map;

/** Headers shared by every AIHubMix inference domain. */
final class AiHubMixHeaders {

    static final String APP_CODE = "NEUE3459";
    static final Map<String, String> DEFAULTS = Map.of("APP-Code", APP_CODE);

    private AiHubMixHeaders() {
    }
}
