package run.halo.aifoundation.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.index.query.Queries;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

record ModelOptionQuery(
    ModelType modelType,
    String providerName,
    String providerType,
    Boolean enabled,
    Boolean available,
    List<ModelFeature> requiredFeatures,
    ModelCapabilityRequirement requiredCapabilities,
    String keyword
) {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    static ModelOptionQuery from(ServerRequest request) {
        var modelType = parseModelType(single(request, "modelType", false));
        var providerName = single(request, "providerName", false);
        var providerType = single(request, "providerType", false);
        var enabled = parseBoolean(single(request, "enabled", false), "enabled");
        var available = parseBoolean(single(request, "available", false), "available");
        var requiredFeatures = parseFeatures(request);
        var requiredCapabilities = parseRequiredCapabilities(
            single(request, "requiredCapabilities", false));
        var keyword = single(request, "keyword", true);
        return new ModelOptionQuery(modelType, providerName, providerType, enabled,
            available, requiredFeatures, requiredCapabilities, keyword);
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
        if (available != null) {
            return available == option.isAvailable();
        }
        if (requiredCapabilities != null) {
            return option.isAvailable();
        }
        return true;
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

    private static ModelCapabilityRequirement parseRequiredCapabilities(String value) {
        if (value == null) {
            return null;
        }
        Map<?, ?> root;
        try {
            root = JSON_MAPPER.readValue(value, Map.class);
        } catch (JacksonException e) {
            throw badRequest("requiredCapabilities must be valid JSON");
        }
        rejectUnknownKeys(root, "requiredCapabilities", List.of("language", "imageGeneration"));
        return ModelCapabilityRequirement.builder()
            .language(parseLanguageCapability(asMap(root.get("language"), "language")))
            .imageGeneration(parseImageGenerationCapability(
                asMap(root.get("imageGeneration"), "imageGeneration")))
            .build();
    }

    private static LanguageCapability parseLanguageCapability(Map<?, ?> value) {
        if (value == null) {
            return null;
        }
        rejectUnknownKeys(value, "language", List.of("imageInput", "fileInput",
            "reasoningHistory", "inputMediaTypes", "inputSources"));
        return LanguageCapability.builder()
            .imageInput(booleanValue(value.get("imageInput"), "language.imageInput"))
            .fileInput(booleanValue(value.get("fileInput"), "language.fileInput"))
            .reasoningHistory(booleanValue(value.get("reasoningHistory"),
                "language.reasoningHistory"))
            .inputMediaTypes(mediaTypes(value.get("inputMediaTypes"),
                "language.inputMediaTypes"))
            .inputSources(inputSources(value.get("inputSources"), "language.inputSources"))
            .build();
    }

    private static ImageGenerationCapability parseImageGenerationCapability(Map<?, ?> value) {
        if (value == null) {
            return null;
        }
        rejectUnknownKeys(value, "imageGeneration", List.of("textToImage", "imageToImage",
            "maskInput", "maxImagesPerCall", "sizes", "aspectRatios", "outputMediaTypes"));
        return ImageGenerationCapability.builder()
            .textToImage(booleanValue(value.get("textToImage"),
                "imageGeneration.textToImage"))
            .imageToImage(booleanValue(value.get("imageToImage"),
                "imageGeneration.imageToImage"))
            .maskInput(booleanValue(value.get("maskInput"), "imageGeneration.maskInput"))
            .maxImagesPerCall(integerValue(value.get("maxImagesPerCall"),
                "imageGeneration.maxImagesPerCall"))
            .sizes(stringList(value.get("sizes"), "imageGeneration.sizes"))
            .aspectRatios(stringList(value.get("aspectRatios"),
                "imageGeneration.aspectRatios"))
            .outputMediaTypes(mediaTypes(value.get("outputMediaTypes"),
                "imageGeneration.outputMediaTypes"))
            .build();
    }

    private static Map<?, ?> asMap(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw badRequest(path + " must be an object");
    }

    private static Boolean booleanValue(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw badRequest(path + " must be a boolean");
    }

    private static Integer integerValue(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            var intValue = number.intValue();
            if (intValue < 1) {
                throw badRequest(path + " must be positive");
            }
            return intValue;
        }
        throw badRequest(path + " must be a number");
    }

    private static List<InputSource> inputSources(Object value, String path) {
        var values = stringList(value, path);
        if (values == null) {
            return null;
        }
        return values.stream()
            .map(source -> InputSource.find(source)
                .orElseThrow(() -> badRequest("Unsupported input source: " + source)))
            .toList();
    }

    private static List<String> mediaTypes(Object value, String path) {
        var values = stringList(value, path);
        if (values == null) {
            return null;
        }
        values.forEach(mediaType -> validateMediaTypePattern(mediaType, path));
        return values;
    }

    private static List<String> stringList(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw badRequest(path + " must be an array");
        }
        var values = new ArrayList<String>();
        for (var item : list) {
            if (!(item instanceof String text) || !StringUtils.hasText(text)) {
                throw badRequest(path + " must contain non-blank strings");
            }
            values.add(text.trim());
        }
        return List.copyOf(values);
    }

    private static void validateMediaTypePattern(String mediaType, String path) {
        var parts = mediaType.split("/", -1);
        if (parts.length != 2 || !StringUtils.hasText(parts[0])
            || !StringUtils.hasText(parts[1])) {
            throw badRequest(path + " contains invalid media type pattern: " + mediaType);
        }
        if (parts[0].contains("*") && !"*".equals(parts[0])) {
            throw badRequest(path + " contains invalid media type pattern: " + mediaType);
        }
        if (parts[1].contains("*") && !"*".equals(parts[1])) {
            throw badRequest(path + " contains invalid media type pattern: " + mediaType);
        }
    }

    private static void rejectUnknownKeys(Map<?, ?> value, String path, List<String> allowed) {
        for (var key : value.keySet()) {
            if (!(key instanceof String text) || !allowed.contains(text)) {
                throw badRequest(path + " contains unsupported key: " + key);
            }
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
