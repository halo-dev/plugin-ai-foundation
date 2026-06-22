package run.halo.aifoundation.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankResult;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.source.RetrievedContext;
import run.halo.aifoundation.source.RetrievedSource;
import org.junit.jupiter.api.Test;

class RagLanguageModelMiddlewareTest {

    @Test
    void generate_invokesRetrieverAndInjectsContextIntoPrompt() {
        var terminalRequest = new AtomicReference<GenerateTextRequest>();
        var model = LanguageModelMiddlewares.wrap(new FakeLanguageModel(terminalRequest),
            RagMiddlewares.rag(request -> Mono.just(RetrievedContext.builder()
                .query(request.getQuery())
                .sources(List.of(source("s1", "Halo is a CMS.")))
                .build())));

        StepVerifier.create(model.generateText("What is Halo?"))
            .assertNext(result -> assertThat(result.getSources())
                .extracting("id")
                .containsExactly("s1"))
            .verifyComplete();

        assertThat(terminalRequest.get().getPrompt())
            .contains("Halo is a CMS.")
            .contains("What is Halo?");
    }

    @Test
    void generate_emptyContextSkipsModelByDefault() {
        var calls = new AtomicInteger();
        var model = LanguageModelMiddlewares.wrap(new FakeLanguageModel(calls),
            RagMiddlewares.rag(request -> Mono.just(RetrievedContext.builder()
                .sources(List.of())
                .build())));

        StepVerifier.create(model.generateText("Unknown"))
            .assertNext(result -> assertThat(result.getText())
                .contains("could not find relevant context"))
            .verifyComplete();

        assertThat(calls).hasValue(0);
    }

    @Test
    void generate_retrievalFailureCanContinueWithWarning() {
        var calls = new AtomicInteger();
        var model = LanguageModelMiddlewares.wrap(new FakeLanguageModel(calls),
            RagMiddlewares.rag(RagMiddlewareOptions.defaults(request ->
                    Mono.error(new IllegalStateException("search down")))
                .toBuilder()
                .retrievalFailurePolicy(RagFailurePolicy.CONTINUE_WITHOUT_CONTEXT)
                .build()));

        StepVerifier.create(model.generateText("Hello"))
            .assertNext(result -> assertThat(result.getWarnings())
                .extracting("code")
                .contains("rag-retrieval-fallback-continue"))
            .verifyComplete();

        assertThat(calls).hasValue(1);
    }

    @Test
    void rerankingModelAdapterReturnsSourcesInRankedOrderAndPreservesMetadata() {
        RerankingModel rerankingModel = request -> Mono.just(RerankResponse.builder()
            .results(List.of(
                RerankResult.builder().index(1).score(0.9)
                    .providerMetadata(Map.of("rank", 1)).build(),
                RerankResult.builder().index(0).score(0.5).build()
            ))
            .build());
        var adapter = new RerankingModelRagSourceReranker(rerankingModel);

        StepVerifier.create(adapter.rerank(RagSourceRerankRequest.builder()
                .query("halo")
                .sources(List.of(source("s1", "one"), source("s2", "two")))
                .build()))
            .assertNext(sources -> assertThat(sources)
                .extracting(RetrievedSource::getId)
                .containsExactly("s2", "s1"))
            .verifyComplete();

        StepVerifier.create(adapter.rerank(RagSourceRerankRequest.builder()
                .query("halo")
                .sources(List.of(source("s1", "one"), RetrievedSource.builder()
                    .id("s2")
                    .content("two")
                    .score(0.42)
                    .metadata(Map.of("origin", "retriever"))
                    .build()))
                .build()))
            .assertNext(sources -> assertThat(sources.getFirst())
                .satisfies(source -> {
                    assertThat(source.getScore()).isEqualTo(0.42);
                    assertThat(source.getMetadata())
                        .containsEntry("origin", "retriever")
                        .containsEntry("rerankScore", 0.9)
                        .containsEntry("rerankProviderMetadata", Map.of("rank", 1));
                }))
            .verifyComplete();
    }

    @Test
    void rerankingModelAdapterFailsInvalidResultIndex() {
        RerankingModel rerankingModel = request -> Mono.just(RerankResponse.builder()
            .results(List.of(RerankResult.builder().index(9).score(0.9).build()))
            .build());
        var adapter = new RerankingModelRagSourceReranker(rerankingModel);

        StepVerifier.create(adapter.rerank(RagSourceRerankRequest.builder()
                .query("halo")
                .sources(List.of(source("s1", "one")))
                .build()))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside submitted sources"))
            .verify();
    }

