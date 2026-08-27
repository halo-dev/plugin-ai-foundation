package run.halo.aifoundation.provider.contract;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;

/** Explicit opt-in and secret-safe access to credentials for billable live smoke tests. */
public final class LiveProviderTestSupport {

    public static final String ENABLED_ENV = "AI_FOUNDATION_LIVE_PROVIDER_TESTS";

    private LiveProviderTestSupport() {
    }

    public static SecretValue requireCredential(String providerType, String environmentName) {
        var environment = System.getenv();
        Assumptions.assumeTrue(isEnabled(providerType, environment),
            () -> "Live " + providerType + " provider tests are not enabled");
        var value = environment.get(environmentName);
        Assumptions.assumeTrue(value != null && !value.isBlank(),
            () -> "Live " + providerType + " provider credential is not configured");
        return new SecretValue(value);
    }

    static boolean isEnabled(String providerType, Map<String, String> environment) {
        var configured = environment.get(ENABLED_ENV);
        if (configured == null || configured.isBlank()) {
            return false;
        }
        var expected = providerType.toLowerCase(Locale.ROOT);
        for (var value : configured.split(",")) {
            var normalized = value.trim().toLowerCase(Locale.ROOT);
            if ("all".equals(normalized) || expected.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public record SecretValue(String value) {
        @Override
        public String toString() {
            return "[REDACTED]";
        }
    }
}
