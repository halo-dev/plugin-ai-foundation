package run.halo.aifoundation.service.audit;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import java.util.IdentityHashMap;
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
        return detectCallerPluginName()
            .map(pluginName -> CallerPluginInfo.builder()
                .detected(true)
                .detectionSource(DETECTION_SOURCE)
                .pluginName(pluginName)
                .build())
            .orElseGet(this::unknown);
    }

    private Optional<String> detectCallerPluginName() {
        var self = pluginWrapperProvider.getIfAvailable();
        if (self == null || self.getPluginManager() == null) {
            return Optional.empty();
        }

        var pluginByClassLoader = new IdentityHashMap<ClassLoader, String>();
        for (var plugin : self.getPluginManager().getStartedPlugins()) {
            if (plugin == null || Objects.equals(self.getPluginId(), plugin.getPluginId())) {
                continue;
            }
            var classLoader = plugin.getPluginClassLoader();
            if (classLoader != null) {
                pluginByClassLoader.put(classLoader, plugin.getPluginId());
            }
        }
        if (pluginByClassLoader.isEmpty()) {
            return Optional.empty();
        }

        return STACK_WALKER.walk(frames -> frames
            .map(StackWalker.StackFrame::getDeclaringClass)
            .filter(this::isCandidateCallerClass)
            .map(Class::getClassLoader)
            .map(pluginByClassLoader::get)
            .filter(Objects::nonNull)
            .findFirst());
    }

    private boolean isCandidateCallerClass(Class<?> type) {
        var name = type.getName();
        return !name.startsWith("java.")
            && !name.startsWith("jdk.")
            && !name.startsWith("reactor.")
            && !name.startsWith("org.springframework.")
            && !name.startsWith("org.pf4j.")
            && !name.startsWith("run.halo.aifoundation.");
    }

    private Mono<CallerPluginInfo> fetchCallerPlugin(String pluginName) {
        return client.fetch(Plugin.class, pluginName)
            .map(plugin -> fromPlugin(plugin, true))
            .defaultIfEmpty(CallerPluginInfo.builder()
                .detected(true)
                .detectionSource(DETECTION_SOURCE)
                .pluginName(pluginName)
                .build());
    }

    private CallerPluginInfo fromPlugin(Plugin plugin, boolean detected) {
        var spec = plugin.getSpec();
        var author = spec != null ? spec.getAuthor() : null;
        return CallerPluginInfo.builder()
            .detected(detected)
            .detectionSource(DETECTION_SOURCE)
            .pluginName(plugin.getMetadata() != null ? plugin.getMetadata().getName() : null)
            .displayName(spec != null ? spec.getDisplayName() : null)
            .description(spec != null ? spec.getDescription() : null)
            .version(spec != null ? spec.getVersion() : null)
            .authorName(author != null ? author.getName() : null)
            .authorWebsite(author != null ? author.getWebsite() : null)
            .homepage(spec != null ? spec.getHomepage() : null)
            .repo(spec != null ? spec.getRepo() : null)
            .issues(spec != null ? spec.getIssues() : null)
            .build();
    }

    private CallerPluginInfo unknown() {
        return CallerPluginInfo.builder()
            .detected(false)
            .detectionSource("none")
            .build();
    }
}
