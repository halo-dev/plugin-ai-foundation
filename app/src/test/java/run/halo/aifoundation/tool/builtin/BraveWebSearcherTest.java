package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class BraveWebSearcherTest {
    @Test
    void searchesFixedBraveEndpointWithEncodedQueryAndBoundedResultCount() {
        var http = mock(PublicHttpClient.class);
        var body = """
            {"web":{"results":[{"title":"<b>Halo</b>","url":"https://halo.run/",
            "description":"Open &amp; published"},{"title":"Other","url":"https://example.com/"}]}}
            """.getBytes(StandardCharsets.UTF_8);
        when(http.get(any(), any(), anyInt(), any())).thenReturn(Mono.just(
            new PublicHttpClient.Response(body, MediaType.APPLICATION_JSON)));
        StepVerifier.create(new BraveWebSearcher(http).search(
                Map.of("query", "Halo & tools", "limit", 1), "test-api-key"))
            .assertNext(output -> {
                var results = (List<?>) ((Map<?, ?>) output).get("results");
                assertThat(results).hasSize(1);
                assertThat(((Map<?, ?>) results.getFirst()).get("title")).isEqualTo("Halo");
                assertThat(((Map<?, ?>) results.getFirst()).get("description"))
                    .isEqualTo("Open & published");
                assertThat(output.toString()).doesNotContain("test-api-key");
            }).verifyComplete();
        var uri = ArgumentCaptor.forClass(URI.class);
        verify(http).get(uri.capture(), org.mockito.ArgumentMatchers.eq(Map.of("Accept",
                "application/json", "X-Subscription-Token", "test-api-key")),
            org.mockito.ArgumentMatchers.eq(262_144),
            org.mockito.ArgumentMatchers.eq(List.of(MediaType.APPLICATION_JSON)));
        assertThat(uri.getValue().getHost()).isEqualTo("api.search.brave.com");
        assertThat(uri.getValue().getScheme()).isEqualTo("https");
        assertThat(uri.getValue().getRawQuery()).contains("q=Halo%20%26%20tools", "count=1");
    }

    @Test
    void failsWhenNoCredentialConfiguredInsteadOfFabricatingSearchResults() {
        StepVerifier.create(new BraveWebSearcher(mock(PublicHttpClient.class))
                .search(Map.of("query", "Halo"), ""))
            .expectErrorMessage("Configure the Brave Search API Key in AI Foundation plugin "
                + "settings before using web search")
            .verify();
    }

    @Test
    void rejectsFractionalAndOutOfRangeLimitsAtExecutorBoundary() {
        var searcher = new BraveWebSearcher(mock(PublicHttpClient.class));
        for (var limit : List.of(0, 11, 1.5, Double.NaN)) {
            StepVerifier.create(searcher.search(Map.of("query", "Halo", "limit", limit), "key"))
                .expectErrorMessage("limit must be an integer between 1 and 10")
                .verify();
        }
    }
}
