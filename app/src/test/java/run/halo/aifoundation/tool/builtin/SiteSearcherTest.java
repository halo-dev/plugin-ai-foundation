package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.search.HaloDocument;
import run.halo.app.search.SearchOption;
import run.halo.app.search.SearchResult;
import run.halo.app.search.SearchService;

class SiteSearcherTest {
    @Test
    void passesPublicFiltersAndDefensivelyRemovesPrivateDraftAndRecycledResults() {
        var service = mock(SearchService.class);
        var result = new SearchResult();
        result.setHits(List.of(document("Public", true, true, false),
            document("Private", false, true, false), document("Draft", true, false, false),
            document("Recycled", true, true, true)));
        when(service.search(any())).thenReturn(Mono.just(result));
        StepVerifier.create(new SiteSearcher(service).search(Map.of("query", "Halo", "limit", 3)))
            .assertNext(output -> {
                var results = (List<?>) ((Map<?, ?>) output).get("results");
                assertThat(results).hasSize(1);
                assertThat(((Map<?, ?>) results.getFirst()).get("title")).isEqualTo("Public");
                assertThat(output.toString()).doesNotContain("Private", "Draft", "Recycled");
            }).verifyComplete();
        var captor = ArgumentCaptor.forClass(SearchOption.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue().getFilterExposed()).isTrue();
        assertThat(captor.getValue().getFilterPublished()).isTrue();
        assertThat(captor.getValue().getFilterRecycled()).isFalse();
        assertThat(captor.getValue().getLimit()).isEqualTo(3);
    }

    @Test
    void returnsEmptyResultsWhenIndexHasNoHits() {
        var service = mock(SearchService.class);
        when(service.search(any())).thenReturn(Mono.just(new SearchResult()));
        StepVerifier.create(new SiteSearcher(service).search(Map.of("query", "missing")))
            .assertNext(output -> assertThat((List<?>) ((Map<?, ?>) output).get("results")).isEmpty())
            .verifyComplete();
    }

    private static HaloDocument document(String title, boolean exposed, boolean published,
        boolean recycled) {
        var document = new HaloDocument();
        document.setTitle(title);
        document.setDescription("<b>Excerpt</b>");
        document.setPermalink("https://example.com/" + title);
        document.setType("post.content.halo.run");
        document.setExposed(exposed);
        document.setPublished(published);
        document.setRecycled(recycled);
        return document;
    }
}
