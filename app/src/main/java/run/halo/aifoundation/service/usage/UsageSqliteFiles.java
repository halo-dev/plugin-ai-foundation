package run.halo.aifoundation.service.usage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class UsageSqliteFiles {

    static final int RETAINED_BACKUPS = 2;
    private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private UsageSqliteFiles() {
    }

    static Path backup(Connection connection, UsageDatabasePaths paths) {
        var directory = paths.backupDirectory();
        var liveName = paths.database().getFileName();
        var temporary = directory.resolve(liveName + ".backup-" + UUID.randomUUID() + ".tmp");
        var published = unique(directory.resolve(liveName + ".bak-"
            + TIMESTAMP.format(Instant.now())));
        try {
            Files.createDirectories(directory);
            vacuumInto(connection, temporary);
            if (!isValidSnapshot(temporary)) {
                throw new IllegalStateException("SQLite backup snapshot failed validation: "
                    + temporary);
            }
            move(temporary, published, false);
            rotate(paths);
            requestPassiveCheckpoint(connection);
            log.info("Published validated AI usage SQLite backup {}", published);
            return published;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to create SQLite statistics snapshot", error);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException error) {
                log.warn("Failed to delete SQLite backup temporary file {}", temporary, error);
            }
        }
    }

    static void migrationBackup(Connection connection, Path target) {
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(temporary);
            vacuumInto(connection, temporary);
            move(temporary, target, true);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to create SQLite migration snapshot", error);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException error) {
                log.warn("Failed to delete SQLite migration temporary file {}", temporary, error);
            }
        }
    }

    static Recovery recoverIfRequired(UsageDatabasePaths paths) {
        var live = paths.database();
        var backups = listBackups(paths);
        if (Files.exists(live) && isUsableLiveDatabase(live)) {
            return Recovery.none();
        }
        if (!hasStorageEvidence(live) && backups.isEmpty()) {
            return Recovery.none();
        }
        var selected = backups.reversed().stream()
            .filter(UsageSqliteFiles::isValidSnapshot)
            .findFirst();
        if (selected.isEmpty()) {
            preserveInvalidLiveOrThrow(paths);
            throw new UsageDatabaseIntegrityException(
                "SQLite statistics evidence exists but no validated recovery snapshot is "
                    + "available");
        }
        var temporary = paths.backupDirectory().resolve(live.getFileName() + ".restore-"
            + UUID.randomUUID() + ".tmp");
        try {
            Files.copy(selected.get(), temporary, StandardCopyOption.COPY_ATTRIBUTES);
            if (!isValidSnapshot(temporary)) {
                throw new IOException("copied recovery snapshot failed validation");
            }
            preserveInvalidLive(paths);
            Files.deleteIfExists(sidecar(live, "-wal"));
            Files.deleteIfExists(sidecar(live, "-shm"));
            move(temporary, live, true);
            log.info("Restored AI usage SQLite database {} from {}", live, selected.get());
            return new Recovery(true, backupCreatedAt(selected.get()));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to restore SQLite statistics database", error);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException error) {
                log.warn("Failed to delete SQLite restore temporary file {}", temporary, error);
            }
        }
    }

    private static Instant backupCreatedAt(Path backup) {
        var name = backup.getFileName().toString();
        var marker = ".bak-";
        var timestampStart = name.lastIndexOf(marker);
        if (timestampStart >= 0) {
            try {
                return Instant.from(TIMESTAMP.parse(name.substring(timestampStart
                    + marker.length())));
            } catch (RuntimeException ignored) {
                // Fall through to the filesystem timestamp for an older backup name.
            }
        }
        try {
            return Files.getLastModifiedTime(backup, LinkOption.NOFOLLOW_LINKS).toInstant();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read SQLite backup timestamp", error);
        }
    }

    record Recovery(boolean restored, Instant snapshotAt) {

        private static Recovery none() {
            return new Recovery(false, null);
        }
    }

    static List<Path> listBackups(UsageDatabasePaths paths) {
        var directory = paths.backupDirectory();
        var prefix = paths.database().getFileName() + ".bak-";
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> !Files.isSymbolicLink(path))
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to list SQLite statistics backups", error);
        }
    }

    static boolean isValidSnapshot(Path path) {
        if (!isRegularNonEmpty(path)) {
            return false;
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            UsageSqliteSchema.validateRecognized(connection);
            try (var statement = connection.createStatement();
                var rows = statement.executeQuery("PRAGMA quick_check")) {
                return rows.next() && "ok".equalsIgnoreCase(rows.getString(1)) && !rows.next();
            }
        } catch (Exception error) {
            log.warn("AI usage SQLite snapshot {} is invalid: {}", path, error.getMessage());
            return false;
        }
    }

    private static boolean isUsableLiveDatabase(Path path) {
        if (!isRegularNonEmpty(path)) {
            return false;
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            UsageSqliteSchema.validateMigratable(connection);
            try (var statement = connection.prepareStatement(
                "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'");
                var row = statement.executeQuery()) {
                if (row.next() && Integer.toString(UsageSqliteSchema.VERSION)
                    .equals(row.getString(1))) {
                    UsageSqliteSchema.validateRecognized(connection);
                }
            }
            try (var statement = connection.createStatement();
                var rows = statement.executeQuery("PRAGMA quick_check")) {
                return rows.next() && "ok".equalsIgnoreCase(rows.getString(1)) && !rows.next();
            }
        } catch (Exception error) {
            log.warn("AI usage SQLite database {} is not usable: {}", path,
                error.getMessage());
            return false;
        }
    }

    private static void preserveInvalidLiveOrThrow(UsageDatabasePaths paths) {
        try {
            preserveInvalidLive(paths);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to preserve invalid SQLite statistics", error);
        }
    }

    private static void preserveInvalidLive(UsageDatabasePaths paths) throws IOException {
        var live = paths.database();
        if (!hasStorageEvidence(live)) {
            return;
        }
        var directory = paths.backupDirectory().resolve("corrupted");
        Files.createDirectories(directory);
        var evidence = directory.resolve(live.getFileName() + ".corrupted-"
            + TIMESTAMP.format(Instant.now()) + "-" + UUID.randomUUID());
        if (Files.isRegularFile(live) && !Files.isSymbolicLink(live)) {
            Files.copy(live, evidence, StandardCopyOption.COPY_ATTRIBUTES);
        }
        copySidecar(live, evidence, "-wal");
        copySidecar(live, evidence, "-shm");
        log.warn("Preserved invalid AI usage SQLite state at {}", evidence);
    }

    private static void copySidecar(Path live, Path evidence, String suffix) throws IOException {
        var source = sidecar(live, suffix);
        if (Files.isRegularFile(source) && !Files.isSymbolicLink(source)) {
            Files.copy(source, sidecar(evidence, suffix), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void rotate(UsageDatabasePaths paths) throws IOException {
        var backups = listBackups(paths);
        for (int index = 0; index < Math.max(0, backups.size() - RETAINED_BACKUPS); index++) {
            Files.deleteIfExists(backups.get(index));
        }
    }

    private static void vacuumInto(Connection connection, Path target) throws Exception {
        var escaped = target.toAbsolutePath().toString().replace("'", "''");
        try (var statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escaped + "'");
        }
    }

    private static void requestPassiveCheckpoint(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("PRAGMA wal_checkpoint(PASSIVE)")) {
            if (rows.next()) {
                log.info("AI usage SQLite passive checkpoint: busy={}, log={}, checkpointed={}",
                    rows.getInt(1), rows.getInt(2), rows.getInt(3));
            }
        }
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException error) {
            if (replace) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        }
    }

    private static Path unique(Path candidate) {
        return Files.exists(candidate)
            ? candidate.resolveSibling(candidate.getFileName() + "-" + UUID.randomUUID())
            : candidate;
    }

    private static boolean isRegularNonEmpty(Path path) {
        try {
            return Files.isRegularFile(path) && !Files.isSymbolicLink(path)
                && Files.size(path) > 0;
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean hasStorageEvidence(Path live) {
        return Files.exists(live, LinkOption.NOFOLLOW_LINKS)
            || Files.exists(sidecar(live, "-wal"), LinkOption.NOFOLLOW_LINKS)
            || Files.exists(sidecar(live, "-shm"), LinkOption.NOFOLLOW_LINKS);
    }

    private static Path sidecar(Path path, String suffix) {
        return path.resolveSibling(path.getFileName() + suffix);
    }
}
