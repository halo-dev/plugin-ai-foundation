package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

@Component
@RequiredArgsConstructor
public class ModelOptionConsoleEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;

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

    private List<ModelOption> toOptions(List<AiModel> models, Map<String, AiProvider> providers,
        ModelOptionQuery query) {
        var providerTypes = providerClientCache.getProviderTypeMap();
        return models.stream()
            .map(model -> toOption(model, providers.get(model.getSpec().getProviderName()),
                providerTypes))
            .filter(query::matches)
            .sorted(optionComparator())
            .toList();
    }

    private ModelOption toOption(AiModel model, AiProvider provider,
        Map<String, run.halo.aifoundation.provider.AiProviderType> providerTypes) {
        var spec = model.getSpec();
        var unavailableReason = unavailableReason(model, provider);
        return ModelOption.builder()
            .name(model.getMetadata().getName())
            .modelId(spec.getModelId())
            .displayName(displayName(spec.getDisplayName(), spec.getModelId()))
            .modelType(spec.getModelType())
            .features(spec.getFeatures() == null ? List.of() : spec.getFeatures())
            .enabled(spec.isEnabled())
            .available(unavailableReason == null)
            .unavailableReason(unavailableReason)
            .provider(toProviderSummary(spec.getProviderName(), provider, providerTypes))
            .build();
    }

    private ModelOptionProvider toProviderSummary(String referencedProviderName,
        AiProvider provider,
        Map<String, run.halo.aifoundation.provider.AiProviderType> providerTypes) {
        if (provider == null) {
            return ModelOptionProvider.builder()
                .name(referencedProviderName)
                .displayName(referencedProviderName)
                .enabled(false)
                .build();
        }
        var spec = provider.getSpec();
        var providerType = spec.getProviderType();
        var type = providerTypes.get(providerType);
        var status = provider.getStatus();
        return ModelOptionProvider.builder()
            .name(provider.getMetadata().getName())
            .displayName(displayName(spec.getDisplayName(), provider.getMetadata().getName()))
            .providerType(providerType)
            .providerTypeDisplayName(type == null ? providerType : type.getDisplayName())
            .iconUrl(type == null ? null : type.getIconUrl())
            .enabled(spec.isEnabled())
            .phase(status == null || status.getPhase() == null ? null : status.getPhase().name())
            .lastCheckedAt(status == null || status.getLastCheckedAt() == null
                ? null : status.getLastCheckedAt().toString())
            .build();
    }

    private ModelOptionUnavailableReason unavailableReason(AiModel model, AiProvider provider) {
        if (!model.getSpec().isEnabled()) {
            return ModelOptionUnavailableReason.MODEL_DISABLED;
        }
        if (provider == null) {
            return ModelOptionUnavailableReason.PROVIDER_MISSING;
        }
        if (!provider.getSpec().isEnabled()) {
            return ModelOptionUnavailableReason.PROVIDER_DISABLED;
        }
        return null;
    }

    private String displayName(String displayName, String fallback) {
        return StringUtils.hasText(displayName) ? displayName : fallback;
    }

    private Comparator<ModelOption> optionComparator() {
        return Comparator
            .comparing((ModelOption option) -> sortable(option.getProvider().getDisplayName()))
            .thenComparing(option -> sortable(option.getDisplayName()))
            .thenComparing(option -> sortable(option.getName()));
    }

    private String sortable(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    record ModelOptionQuery(
        ModelType modelType,
        String providerName,
        String providerType,
        Boolean enabled,
        Boolean available,
        List<ModelFeature> requiredFeatures,
        String keyword
    ) {

        static ModelOptionQuery from(ServerRequest request) {
            var modelType = parseModelType(single(request, "modelType", false));
            var providerName = single(request, "providerName", false);
            var providerType = single(request, "providerType", false);
            var enabled = parseBoolean(single(request, "enabled", false), "enabled");
            var available = parseBoolean(single(request, "available", false), "available");
            var requiredFeatures = parseFeatures(request);
            var keyword = single(request, "keyword", true);
            return new ModelOptionQuery(modelType, providerName, providerType, enabled,
                available, requiredFeatures, keyword);
        }

        ListOptions modelListOptions() {
            if (!StringUtils.hasText(providerName)) {
                return new ListOptions();
            }
            return ListOptions.builder()
                .fieldQuery(Queries.equal("spec.providerName", providerName))
                .build();
        }

        boolean matches(ModelOption option) {
            return matchesModelType(option)
                && matchesProviderName(option)
                && matchesProviderType(option)
                && matchesEnabled(option)
                && matchesAvailable(option)
                && matchesRequiredFeatures(option)
                && matchesKeyword(option);
        }

        private boolean matchesModelType(ModelOption option) {
            return modelType == null || option.getModelType() == modelType;
        }

        private boolean matchesProviderName(ModelOption option) {
            return !StringUtils.hasText(providerName)
                || providerName.equals(option.getProvider().getName());
        }

        private boolean matchesProviderType(ModelOption option) {
            return !StringUtils.hasText(providerType)
                || providerType.equals(option.getProvider().getProviderType());
        }

        private boolean matchesEnabled(ModelOption option) {
            return enabled == null || enabled == option.isEnabled();
        }

        private boolean matchesAvailable(ModelOption option) {
            return available == null || available == option.isAvailable();
        }

        private boolean matchesRequiredFeatures(ModelOption option) {
            return option.getFeatures().containsAll(requiredFeatures);
        }

        private boolean matchesKeyword(ModelOption option) {
            if (!StringUtils.hasText(keyword)) {
                return true;
            }
            var normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            return contains(option.getName(), normalizedKeyword)
                || contains(option.getModelId(), normalizedKeyword)
                || contains(option.getDisplayName(), normalizedKeyword)
                || contains(option.getProvider().getName(), normalizedKeyword)
                || contains(option.getProvider().getDisplayName(), normalizedKeyword)
                || contains(option.getProvider().getProviderType(), normalizedKeyword);
        }

        private static boolean contains(String value, String keyword) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
        }

        private static String single(ServerRequest request, String name, boolean allowBlank) {
            var values = request.queryParams().get(name);
            if (values == null || values.isEmpty()) {
                return null;
            }
            if (values.size() > 1) {
                throw badRequest("Query parameter '" + name + "' must be provided at most once");
            }
            var value = values.get(0);
            if (!StringUtils.hasText(value)) {
                if (allowBlank) {
                    return null;
                }
                throw badRequest("Query parameter '" + name + "' must not be blank");
            }
            return value.trim();
        }

        private static ModelType parseModelType(String value) {
            if (value == null) {
                return null;
            }
            return ModelType.find(value)
                .orElseThrow(() -> badRequest("Unsupported modelType: " + value));
        }

        private static Boolean parseBoolean(String value, String name) {
            if (value == null) {
                return null;
            }
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw badRequest("Query parameter '" + name + "' must be true or false");
        }

        private static List<ModelFeature> parseFeatures(ServerRequest request) {
            var values = request.queryParams().get("requiredFeatures");
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            var features = new ArrayList<ModelFeature>();
            for (var value : values) {
                if (!StringUtils.hasText(value)) {
                    throw badRequest("requiredFeatures must not be blank");
                }
                for (var part : value.split(",")) {
                    var feature = part.trim();
                    if (!StringUtils.hasText(feature)) {
                        throw badRequest("requiredFeatures contains a blank feature");
                    }
                    features.add(ModelFeature.find(feature)
                        .orElseThrow(() -> badRequest("Unsupported model feature: " + feature)));
                }
            }
            return List.copyOf(features);
        }

        private static ResponseStatusException badRequest(String message) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
