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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

class AuditedRerankingModelTest {

    private final RerankingModel delegate = mock(RerankingModel.class);
    private final ModelCallContext context = new ModelCallContext(
        ModelType.RERANK, "default-rerank", "provider", "openai", "rerank");
    private final UsageStatisticsService statistics = mock(UsageStatisticsService.class);
    private final AuditedRerankingModel model = new AuditedRerankingModel(delegate, context,
        mock(CallerPluginAuditRecorder.class), statistics);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assemblesBothOverloadsOnceBeforeSubscription(boolean enabled) {
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(context, "rerank.rerank", false, null))
            .thenReturn(enabled ? descriptor : null);
        when(statistics.beginCall(descriptor)).thenReturn(mock(UsageCallSession.class));
        var documents = List.of("hello");
        var request = RerankRequest.builder().query("hello").documents("hello").build();
        var executions = new AtomicInteger();
        var response = RerankResponse.builder().build();
        var source = Mono.fromSupplier(() -> {
            executions.incrementAndGet();
            return response;
        });
        when(delegate.rerank("hello", documents)).thenReturn(source);
        when(delegate.rerank(request)).thenReturn(source);

        var calls = List.of(model.rerank("hello", documents), model.rerank(request));

        verify(delegate).rerank("hello", documents);
        verify(delegate).rerank(request);
        assertThat(executions).hasValue(0);
        verify(statistics, never()).beginCall(any());
        calls.forEach(call -> {
            assertThat(call.block()).isSameAs(response);
            assertThat(call.block()).isSameAs(response);
        });
        assertThat(executions).hasValue(4);
        verify(delegate).rerank("hello", documents);
        verify(delegate).rerank(request);
        verify(statistics, times(enabled ? 4 : 0)).beginCall(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousDelegateFailureDoesNotCreateSession(boolean enabled) {
        when(statistics.describeCall(context, "rerank.rerank", false, null))
            .thenReturn(enabled ? mock(UsageCallDescriptor.class) : null);
        var request = RerankRequest.builder().query("hello").documents("hello").build();
        var failure = new IllegalStateException("invalid request");
        when(delegate.rerank(request)).thenThrow(failure);

        assertThatThrownBy(() -> model.rerank(request)).isSameAs(failure);

        verify(statistics, never()).beginCall(any());
    }
}
