package run.halo.aifoundation.endpoint;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.service.capability.CapabilityMatchResult;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.capability.ModelCapabilityService;

@Component
final class ModelOptionAssembler {

    private final ModelCapabilityService capabilityService;
    private final ModelCapabilityMatcher capabilityMatcher;

    ModelOptionAssembler(ModelCapabilityService capabilityService,
        ModelCapabilityMatcher capabilityMatcher) {
        this.capabilityService = capabilityService;
        this.capabilityMatcher = capabilityMatcher;
    }

    List<ModelOption> toOptions(List<AiModel> models, Map<String, AiProvider> providers,
        Map<String, AiProviderType> providerTypes, ModelOptionQuery query) {
        return models.stream()
            .map(model -> toOption(model, providers.get(model.getSpec().getProviderName()),
                providerTypes, query))
            .filter(query::matches)
            .sorted(optionComparator())
            .toList();
    }

    private ModelOption toOption(AiModel model, AiProvider provider,
        Map<String, AiProviderType> providerTypes, ModelOptionQuery query) {
        var spec = model.getSpec();
        var providerType = providerType(provider, providerTypes);
        var capabilities = capabilityService.effectiveCapabilities(model, providerType);
        var capabilityMatch = capabilityMatcher.match(capabilities, query.requiredCapabilities());
        var unavailableReason = unavailableReason(model, provider, capabilityMatch);
        return ModelOption.builder()
            .name(model.getMetadata().getName())
            .modelId(spec.getModelId())
            .displayName(displayName(spec.getDisplayName(), spec.getModelId()))
            .modelType(spec.getModelType())
            .features(spec.getFeatures() == null ? List.of() : spec.getFeatures())
            .capabilities(capabilities)
            .capabilitySources(capabilities.getSources())
            .enabled(spec.isEnabled())
            .available(unavailableReason == null)
            .unavailableReason(unavailableReason)
            .unavailableDetails(unavailableDetails(capabilityMatch))
            .provider(toProviderSummary(spec.getProviderName(), provider, providerTypes))
            .build();
    }

    private ModelOptionProvider toProviderSummary(String referencedProviderName,
        AiProvider provider, Map<String, AiProviderType> providerTypes) {
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
        return unavailableReason(model, provider, CapabilityMatchResult.success());
    }

    private ModelOptionUnavailableReason unavailableReason(AiModel model, AiProvider provider,
        CapabilityMatchResult capabilityMatch) {
        if (!model.getSpec().isEnabled()) {
            return ModelOptionUnavailableReason.MODEL_DISABLED;
        }
        if (provider == null) {
            return ModelOptionUnavailableReason.PROVIDER_MISSING;
        }
        if (!provider.getSpec().isEnabled()) {
            return ModelOptionUnavailableReason.PROVIDER_DISABLED;
        }
        if (capabilityMatch != null && !capabilityMatch.matched()) {
            return ModelOptionUnavailableReason.CAPABILITY_UNSUPPORTED;
        }
        return null;
    }

    private List<ModelOptionUnavailableDetail> unavailableDetails(
        CapabilityMatchResult capabilityMatch) {
        if (capabilityMatch == null || capabilityMatch.matched()) {
            return List.of();
        }
        return capabilityMatch.issues().stream()
            .map(issue -> new ModelOptionUnavailableDetail(issue.path(), issue.expected(),
                issue.actual()))
            .toList();
    }

    private AiProviderType providerType(AiProvider provider, Map<String, AiProviderType> types) {
        if (provider == null || provider.getSpec() == null || types == null) {
            return null;
        }
        return types.get(provider.getSpec().getProviderType());
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
}
