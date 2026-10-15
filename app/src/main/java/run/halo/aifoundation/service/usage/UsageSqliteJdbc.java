package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/** Shared JDBC helpers for the usage statistics store. */
@Slf4j
final class UsageSqliteJdbc {

    private UsageSqliteJdbc() {
    }

    static void putMeta(Connection connection, String key, String value) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO ai_statistics_meta(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    static long longMeta(Connection connection, String key, long fallback) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Long.parseLong(rows.getString(1)) : fallback;
            }
        }
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        var value = nullableLong(row, column);
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    static Long nullableLong(ResultSet row, String column) throws SQLException {
        var value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    static void setLong(PreparedStatement statement, int index, Long value)
        throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    static void setAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException error) {
            log.warn("Failed to restore AI usage statistics auto-commit", error);
        }
    }
}
