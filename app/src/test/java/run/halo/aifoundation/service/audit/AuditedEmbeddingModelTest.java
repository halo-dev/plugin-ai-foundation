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
import run.halo.aifoundation.service.usage.UsageCallDescriptor;
import run.halo.aifoundation.service.usage.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

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
    private final UsageStatisticsService statistics = mock(UsageStatisticsService.class);
    private final AuditedEmbeddingModel model = new AuditedEmbeddingModel(delegate, context,
        auditRecorder, statistics);

    @Test
    void embedRecordsModelInvocation() {
        var inputs = List.of("hello");
        var response = Mono.just(EmbeddingResponse.builder().build());
        when(delegate.embed(inputs)).thenReturn(response);
        when(statistics.describeCall(context, "embedding.embed", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(UsageCallSession.class));

        assertThat(model.embed(inputs).block()).isSameAs(response.block());

        verify(auditRecorder).recordModelInvocation(context, "embedding.embed");
        verify(delegate).embed(inputs);
    }

    @Test
    void embedQueryRecordsModelInvocation() {
        var response = Mono.just(new float[] {1f});
        when(delegate.embedQuery("hello")).thenReturn(response);
        when(statistics.describeCall(context, "embedding.embedQuery", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(UsageCallSession.class));

        assertThat(model.embedQuery("hello").block()).isSameAs(response.block());

        verify(auditRecorder).recordModelInvocation(context, "embedding.embedQuery");
        verify(delegate).embedQuery("hello");
    }
}
