package run.halo.aifoundation.service.language.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.exception.StructuredOutputTerminationException;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.service.language.mapping.LanguageModelMessageMapper;
import run.halo.aifoundation.service.language.mapping.LanguageModelResponseMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class LanguageModelStructuredOutputDiagnosticsTest {

    @Test
    void productionFailureSummaryIsSingleAndContentFree() {
        var logger = (Logger) LoggerFactory.getLogger(AiFoundationDiagnostics.LOGGER_NAME);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            var output = OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("privateField", Map.of("type", "string"))));
            var response = GenerationResponseMetadata.builder()
                .id("response-42")
                .model("deepseek-v4-pro")
                .headers(Map.of("Authorization", List.of("Bearer response-secret")))
                .body("private-provider-response")
                .metadata(Map.of(AiFoundationDiagnostics.CORRELATION_ID_KEY,
                    "ai_production_test"))
                .build();
            var usage = LanguageModelUsage.builder()
                .inputTokens(120)
                .outputTokens(500)
                .reasoningTokens(498)
                .totalTokens(620)
                .raw(Map.of("privateUsage", "raw-secret"))
                .build();

            handler().enrich(new StructuredOutputValidationException("invalid JSON"), output,
                "private-model-output", 0, usage, response, FinishReason.LENGTH, "length");

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("event=structured-output-failure-summary",
                    "diagnosticId=ai_production_test",
                    "errorType=\"StructuredOutputTerminationException\"",
                    "finishReason=\"LENGTH\"",
                    "rawFinishReason=\"length\"",
                    "model=\"deepseek-v4-pro\"",
                    "responseId=\"response-42\"",
                    "outputTokens=\"500\"",
                    "reasoningTokens=\"498\"",
                    "outputChars=\"20\"")
                .doesNotContain("private-model-output", "privateField",
                    "private-provider-response", "response-secret", "raw-secret");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void successfulStructuredOutputIsSilentAtProductionLogLevels() {
        var logger = (Logger) LoggerFactory.getLogger(AiFoundationDiagnostics.LOGGER_NAME);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            var output = OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("title", Map.of("type", "string"))));

            handler().parse(output, "{\"title\":\"Halo\"}", "ai_success_test");

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void parseFailureRecordsContractExtractedTextStageAndCorrelationId() {
        var logger = (Logger) LoggerFactory.getLogger(AiFoundationDiagnostics.LOGGER_NAME);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
        try {
            var output = OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("title", Map.of("type", "string"))));

            assertThatThrownBy(() -> handler().parse(output,
                "the model returned no JSON", "ai_structured_test"))
                .isInstanceOf(StructuredOutputValidationException.class);

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                    .contains("event=structured-output-input",
                        "diagnosticId=ai_structured_test",
                        "modelText=\"the model returned no JSON\"",
                        "extractedText=\"the model returned no JSON\""))
                .anySatisfy(message -> assertThat(message)
                    .contains("event=structured-output-failure",
                        "diagnosticId=ai_structured_test",
                        "stage=\"json-parse\"",
                        "validationPath=\"$\""));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void terminationRecordsFinishReasonUsageAndCorrelationId() {
        var logger = (Logger) LoggerFactory.getLogger(AiFoundationDiagnostics.LOGGER_NAME);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
        try {
            var output = OutputSpec.object(Map.of("type", "object"));
            var response = GenerationResponseMetadata.builder()
                .metadata(Map.of(AiFoundationDiagnostics.CORRELATION_ID_KEY,
                    "ai_termination_test"))
                .build();
            var usage = LanguageModelUsage.builder().outputTokens(500).reasoningTokens(498).build();

            var error = handler().enrich(
                new StructuredOutputValidationException("invalid JSON"), output, "", 0, usage,
                response, FinishReason.LENGTH, "length");

            assertThat(error).isInstanceOf(StructuredOutputTerminationException.class);
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                    .contains("event=structured-output-termination",
                        "diagnosticId=ai_termination_test",
                        "finishReason=\"LENGTH\"",
                        "rawFinishReason=\"length\"",
                        "outputTokens=500",
                        "reasoningTokens=498"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private LanguageModelStructuredOutputHandler handler() {
        var messageMapper = new LanguageModelMessageMapper("test");
        var responseMapper = new LanguageModelResponseMapper("test", messageMapper);
        var jsonMapper = JsonMapper.builder().build();
        return new LanguageModelStructuredOutputHandler(responseMapper, value -> {
            try {
                return jsonMapper.writeValueAsString(value);
            } catch (JacksonException e) {
                throw new IllegalStateException("Failed to serialize test value", e);
            }
        });
    }
}
