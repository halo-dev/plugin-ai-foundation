package run.halo.aifoundation.service.usage;

import java.nio.file.Path;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import run.halo.app.plugin.PluginsRootGetter;

@Component
public class UsageDatabasePaths {

    private final Path database;
    private final Path backupDirectory;
    private final Path migrationBackup;

    @Autowired
    public UsageDatabasePaths(PluginsRootGetter pluginsRootGetter) {
        this(pluginsRootGetter.get());
    }

    UsageDatabasePaths(Path pluginsRoot) {
        var pluginDirectory = pluginsRoot.toAbsolutePath().normalize().resolve("ai-foundation");
        this.database = pluginDirectory.resolve("ai-foundation.sqlite");
        this.backupDirectory = pluginDirectory.resolve("backups");
        this.migrationBackup = backupDirectory.resolve("ai-foundation.sqlite.pre-migration");
    }

    public Path database() {
        return database;
    }

    public Path backupDirectory() {
        return backupDirectory;
    }

    public Path migrationBackup() {
        return migrationBackup;
    }
}
