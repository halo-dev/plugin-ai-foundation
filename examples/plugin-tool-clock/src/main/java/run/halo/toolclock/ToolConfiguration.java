package run.halo.toolclock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "run.halo.aifoundation.tool.AiToolProvider")
public class ToolConfiguration {
    @Bean
    ClockToolProvider clockToolProvider(ClockToolSettingsStore settings) {
        return new ClockToolProvider(settings);
    }
}
