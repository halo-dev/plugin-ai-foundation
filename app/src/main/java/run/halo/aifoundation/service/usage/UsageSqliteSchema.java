package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class UsageSqliteSchema {

    static final int VERSION = 4;

    private UsageSqliteSchema() {
    }

    static void migrate(Connection connection, UsageDatabasePaths paths) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ai_statistics_meta (
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL
                ) STRICT
                """);
        }
        var version = (int) longMeta(connection, "schema_version", 0);
        if (version > VERSION) {
            throw new SQLException("Unsupported statistics schema version " + version);
        }
        if (version > 0 && version < VERSION) {
            UsageSqliteFiles.migrationBackup(connection, paths.migrationBackup());
        }
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            if (version < 1) {
                createVersionOne(statement);
                putMeta(connection, "schema_version", "1");
                putMeta(connection, "statistics_epoch", "1");
            }
            if (version < 2) {
                createVersionTwo(statement);
                putMeta(connection, "schema_version", "2");
            }
            if (version < 3) {
                createVersionThree(statement);
                putMeta(connection, "schema_version", "3");
            }
            if (version < 4) {
                createVersionFour(statement);
                putMeta(connection, "schema_version", "4");
            }
            statement.execute("PRAGMA user_version = " + VERSION);
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void createVersionTwo(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE ai_statistics_health (
              id INTEGER PRIMARY KEY CHECK (id = 1),
              affected_since_ms INTEGER, last_write_error_at_ms INTEGER,
              dropped_events INTEGER NOT NULL DEFAULT 0,
              incomplete_calls INTEGER NOT NULL DEFAULT 0,
              write_failures INTEGER NOT NULL DEFAULT 0,
              migration_error TEXT, integrity_error TEXT
            ) STRICT
            """);
        statement.execute("INSERT INTO ai_statistics_health(id) VALUES (1)");
    }

    private static void createVersionThree(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE ai_token_usage_daily (
              day TEXT NOT NULL, caller_plugin_name TEXT NOT NULL, feature TEXT NOT NULL,
              provider_name TEXT NOT NULL, provider_type TEXT NOT NULL, model_name TEXT NOT NULL,
              model_type TEXT NOT NULL, operation TEXT NOT NULL, status TEXT NOT NULL,
              usage_quality TEXT NOT NULL, fact_count INTEGER NOT NULL,
              input_tokens INTEGER, output_tokens INTEGER, cache_read_input_tokens INTEGER,
              cache_creation_input_tokens INTEGER, reasoning_output_tokens INTEGER,
              accounted_total_tokens INTEGER,
              PRIMARY KEY(day, caller_plugin_name, feature, provider_name, provider_type,
                model_name, model_type, operation, status, usage_quality)
            ) STRICT
            """);
        statement.execute("""
            INSERT INTO ai_token_usage_daily (
              day, caller_plugin_name, feature, provider_name, provider_type, model_name,
              model_type, operation, status, usage_quality, fact_count, input_tokens,
              output_tokens, cache_read_input_tokens, cache_creation_input_tokens,
              reasoning_output_tokens, accounted_total_tokens
            )
            SELECT day, caller_plugin_name, feature, provider_name, provider_type, model_name,
              model_type, operation, status, usage_quality, call_count, input_tokens,
              output_tokens, cache_read_input_tokens, cache_creation_input_tokens,
              reasoning_output_tokens, accounted_total_tokens FROM ai_usage_daily
            """);
        statement.execute("CREATE INDEX idx_token_daily_day ON ai_token_usage_daily(day)");
        statement.execute("""
            CREATE INDEX idx_executions_started
            ON ai_model_executions(started_at_ms)
            """);
    }

    private static void createVersionFour(Statement statement) throws SQLException {
        statement.execute(
            "ALTER TABLE ai_statistics_health ADD COLUMN affected_until_ms INTEGER");
        statement.execute("""
            UPDATE ai_statistics_health
            SET affected_until_ms = affected_since_ms
            WHERE affected_since_ms IS NOT NULL
            """);
    }

    private static void createVersionOne(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE ai_calls (
              id TEXT PRIMARY KEY, epoch INTEGER NOT NULL, started_at_ms INTEGER NOT NULL,
              completed_at_ms INTEGER, duration_ms INTEGER,
              caller_plugin_name TEXT, caller_plugin_version TEXT,
              caller_detection_source TEXT NOT NULL, feature TEXT, operation TEXT NOT NULL,
              model_type TEXT NOT NULL, model_name TEXT NOT NULL, provider_name TEXT NOT NULL,
              provider_type TEXT NOT NULL, request_model_id TEXT NOT NULL,
              response_model_id TEXT, streaming INTEGER NOT NULL,
              status TEXT NOT NULL, error_type TEXT, error_code TEXT,
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
              error_type TEXT, error_code TEXT, request_model_id TEXT,
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
              missing_usage_calls INTEGER NOT NULL, duration_sum_ms INTEGER NOT NULL,
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
    }

    static void validateRecognized(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("PRAGMA user_version")) {
            if (!rows.next() || rows.getInt(1) != VERSION) {
                throw new SQLException("Unsupported SQLite statistics schema version");
            }
        }
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'");
            var rows = statement.executeQuery()) {
            if (!rows.next() || !Integer.toString(VERSION).equals(rows.getString(1))) {
                throw new SQLException("SQLite statistics schema marker is missing");
            }
        }
        validateColumns(connection, "ai_calls",
            "id", "epoch", "started_at_ms", "status", "accounted_total_tokens");
        validateColumns(connection, "ai_model_executions",
            "id", "call_id", "unit_kind", "attempt_index", "status",
            "accounted_total_tokens");
        validateColumns(connection, "ai_usage_daily",
            "day", "status", "usage_quality", "call_count", "accounted_total_tokens");
        validateColumns(connection, "ai_statistics_health",
            "id", "dropped_events", "incomplete_calls", "write_failures",
            "affected_since_ms", "affected_until_ms");
        validateColumns(connection, "ai_token_usage_daily",
            "day", "status", "usage_quality", "fact_count", "accounted_total_tokens");
    }

    static void validateMigratable(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'");
            var rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SQLException("SQLite statistics schema marker is missing");
            }
            int version;
            try {
                version = Integer.parseInt(rows.getString(1));
            } catch (NumberFormatException error) {
                throw new SQLException("Invalid SQLite statistics schema marker", error);
            }
            if (version < 1 || version > VERSION) {
                throw new SQLException("Unsupported SQLite statistics schema version " + version);
            }
        }
    }

    private static void validateColumns(Connection connection, String table, String... columns)
        throws SQLException {
        var sql = "SELECT " + String.join(", ", columns) + " FROM " + table + " WHERE 0";
        try (var statement = connection.createStatement()) {
            statement.executeQuery(sql).close();
        }
    }

    private static long longMeta(Connection connection, String key, long fallback)
        throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Long.parseLong(rows.getString(1)) : fallback;
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
}
