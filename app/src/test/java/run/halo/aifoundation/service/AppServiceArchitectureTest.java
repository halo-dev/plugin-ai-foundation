package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppServiceArchitectureTest {

    private static final Set<String> ALLOWED_SERVICE_ROOT_FILES = Set.of(
        "AiModelServiceImpl.java",
        "AiModelResolver.java",
        "EmbeddingModelFactory.java",
        "LanguageModelFactory.java"
    );

    @Test
    void serviceRootContainsOnlyServiceBoundaries() throws IOException {
        var serviceRoot = Path.of("src/main/java/run/halo/aifoundation/service");

        try (var files = Files.list(serviceRoot)) {
            var unexpectedFiles = files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(fileName -> fileName.endsWith(".java"))
                .filter(fileName -> !ALLOWED_SERVICE_ROOT_FILES.contains(fileName))
                .toList();

            assertThat(unexpectedFiles)
                .as("Implementation helpers must live in cohesive service subpackages")
                .isEmpty();
        }
    }
}
