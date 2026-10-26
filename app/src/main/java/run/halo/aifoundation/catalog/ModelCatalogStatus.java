package run.halo.aifoundation.catalog;

import java.time.Instant;

public record ModelCatalogStatus(String sourceUrl, Instant fetchedAt, Instant lastAttemptAt,
                                 boolean available, boolean stale, boolean refreshing,
                                 String error) {
}
