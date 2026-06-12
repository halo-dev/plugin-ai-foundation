package example.consumer;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;

public final class ConsumerPluginCaller {

    private ConsumerPluginCaller() {}

    public static Mono<CallerPluginInfo> resolve(CallerPluginResolver resolver) {
        return resolver.resolveCurrentCaller();
    }

    public static CallerPluginInfo resolveSnapshot(CallerPluginResolver resolver) {
        return resolver.resolveCurrentCallerSnapshot();
    }
}
