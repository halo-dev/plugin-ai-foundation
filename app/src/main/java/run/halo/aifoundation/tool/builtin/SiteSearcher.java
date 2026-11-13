package run.halo.aifoundation.tool.builtin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.search.HaloDocument;
import run.halo.app.search.SearchOption;
import run.halo.app.search.SearchService;

/**
 * Searches Halo's index for publicly readable, published content.
 *
 * <p>Both search options and returned hits are filtered, including when another plugin supplies the search engine.
 * Drafts, private documents and recycled documents are excluded. The result is a bounded summary, not an editable
 * article or page resource.
 *
 * <pre>{@code
 * searcher.search(Map.of("query", "getting started", "limit", 5));
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class SiteSearcher {
    private final SearchService searchService;

    /**
     * Searches by topic or full-text keyword using Halo's configured search service.
     *
     * @param input required {@code query} and optional {@code limit} between 1 and 10
     * @return the query and public result titles, URLs, excerpts and content types
     */
    public Mono<Object> search(Map<String, Object> input) {
        return Mono.defer(() -> {
            var keyword = BuiltInToolInputs.requiredString(input, "query", 500);
            var limit = BuiltInToolInputs.limit(input);
            var option = new SearchOption();
            option.setKeyword(keyword);
            option.setLimit(limit);
            option.setFilterExposed(true);
            option.setFilterPublished(true);
            option.setFilterRecycled(false);
            option.setHighlightPreTag("");
            option.setHighlightPostTag("");
            return searchService
                    .search(option)
                    .map(result -> {
                        var hits = result.getHits() == null ? List.<HaloDocument>of() : result.getHits();
                        // A third-party SearchEngine must not bypass the public-content contract.
                        var results = hits.stream()
                                .filter(SiteSearcher::publicDocument)
                                .limit(limit)
                                .map(SiteSearcher::result)
                                .toList();
                        return (Object) Map.of("query", keyword, "results", results);
                    })
                    .timeout(Duration.ofSeconds(10));
        });
    }

    private static boolean publicDocument(HaloDocument document) {
        if (document == null || !document.isExposed()) {
            return false;
        }
        if (!document.isPublished()) {
            return false;
        }
        return !document.isRecycled();
    }

    private static Map<String, Object> result(HaloDocument document) {
        var description = document.getDescription();
        if (description == null || description.isBlank()) {
            description = document.getContent();
        }
        return Map.of(
                "title",
                plain(document.getTitle(), 300),
                "url",
                BuiltInToolInputs.truncate(document.getPermalink(), 2_048),
                "description",
                plain(description, 1_000),
                "type",
                BuiltInToolInputs.truncate(document.getType(), 200));
    }

    private static String plain(String value, int limit) {
        return BuiltInToolInputs.truncate(
                Jsoup.parse(BuiltInToolInputs.truncate(value, 12_000)).text(), limit);
    }
}
