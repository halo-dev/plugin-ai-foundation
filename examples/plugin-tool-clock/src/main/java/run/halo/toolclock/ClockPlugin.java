package run.halo.toolclock;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Halo entry point for the shared clock-tool example plugin.
 *
 * <p>Install this plugin alongside AI Foundation to expose the tools registered by {@link ClockToolProvider}. Tool
 * configuration is owned by this plugin's ConfigMap.
 *
 * @see ToolConfiguration
 */
@Component
public class ClockPlugin extends BasePlugin {
    /**
     * Creates the plugin with Halo's lifecycle context.
     *
     * @param pluginContext identity and storage names assigned to the installed plugin
     */
    public ClockPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
