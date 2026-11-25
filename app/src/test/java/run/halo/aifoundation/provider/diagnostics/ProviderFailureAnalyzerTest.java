package run.halo.aifoundation.provider.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.transport.ProviderHttpException;

class ProviderFailureAnalyzerTest {

    private final ProviderFailureAnalyzer analyzer = new ProviderFailureAnalyzer();

    @Test
    void classifiesAuthenticationStatus() {
        var analysis = analyzer.analyze(
                new ProviderHttpException("openai", "models", 401, "{\"error\":{\"message\":\"Invalid API key\"}}"));

        assertThat(analysis.category()).isEqualTo(ProviderFailureCategory.AUTHENTICATION);
        assertThat(analysis.httpStatus()).isEqualTo(401);
        assertThat(analysis.summary()).contains("401").contains("Invalid API key");
        assertThat(analysis.detail()).contains("Invalid API key");
    }

    @Test
    void classifiesForbiddenAsAuthentication() {
        var analysis = analyzer.analyze(new ProviderHttpException("openai", "models", 403, "{}"));

        assertThat(analysis.category()).isEqualTo(ProviderFailureCategory.AUTHENTICATION);
    }

    @Test
    void classifiesConfigurationStatuses() {
        assertThat(analyzer.analyze(httpFailure(400)).category()).isEqualTo(ProviderFailureCategory.CONFIGURATION);
        assertThat(analyzer.analyze(httpFailure(404)).category()).isEqualTo(ProviderFailureCategory.CONFIGURATION);
        assertThat(analyzer.analyze(httpFailure(409)).category()).isEqualTo(ProviderFailureCategory.CONFIGURATION);
        assertThat(analyzer.analyze(httpFailure(422)).category()).isEqualTo(ProviderFailureCategory.CONFIGURATION);
    }

    @Test
    void classifiesUnsupportedCapability() {
        assertThat(analyzer.analyze(httpFailure(405)).category())
                .isEqualTo(ProviderFailureCategory.CAPABILITY_UNSUPPORTED);
        assertThat(analyzer.analyze(httpFailure(501)).category())
                .isEqualTo(ProviderFailureCategory.CAPABILITY_UNSUPPORTED);
    }

    @Test
    void classifiesRateLimit() {
        assertThat(analyzer.analyze(httpFailure(429)).category()).isEqualTo(ProviderFailureCategory.RATE_LIMITED);
    }

    @Test
    void classifiesProviderErrors() {
        assertThat(analyzer.analyze(httpFailure(500)).category()).isEqualTo(ProviderFailureCategory.PROVIDER_ERROR);
        assertThat(analyzer.analyze(httpFailure(503)).category()).isEqualTo(ProviderFailureCategory.PROVIDER_ERROR);
    }

    @Test
    void classifiesTimeoutStatusesAsNetwork() {
        assertThat(analyzer.analyze(httpFailure(408)).category()).isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(analyzer.analyze(httpFailure(504)).category()).isEqualTo(ProviderFailureCategory.NETWORK);
    }

    @Test
    void classifiesTransportFailuresAsNetwork() {
        assertThat(analyzer.analyze(new RuntimeException(new ConnectException("refused")))
                        .category())
                .isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(analyzer.analyze(new UnknownHostException("api.example.com")).category())
                .isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(analyzer.analyze(new RuntimeException(new SocketTimeoutException("timeout")))
                        .category())
                .isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(analyzer.analyze(new RuntimeException(new TimeoutException()))
                        .category())
                .isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(analyzer.analyze(new RuntimeException(new SSLHandshakeException("handshake")))
                        .category())
                .isEqualTo(ProviderFailureCategory.NETWORK);
    }

    @Test
    void classifiesLocalConfigurationErrors() {
        assertThat(analyzer.analyze(new IllegalArgumentException("Base URL is required"))
                        .category())
                .isEqualTo(ProviderFailureCategory.CONFIGURATION);
    }

    @Test
    void fallsBackToUnknown() {
        var analysis = analyzer.analyze(new IllegalStateException("boom"));

        assertThat(analysis.category()).isEqualTo(ProviderFailureCategory.UNKNOWN);
        assertThat(analysis.summary()).contains("boom");
        assertThat(analysis.httpStatus()).isNull();
    }

    @Test
    void redactsCredentialsFromSummaryAndDetail() {
        var body = "{\"error\":{\"message\":\"invalid key sk-abcdef1234567890\"},"
                + "\"api_key\":\"sk-secretvalue\","
                + "\"url\":\"https://user:pass@example.com\"}";

        var analysis = analyzer.analyze(new ProviderHttpException("openai", "models", 401, body));

        assertThat(analysis.summary()).doesNotContain("sk-abcdef1234567890").contains("[REDACTED]");
        assertThat(analysis.detail())
                .doesNotContain("sk-abcdef1234567890")
                .doesNotContain("sk-secretvalue")
                .doesNotContain("pass");
    }

    @Test
    void boundsLongProviderBodies() {
        var body = "x".repeat(5_000);

        var analysis = analyzer.analyze(new ProviderHttpException("openai", "models", 500, body));

        assertThat(analysis.summary().length()).isLessThanOrEqualTo(300);
        assertThat(analysis.detail().length()).isLessThanOrEqualTo(1_024);
    }

    private ProviderHttpException httpFailure(int status) {
        return new ProviderHttpException("openai", "models", status, "{}");
    }
}
