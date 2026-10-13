package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class UsageSqliteSchema {

    static final int VERSION = 1;

    private UsageSqliteSchema() {
    }

    static void initialize(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        var restoreAutoCommit = true;
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE ai_statistics_meta (
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL
                ) STRICT
                """);
            createTables(statement);
            createHealthTable(statement);
            UsageSqliteJdbc.putMeta(connection, "schema_version", Integer.toString(VERSION));
            UsageSqliteJdbc.putMeta(connection, "statistics_epoch", "1");
            connection.commit();
        } catch (SQLException | RuntimeException | LinkageError error) {
            restoreAutoCommit = UsageSqliteTransactions.rollback(connection, error);
            throw error;
        } finally {
            if (restoreAutoCommit) {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void createHealthTable(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE ai_statistics_health (
              id INTEGER PRIMARY KEY CHECK (id = 1),
              affected_since_ms INTEGER, affected_until_ms INTEGER, last_write_error_at_ms INTEGER,
              dropped_events INTEGER NOT NULL DEFAULT 0,
              incomplete_calls INTEGER NOT NULL DEFAULT 0,
              write_failures INTEGER NOT NULL DEFAULT 0,
              initialization_error TEXT, integrity_error TEXT
            ) STRICT
            """);
        statement.execute("INSERT INTO ai_statistics_health(id) VALUES (1)");
    }

    private static void createTables(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE ai_calls (
              id TEXT PRIMARY KEY, epoch INTEGER NOT NULL, started_at_ms INTEGER NOT NULL,
              completed_at_ms INTEGER, duration_ms INTEGER,
              caller_plugin_name TEXT, caller_plugin_version TEXT,
              caller_detection_source TEXT NOT NULL, feature TEXT, operation TEXT NOT NULL,
              model_type TEXT NOT NULL, model_name TEXT NOT NULL, provider_name TEXT NOT NULL,
              provider_type TEXT NOT NULL, request_model_id TEXT NOT NULL,
              response_model_id TEXT, streaming INTEGER NOT NULL,
              status TEXT NOT NULL, error_type TEXT,
              step_count INTEGER NOT NULL DEFAULT 0,
              attempt_count INTEGER NOT NULL DEFAULT 0,
              missing_execution_count INTEGER NOT NULL DEFAULT 0,
              complete INTEGER NOT NULL DEFAULT 1,
              input_tokens INTEGER, output_tokens INTEGER,
              cache_read_input_tokens INTEGER, cache_creation_input_tokens INTEGER,
              reasoning_output_tokens INTEGER, provider_total_tokens INTEGER,
              accounted_total_tokens INTEGER, usage_quality TEXT NOT NULL DEFAULT 'MISSING'
            ) STRICT
            """);
        statement.execute("""
            CREATE TABLE ai_model_executions (
              id TEXT PRIMARY KEY, call_id TEXT NOT NULL, epoch INTEGER NOT NULL,
              unit_kind TEXT NOT NULL, unit_index INTEGER NOT NULL,
              attempt_index INTEGER NOT NULL, started_at_ms INTEGER NOT NULL,
              completed_at_ms INTEGER, status TEXT NOT NULL,
              error_type TEXT, request_model_id TEXT,
              response_model_id TEXT, input_tokens INTEGER, output_tokens INTEGER,
              cache_read_input_tokens INTEGER, cache_creation_input_tokens INTEGER,
              reasoning_output_tokens INTEGER, provider_total_tokens INTEGER,
              accounted_total_tokens INTEGER, usage_quality TEXT NOT NULL,
              FOREIGN KEY(call_id) REFERENCES ai_calls(id) ON DELETE CASCADE,
              UNIQUE(call_id, unit_kind, unit_index, attempt_index)
            ) STRICT
            """);
        statement.execute("""
            CREATE TABLE ai_usage_daily (
              day TEXT NOT NULL, caller_plugin_name TEXT NOT NULL,
              feature TEXT NOT NULL, provider_name TEXT NOT NULL,
              provider_type TEXT NOT NULL, model_name TEXT NOT NULL,
              model_type TEXT NOT NULL, operation TEXT NOT NULL,
              status TEXT NOT NULL, usage_quality TEXT NOT NULL,
              call_count INTEGER NOT NULL, input_tokens INTEGER,
              output_tokens INTEGER, cache_read_input_tokens INTEGER,
              cache_creation_input_tokens INTEGER, reasoning_output_tokens INTEGER,
              accounted_total_tokens INTEGER, known_usage_calls INTEGER NOT NULL,
              missing_usage_calls INTEGER NOT NULL,
              incomplete_call_count INTEGER NOT NULL,
              PRIMARY KEY(day, caller_plugin_name, feature, provider_name, provider_type,
                model_name, model_type, operation, status, usage_quality)
            ) STRICT
            """);
        statement.execute(
            "CREATE INDEX idx_calls_started ON ai_calls(started_at_ms DESC, id DESC)");
        statement.execute("""
            CREATE INDEX idx_calls_caller_started
            ON ai_calls(caller_plugin_name, started_at_ms DESC, id DESC)
            """);
        statement.execute("""
            CREATE INDEX idx_calls_model_started
            ON ai_calls(model_name, started_at_ms DESC, id DESC)
            """);
        statement.execute("""
            CREATE INDEX idx_executions_call_started
            ON ai_model_executions(call_id, started_at_ms, unit_index, attempt_index)
            """);
        statement.execute("CREATE INDEX idx_daily_day ON ai_usage_daily(day)");
        statement.execute("""
            CREATE INDEX idx_calls_filtered_hour ON ai_calls(
              (started_at_ms / 3600000), status, usage_quality, complete, started_at_ms, input_tokens,
              output_tokens, cache_read_input_tokens, cache_creation_input_tokens,
              reasoning_output_tokens, accounted_total_tokens, caller_plugin_name, feature,
              provider_name, model_name, model_type, operation
            )
            """);
        statement.execute("""
            CREATE INDEX idx_executions_retention
            ON ai_model_executions(started_at_ms, call_id)
            """);
        statement.execute("""
            CREATE INDEX idx_calls_retention
            ON ai_calls(started_at_ms, id) WHERE status <> 'IN_PROGRESS'
            """);
    }

    static void validateRecognized(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'");
            var rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SQLException("Missing statistics schema metadata");
            }
            if (!Integer.toString(VERSION).equals(rows.getString(1))) {
                throw new SQLException("Unsupported statistics schema version");
            }
        }
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("SELECT id FROM ai_statistics_health WHERE id = 1")) {
            if (!rows.next()) {
                throw new SQLException("SQLite statistics health row is missing");
            }
        }
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("PRAGMA quick_check")) {
            if (!rows.next() || !"ok".equalsIgnoreCase(rows.getString(1)) || rows.next()) {
                throw new UsageDatabaseIntegrityException("SQLite statistics quick_check failed");
            }
        }
    }
}
