package run.halo.toolclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolExecutionContext;

class ClockToolProviderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T12:34:56Z"), ZoneOffset.UTC);
    private final ClockToolSettingsStore settings = mock(ClockToolSettingsStore.class);
    private ClockToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ClockToolProvider(settings, CLOCK);
    }

    @Test
    void advertisesBothToolsAndMarksOnlyFormattedTimeUnconfigured() {
        when(settings.read()).thenReturn(Mono.just(ClockToolSettingsStore.Settings.empty()));
        var tools = provider.tools().collectList().block();
        assertThat(tools)
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactly("current_time", "formatted_time");
        assertThat(tools.getFirst().isConfigured()).isTrue();
        assertThat(tools.getLast().isConfigured()).isFalse();
        assertThat(tools.getLast().getConfiguration().getSchema())
                .extracting(field -> field.get("$formkit"))
                .containsExactly("text", "select");
        StepVerifier.create(tools.getFirst().getDefinition().getExecutor().execute(context()))
                .assertNext(
                        result -> assertThat(((Map<?, ?>) result).get("utc")).isEqualTo("2026-10-14T12:34:56Z"))
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({"datetime, 2026-10-14T20:34:56+08:00", "date, 2026-10-14", "time, 20:34:56+08:00"})
    void usesConfiguredTimezoneAndFormatWhenExecuted(String format, String expected) {
        when(settings.read()).thenReturn(Mono.just(new ClockToolSettingsStore.Settings("Asia/Shanghai", format)));
        var tool = provider.tools()
                .filter(item -> item.getDefinition().getName().equals("formatted_time"))
                .blockFirst();
        assertThat(tool.isConfigured()).isTrue();
        assertThat(tool.getDefinition().getDescription())
                .contains("Configured zoneId=Asia/Shanghai", "format=" + format, "takes no arguments");
        StepVerifier.create(tool.getDefinition().getExecutor().execute(context()))
                .assertNext(result -> {
                    var output = (Map<?, ?>) result;
                    assertThat(output.get("zoneId")).isEqualTo("Asia/Shanghai");
                    assertThat(output.get("format")).isEqualTo(format);
                    assertThat(output.get("formatted")).isEqualTo(expected);
                    assertThat(output.get("isoInstant")).isEqualTo("2026-10-14T12:34:56Z");
                })
                .verifyComplete();
    }

    @Test
    void refusesExecutionWhenConfigurationBecomesUnavailable() {
        when(settings.read())
                .thenReturn(
                        Mono.just(new ClockToolSettingsStore.Settings("UTC", "date")),
                        Mono.just(ClockToolSettingsStore.Settings.empty()));
        var tool = provider.tools()
                .filter(item -> item.getDefinition().getName().equals("formatted_time"))
                .blockFirst();
        assertThat(tool.isConfigured()).isTrue();
        StepVerifier.create(tool.getDefinition().getExecutor().execute(context()))
                .expectErrorMessage("请先配置格式化时间工具的时区和输出格式")
                .verify();
    }

    @Test
    void configurationCallbacksUseTheProviderOwnedStore() {
        when(settings.read()).thenReturn(Mono.just(ClockToolSettingsStore.Settings.empty()));
        when(settings.values()).thenReturn(Mono.just(Map.of("zoneId", "", "format", "datetime")));
        var values = Map.<String, Object>of("zoneId", "UTC", "format", "date");
        when(settings.save(values)).thenReturn(Mono.empty());
        var configuration = provider.tools().collectList().block().getLast().getConfiguration();

        StepVerifier.create(configuration.getValues().get())
                .assertNext(current -> assertThat(current).containsEntry("format", "datetime"))
                .verifyComplete();
        StepVerifier.create(configuration.getSave().apply(values)).verifyComplete();
        verify(settings).save(values);
    }

    @Test
    void hangingSettingsStillExposeTheClockAndConfigurableTool() {
        when(settings.read()).thenReturn(Mono.never());
        StepVerifier.withVirtualTime(() -> provider.tools().collectList())
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(2))
                .assertNext(tools -> {
                    assertThat(tools).hasSize(2);
                    assertThat(tools.getFirst().isConfigured()).isTrue();
                    assertThat(tools.getLast().isConfigured()).isFalse();
                })
                .verifyComplete();
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.builder()
                .toolCallId("test-call")
                .input(Map.of())
                .build();
    }
}
