package run.halo.toolclock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers clock tools when the shared-tool API is available to this plugin.
 *
 * <p>The provider is an ordinary plugin bean implementing {@link run.halo.aifoundation.tool.AiToolProvider}; AI
 * Foundation discovers it through Halo's enabled extension mechanism. Consumers do not need a static service locator or
 * another plugin's beans.
 */
@Configuration
@ConditionalOnClass(name = "run.halo.aifoundation.tool.AiToolProvider")
public class ToolConfiguration {
    @Bean
    ClockToolProvider clockToolProvider(ClockToolSettingsStore settings) {
        return new ClockToolProvider(settings);
    }
}
