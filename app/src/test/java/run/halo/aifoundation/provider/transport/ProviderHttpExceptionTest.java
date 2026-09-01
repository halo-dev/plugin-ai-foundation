package run.halo.aifoundation.provider.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderHttpExceptionTest {

    @Test
    void preservesStructuredErrorBodyAndRedactsExceptionMessage() {
        var body = """
            {"error":{"message":"quota exceeded"},"api_key":"secret-value"}
            """;

        var error = new ProviderHttpException("deepseek", "chat", 429, body);

        assertThat(error.getProviderType()).isEqualTo("deepseek");
        assertThat(error.getOperation()).isEqualTo("chat");
        assertThat(error.getStatusCode()).isEqualTo(429);
        assertThat(error.getResponseBody()).isEqualTo(body);
        assertThat(error.getErrorBody()).isInstanceOf(Map.class);
        assertThat(error.getMessage())
            .contains("quota exceeded", "[REDACTED]")
            .doesNotContain("secret-value");
    }

    @Test
    void retainsNonJsonErrorBodies() {
        var error = new ProviderHttpException("ollama", "chat", 500, "upstream unavailable");

        assertThat(error.getErrorBody()).isEqualTo("upstream unavailable");
    }
}
