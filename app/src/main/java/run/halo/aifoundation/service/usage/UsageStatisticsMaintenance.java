package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

final class UsageStatisticsMaintenance {

    private static final int CALL_RETENTION_DAYS = 90;
    private static final int EXECUTION_RETENTION_DAYS = 30;

    static final int CALL_BATCH_SIZE = 256;
    static final int EXECUTION_BATCH_SIZE = 2048;

    boolean rollupAndRetainBatch(Connection connection, Clock clock) {
        var now = clock.instant();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        var callCutoff = today.minusDays(CALL_RETENTION_DAYS);
        var executionCutoff = today.minusDays(EXECUTION_RETENTION_DAYS);
        var restoreAutoCommit = true;
        try {
            connection.setAutoCommit(false);
            var selected = selectCallBatch(connection, callCutoff);
            // Bound child deletion too: one old call can have arbitrarily many attempts.
            var children = deleteCallExecutions(connection);
            if (children < EXECUTION_BATCH_SIZE) {
                for (var day : callRollupDays(connection, callCutoff)) {
                    rollupCallDay(connection, day);
                }
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM ai_calls WHERE id IN (SELECT id FROM usage_retention_batch)");
                }
            }
            putMeta(connection, "call_detail_start", callCutoff.toString());
            putMeta(connection, "execution_detail_start", executionCutoff.toString());
            var executions = deleteExpiredExecutions(connection, executionCutoff);
            connection.commit();
            return selected == CALL_BATCH_SIZE || children == EXECUTION_BATCH_SIZE
                || executions == EXECUTION_BATCH_SIZE;
        } catch (RuntimeException | SQLException error) {
            restoreAutoCommit = UsageSqliteTransactions.rollback(connection, error);
            throw new IllegalStateException("Failed to roll up and retain statistics", error);
        } catch (Error error) {
            restoreAutoCommit = UsageSqliteTransactions.rollback(connection, error);
            throw error;
        } finally {
            if (restoreAutoCommit) {
                setAutoCommit(connection, true);
            }
        }
    }

    private static List<LocalDate> callRollupDays(Connection connection,
        LocalDate rollupBefore) throws SQLException {
        var days = new ArrayList<LocalDate>();
        try (var statement = connection.prepareStatement("""
            SELECT DISTINCT date(started_at_ms / 1000, 'unixepoch') AS day
            FROM ai_calls
            WHERE id IN (SELECT id FROM usage_retention_batch) AND started_at_ms < ?
              AND status <> 'IN_PROGRESS'
            ORDER BY day
            """)) {
            statement.setLong(1,
                rollupBefore.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
            addDays(statement.executeQuery(), days);
        }
        return days;
    }

    private static void addDays(java.sql.ResultSet rows, List<LocalDate> days)
        throws SQLException {
        try (rows) {
            while (rows.next()) {
                days.add(LocalDate.parse(rows.getString("day")));
            }
        }
    }

    private static void rollupCallDay(Connection connection, LocalDate day)
        throws SQLException {
        var from = startOfDay(day);
        var to = startOfDay(day.plusDays(1));
        try (var statement = connection.prepareStatement("""
            INSERT INTO ai_usage_daily (
              day, caller_plugin_name, feature, provider_name, provider_type, model_name,
              model_type, operation, status, usage_quality, call_count, input_tokens,
              output_tokens, cache_read_input_tokens, cache_creation_input_tokens,
              reasoning_output_tokens, accounted_total_tokens, known_usage_calls,
              missing_usage_calls, duration_sum_ms, incomplete_call_count
            )
            SELECT ?, COALESCE(caller_plugin_name, ''), COALESCE(feature, ''),
              provider_name, provider_type, model_name, model_type, operation, status,
              usage_quality, COUNT(*), SUM(input_tokens), SUM(output_tokens),
              SUM(cache_read_input_tokens), SUM(cache_creation_input_tokens),
              SUM(reasoning_output_tokens), SUM(accounted_total_tokens),
              SUM(CASE WHEN usage_quality <> 'MISSING' THEN 1 ELSE 0 END),
              SUM(CASE WHEN usage_quality = 'MISSING' THEN 1 ELSE 0 END),
              COALESCE(SUM(duration_ms), 0), SUM(CASE WHEN complete = 0 THEN 1 ELSE 0 END)
            FROM ai_calls WHERE id IN (SELECT id FROM usage_retention_batch)
              AND started_at_ms >= ? AND started_at_ms < ?
              AND status <> 'IN_PROGRESS'
            GROUP BY COALESCE(caller_plugin_name, ''), COALESCE(feature, ''), provider_name,
              provider_type, model_name, model_type, operation, status, usage_quality
            ON CONFLICT(day, caller_plugin_name, feature, provider_name, provider_type,
              model_name, model_type, operation, status, usage_quality) DO UPDATE SET
              call_count = ai_usage_daily.call_count + excluded.call_count,
              known_usage_calls = ai_usage_daily.known_usage_calls + excluded.known_usage_calls,
              missing_usage_calls = ai_usage_daily.missing_usage_calls + excluded.missing_usage_calls,
              duration_sum_ms = ai_usage_daily.duration_sum_ms + excluded.duration_sum_ms,
              incomplete_call_count = ai_usage_daily.incomplete_call_count + excluded.incomplete_call_count,
              input_tokens = CASE
                WHEN ai_usage_daily.input_tokens IS NULL AND excluded.input_tokens IS NULL THEN NULL
                ELSE COALESCE(ai_usage_daily.input_tokens, 0) + COALESCE(excluded.input_tokens, 0) END,
              output_tokens = CASE
                WHEN ai_usage_daily.output_tokens IS NULL AND excluded.output_tokens IS NULL THEN NULL
                ELSE COALESCE(ai_usage_daily.output_tokens, 0) + COALESCE(excluded.output_tokens, 0) END,
              cache_read_input_tokens = CASE
                WHEN ai_usage_daily.cache_read_input_tokens IS NULL AND excluded.cache_read_input_tokens IS NULL THEN NULL
                ELSE COALESCE(ai_usage_daily.cache_read_input_tokens, 0) + COALESCE(excluded.cache_read_input_tokens, 0) END,
              cache_creation_input_tokens = CASE
                WHEN ai_usage_daily.cache_creation_input_tokens IS NULL AND excluded.cache_creation_input_tokens IS NULL THEN NULL
                ELSE COALESCE(ai_usage_daily.cache_creation_input_tokens, 0) + COALESCE(excluded.cache_creation_input_tokens, 0) END,
              reasoning_output_tokens = CASE
                WHEN ai_usage_daily.reasoning_output_tokens IS NULL AND excluded.reasoning_output_tokens IS NULL THEN NULL
                ELSE COALESCE(ai_usage_daily.reasoning_output_tokens, 0) + COALESCE(excluded.reasoning_output_tokens, 0) END,
              accounted_total_tokens = CASE
                WHEN ai_usage_daily.accounted_total_tokens IS NULL AND excluded.accounted_total_tokens IS NULL THEN NULL
                ELSE COALESCE(ai_usage_daily.accounted_total_tokens, 0) + COALESCE(excluded.accounted_total_tokens, 0) END
            """)) {
            statement.setString(1, day.toString());
            statement.setLong(2, from);
            statement.setLong(3, to);
            statement.executeUpdate();
        }
    }

    private static int selectCallBatch(Connection connection, LocalDate cutoff) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE IF NOT EXISTS usage_retention_batch(id TEXT PRIMARY KEY)");
            statement.executeUpdate("DELETE FROM usage_retention_batch");
        }
        try (var statement = connection.prepareStatement("""
            INSERT INTO usage_retention_batch
            SELECT id FROM ai_calls WHERE started_at_ms < ? AND status <> 'IN_PROGRESS'
            ORDER BY started_at_ms, id LIMIT ?
            """)) {
            statement.setLong(1, startOfDay(cutoff));
            statement.setInt(2, CALL_BATCH_SIZE);
            return statement.executeUpdate();
        }
    }

    private static int deleteCallExecutions(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
            DELETE FROM ai_model_executions WHERE rowid IN (
              SELECT rowid FROM ai_model_executions
              WHERE call_id IN (SELECT id FROM usage_retention_batch) LIMIT ?)
            """)) {
            statement.setInt(1, EXECUTION_BATCH_SIZE);
            return statement.executeUpdate();
        }
    }

    private static int deleteExpiredExecutions(Connection connection, LocalDate cutoff)
        throws SQLException {
        try (var statement = connection.prepareStatement("""
            DELETE FROM ai_model_executions WHERE rowid IN (
              SELECT e.rowid FROM ai_model_executions e WHERE e.started_at_ms < ?
                AND EXISTS (SELECT 1 FROM ai_calls c WHERE c.id = e.call_id
                  AND c.status <> 'IN_PROGRESS')
              ORDER BY e.started_at_ms LIMIT ?)
            """)) {
            statement.setLong(1, startOfDay(cutoff));
            statement.setInt(2, EXECUTION_BATCH_SIZE);
            return statement.executeUpdate();
        }
    }

    private static long startOfDay(LocalDate day) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static void putMeta(Connection connection, String key, String value)
        throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO ai_statistics_meta(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private static void setAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException ignored) {
            // The connection is no longer usable and the caller will surface the failure.
        }
    }
}
