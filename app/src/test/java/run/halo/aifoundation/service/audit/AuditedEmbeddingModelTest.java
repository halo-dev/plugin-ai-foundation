package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assemblesAllEmbeddingOverloadsOnceBeforeSubscription(boolean enabled) {
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        when(statistics.describeCall(org.mockito.ArgumentMatchers.eq(context), any(),
            org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(enabled ? descriptor : null);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        var executions = new AtomicInteger();
        var inputs = List.of("hello");
        var request = EmbeddingRequest.builder().inputs(inputs).build();
        var response = EmbeddingResponse.builder().build();
        var source = Mono.fromSupplier(() -> {
            executions.incrementAndGet();
            return response;
        });
        when(delegate.embed(inputs)).thenReturn(source);
        when(delegate.embed(request)).thenReturn(source);
        when(delegate.embedQuery("hello")).thenReturn(Mono.fromSupplier(() -> {
            executions.incrementAndGet();
            return new float[] {1f};
        }));

        var calls = List.of(model.embed(inputs), model.embed(request), model.embedQuery("hello"));

        verify(delegate).embed(inputs);
        verify(delegate).embed(request);
        verify(delegate).embedQuery("hello");
        assertThat(executions).hasValue(0);
        verify(statistics, never()).beginCall(any());
        calls.forEach(call -> {
            call.block();
            call.block();
        });
        assertThat(executions).hasValue(6);
        verify(delegate).embed(inputs);
        verify(delegate).embed(request);
        verify(delegate).embedQuery("hello");
        verify(statistics, times(enabled ? 6 : 0)).beginCall(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousDelegateFailureDoesNotCreateSession(boolean enabled) {
        when(statistics.describeCall(context, "embedding.embed", false, null))
            .thenReturn(enabled ? mock(UsageCallDescriptor.class) : null);
        var request = EmbeddingRequest.builder().inputs(List.of("hello")).build();
        var failure = new IllegalStateException("invalid request");
        when(delegate.embed(request)).thenThrow(failure);

        assertThatThrownBy(() -> model.embed(request)).isSameAs(failure);

        verify(statistics, never()).beginCall(any());
    }

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
