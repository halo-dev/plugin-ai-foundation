package run.halo.aifoundation.service.audit;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class StackWalkingCallerPluginResolver implements CallerPluginResolver {

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
    private static final String DETECTION_SOURCE = "stack-classloader";
    private static final String CONSOLE_TEST_CLASS_NAME =
        "run.halo.aifoundation.endpoint.ModelConsoleEndpoint";
    private static final List<String> FRAMEWORK_PACKAGE_PREFIXES = List.of(
        "java.", "jdk.", "reactor.", "org.springframework.", "org.pf4j.",
        "run.halo.aifoundation.");

    private final ReactiveExtensionClient client;
    private final ObjectProvider<PluginWrapper> pluginWrapperProvider;

    @Override
    public Mono<CallerPluginInfo> resolveCurrentCaller() {
        var snapshot = resolveCurrentCallerSnapshot();
        if (!snapshot.isDetected() || snapshot.getPluginName() == null) {
            return Mono.just(snapshot);
        }
        return fetchCallerPlugin(snapshot.getPluginName());
    }

    @Override
    public CallerPluginInfo resolveCurrentCallerSnapshot() {
        return detectCallerPlugin()
            .map(plugin -> CallerPluginInfo.builder()
                .detected(true)
                .detectionSource(DETECTION_SOURCE)
                .pluginName(plugin.getPluginId())
                .version(plugin.getDescriptor().getVersion())
                .build())
            .orElseGet(this::unknown);
    }

    private Optional<PluginWrapper> detectCallerPlugin() {
        var self = pluginWrapperProvider.getIfAvailable();
        if (self == null || self.getPluginManager() == null) {
            return Optional.empty();
        }

        var pluginByClassLoader = new IdentityHashMap<ClassLoader, PluginWrapper>();
        for (var plugin : self.getPluginManager().getStartedPlugins()) {
            if (plugin == null || Objects.equals(self.getPluginId(), plugin.getPluginId())) {
                continue;
            }
            var classLoader = plugin.getPluginClassLoader();
            if (classLoader != null) {
                pluginByClassLoader.put(classLoader, plugin);
            }
        }
        return STACK_WALKER.walk(frames -> frames
            .map(StackWalker.StackFrame::getDeclaringClass)
            .map(type -> {
                // Only the Console test boundary may identify Foundation as its own caller.
                // Include nested classes for deferred RAG reranking invocations.
                if (type.getClassLoader() == self.getPluginClassLoader()
                    && (type.getName().equals(CONSOLE_TEST_CLASS_NAME)
                        || type.getName().startsWith(CONSOLE_TEST_CLASS_NAME + "$"))) {
                    return self;
                }
                return isCandidateCallerClass(type)
                    ? pluginByClassLoader.get(type.getClassLoader()) : null;
            })
            .filter(Objects::nonNull)
            .findFirst());
    }

    private boolean isCandidateCallerClass(Class<?> type) {
        var name = type.getName();
        for (var prefix : FRAMEWORK_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private Mono<CallerPluginInfo> fetchCallerPlugin(String pluginName) {
        return client.fetch(Plugin.class, pluginName)
            .map(this::fromPlugin)
            .defaultIfEmpty(CallerPluginInfo.builder()
                .detected(true)
                .detectionSource(DETECTION_SOURCE)
                .pluginName(pluginName)
                .build());
    }

    private CallerPluginInfo fromPlugin(Plugin plugin) {
        return CallerPluginInfo.fromPlugin(plugin, DETECTION_SOURCE);
    }

    private CallerPluginInfo unknown() {
        return CallerPluginInfo.builder()
            .detected(false)
            .detectionSource("none")
            .build();
    }
}
