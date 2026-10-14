package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.usage.UsageBreakdown;
import run.halo.aifoundation.service.usage.UsageBreakdownDimension;
import run.halo.aifoundation.service.usage.UsageBreakdownItem;
import run.halo.aifoundation.service.usage.UsageCallPage;
import run.halo.aifoundation.service.usage.UsageHealth;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.aifoundation.service.usage.UsageSummary;
import run.halo.aifoundation.service.usage.UsageQuery;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.usage.UsageTrendDimensionValue;
import run.halo.aifoundation.service.usage.UsageTrendPoint;
import run.halo.aifoundation.service.usage.UsageTrendResolution;
import org.mockito.ArgumentCaptor;

class UsageStatisticsConsoleEndpointTest {

    private UsageStatisticsService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(UsageStatisticsService.class);
        client = WebTestClient.bindToRouterFunction(
            new UsageStatisticsConsoleEndpoint(service).endpoint()).build();
    }

    @Test
    void validatesDateRangeAndPageSize() {
        client.get().uri("/usage-statistics/summary?from=invalid")
            .exchange().expectStatus().isBadRequest();
        client.get().uri("/usage-statistics/calls?size=201")
            .exchange().expectStatus().isBadRequest();
        client.get().uri("/usage-statistics/summary?feature=Invalid%20Feature")
            .exchange().expectStatus().isBadRequest();
        client.get().uri("/usage-statistics/trends?resolution=minute")
            .exchange().expectStatus().isBadRequest();
    }

    @Test
    void rejectsInvalidTimeZoneOnAllQueries() {
        for (var endpoint : java.util.List.of("summary", "trends", "breakdown?dimension=model")) {
            var separator = endpoint.contains("?") ? "&" : "?";
            client.get().uri("/usage-statistics/" + endpoint + separator + "timeZone=Unknown/Place")
                .exchange().expectStatus().isBadRequest();
        }
    }

    @Test
    void passesTheSameTimeZoneAndRangeToAllAggregates() {
        when(service.summary(any())).thenReturn(Mono.just(new UsageSummary(0, 0, 0, 0,
            null, null, null, null, null, null, 0, 0, 1D, true)));
        when(service.trends(any(), nullable(UsageBreakdownDimension.class)))
            .thenReturn(Mono.just(java.util.List.of()));
        when(service.breakdown(any(), eq(UsageBreakdownDimension.MODEL)))
            .thenReturn(Mono.just(new UsageBreakdown(java.util.List.of())));
        var filters = "from=2026-03-31T18:15:00Z&to=2026-04-01T18:15:00Z"
            + "&timeZone=Asia/Kathmandu";
        client.get().uri("/usage-statistics/summary?" + filters)
            .exchange().expectStatus().isOk();
        client.get().uri("/usage-statistics/trends?" + filters)
            .exchange().expectStatus().isOk();
        client.get().uri("/usage-statistics/breakdown?dimension=model&" + filters)
            .exchange().expectStatus().isOk();
        var queries = ArgumentCaptor.forClass(UsageQuery.class);
        verify(service).summary(queries.capture());
        verify(service).trends(queries.capture(), nullable(UsageBreakdownDimension.class));
        verify(service).breakdown(queries.capture(), eq(UsageBreakdownDimension.MODEL));
        assertThat(queries.getAllValues()).allSatisfy(query -> {
            assertThat(query.from()).isEqualTo(Instant.parse("2026-03-31T18:15:00Z"));
            assertThat(query.to()).isEqualTo(Instant.parse("2026-04-01T18:15:00Z"));
            assertThat(query.timeZone()).isEqualTo(java.time.ZoneId.of("Asia/Kathmandu"));
        });
    }

    @Test
    void acceptsHourlyTrendResolution() {
        when(service.trends(any(), nullable(UsageBreakdownDimension.class)))
            .thenReturn(Mono.just(java.util.List.of(
                new UsageTrendPoint(Instant.EPOCH, UsageTrendResolution.HOUR,
                    1, 2L, 3L, 5L, 0, 0, false, null))));

        client.get().uri("/usage-statistics/trends?resolution=hour")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$[0].complete").isEqualTo(false)
            .jsonPath("$[0].byDimension").doesNotExist();

        var query = ArgumentCaptor.forClass(UsageQuery.class);
        verify(service).trends(query.capture(), nullable(UsageBreakdownDimension.class));
        assertThat(query.getValue().resolution()).isEqualTo(UsageTrendResolution.HOUR);
        assertThat(query.getValue().timeZone()).isEqualTo(java.time.ZoneId.of("UTC"));
    }

    @Test
    void passesTrendDimensionAndRejectsUnknownDimension() {
        when(service.trends(any(), eq(UsageBreakdownDimension.MODEL)))
            .thenReturn(Mono.just(java.util.List.of(
                new UsageTrendPoint(Instant.EPOCH, UsageTrendResolution.DAY,
                    1, 2L, 3L, 5L, 0, 0, true,
                    java.util.Map.of("m1", new UsageTrendDimensionValue(1, 5L))))));

        client.get().uri("/usage-statistics/trends?dimension=model")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$[0].byDimension.m1.callCount").isEqualTo(1);

        verify(service).trends(any(), eq(UsageBreakdownDimension.MODEL));

        client.get().uri("/usage-statistics/trends?dimension=bogus")
            .exchange().expectStatus().isBadRequest();
    }

    @Test
    void breakdownRequiresDimensionAndMapsFilters() {
        var breakdown = new UsageBreakdown(
            java.util.List.of(new UsageBreakdownItem("search", 3, 1, 9L)));
        when(service.breakdown(any(), eq(UsageBreakdownDimension.CALLER_PLUGIN)))
            .thenReturn(Mono.just(breakdown));

        client.get().uri("/usage-statistics/breakdown")
            .exchange().expectStatus().isBadRequest();
        client.get().uri("/usage-statistics/breakdown?dimension=bogus")
            .exchange().expectStatus().isBadRequest();

        client.get().uri("/usage-statistics/breakdown?dimension=callerPlugin"
                + "&from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z&providerName=provider")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.dimension").doesNotExist()
            .jsonPath("$.items[0].value").isEqualTo("search")
            .jsonPath("$.items[0].failedCount").isEqualTo(1)
            .jsonPath("$.preciseRange").doesNotExist()
            .jsonPath("$.dataTo").doesNotExist();

        var query = ArgumentCaptor.forClass(UsageQuery.class);
        verify(service).breakdown(query.capture(), eq(UsageBreakdownDimension.CALLER_PLUGIN));
        assertThat(query.getValue().providerName()).isEqualTo("provider");
    }

    @Test
    void returnsSummaryAndCursorPage() {
        var summary = new UsageSummary(0, 0, 0, 0, null, null, null, null, null,
            null, 0, 0, 1D, true);
        when(service.summary(any())).thenReturn(Mono.just(summary));
        when(service.listCalls(any(), eq(50), eq(null)))
            .thenReturn(Mono.just(new UsageCallPage(java.util.List.of(), null)));

        client.get().uri("/usage-statistics/summary?from=2026-08-01T00:00:00Z"
                + "&to=2026-08-02T00:00:00Z")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.callCount").isEqualTo(0);
        client.get().uri("/usage-statistics/calls")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.items").isArray();
    }

    @Test
    void defaultDateRangeRemainsStableAcrossCursorPages() {
        when(service.listCalls(any(), eq(1), eq(null)))
            .thenReturn(Mono.just(new UsageCallPage(java.util.List.of(), "next")));
        when(service.listCalls(any(), eq(1), eq("next")))
            .thenReturn(Mono.just(new UsageCallPage(java.util.List.of(), null)));

        client.get().uri("/usage-statistics/calls?size=1")
            .exchange().expectStatus().isOk();
        client.get().uri("/usage-statistics/calls?size=1&cursor=next")
            .exchange().expectStatus().isOk();

        var query = ArgumentCaptor.forClass(UsageQuery.class);
        verify(service).listCalls(query.capture(), eq(1), eq(null));
        verify(service).listCalls(query.capture(), eq(1), eq("next"));
        assertThat(query.getAllValues().get(0)).isEqualTo(query.getAllValues().get(1));
    }

    @Test
    void returnsNotFoundAndHealth() {
        when(service.getCall("missing")).thenReturn(Mono.just(Optional.empty()));
        when(service.health()).thenReturn(new UsageHealth(true, true, 0, 0, 0, 0,
            null, null, null, null, null));

        client.get().uri("/usage-statistics/calls/missing")
            .exchange().expectStatus().isNotFound();
        client.get().uri("/usage-statistics/health")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.available").isEqualTo(true);
    }

    @Test
    void returnsServiceUnavailableWhenStatisticsStorageCannotBeRead() {
        when(service.summary(any()))
            .thenReturn(Mono.error(new IllegalStateException("statistics unavailable")));
        when(service.getCall("call"))
            .thenReturn(Mono.error(new IllegalStateException("statistics unavailable")));

        client.get().uri("/usage-statistics/summary")
            .exchange().expectStatus().isEqualTo(503);
        client.get().uri("/usage-statistics/calls/call")
            .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void resetRequiresExactConfirmation() {
        when(service.reset("RESET")).thenReturn(Mono.empty());

        client.post().uri("/usage-statistics/reset")
            .bodyValue("{\"confirmation\":\"RESET\"}")
            .header("Content-Type", "application/json")
            .exchange().expectStatus().isOk().expectBody().isEmpty();
    }

    @Test
    void acceptsOffsetInstantsAcrossDaylightSavingBoundary() {
        var summary = new UsageSummary(0, 0, 0, 0, null, null, null, null, null,
            null, 0, 0, 1D, true);
        when(service.summary(any())).thenReturn(Mono.just(summary));

        client.get().uri("/usage-statistics/summary?from=2026-03-08T00:00:00-08:00"
                + "&to=2026-03-09T00:00:00-07:00")
            .exchange().expectStatus().isOk();
    }

    @Test
    void mapsEveryConfirmedFilterAndDisclosesIncompleteSummary() {
        var summary = new UsageSummary(2, 0, 1, 1, null, 5L, null, null, 2L,
            5L, 1, 1, 0D, false);
        when(service.summary(any())).thenReturn(Mono.just(summary));

        client.get().uri("/usage-statistics/summary?from=2026-01-01T00:00:00Z"
                + "&to=2026-01-02T00:00:00Z&callerPlugin=search&feature=rag"
                + "&providerName=provider&modelName=model&modelType=language"
                + "&operation=language.generateText&status=failed&usageQuality=partial"
                + "&timeZone=Asia/Kathmandu")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.inputTokens").doesNotExist()
            .jsonPath("$.usageCoverage").isEqualTo(0)
            .jsonPath("$.complete").isEqualTo(false)
            .jsonPath("$.preciseRange").doesNotExist()
            .jsonPath("$.dataFrom").doesNotExist()
            .jsonPath("$.dataTo").doesNotExist();

        var query = ArgumentCaptor.forClass(UsageQuery.class);
        verify(service).summary(query.capture());
        assertThat(query.getValue().callerPlugin()).isEqualTo("search");
        assertThat(query.getValue().feature()).isEqualTo("rag");
        assertThat(query.getValue().providerName()).isEqualTo("provider");
        assertThat(query.getValue().modelName()).isEqualTo("model");
        assertThat(query.getValue().modelType()).isEqualTo("language");
        assertThat(query.getValue().operation()).isEqualTo("language.generateText");
        assertThat(query.getValue().status()).isEqualTo(UsageStatus.FAILED);
        assertThat(query.getValue().usageQuality()).isEqualTo(UsageQuality.PARTIAL);
        assertThat(query.getValue().timeZone()).isEqualTo(java.time.ZoneId.of("Asia/Kathmandu"));
    }
}
