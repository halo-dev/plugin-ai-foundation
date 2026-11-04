package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.gitee.GiteeProvider;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.mapping.ParameterMappingValidator;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.extension.ReactiveExtensionClient;

class ModelConsoleModelValidatorTest {

    private final GiteeProvider type = spy(new GiteeProvider());
    private ModelConsoleModelValidator validator;

    @BeforeEach
    void setup() {
        var client = mock(ReactiveExtensionClient.class);
        var cache = mock(ProviderClientCache.class);
        var provider = new AiProvider();
        var spec = new AiProvider.AiProviderSpec();
        var providerType = type.getProviderType();
        spec.setProviderType(providerType);
        provider.setSpec(spec);
        when(client.fetch(AiProvider.class, "provider")).thenReturn(Mono.just(provider));
        when(cache.getProviderTypeMap()).thenReturn(Map.of(providerType, type));
        validator = new ModelConsoleModelValidator(client, cache,
            new ParameterMappingValidator(new ParameterMappingTemplateRegistry()));
    }

    @ParameterizedTest
    @CsvSource({
        "TOOL_CALL, false", "TOOL_CALL, true",
        "STRUCTURED_OUTPUT, false", "STRUCTURED_OUTPUT, true",
        "REASONING, false", "REASONING, true"
    })
    void rejectsUnsupportedConstraintFeatures(ModelFeature feature, boolean catalog) {
        var model = model();
        var constraints = new ModelParameterConstraints();
        switch (feature) {
            case TOOL_CALL -> constraints.setToolCalling(true);
            case STRUCTURED_OUTPUT -> {
                constraints.setStructuredOutput(true);
                doReturn(List.of(ModelFeature.STREAMING)).when(type)
                    .getSupportedFeatures(AdapterType.GITEE_MESSAGES);
            }
            case REASONING -> {
                var reasoning = new ModelParameterConstraints.Reasoning();
                reasoning.setSupported(true);
                constraints.setReasoning(reasoning);
            }
            default -> throw new IllegalArgumentException("Unexpected feature: " + feature);
        }
        setConstraints(model, constraints, catalog);

        StepVerifier.create(validator.validate(model))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ResponseStatusException.class);
                var response = (ResponseStatusException) error;
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getReason()).contains(feature.name(), "gitee-messages");
            })
            .verify();
    }

    @ParameterizedTest
    @CsvSource({
        "true, false", "true, true",
        "false, false", "false, true",
        ", false", ", true"
    })
    void preservesAllowedAndUndeclaredConstraints(Boolean supported, boolean catalog) {
        var model = model();
        if (Boolean.TRUE.equals(supported)) {
            model.getSpec().setAdapterType(AdapterType.GITEE_CHAT);
        }
        var constraints = new ModelParameterConstraints();
        constraints.setToolCalling(supported);
        constraints.setStructuredOutput(supported);
        constraints.setOutputTokens(4096L);
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setSupported(supported);
        reasoning.setBudgetMinimum(512L);
        reasoning.setBudgetMaximum(2048L);
        constraints.setReasoning(reasoning);
        setConstraints(model, constraints, catalog);

        StepVerifier.create(validator.validate(model)).verifyComplete();

        assertThat(constraints.getToolCalling()).isEqualTo(supported);
        assertThat(constraints.getStructuredOutput()).isEqualTo(supported);
        assertThat(constraints.getReasoning().getSupported()).isEqualTo(supported);
        assertThat(constraints.getOutputTokens()).isEqualTo(4096L);
        assertThat(constraints.getReasoning().getBudgetMinimum()).isEqualTo(512L);
        assertThat(constraints.getReasoning().getBudgetMaximum()).isEqualTo(2048L);
        assertThat(catalog ? model.getSpec().getCatalogParameters().getConstraints()
            : model.getSpec().getParameterConstraints()).isSameAs(constraints);
    }

    @Test
    void discardsMismatchedCatalogBeforeCheckingItsConstraints() {
        var model = model();
        var constraints = new ModelParameterConstraints();
        constraints.setToolCalling(true);
        setConstraints(model, constraints, true);
        model.getSpec().getCatalogParameters().setAdapterType(AdapterType.GITEE_CHAT);

        StepVerifier.create(validator.validate(model)).verifyComplete();

        assertThat(model.getSpec().getCatalogParameters()).isNull();
    }

    private AiModel model() {
        var model = new AiModel();
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("provider");
        spec.setModelId("test-model");
        spec.setModelType(ModelType.LANGUAGE);
        spec.setAdapterType(AdapterType.GITEE_MESSAGES);
        spec.setFeatures(List.of());
        model.setSpec(spec);
        return model;
    }

    private void setConstraints(AiModel model, ModelParameterConstraints constraints,
        boolean catalog) {
        if (!catalog) {
            model.getSpec().setParameterConstraints(constraints);
            return;
        }
        var profile = new ModelParameterProfile();
        profile.setProviderType(type.getProviderType());
        profile.setProviderBaseUrl(type.getDefaultBaseUrl());
        profile.setModelId(model.getSpec().getModelId());
        profile.setAdapterType(model.getSpec().getAdapterType());
        profile.setConstraints(constraints);
        model.getSpec().setCatalogParameters(profile);
    }
}