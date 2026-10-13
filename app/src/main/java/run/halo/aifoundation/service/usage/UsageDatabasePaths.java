package run.halo.aifoundation.service.usage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginsRootGetter;

@Component
class UsageDatabasePaths {

    private final Path database;
    private final Path backupDirectory;

    @Autowired
    UsageDatabasePaths(PluginsRootGetter pluginsRootGetter) {
        this(pluginsRootGetter.get());
    }

    UsageDatabasePaths(Path pluginsRoot) {
        var pluginDirectory = pluginsRoot.toAbsolutePath().normalize().resolve("ai-foundation");
        this.database = pluginDirectory.resolve("ai-foundation.sqlite");
        this.backupDirectory = pluginDirectory.resolve("backups");
    }

    Path database() {
        return database;
    }

    Path backupDirectory() {
        return backupDirectory;
    }

}
