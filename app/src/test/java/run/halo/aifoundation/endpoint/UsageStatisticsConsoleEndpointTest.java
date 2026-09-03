package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.usage.UsageCallPage;
import run.halo.aifoundation.service.usage.UsageHealth;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.aifoundation.service.usage.UsageSummary;
import run.halo.aifoundation.service.usage.UsageQuery;
import run.halo.aifoundation.service.usage.UsageStatus;
import run.halo.aifoundation.service.usage.UsageQuality;
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
    void acceptsHourlyTrendResolution() {
        when(service.trends(any())).thenReturn(Mono.just(java.util.List.of(
            new UsageTrendPoint(Instant.EPOCH, UsageTrendResolution.HOUR,
                1, 2L, 3L, 5L, 1, 0, false))));

        client.get().uri("/usage-statistics/trends?resolution=hour")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$[0].complete").isEqualTo(false);

        var query = ArgumentCaptor.forClass(UsageQuery.class);
        verify(service).trends(query.capture());
        assertThat(query.getValue().resolution()).isEqualTo(UsageTrendResolution.HOUR);
    }

    @Test
    void returnsSummaryAndCursorPage() {
        var summary = new UsageSummary(0, 0, 0, 0, 0, 0, 0, null, null, null, null, null,
            null, 0, 0, 1D, true, "MILLISECOND", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
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
        when(service.reset("RESET")).thenReturn(Mono.just(2L));

        client.post().uri("/usage-statistics/reset")
            .bodyValue("{\"confirmation\":\"RESET\"}")
            .header("Content-Type", "application/json")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.epoch").isEqualTo(2);
    }

    @Test
    void acceptsOffsetInstantsAcrossDaylightSavingBoundary() {
        var summary = new UsageSummary(0, 0, 0, 0, 0, 0, 0, null, null, null, null, null,
            null, 0, 0, 1D, true, "MILLISECOND", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        when(service.summary(any())).thenReturn(Mono.just(summary));

        client.get().uri("/usage-statistics/summary?from=2026-03-08T00:00:00-08:00"
                + "&to=2026-03-09T00:00:00-07:00")
            .exchange().expectStatus().isOk();
    }

    @Test
    void mapsEveryConfirmedFilterAndDisclosesDegradedDailySummary() {
        var summary = new UsageSummary(2, 0, 1, 1, 0, 0, 0, null, 5L, null, null, 2L,
            5L, 1, 1, 0.5D, false, "DAY", Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));
        when(service.summary(any())).thenReturn(Mono.just(summary));

        client.get().uri("/usage-statistics/summary?from=2026-01-01T00:00:00Z"
                + "&to=2026-01-02T00:00:00Z&callerPlugin=search&feature=rag"
                + "&providerName=provider&modelName=model&modelType=language"
                + "&operation=language.generateText&status=failed&usageQuality=partial")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.inputTokens").doesNotExist()
            .jsonPath("$.usageCoverage").isEqualTo(0.5)
            .jsonPath("$.complete").isEqualTo(false)
            .jsonPath("$.resolution").isEqualTo("DAY");

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
    }
}
