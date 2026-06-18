package run.halo.aifoundation.chat.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.part.TextStreamPart;

class LanguageModelMiddlewaresTest {

    @Test
    void generateTextAppliesModelAndRequestMiddlewareInOrder() {
        var events = new ArrayList<String>();
        var model = new RecordingLanguageModel();
        var wrapped = LanguageModelMiddlewares.wrap(model,
            appendPrompt("model", events, "|model"));
        var request = GenerateTextRequest.builder()
            .prompt("hello")
            .middleware(appendPrompt("request", events, "|request"))
            .build();

        StepVerifier.create(wrapped.generateText(request))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("hello|model|request"))
            .verifyComplete();

        assertThat(events).containsExactly("model", "request");
        assertThat(model.capturedRequest.getMiddleware()).isNull();
    }

    @Test
    void applyRequestMiddlewareHonorsRequestMiddlewareWithoutExplicitWrap() {
        var events = new ArrayList<String>();
        var model = new RecordingLanguageModel();
        var request = GenerateTextRequest.builder()
            .prompt("hello")
            .middleware(appendPrompt("request", events, "|request"))
            .build();

        StepVerifier.create(LanguageModelMiddlewares.applyRequestMiddleware(model, request))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("hello|request"))
            .verifyComplete();

        assertThat(events).containsExactly("request");
        assertThat(model.capturedRequest.getMiddleware()).isNull();
    }

    @Test
    void streamTextSharesAsyncTransformedExecutionAcrossViews() {
        var model = new RecordingLanguageModel();
        var wrapped = LanguageModelMiddlewares.wrap(model,
            appendPrompt("model", new ArrayList<>(), "|model"));

        var stream = wrapped.streamText(GenerateTextRequest.builder().prompt("hello").build());

        StepVerifier.create(stream.textStream().collectList())
            .assertNext(text -> assertThat(text).containsExactly("hello|model"))
            .verifyComplete();
        StepVerifier.create(stream.result())
            .assertNext(result -> assertThat(result.getText()).isEqualTo("hello|model"))
            .verifyComplete();

        assertThat(model.streamCalls).hasValue(1);
        assertThat(model.streamSubscriptions).hasValue(1);
    }

    private static LanguageModelMiddleware appendPrompt(String name, List<String> events,
        String suffix) {
        return new LanguageModelMiddleware() {
            @Override
            public Mono<GenerateTextRequest> transformRequest(LanguageModelRequestContext context) {
                return Mono.fromSupplier(() -> {
                    events.add(name);
                    return GenerateTextRequest.builder()
                        .prompt(context.request().getPrompt() + suffix)
                        .build();
                });
            }
        };
    }

    private static final class RecordingLanguageModel implements LanguageModel {
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final AtomicInteger streamSubscriptions = new AtomicInteger();
        private GenerateTextRequest capturedRequest;

        @Override
        public Mono<GenerateTextResult> generateText(String prompt) {
            return generateText(GenerateTextRequest.builder().prompt(prompt).build());
        }

        @Override
        public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
            capturedRequest = request;
            return Mono.just(GenerateTextResult.builder()
                .text(request.getPrompt())
                .build());
        }

        @Override
        public StreamTextResult streamText(GenerateTextRequest request) {
            streamCalls.incrementAndGet();
            capturedRequest = request;
            var text = request.getPrompt();
            var fullStream = Flux.defer(() -> {
                streamSubscriptions.incrementAndGet();
                return Flux.just(
                    TextStreamPart.start("msg_test"),
                    TextStreamPart.textStart("text_0"),
                    TextStreamPart.textDelta("text_0", text),
                    TextStreamPart.textEnd("text_0"),
                    TextStreamPart.finish(null, null, null)
                );
            });
            return new StreamTextResult(
                fullStream,
                fullStream.filter(part -> "text-delta".equals(part.getType()))
                    .map(TextStreamPart::getDelta),
                Flux.empty(),
                Flux.empty(),
                Mono.empty(),
                Mono.just(GenerateTextResult.builder().text(text).build())
            );
        }
    }
}
