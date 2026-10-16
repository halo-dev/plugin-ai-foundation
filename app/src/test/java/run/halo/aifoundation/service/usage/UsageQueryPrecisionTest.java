package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static run.halo.aifoundation.service.usage.UsageStatisticsTestSupport.retain;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageStatus;

class UsageQueryPrecisionTest {

    @TempDir
    Path temp;

    @Test
    void historicalLocalDayAndPartialRangesStayExactAcrossAllAggregates() {
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp))) {
            store.initialize();
            var from = Instant.parse("2026-03-31T18:15:00Z");
            var to = Instant.parse("2026-04-01T18:15:00Z");
            finish(store, from.minusMillis(1));
            finish(store, from);
            finish(store, to.minusMillis(1));
            finish(store, to);
            retain(store, Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC));
            var query = query(from, to, "Asia/Kathmandu", UsageTrendResolution.DAY);
            assertThat(store.summary(query, true).callCount()).isEqualTo(2);
            assertThat(store.summary(query, true).accountedTotalTokens()).isEqualTo(30);
            assertThat(store.breakdown(query, UsageBreakdownDimension.MODEL).items())
                .singleElement().satisfies(item -> assertThat(item.callCount()).isEqualTo(2));
            assertThat(store.trends(query, true, UsageBreakdownDimension.MODEL))
                .singleElement().satisfies(point -> {
                    assertThat(point.bucketStart()).isEqualTo(from);
                    assertThat(point.callCount()).isEqualTo(2);
                    assertThat(point.byDimension().get("model").callCount()).isEqualTo(2);
                });
            var partial = query(from.plusSeconds(1), to.minusSeconds(1), "Asia/Kathmandu",
                UsageTrendResolution.HOUR);
            assertThat(store.summary(partial, true).callCount()).isZero();
            assertThat(store.breakdown(partial, UsageBreakdownDimension.MODEL).items()).isEmpty();
            assertThat(store.trends(partial, true, null)).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"Asia/Kathmandu,2026-04-01T04:15:00Z", "Asia/Kolkata,2026-04-01T04:30:00Z"})
    void hourlyBucketsAlignWithFractionalOffsets(String zone, String boundary) {
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp))) {
            store.initialize();
            var at = Instant.parse(boundary);
            finish(store, at.minusMillis(1));
            finish(store, at);
            finish(store, at.plusSeconds(600));
            var query = query(at.minusSeconds(1), at.plusSeconds(601), zone,
                UsageTrendResolution.HOUR);
            assertThat(store.trends(query, true, null)).satisfiesExactly(
                point -> {
                    assertThat(point.bucketStart()).isEqualTo(at.minusSeconds(3600));
                    assertThat(point.callCount()).isEqualTo(1);
                },
                point -> {
                    assertThat(point.bucketStart()).isEqualTo(at);
                    assertThat(point.callCount()).isEqualTo(2);
                });
        }
    }

    @ParameterizedTest
    @CsvSource({"2026-03-08,23", "2026-11-01,25"})
    void dstDaysRetainEveryHourAndMergeIntoOneLocalDay(String date, int hours) {
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp))) {
            store.initialize();
            var zone = ZoneId.of("America/New_York");
            var day = LocalDate.parse(date);
            var from = day.atStartOfDay(zone).toInstant();
            var to = day.plusDays(1).atStartOfDay(zone).toInstant();
            for (var at = from; at.isBefore(to); at = at.plusSeconds(3600)) {
                finish(store, at.plusSeconds(60));
            }
            var hourly = query(from, to, zone.getId(), UsageTrendResolution.HOUR);
            var points = store.trends(hourly, true, null);
            assertThat(points).hasSize(hours);
            assertThat(points).extracting(UsageTrendPoint::bucketStart).doesNotHaveDuplicates();
            assertThat(points).allSatisfy(point -> assertThat(point.callCount()).isEqualTo(1));
            assertThat(points.getFirst().bucketStart()).isEqualTo(from);
            assertThat(points.getLast().bucketStart()).isEqualTo(to.minusSeconds(3600));
            var daily = query(from, to, zone.getId(), UsageTrendResolution.DAY);
            assertThat(store.trends(daily, true, UsageBreakdownDimension.MODEL))
                .singleElement().satisfies(point -> {
                    assertThat(point.bucketStart()).isEqualTo(from);
                    assertThat(point.callCount()).isEqualTo(hours);
                    assertThat(point.byDimension().get("model").callCount()).isEqualTo(hours);
                });
            assertThat(store.summary(daily, true).callCount()).isEqualTo(hours);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "2026-04-04T14:59:00Z,2026-04-04T15:01:00Z,2026-04-04T14:00:00Z,2026-04-04T15:00:00Z",
        "2026-10-03T15:29:00Z,2026-10-03T15:31:00Z,2026-10-03T14:30:00Z,2026-10-03T15:30:00Z"
    })
    void fractionalDstTransitionsKeepDistinctPartialHours(String before, String after,
        String firstBucket, String secondBucket) {
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp))) {
            store.initialize();
            var from = Instant.parse(before);
            var to = Instant.parse(after).plusSeconds(1);
            finish(store, from);
            finish(store, Instant.parse(after));
            assertThat(store.trends(query(from, to, "Australia/Lord_Howe",
                UsageTrendResolution.HOUR), true, null))
                .extracting(UsageTrendPoint::bucketStart)
                .containsExactly(Instant.parse(firstBucket), Instant.parse(secondBucket));
        }
    }

    @Test
    void partialUsageNeverCountsAsCompleteCoverageAfterRetention() {
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp))) {
            store.initialize();
            var at = Instant.parse("2026-04-01T10:00:00Z");
            finish(store, at, NormalizedUsage.sum(List.of(NormalizedUsage.missing(), usage())));
            var query = query(at, at.plusSeconds(1), "UTC", UsageTrendResolution.DAY);
            assertThat(store.summary(query, true).usageCoverage()).isZero();
            retain(store, Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC));
            var summary = store.summary(query, true);
            assertThat(summary.usageCoverage()).isZero();
            assertThat(summary.partialUsageCalls()).isEqualTo(1);
            assertThat(summary.accountedTotalTokens()).isEqualTo(15L);
        }
    }

    private static void finish(SqliteUsageStatisticsStore store, Instant at) {
        finish(store, at, usage());
    }

    private static NormalizedUsage usage() {
        return new NormalizedUsage(10L, 5L, null, null, null, null, null, null);
    }

    private static void finish(SqliteUsageStatisticsStore store, Instant at, NormalizedUsage usage) {
        var start = new UsageCallStart(at.toString(), 1, at, "plugin", "1", "test", null,
            "language.generateText", "LANGUAGE", "model", "provider", "openai", "gpt", false);
        store.finishCall(new UsageCallTerminal(start, start.startedAt().plusSeconds(1),
            UsageStatus.SUCCEEDED, null, "gpt", 1, 1, 0, true, usage));
    }

    private static UsageQuery query(Instant from, Instant to, String zone,
        UsageTrendResolution resolution) {
        return new UsageQuery(from, to, null, null, null, null, null, null, null, null,
            resolution, ZoneId.of(zone));
    }
}
