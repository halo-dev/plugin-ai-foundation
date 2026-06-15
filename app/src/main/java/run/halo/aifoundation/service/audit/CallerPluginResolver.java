package run.halo.aifoundation.service.audit;

import reactor.core.publisher.Mono;

public interface CallerPluginResolver {

    Mono<CallerPluginInfo> resolveCurrentCaller();

    CallerPluginInfo resolveCurrentCallerSnapshot();
}
