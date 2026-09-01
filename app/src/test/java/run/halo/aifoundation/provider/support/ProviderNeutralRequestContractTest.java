package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.rerank.RerankRequest;

class ProviderNeutralRequestContractTest {

    @Test
    void publicRequestsDoNotExposeProviderSpecificOptions() {
        var requestTypes = List.of(
            GenerateTextRequest.class,
            EmbeddingRequest.class,
            RerankRequest.class,
            GenerateImageRequest.class);

        assertThat(requestTypes)
            .allSatisfy(type -> assertThat(type.getMethods())
                .extracting(Method::getName)
                .noneMatch(name -> name.toLowerCase().contains("provideroption")));
    }
}
