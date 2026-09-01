package run.halo.aifoundation.provider.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveProviderTestSupportTest {

    @Test
    void liveTestsRequireExplicitProviderOrAllOptIn() {
        assertThat(LiveProviderTestSupport.isEnabled("openai", Map.of())).isFalse();
        assertThat(LiveProviderTestSupport.isEnabled("openai",
            Map.of(LiveProviderTestSupport.ENABLED_ENV, "deepseek, openai"))).isTrue();
        assertThat(LiveProviderTestSupport.isEnabled("openai",
            Map.of(LiveProviderTestSupport.ENABLED_ENV, "all"))).isTrue();
        assertThat(LiveProviderTestSupport.isEnabled("openai",
            Map.of(LiveProviderTestSupport.ENABLED_ENV, "deepseek"))).isFalse();
    }

    @Test
    void secretValueNeverRendersCredential() {
        var value = new LiveProviderTestSupport.SecretValue("secret-value");

        assertThat(value.value()).isEqualTo("secret-value");
        assertThat(value).hasToString("[REDACTED]");
    }
}
