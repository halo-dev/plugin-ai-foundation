package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.provider.support.ModelType;

class AuditedLanguageModelTest {

    private final CallerPluginAuditRecorder auditRecorder = mock(CallerPluginAuditRecorder.class);
    private final LanguageModel delegate = mock(LanguageModel.class);
    private final ModelCallContext context = new ModelCallContext(
        ModelType.LANGUAGE,
        "default-language",
        "openai-provider",
        "openai",
        "gpt-4"
    );
    private final AuditedLanguageModel model = new AuditedLanguageModel(delegate, context,
        auditRecorder);

    @Test
    void generateTextRecordsModelInvocation() {
        var result = Mono.just(GenerateTextResult.builder().text("ok").build());
        when(delegate.generateText("hello")).thenReturn(result);

        assertThat(model.generateText("hello")).isSameAs(result);

        verify(auditRecorder).recordModelInvocation(context, "language.generateText");
        verify(delegate).generateText("hello");
    }

    @Test
    void streamTextRecordsModelInvocationForUiMessageFlow() {
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var result = mock(StreamTextResult.class);
        when(delegate.streamText(request)).thenReturn(result);

        assertThat(model.streamText(request)).isSameAs(result);

        verify(auditRecorder).recordModelInvocation(context, "language.streamText");
        verify(delegate).streamText(request);
    }

    @Test
    void capabilitiesDoesNotRecordModelInvocation() {
        var capabilities = LanguageModelCapabilities.defaults();
        when(delegate.capabilities()).thenReturn(capabilities);

        assertThat(model.capabilities()).isSameAs(capabilities);

        verify(delegate).capabilities();
    }
}
