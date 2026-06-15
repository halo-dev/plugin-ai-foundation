package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginObservationRegistry;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CallerPluginConsoleEndpoint implements CustomEndpoint {

    private final CallerPluginObservationRegistry observationRegistry;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/CallerPlugin";
        return route()
            .GET("observed-caller-plugins", this::listObservedCallerPlugins,
                builder -> builder.operationId("ListObservedCallerPlugins")
                    .description("List caller plugins observed by AI Foundation since startup.")
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
        return ServerResponse.ok().bodyValue(observationRegistry.list());
    }
}
