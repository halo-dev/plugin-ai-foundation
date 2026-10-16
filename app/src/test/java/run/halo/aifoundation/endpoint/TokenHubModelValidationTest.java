package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.mapping.ParameterMappingValidator;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.tokenhub.TokenHubProvider;
import run.halo.app.extension.ReactiveExtensionClient;

class TokenHubModelValidationTest {

    @Test
    void acceptsExplicitToolCapabilityWithoutEnablingItForEveryModel() {
        var client = mock(ReactiveExtensionClient.class);
        var cache = mock(ProviderClientCache.class);
        var provider = new AiProvider();
        var providerSpec = new AiProvider.AiProviderSpec();
        providerSpec.setProviderType("tokenhub");
        provider.setSpec(providerSpec);
        when(client.fetch(AiProvider.class, "tokenhub-test")).thenReturn(Mono.just(provider));
        when(cache.getProviderTypeMap()).thenReturn(Map.of("tokenhub", new TokenHubProvider()));
        var validator = new ModelConsoleModelValidator(client, cache,
            new ParameterMappingValidator(new ParameterMappingTemplateRegistry()));
        var model = new AiModel();
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("tokenhub-test");
        spec.setModelId("configured-model");
        spec.setModelType(ModelType.LANGUAGE);
        spec.setAdapterType(AdapterType.TOKENHUB_CHAT);
        model.setSpec(spec);

        StepVerifier.create(validator.validate(model)).verifyComplete();
        assertThat(spec.getFeatures()).isEmpty();

        spec.setFeatures(List.of(ModelFeature.TOOL_CALL));
        StepVerifier.create(validator.validate(model)).verifyComplete();
        assertThat(spec.getFeatures()).containsExactly(ModelFeature.TOOL_CALL);

        spec.setFeatures(List.of(ModelFeature.VISION));
        StepVerifier.create(validator.validate(model))
            .expectError(org.springframework.web.server.ResponseStatusException.class)
            .verify();
    }
}
