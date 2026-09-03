package run.halo.aifoundation.provider.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProviderContractSourceTest {

    @Test
    void recordsOnlyOfficialDocumentationProvenance() {
        var fields = Arrays.stream(ProviderContractSource.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();

        assertThat(fields)
            .containsExactlyInAnyOrder("provider", "officialDocumentation", "retrievedAt");
    }

    @Test
    void remainsSourceOnlyMetadata() {
        var retention = ProviderContractSource.class.getAnnotation(Retention.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.SOURCE);
    }
}
