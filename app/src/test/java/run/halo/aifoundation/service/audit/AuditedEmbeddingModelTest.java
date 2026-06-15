package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.provider.support.ModelType;

class AuditedEmbeddingModelTest {

    private final CallerPluginAuditRecorder auditRecorder = mock(CallerPluginAuditRecorder.class);
    private final EmbeddingModel delegate = mock(EmbeddingModel.class);
    private final ModelCallContext context = new ModelCallContext(
        ModelType.EMBEDDING,
        "default-embedding",
        "openai-provider",
        "openai",
        "text-embedding-3-small"
    );
    private final AuditedEmbeddingModel model = new AuditedEmbeddingModel(delegate, context,
        auditRecorder);

    @Test
    void embedRecordsModelInvocation() {
        var inputs = List.of("hello");
        var response = Mono.just(EmbeddingResponse.builder().build());
        when(delegate.embed(inputs)).thenReturn(response);

        assertThat(model.embed(inputs)).isSameAs(response);

        verify(auditRecorder).recordModelInvocation(context, "embedding.embed");
        verify(delegate).embed(inputs);
    }

    @Test
    void embedQueryRecordsModelInvocation() {
        var response = Mono.just(new float[] {1f});
        when(delegate.embedQuery("hello")).thenReturn(response);

        assertThat(model.embedQuery("hello")).isSameAs(response);

        verify(auditRecorder).recordModelInvocation(context, "embedding.embedQuery");
        verify(delegate).embedQuery("hello");
    }
}
