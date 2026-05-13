package run.halo.aifoundation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

@ExtendWith(MockitoExtension.class)
class AiFoundationPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemeManager;

    AiFoundationPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new AiFoundationPlugin(context);
        ReflectionTestUtils.setField(plugin, "schemeManager", schemeManager);
    }

    @Test
    void contextLoads() {
        plugin.start();
        plugin.stop();
    }
}
