package run.halo.aifoundation.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AiFoundationDiagnosticsTest {

    @Test
    void diagnosticsAreDisabledBelowTraceAndRedactCredentialsWhenEnabled() {
        var logger = (Logger) LoggerFactory.getLogger(AiFoundationDiagnostics.LOGGER_NAME);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var evaluated = new AtomicBoolean();
            logger.setLevel(Level.INFO);

            AiFoundationDiagnostics.trace("provider-request", "ai_disabled", () -> {
                evaluated.set(true);
                return "body=should-not-be-built";
            });

            assertThat(evaluated).isFalse();
            assertThat(appender.list).isEmpty();

            logger.setLevel(Level.TRACE);
            AiFoundationDiagnostics.trace("provider-request", "ai_enabled",
                () -> AiFoundationDiagnostics.fields(
                    "body", "{\"api_key\":\"sk-secret\",\"prompt\":\"recognize link\"}",
                    "header", "Authorization: Bearer another-secret"));

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("event=provider-request", "diagnosticId=ai_enabled",
                    "recognize link", "[REDACTED]")
                .doesNotContain("sk-secret", "another-secret");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void invocationIdsAreUniqueAndStableForLogCorrelation() {
        var first = AiFoundationDiagnostics.newInvocationId();
        var second = AiFoundationDiagnostics.newInvocationId();

        assertThat(first).startsWith("ai_").hasSize(35);
        assertThat(second).startsWith("ai_").hasSize(35).isNotEqualTo(first);
    }
}