    @Test
    void lifecycleReceivesRetrievalRerankAndContextEvents() {
        var stages = new ArrayList<String>();
        var lifecycle = new RagLifecycle() {
            @Override
            public Mono<Void> onRetrievalStart(RagLifecycleEvent event) {
                stages.add(event.getStage() + "-start");
                return Mono.empty();
            }

            @Override
            public Mono<Void> onRetrievalFinish(RagLifecycleEvent event) {
                stages.add(event.getStage() + "-finish");
                return Mono.empty();
            }

            @Override
            public Mono<Void> onRerankStart(RagLifecycleEvent event) {
                stages.add(event.getStage() + "-start");
                return Mono.empty();
            }

            @Override
            public Mono<Void> onRerankFinish(RagLifecycleEvent event) {
                stages.add(event.getStage() + "-finish");
                return Mono.empty();
            }

            @Override
            public Mono<Void> onContextPacked(RagLifecycleEvent event) {
                stages.add(event.getStage() + "-finish");
                return Mono.empty();
            }
        };
        var model = LanguageModelMiddlewares.wrap(new FakeLanguageModel(new AtomicInteger()),
            RagMiddlewares.rag(RagMiddlewareOptions.defaults(request -> Mono.just(
                    RetrievedContext.builder()
                        .sources(List.of(source("s1", "Halo context")))
                        .build()))
                .toBuilder()
                .reranker(request -> Mono.just(request.getSources()))
                .lifecycle(lifecycle)
                .build()));

        StepVerifier.create(model.generateText("Halo"))
            .expectNextCount(1)
            .verifyComplete();

        assertThat(stages).containsExactly("retrieval-start", "retrieval-finish",
            "rerank-start", "rerank-finish", "context-finish");
    }

    @Test
    void streamEmitsSourcesBeforeTextDeltas() {
        var model = LanguageModelMiddlewares.wrap(new FakeLanguageModel(new AtomicInteger()),
            RagMiddlewares.rag(request -> Mono.just(RetrievedContext.builder()
                .sources(List.of(source("s1", "Halo context")))
                .build())));

        StepVerifier.create(model.streamText(GenerateTextRequest.builder()
                    .messages(List.of(ModelMessage.user("Halo?")))
                    .build())
                .fullStream()
                .map(TextStreamPart::getType)
                .take(4))
            .expectNext(PartType.START, PartType.SOURCE, PartType.TEXT_START,
                PartType.TEXT_DELTA)
            .verifyComplete();
    }

    private RetrievedSource source(String id, String content) {
        return RetrievedSource.builder()
            .id(id)
            .title(id)
            .content(content)
            .visible(true)
            .usedForContext(true)
            .build();
    }

    private static class FakeLanguageModel implements LanguageModel {

        private final AtomicReference<GenerateTextRequest> requestRef;
        private final AtomicInteger calls;

        FakeLanguageModel(AtomicReference<GenerateTextRequest> requestRef) {
            this.requestRef = requestRef;
            this.calls = new AtomicInteger();
        }

        FakeLanguageModel(AtomicInteger calls) {
            this.requestRef = new AtomicReference<>();
            this.calls = calls;
        }

        @Override
        public Mono<GenerateTextResult> generateText(String prompt) {
            return generateText(GenerateTextRequest.builder().prompt(prompt).build());
        }

        @Override
        public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
            calls.incrementAndGet();
            requestRef.set(request);
            return Mono.just(GenerateTextResult.builder()
                .text("answer")
                .finishReason(FinishReason.STOP)
                .build());
        }

        @Override
        public StreamTextResult streamText(GenerateTextRequest request) {
            calls.incrementAndGet();
            requestRef.set(request);
            var full = Flux.just(
                TextStreamPart.start("msg"),
                TextStreamPart.textStart("txt"),
                TextStreamPart.textDelta("txt", "answer"),
                TextStreamPart.textEnd("txt"),
                TextStreamPart.finish(FinishReason.STOP, "stop", null)
            );
            return new StreamTextResult(full, Flux.just("answer"), Flux.empty(), Flux.empty(),
                Mono.empty(), Mono.just(GenerateTextResult.builder()
                    .text("answer")
                    .finishReason(FinishReason.STOP)
                    .build()));
        }
    }
}
