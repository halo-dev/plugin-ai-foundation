package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;

final class UsageStatisticsMaintenance {

    private static final int EXECUTION_RETENTION_DAYS = 30;
    static final int EXECUTION_BATCH_SIZE = 2048;

    boolean retainBatch(Connection connection, Clock clock) {
        var cutoff = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
            .minusDays(EXECUTION_RETENTION_DAYS).atStartOfDay().toInstant(ZoneOffset.UTC);
        // Calls remain as the permanent statistical facts. Keep unfinished executions for
        // lost-terminal recovery, and bound each deletion so the writer can serve new calls.
        try (var statement = connection.prepareStatement("""
            DELETE FROM ai_model_executions WHERE rowid IN (
              SELECT e.rowid FROM ai_model_executions e WHERE e.started_at_ms < ?
                AND EXISTS (SELECT 1 FROM ai_calls c WHERE c.id = e.call_id
                  AND c.status <> 'IN_PROGRESS')
              ORDER BY e.started_at_ms LIMIT ?)
            """)) {
            statement.setLong(1, cutoff.toEpochMilli());
            statement.setInt(2, EXECUTION_BATCH_SIZE);
            return statement.executeUpdate() == EXECUTION_BATCH_SIZE;
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to retain statistics", error);
        }
    }
}
