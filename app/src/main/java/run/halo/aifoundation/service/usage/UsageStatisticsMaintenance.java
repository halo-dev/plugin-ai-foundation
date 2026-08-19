package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

final class UsageStatisticsMaintenance {

    private static final int CALL_RETENTION_DAYS = 90;
    private static final int EXECUTION_RETENTION_DAYS = 30;
    private static final Duration ROLLUP_SAFETY_DELAY = Duration.ofHours(1);

    void rollupAndRetain(Connection connection, Clock clock) {
        var now = clock.instant();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        var rollupBefore = now.minus(ROLLUP_SAFETY_DELAY)
            .atZone(ZoneOffset.UTC).toLocalDate();
        var callCutoff = today.minusDays(CALL_RETENTION_DAYS);
        var executionCutoff = today.minusDays(EXECUTION_RETENTION_DAYS);
        try {
            connection.setAutoCommit(false);
            var callDays = callRollupDays(connection, rollupBefore,
                "call_rollup_frozen_watermark");
            var tokenDays = tokenRollupDays(connection, rollupBefore,
                "rollup_frozen_watermark");
            for (var day : callDays) {
                rollupCallDay(connection, day);
            }
            for (var day : tokenDays) {
                rollupTokenDay(connection, day);
            }
            putMeta(connection, "rollup_watermark", rollupBefore.minusDays(1).toString());
            putMeta(connection, "call_rollup_frozen_watermark",
                callCutoff.minusDays(1).toString());
            putMeta(connection, "rollup_frozen_watermark",
                executionCutoff.minusDays(1).toString());
            putMeta(connection, "execution_detail_start", executionCutoff.toString());
            deleteExpiredDetails(connection, callCutoff, executionCutoff);
            connection.commit();
        } catch (SQLException error) {
            rollback(connection);
            throw new IllegalStateException("Failed to roll up and retain statistics", error);
        } finally {
            setAutoCommit(connection, true);
        }
    }

