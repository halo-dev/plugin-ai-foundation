package run.halo.aifoundation.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.index.query.Queries;

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
