package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsageDatabasePathsTest {

    @Test
    void resolvesFixedRuntimeAndBackupLocationsFromPluginsRoot() {
        var paths = new UsageDatabasePaths(Path.of("/var/lib/halo/plugins"));

        assertThat(paths.database())
            .isEqualTo(Path.of("/var/lib/halo/plugins/ai-foundation/ai-foundation.sqlite"));
        assertThat(paths.backupDirectory())
            .isEqualTo(Path.of("/var/lib/halo/plugins/ai-foundation/backups"));
        assertThat(paths.migrationBackup())
            .isEqualTo(Path.of("/var/lib/halo/plugins/ai-foundation/backups/"
                + "ai-foundation.sqlite.pre-migration"));
    }
}
