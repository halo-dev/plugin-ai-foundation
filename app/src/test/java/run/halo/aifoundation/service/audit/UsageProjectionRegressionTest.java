package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageObservation;

class UsageProjectionRegressionTest {

    @Test
    void cacheResultUsageStaysMissingRegardlessOfProjectionOrderWithoutExtraSubscriptions() {
        assertThat(run(false).usage()).isEqualTo(NormalizedUsage.missing());
        assertThat(run(true).usage()).isEqualTo(NormalizedUsage.missing());
    }

    private UsageCallTerminal run(boolean textFirst) {
        var sink = mock(UsageObservation.class);
        var start = new UsageCallStart("id", 1, Instant.now(), "plugin", "1", "stack", null,
            "language.streamText", "LANGUAGE", "m", "p", "openai", "gpt", true);
        var session = new UsageCallSession(sink, start, Clock.systemUTC());
        var observation = mock(UsageObservation.class);
        when(observation.beginCall(null)).thenReturn(session);
        var delegate = mock(LanguageModel.class);
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var result = GenerateTextResult.builder().text("cached answer")
            .totalUsage(LanguageModelUsage.builder().inputTokens(10).outputTokens(5)
                .totalTokens(15).build()).build();
        var resultSubscriptions = new AtomicInteger();
        when(delegate.streamText(request)).thenReturn(new StreamTextResult(Flux.empty(),
            Flux.just("cached answer"), Flux.empty(), Flux.empty(), Mono.empty(), Mono.defer(() -> {
                resultSubscriptions.incrementAndGet();
                return Mono.just(result);
            })));
        var audited = new AuditedLanguageModel(delegate,
            new ModelCallContext(ModelType.LANGUAGE, "m", "p", "openai", "gpt"),
            mock(CallerPluginAuditRecorder.class), observation);
        var stream = audited.streamText(request);
        if (textFirst) {
            stream.textStream().collectList().block();
            assertThat(resultSubscriptions.get()).isZero();
        }
        assertThat(stream.result().block()).isSameAs(result);
        assertThat(result.getTotalUsage().getTotalTokens()).isEqualTo(15);
        assertThat(resultSubscriptions.get()).isEqualTo(1);
        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(sink, times(textFirst ? 2 : 1)).finishCall(eq(session), terminal.capture());
        verify(observation).beginCall(null);
        var terminals = terminal.getAllValues();
        assertThat(terminals).extracting(UsageCallTerminal::callId).containsOnly("id");
        assertThat(terminals).extracting(UsageCallTerminal::completedAt)
            .containsOnly(terminals.getFirst().completedAt());
        assertThat(terminals).extracting(UsageCallTerminal::usage)
            .containsOnly(NormalizedUsage.missing());
        assertThat(terminals).extracting(UsageCallTerminal::attemptCount).containsOnly(0);
        assertThat(terminals).extracting(UsageCallTerminal::missingExecutionCount).containsOnly(0);
        assertThat(terminal.getValue().stepCount()).isEqualTo(1);
        return terminal.getValue();
    }
}
