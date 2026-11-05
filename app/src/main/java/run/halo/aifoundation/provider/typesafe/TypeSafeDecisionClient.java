package run.halo.aifoundation.provider.typesafe;

import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.protocol.systemone.SystemOneDecisionClient;

/** Direct TypeSafe access using the System One protocol. */
public class TypeSafeDecisionClient extends SystemOneDecisionClient {

    public TypeSafeDecisionClient(String baseUrl, String apiKey, String model,
        WebClient.Builder builder) {
        super("typesafe", baseUrl, apiKey, model, Map.of(), builder);
    }
}
