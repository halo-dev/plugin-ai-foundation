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

public final class ClockToolProvider implements AiToolProvider {
    private final ClockToolSettingsStore settings;
    private final Clock clock;

    public ClockToolProvider(ClockToolSettingsStore settings) {
        this(settings, Clock.systemUTC());
    }

    ClockToolProvider(ClockToolSettingsStore settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(ToolRegistration.builder()
            .displayName("当前时间")
            .displayDescription("查询当前 UTC 时间")
            .category("实用工具")
            .tags(List.of("时间", "只读"))
            .definition(ToolDefinition.builder()
                .name("current_time")
                .description("Get the current UTC time.")
                .inputSchema(JsonSchema.object().build())
                .executor(context -> Mono.fromSupplier(() -> (Object) Map.of(
                    "utc", clock.instant().toString())))
                .build())
            .build())
            .concatWith(Mono.defer(settings::read)
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn(ClockToolSettingsStore.Settings.empty())
                .map(config -> ToolRegistration.builder()
                    .displayName("格式化时间")
                    .displayDescription("按配置的时区和格式查询当前时间")
                    .category("实用工具")
                    .tags(List.of("时间", "时区", "只读"))
                    .configured(config.ready())
                    .configuration(run.halo.aifoundation.tool.ToolConfiguration.builder()
                        .schema(configurationSchema())
                        .values(settings::values)
                        .save(settings::save)
                        .build())
                    .definition(ToolDefinition.builder()
                        .name("formatted_time")
                        .description("Get the current time in the administrator-configured timezone "
                            + "and format. Returns the timezone, format, formatted value and UTC instant.")
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
            var formatter = switch (config.format()) {
                case "date" -> DateTimeFormatter.ISO_LOCAL_DATE;
                case "time" -> DateTimeFormatter.ISO_OFFSET_TIME;
                default -> DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            };
            return (Object) Map.of("zoneId", config.zoneId(), "format", config.format(),
                "formatted", formatter.format(instant.atZone(ZoneId.of(config.zoneId()))),
                "isoInstant", instant.toString());
        });
    }

    private static List<Map<String, Object>> configurationSchema() {
        return List.of(
            Map.of("$formkit", "text", "name", "zoneId", "label", "时区",
                "validation", "required", "placeholder", "Asia/Shanghai",
                "help", "例如 Asia/Shanghai、UTC 或 America/New_York"),
            Map.of("$formkit", "select", "name", "format", "label", "输出格式",
                "validation", "required", "value", "datetime",
                "options", List.of(Map.of("label", "日期和时间", "value", "datetime"),
                    Map.of("label", "仅日期", "value", "date"),
                    Map.of("label", "仅时间", "value", "time"))));
    }
}
