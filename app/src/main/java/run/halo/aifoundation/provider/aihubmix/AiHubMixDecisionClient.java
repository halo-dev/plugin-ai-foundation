package run.halo.aifoundation.provider.aihubmix;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.systemone.SystemOneDecisionClient;

/** AIHubMix System One gateway with its attribution headers and diagnostics. */
public class AiHubMixDecisionClient extends SystemOneDecisionClient {

    public AiHubMixDecisionClient(String baseUrl, String apiKey, String model,
        WebClient.Builder builder) {
        super("aihubmix", baseUrl, apiKey, model, AiHubMixHeaders.DEFAULTS, builder);
    }
}
