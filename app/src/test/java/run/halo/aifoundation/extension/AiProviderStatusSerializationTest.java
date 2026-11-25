package run.halo.aifoundation.extension;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.diagnostics.ProviderCheckResult;
import run.halo.aifoundation.provider.diagnostics.ProviderCheckTarget;
import run.halo.aifoundation.provider.diagnostics.ProviderFailureCategory;
import run.halo.aifoundation.provider.diagnostics.ProviderHealthCheck;
import run.halo.aifoundation.provider.diagnostics.ProviderSkipReason;

class AiProviderStatusSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsLegacyStatusWithoutNewFields() throws Exception {
        var json = """
                {
                  "phase": "OK",
                  "message": "Connectivity check passed",
                  "lastCheckedAt": "2026-11-24T10:00:00Z"
                }
                """;

        var status = mapper.readValue(json, AiProvider.AiProviderStatus.class);

        assertThat(status.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.OK);
        assertThat(status.getMessage()).isEqualTo("Connectivity check passed");
        assertThat(status.getLastCheckedAt()).isEqualTo(Instant.parse("2026-11-24T10:00:00Z"));
        assertThat(status.getFailureCategory()).isNull();
        assertThat(status.getChecks()).isEmpty();
    }

    @Test
    void readsStructuredStatusWithEnumsAndOptionalFields() throws Exception {
        var json = """
                {
                  "phase": "ERROR",
                  "message": "HTTP 401 Unauthorized",
                  "failureCategory": "AUTHENTICATION",
                  "lastCheckedAt": "2026-11-24T10:00:00Z",
                  "checks": [
                    {
                      "target": "NETWORK",
                      "result": "PASSED",
                      "httpStatus": 401,
                      "latencyMs": 420,
                      "message": "Endpoint reachable",
                      "checkedAt": "2026-11-24T10:00:01Z"
                    },
                    {
                      "target": "MODEL_DISCOVERY",
                      "result": "SKIPPED",
                      "skipReason": "BLOCKED_BY_PREVIOUS_FAILURE",
                      "checkedAt": "2026-11-24T10:00:02Z"
                    }
                  ]
                }
                """;

        var status = mapper.readValue(json, AiProvider.AiProviderStatus.class);

        assertThat(status.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.ERROR);
        assertThat(status.getFailureCategory()).isEqualTo(ProviderFailureCategory.AUTHENTICATION);
        assertThat(status.getChecks()).hasSize(2);
        var network = status.getChecks().getFirst();
        assertThat(network.getTarget()).isEqualTo(ProviderCheckTarget.NETWORK);
        assertThat(network.getResult()).isEqualTo(ProviderCheckResult.PASSED);
        assertThat(network.getHttpStatus()).isEqualTo(401);
        assertThat(network.getLatencyMs()).isEqualTo(420L);
        assertThat(network.getCategory()).isNull();
        var discovery = status.getChecks().get(1);
        assertThat(discovery.getResult()).isEqualTo(ProviderCheckResult.SKIPPED);
        assertThat(discovery.getSkipReason()).isEqualTo(ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
    }

    @Test
    void writesStructuredStatusWithStableEnumNames() throws Exception {
        var status = new AiProvider.AiProviderStatus();
        status.setPhase(AiProvider.AiProviderStatus.Phase.ERROR);
        status.setFailureCategory(ProviderFailureCategory.NETWORK);
        status.setChecks(List.of(ProviderHealthCheck.builder()
                .target(ProviderCheckTarget.AUTHENTICATION)
                .result(ProviderCheckResult.FAILED)
                .category(ProviderFailureCategory.NETWORK)
                .build()));

        var tree = mapper.readTree(mapper.writeValueAsString(status));

        assertThat(tree.get("phase").asText()).isEqualTo("ERROR");
        assertThat(tree.get("failureCategory").asText()).isEqualTo("NETWORK");
        assertThat(tree.get("checks")).hasSize(1);
        var check = tree.get("checks").get(0);
        assertThat(check.get("target").asText()).isEqualTo("AUTHENTICATION");
        assertThat(check.get("result").asText()).isEqualTo("FAILED");
        assertThat(check.get("category").asText()).isEqualTo("NETWORK");
    }

    @Test
    void roundTripsProviderWithStructuredStatus() throws Exception {
        var provider = new AiProvider();
        var metadata = new run.halo.app.extension.Metadata();
        metadata.setName("openai-prod");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setDisplayName("OpenAI Production");
        provider.setSpec(spec);
        var status = new AiProvider.AiProviderStatus();
        status.setPhase(AiProvider.AiProviderStatus.Phase.OK);
        status.setChecks(List.of(ProviderHealthCheck.builder()
                .target(ProviderCheckTarget.CONFIGURATION)
                .result(ProviderCheckResult.PASSED)
                .message("Configuration is valid")
                .build()));
        provider.setStatus(status);

        var json = mapper.writeValueAsString(provider);
        var restored = mapper.readValue(json, AiProvider.class);

        assertThat(restored.getMetadata().getName()).isEqualTo("openai-prod");
        assertThat(restored.getStatus().getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.OK);
        assertThat(restored.getStatus().getChecks()).hasSize(1);
        assertThat(restored.getStatus().getChecks().getFirst().getTarget())
                .isEqualTo(ProviderCheckTarget.CONFIGURATION);
        assertThat(restored.getStatus().getFailureCategory()).isNull();
    }
}
