package run.halo.aifoundation.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.TextStreamPart;

class SourceReferencesTest {

    @Test
    void mapsRetrievedSourceToDisplaySafeReferenceWithoutContent() {
        var retrieved = RetrievedSource.builder()
            .id("chunk-1")
            .sourceType("post")
            .title("Hello")
            .content("private context")
            .score(0.82)
            .metadata(Map.of("postName", "hello"))
            .build();

        var reference = SourceReferences.fromRetrievedSource(retrieved);

        assertThat(reference.getId()).isEqualTo("chunk-1");
        assertThat(reference.getSourceType()).isEqualTo("post");
        assertThat(reference.getTitle()).isEqualTo("Hello");
        assertThat(reference.getScore()).isEqualTo(0.82);
        assertThat(reference.getMetadata())
            .containsEntry("postName", "hello")
            .containsEntry("sourceType", "post")
            .containsEntry("score", 0.82);
        assertThat(reference.getMetadata()).doesNotContainKey("content");
    }

    @Test
    void mapsUrlReferenceToExistingContentAndUiParts() {
        var reference = SourceReference.builder()
            .id("src-1")
            .sourceType("url")
            .title("Docs")
            .url("https://example.com/docs")
            .score(0.9)
            .build();

        var content = SourceReferences.toContentPart(reference);
        var uiPart = SourceReferences.toSourceUrlPart(reference);

        assertThat(content.getType()).isEqualTo("source");
        assertThat(content.getId()).isEqualTo("src-1");
        assertThat(content.getUrl()).isEqualTo("https://example.com/docs");
        assertThat(content.getMetadata()).containsEntry("sourceType", "url");
        assertThat(uiPart.sourceId()).isEqualTo("src-1");
        assertThat(uiPart.url()).isEqualTo("https://example.com/docs");
    }

    @Test
    void resultAndStreamExposeSourcesFromContentParts() {
        var sourcePart = GenerationContentPart.source("src-1", "https://example.com", "Docs",
            Map.of("score", 0.7));
        var result = GenerateTextResult.builder()
            .content(List.of(sourcePart))
            .build();
        var stream = new StreamTextResult(
            Flux.just(TextStreamPart.source(sourcePart)),
            Flux.empty(),
            Flux.empty(),
            Flux.empty(),
            Mono.empty(),
            Mono.just(result)
        );

        assertThat(result.getSources()).singleElement()
            .satisfies(source -> {
                assertThat(source.getId()).isEqualTo("src-1");
                assertThat(source.getScore()).isEqualTo(0.7);
            });
        StepVerifier.create(stream.sources())
            .assertNext(sources -> assertThat(sources).singleElement()
                .satisfies(source -> assertThat(source.getTitle()).isEqualTo("Docs")))
            .verifyComplete();
    }
}
