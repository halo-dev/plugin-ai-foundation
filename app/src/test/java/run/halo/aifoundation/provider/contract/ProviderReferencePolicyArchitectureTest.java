package run.halo.aifoundation.provider.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProviderReferencePolicyArchitectureTest {

    private static final Pattern INLINE_REFERENCE_CLASSIFICATION = Pattern.compile(
        "\\.startsWith\\(\\\"(?:https?://|data:|ms://)\\\"\\)");

    @Test
    void productionProvidersUseNamedReferencePolicies() throws IOException {
        var violations = new ArrayList<String>();
        try (var files = Files.walk(providerSourceRoot())) {
            files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.endsWith("UriReferencePolicy.java"))
                .forEach(path -> inspect(path, violations));
        }

        assertThat(violations)
            .as("Provider URI acceptance rules must be expressed by named policies")
            .isEmpty();
    }

    private void inspect(Path path, List<String> violations) {
        try {
            var matcher = INLINE_REFERENCE_CLASSIFICATION.matcher(Files.readString(path));
            if (matcher.find()) {
                violations.add(providerSourceRoot().relativize(path) + ": " + matcher.group());
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to inspect " + path, error);
        }
    }

    private Path providerSourceRoot() {
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        var moduleSource = workingDirectory.resolve("src/main/java/run/halo/aifoundation/provider");
        if (Files.isDirectory(moduleSource)) {
            return moduleSource;
        }
        return workingDirectory.resolve("app/src/main/java/run/halo/aifoundation/provider");
    }
}
