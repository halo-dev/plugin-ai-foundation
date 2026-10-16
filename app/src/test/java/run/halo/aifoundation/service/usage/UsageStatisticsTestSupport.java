package run.halo.aifoundation.service.usage;

import java.time.Clock;

final class UsageStatisticsTestSupport {

    private UsageStatisticsTestSupport() {
    }

    static void retain(UsageStatisticsStore store, Clock clock) {
        while (store.retainBatch(clock)) {
            // Tests drain all batches synchronously; the service yields after each batch.
        }
    }

    // White-box seam replacing the deleted test-only constructor: installs a query
    // repository subclass/mock so tests can observe or block reader connections.
    static SqliteUsageStatisticsStore storeWithQueries(UsageDatabasePaths paths,
        UsageStatisticsQueryRepository queries) {
        var store = new SqliteUsageStatisticsStore(paths);
        try {
            var field = SqliteUsageStatisticsStore.class.getDeclaredField("queries");
            field.setAccessible(true);
            field.set(store, queries);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to install test query repository", error);
        }
        return store;
    }
}
