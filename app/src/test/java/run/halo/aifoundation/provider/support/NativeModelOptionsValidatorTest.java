package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeModelOptionsValidatorTest {

    @Test
    void acceptsProviderOwnedFields() {
        assertThatCode(() -> NativeModelOptionsValidator.validate(Map.of(
            "reasoning_effort", "high",
            "thinking", Map.of("type", "enabled"))))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvocationOwnedFields() {
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(Map.of("model", "other")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owned by the model invocation");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(Map.of("Model", "other")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owned by the model invocation");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
            Map.of("tool-choice", "required")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owned by the model invocation");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(Map.of("stream", true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owned by the model invocation");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
            Map.of("streamOptions", Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("owned by the model invocation");
    }

    @Test
    void rejectsBlankNamesAndNullValues() {
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(Map.of("", true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");
        var nullValue = new java.util.LinkedHashMap<String, Object>();
        nullValue.put("thinking", null);
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(nullValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null");
    }

    @Test
    void rejectsPlaintextCredentials() {
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
            Map.of("Authorization", "Bearer secret")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider Secret");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
            Map.of("transport", Map.of("access-token", "secret"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transport.access-token")
            .hasMessageContaining("provider Secret");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
            Map.of("x-api-key", "secret")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider Secret");
        assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
            Map.of("oauth", Map.of("client_secret", "secret"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("oauth.client_secret")
            .hasMessageContaining("provider Secret");
    }

    @Test
    void rejectsCredentialAliasesRegardlessOfCaseOrSeparatorStyle() {
        for (var name : java.util.List.of(
            "xApiKey", "accessToken", "bearerToken", "secretKey", "clientSecret",
            "privateKey", "X.API.KEY")) {
            assertThatThrownBy(() -> NativeModelOptionsValidator.validate(
                Map.of(name, "secret")))
                .as(name)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider Secret");
        }
    }
}
