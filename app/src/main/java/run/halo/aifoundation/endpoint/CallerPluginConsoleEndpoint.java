package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginObservationRegistry;
import run.halo.aifoundation.service.usage.UsageCallerPlugin;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.app.core.extension.Plugin;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallerPluginConsoleEndpoint implements CustomEndpoint {

    private final UsageStatisticsService usageStatisticsService;
    private final CallerPluginObservationRegistry observationRegistry;
    private final ReactiveExtensionClient client;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/CallerPlugin";
        return route()
            .GET("observed-caller-plugins", this::listObservedCallerPlugins,
                builder -> builder.operationId("ListObservedCallerPlugins")
                    .description("List caller plugins recorded in usage statistics, enriched "
                        + "with plugin metadata.")
                    .tag(tag)
                    .response(responseBuilder().implementationArray(CallerPluginInfo.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listObservedCallerPlugins(ServerRequest request) {
        return usageStatisticsService.callerPlugins()
            .flatMapMany(Flux::fromIterable)
            .concatMap(this::describe)
            .collectList()
            .flatMap(callers -> ServerResponse.ok().bodyValue(callers))
            .onErrorResume(error -> {
                // Keep the endpoint usable when usage statistics are disabled.
                log.debug("Falling back to in-memory caller plugins.", error);
                return ServerResponse.ok().bodyValue(observationRegistry.list());
            });
    }

    private Mono<CallerPluginInfo> describe(UsageCallerPlugin caller) {
        var fallback = CallerPluginInfo.builder()
            .detected(true)
            .detectionSource(caller.detectionSource())
            .pluginName(caller.name())
            .version(caller.version())
            .build();
        return client.fetch(Plugin.class, caller.name())
            .map(plugin -> CallerPluginInfo.fromPlugin(plugin, caller.detectionSource()))
            .defaultIfEmpty(fallback)
            .onErrorResume(error -> {
                log.debug("Failed to load plugin {} metadata.", caller.name(), error);
                return Mono.just(fallback);
            });
    }
}
