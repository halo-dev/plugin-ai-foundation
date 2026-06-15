package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import example.consumer.ConsumerPluginCaller;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class StackWalkingCallerPluginResolverTest {

    @Test
    void resolveCurrentCaller_detectsPluginFromCallerClassLoader() {
        var client = mock(ReactiveExtensionClient.class);
        var self = mock(PluginWrapper.class);
        var caller = mock(PluginWrapper.class);
        var pluginManager = mock(PluginManager.class);
        var provider = mockPluginWrapperProvider(self);
        var resolver = new StackWalkingCallerPluginResolver(client, provider);
        var callerPlugin = callerPlugin();

        when(self.getPluginId()).thenReturn("ai-foundation");
        when(self.getPluginManager()).thenReturn(pluginManager);
        when(caller.getPluginId()).thenReturn("consumer-plugin");
        when(caller.getPluginClassLoader()).thenReturn(ConsumerPluginCaller.class.getClassLoader());
        when(pluginManager.getStartedPlugins()).thenReturn(List.of(self, caller));
        when(client.fetch(Plugin.class, "consumer-plugin")).thenReturn(Mono.just(callerPlugin));

        var snapshot = ConsumerPluginCaller.resolveSnapshot(resolver);
        assertThat(snapshot.isDetected()).isTrue();
        assertThat(snapshot.getPluginName()).isEqualTo("consumer-plugin");
        assertThat(snapshot.getDisplayName()).isNull();

        StepVerifier.create(ConsumerPluginCaller.resolve(resolver))
            .assertNext(info -> {
                assertThat(info.isDetected()).isTrue();
                assertThat(info.getDetectionSource()).isEqualTo("stack-classloader");
                assertThat(info.getPluginName()).isEqualTo("consumer-plugin");
                assertThat(info.getDisplayName()).isEqualTo("Consumer Plugin");
                assertThat(info.getDescription()).isEqualTo("Calls AI Foundation in tests.");
                assertThat(info.getVersion()).isEqualTo("1.2.3");
                assertThat(info.getAuthorName()).isEqualTo("Halo");
                assertThat(info.getRepo()).isEqualTo("https://github.com/example/consumer-plugin");
            })
            .verifyComplete();
    }

    @Test
    void resolveCurrentCaller_returnsUndetectedWhenNoPluginWrapperAvailable() {
        var client = mock(ReactiveExtensionClient.class);
        var provider = mockPluginWrapperProvider(null);
        var resolver = new StackWalkingCallerPluginResolver(client, provider);

        StepVerifier.create(resolver.resolveCurrentCaller())
            .assertNext(info -> {
                assertThat(info.isDetected()).isFalse();
                assertThat(info.getDetectionSource()).isEqualTo("none");
                assertThat(info.getPluginName()).isNull();
            })
            .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PluginWrapper> mockPluginWrapperProvider(PluginWrapper pluginWrapper) {
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(pluginWrapper);
        return provider;
    }

    private Plugin callerPlugin() {
        var plugin = new Plugin();
        var metadata = new Metadata();
        metadata.setName("consumer-plugin");
        plugin.setMetadata(metadata);
        var spec = new Plugin.PluginSpec();
        spec.setDisplayName("Consumer Plugin");
        spec.setDescription("Calls AI Foundation in tests.");
        spec.setVersion("1.2.3");
        spec.setHomepage("https://example.com/consumer-plugin");
        spec.setRepo("https://github.com/example/consumer-plugin");
        spec.setIssues("https://github.com/example/consumer-plugin/issues");
        var author = new Plugin.PluginAuthor();
        author.setName("Halo");
        author.setWebsite("https://www.halo.run");
        spec.setAuthor(author);
        plugin.setSpec(spec);
        return plugin;
    }
}
