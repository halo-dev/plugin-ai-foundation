package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.exception.EmbeddingTimeoutException;
import run.halo.aifoundation.exception.RerankTimeoutException;
import run.halo.aifoundation.service.observation.UsageError;

class UsageErrorTest {

    @Test
    void persistsOnlyBoundedErrorClassification() {
        var error = UsageError.from(new SecretBearingException("sk-secret prompt content"));

        assertThat(error.type()).isEqualTo("SECRETBEARINGEXCEPTION");
        assertThat(error.code()).isNull();
        assertThat(error.toString()).doesNotContain("sk-secret", "prompt content");
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void classifiesDirectAndWrappedTimeoutsWithoutLeakingTheirMessage(Throwable timeout) {
        assertThat(UsageError.from(timeout).type()).isEqualTo("TIMEOUT");
        assertThat(UsageError.from(new IllegalStateException("wrapper", timeout)).type())
            .isEqualTo("TIMEOUT");
        assertThat(UsageError.from(timeout).toString()).doesNotContain("private");
    }

    static Stream<Throwable> timeouts() {
        return Stream.of(
            new AiGenerationTimeoutException("tool", "private prompt"),
            new EmbeddingTimeoutException(Duration.ofSeconds(1), null),
            new RerankTimeoutException(Duration.ofSeconds(1), null),
            new java.util.concurrent.TimeoutException("private"),
            new java.net.SocketTimeoutException("private"),
            new java.net.http.HttpTimeoutException("private"));
    }

    private static final class SecretBearingException extends RuntimeException {
        private SecretBearingException(String message) {
            super(message);
        }
    }
}