    private static List<LocalDate> callRollupDays(Connection connection,
        LocalDate rollupBefore, String frozenWatermarkKey) throws SQLException {
        var days = new ArrayList<LocalDate>();
        var frozenWatermark = stringMeta(connection, frozenWatermarkKey);
        try (var statement = connection.prepareStatement("""
            SELECT DISTINCT date(started_at_ms / 1000, 'unixepoch') AS day
            FROM ai_calls
            WHERE started_at_ms < ?
              AND (? IS NULL OR date(started_at_ms / 1000, 'unixepoch') > ?)
            ORDER BY day
            """)) {
            statement.setLong(1,
                rollupBefore.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
            statement.setString(2, frozenWatermark);
            statement.setString(3, frozenWatermark);
            addDays(statement.executeQuery(), days);
        }
        return days;
    }

    private static List<LocalDate> tokenRollupDays(Connection connection,
        LocalDate rollupBefore, String frozenWatermarkKey) throws SQLException {
        var days = new ArrayList<LocalDate>();
        var frozenWatermark = stringMeta(connection, frozenWatermarkKey);
        try (var statement = connection.prepareStatement("""
            SELECT DISTINCT day
            FROM (
              SELECT date(e.started_at_ms / 1000, 'unixepoch') AS day
              FROM ai_model_executions e
              WHERE e.started_at_ms < ?
              UNION
              SELECT date(c.started_at_ms / 1000, 'unixepoch') AS day
              FROM ai_calls c
              WHERE c.started_at_ms < ?
                AND NOT EXISTS (
                  SELECT 1 FROM ai_model_executions e WHERE e.call_id = c.id
                )
            ) token_days
            WHERE (? IS NULL OR day > ?)
            ORDER BY day
            """)) {
            var before = rollupBefore.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            statement.setLong(1, before);
            statement.setLong(2, before);
            statement.setString(3, frozenWatermark);
            statement.setString(4, frozenWatermark);
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
        try (var delete = connection.prepareStatement(
            "DELETE FROM ai_usage_daily WHERE day = ?")) {
            delete.setString(1, day.toString());
            delete.executeUpdate();
        }
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
            FROM ai_calls WHERE started_at_ms >= ? AND started_at_ms < ?
            GROUP BY COALESCE(caller_plugin_name, ''), COALESCE(feature, ''), provider_name,
              provider_type, model_name, model_type, operation, status, usage_quality
            """)) {
            statement.setString(1, day.toString());
            statement.setLong(2, from);
            statement.setLong(3, to);
            statement.executeUpdate();
        }
    }

    private static void rollupTokenDay(Connection connection, LocalDate day)
        throws SQLException {
        var from = startOfDay(day);
        var to = startOfDay(day.plusDays(1));
        try (var delete = connection.prepareStatement(
            "DELETE FROM ai_token_usage_daily WHERE day = ?")) {
            delete.setString(1, day.toString());
            delete.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
            INSERT INTO ai_token_usage_daily (
              day, caller_plugin_name, feature, provider_name, provider_type, model_name,
              model_type, operation, status, usage_quality, fact_count, input_tokens,
              output_tokens, cache_read_input_tokens, cache_creation_input_tokens,
              reasoning_output_tokens, accounted_total_tokens
            )
            SELECT day, caller_plugin_name, feature, provider_name, provider_type, model_name,
              model_type, operation, status, usage_quality, COUNT(*), SUM(input_tokens),
              SUM(output_tokens), SUM(cache_read_input_tokens),
              SUM(cache_creation_input_tokens), SUM(reasoning_output_tokens),
              SUM(accounted_total_tokens)
            FROM (
              SELECT ? day, COALESCE(c.caller_plugin_name, '') caller_plugin_name,
                COALESCE(c.feature, '') feature, c.provider_name, c.provider_type, c.model_name,
                c.model_type, c.operation, e.status, e.usage_quality, e.input_tokens,
                e.output_tokens, e.cache_read_input_tokens, e.cache_creation_input_tokens,
                e.reasoning_output_tokens, e.accounted_total_tokens
              FROM ai_model_executions e JOIN ai_calls c ON c.id = e.call_id
              WHERE e.started_at_ms >= ? AND e.started_at_ms < ?
              UNION ALL
              SELECT ? day, COALESCE(c.caller_plugin_name, ''), COALESCE(c.feature, ''),
                c.provider_name, c.provider_type, c.model_name, c.model_type, c.operation,
                c.status, c.usage_quality, c.input_tokens, c.output_tokens,
                c.cache_read_input_tokens, c.cache_creation_input_tokens,
                c.reasoning_output_tokens, c.accounted_total_tokens
              FROM ai_calls c WHERE c.started_at_ms >= ? AND c.started_at_ms < ?
                AND NOT EXISTS (
                  SELECT 1 FROM ai_model_executions e WHERE e.call_id = c.id
                )
            ) facts
            GROUP BY day, caller_plugin_name, feature, provider_name, provider_type, model_name,
              model_type, operation, status, usage_quality
            """)) {
            statement.setString(1, day.toString());
            statement.setLong(2, from);
            statement.setLong(3, to);
            statement.setString(4, day.toString());
            statement.setLong(5, from);
            statement.setLong(6, to);
            statement.executeUpdate();
        }
    }

    private static void deleteExpiredDetails(Connection connection, LocalDate callCutoff,
        LocalDate executionCutoff) throws SQLException {
        try (var deleteExecutions = connection.prepareStatement(
            "DELETE FROM ai_model_executions WHERE started_at_ms < ?");
            var deleteCalls = connection.prepareStatement(
                "DELETE FROM ai_calls WHERE started_at_ms < ?")) {
            deleteExecutions.setLong(1, startOfDay(executionCutoff));
            deleteExecutions.executeUpdate();
            deleteCalls.setLong(1, startOfDay(callCutoff));
            deleteCalls.executeUpdate();
        }
    }

    private static long startOfDay(LocalDate day) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String stringMeta(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
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

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original maintenance failure.
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
