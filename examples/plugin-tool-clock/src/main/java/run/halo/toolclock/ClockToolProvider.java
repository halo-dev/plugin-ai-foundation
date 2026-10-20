package run.halo.toolclock;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.schema.JsonSchema;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolRegistration;

/**
 * Demonstrates a ready-to-use shared tool and a second tool with plugin-owned configuration.
 *
 * <p>{@code current_time} always returns UTC. {@code formatted_time} remains visible but unavailable until its timezone
 * and output format are valid. Both tools are read-only and accept no arguments.
 *
 * <pre>{@code
 * // Consumer plugins resolve these identifiers through AiToolService.
 * var identifiers = List.of("tool-clock/current_time", "tool-clock/formatted_time");
 * }</pre>
 */
public final class ClockToolProvider implements AiToolProvider {
    private final ClockToolSettingsStore settings;
    private final Clock clock;

    /**
     * Creates a provider using the system UTC clock.
     *
     * @param settings this plugin's configuration store
     */
    public ClockToolProvider(ClockToolSettingsStore settings) {
        this(settings, Clock.systemUTC());
    }

    ClockToolProvider(ClockToolSettingsStore settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Describes both clock tools and exposes the configurable tool's native FormKit form.
     *
     * @return registrations with stable names and independently evaluated readiness
     */
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(ToolRegistration.builder()
                        .displayName("当前时间")
                        .displayDescription("查询当前 UTC 时间")
                        .category("utility")
                        .tags(List.of("时间", "只读"))
                        .definition(ToolDefinition.builder()
                                .name("current_time")
                                .description("Read the current instant as an ISO-8601 UTC timestamp in utc. "
                                        + "Takes no arguments and ignores configured local timezones. "
                                        + "Use formatted_time for this plugin's configured local date or time.")
                                .inputSchema(JsonSchema.object().build())
                                .executor(context -> Mono.fromSupplier(() ->
                                        (Object) Map.of("utc", clock.instant().toString())))
                                .build())
                        .build())
                .concatWith(Mono.defer(settings::read)
                        .timeout(Duration.ofSeconds(2))
                        .onErrorReturn(ClockToolSettingsStore.Settings.empty())
                        .map(config -> ToolRegistration.builder()
                                .displayName("格式化时间")
                                .displayDescription("按配置的时区和格式查询当前时间")
                                .category("utility")
                                .tags(List.of("时间", "时区", "只读"))
                                .configured(config.ready())
                                .configuration(run.halo.aifoundation.tool.ToolConfiguration.builder()
                                        .schema(configurationSchema())
                                        .values(settings::values)
                                        .save(settings::save)
                                        .build())
                                .definition(ToolDefinition.builder()
                                        .name("formatted_time")
                                        .description(
                                                "Read the current instant in the administrator-configured timezone "
                                                        + "and format; takes no arguments and cannot select a different timezone per call. "
                                                        + (config.ready()
                                                                ? "Configured zoneId=" + config.zoneId() + ", format="
                                                                        + config.format() + ". "
                                                                : "")
                                                        + "Returns zoneId, format, formatted and isoInstant (UTC). datetime includes date, "
                                                        + "time and offset; date is YYYY-MM-DD; time includes its UTC offset. "
                                                        + "Use current_time when only the UTC timestamp is needed.")
                                        .inputSchema(JsonSchema.object().build())
                                        .executor(context -> formattedTime())
                                        .build())
                                .build()));
    }

    private Mono<Object> formattedTime() {
        return settings.read().map(config -> {
            if (!config.ready()) {
                throw new IllegalStateException("请先配置格式化时间工具的时区和输出格式");
            }
            var instant = clock.instant();
            var formatter =
                    switch (config.format()) {
                        case "date" -> DateTimeFormatter.ISO_LOCAL_DATE;
                        case "time" -> DateTimeFormatter.ISO_OFFSET_TIME;
                        default -> DateTimeFormatter.ISO_OFFSET_DATE_TIME;
                    };
            return (Object) Map.of(
                    "zoneId",
                    config.zoneId(),
                    "format",
                    config.format(),
                    "formatted",
                    formatter.format(instant.atZone(ZoneId.of(config.zoneId()))),
                    "isoInstant",
                    instant.toString());
        });
    }

    private static List<Map<String, Object>> configurationSchema() {
        return List.of(
                Map.of(
                        "$formkit",
                        "text",
                        "name",
                        "zoneId",
                        "label",
                        "时区",
                        "validation",
                        "required",
                        "placeholder",
                        "Asia/Shanghai",
                        "help",
                        "例如 Asia/Shanghai、UTC 或 America/New_York"),
                Map.of(
                        "$formkit",
                        "select",
                        "name",
                        "format",
                        "label",
                        "输出格式",
                        "validation",
                        "required",
                        "value",
                        "datetime",
                        "options",
                        List.of(
                                Map.of("label", "日期和时间", "value", "datetime"),
                                Map.of("label", "仅日期", "value", "date"),
                                Map.of("label", "仅时间", "value", "time"))));
    }
}
