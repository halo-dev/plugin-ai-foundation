package run.halo.aifoundation.service.usage;

import java.time.Clock;

final class UsageStatisticsTestSupport {

    private UsageStatisticsTestSupport() {
    }

    static void rollupAndRetain(UsageStatisticsStore store, Clock clock) {
        while (store.rollupAndRetainBatch(clock)) {
            // Tests drain all batches synchronously; the service yields after each batch.
        }
    }
}
