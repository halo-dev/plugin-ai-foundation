package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsageErrorTest {

    @Test
    void persistsOnlyBoundedErrorClassification() {
        var error = UsageError.from(new SecretBearingException("sk-secret prompt content"));

        assertThat(error.type()).isEqualTo("SECRETBEARINGEXCEPTION");
        assertThat(error.code()).isNull();
        assertThat(error.toString()).doesNotContain("sk-secret", "prompt content");
    }

    private static final class SecretBearingException extends RuntimeException {
        private SecretBearingException(String message) {
            super(message);
        }
    }
}
