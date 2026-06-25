package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class ModelOptionConsoleEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final ModelOptionAssembler modelOptionAssembler;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/ModelOption";
        return route()
            .GET("model-options", this::listModelOptions,
                builder -> builder.operationId("ListModelOptions")
                    .description("List aggregated AI model options for selectors.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("modelType")
                        .in(ParameterIn.QUERY)
                        .description("Filter by model type, for example language")
                        .implementation(String.class))
                    .parameter(parameterBuilder()
                        .name("providerName")
                        .in(ParameterIn.QUERY)
                        .description("Filter by AiProvider.metadata.name")
                        .implementation(String.class))
                    .parameter(parameterBuilder()
                        .name("providerType")
                        .in(ParameterIn.QUERY)
                        .description("Filter by AiProvider.spec.providerType")
                        .implementation(String.class))
                    .parameter(parameterBuilder()
                        .name("enabled")
                        .in(ParameterIn.QUERY)
                        .description("Filter by AiModel.spec.enabled")
                        .implementation(Boolean.class))
                    .parameter(parameterBuilder()
                        .name("available")
                        .in(ParameterIn.QUERY)
                        .description("Filter by computed local availability")
                        .implementation(Boolean.class))
                    .parameter(parameterBuilder()
                        .name("requiredFeatures")
                        .in(ParameterIn.QUERY)
                        .description("Comma-separated all-of feature filter")
                        .implementation(String.class))
                    .parameter(parameterBuilder()
                        .name("requiredCapabilities")
                        .in(ParameterIn.QUERY)
                        .description("JSON all-of fine-grained capability filter")
                        .implementation(String.class))
                    .parameter(parameterBuilder()
                        .name("keyword")
                        .in(ParameterIn.QUERY)
                        .description("Case-insensitive keyword filter")
                        .implementation(String.class))
                    .response(responseBuilder().implementationArray(ModelOption.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listModelOptions(ServerRequest request) {
        var query = ModelOptionQuery.from(request);
        return Mono.zip(
                client.listAll(AiModel.class, query.modelListOptions(), Sort.unsorted())
                    .collectList(),
                client.listAll(AiProvider.class, new ListOptions(), Sort.unsorted())
                    .collectMap(provider -> provider.getMetadata().getName())
            )
            .map(tuple -> toOptions(tuple.getT1(), tuple.getT2(), query))
            .flatMap(options -> ServerResponse.ok().bodyValue(options));
    }

    private java.util.List<ModelOption> toOptions(java.util.List<AiModel> models,
        java.util.Map<String, AiProvider> providers, ModelOptionQuery query) {
        return modelOptionAssembler.toOptions(models, providers,
            providerClientCache.getProviderTypeMap(), query);
    }
}
