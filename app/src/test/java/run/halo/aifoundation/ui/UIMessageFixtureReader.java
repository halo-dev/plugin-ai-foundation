package run.halo.aifoundation.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class UIMessageFixtureReader {

    private static final Path FIXTURE_DIR = findFixtureDir();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private UIMessageFixtureReader() {
    }

    static UIMessageFixture read(String name) {
        try {
            var path = FIXTURE_DIR.resolve(name + ".json");
            return MAPPER.readValue(Files.readString(path), UIMessageFixture.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read UI message fixture " + name, e);
        }
    }

    record UIMessageFixture(
        String name,
        String description,
        List<Map<String, Object>> chunks,
        Map<String, Object> expectedMessage,
        List<Map<String, Object>> expectedTransientEvents,
        Map<String, Object> expectedTerminal
    ) {

        UIMessageFixture {
            chunks = List.copyOf(chunks == null ? List.of() : chunks);
            expectedMessage = expectedMessage == null ? Map.of() : Map.copyOf(expectedMessage);
            expectedTransientEvents =
                List.copyOf(expectedTransientEvents == null ? List.of() : expectedTransientEvents);
            expectedTerminal = expectedTerminal == null ? Map.of() : Map.copyOf(expectedTerminal);
        }
    }

    static List<Map<String, Object>> fixtureIndex() {
        try (var paths = Files.list(FIXTURE_DIR)) {
            return paths
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted()
                .map(path -> {
                    try {
                        return MAPPER.readValue(Files.readString(path),
                            new TypeReference<Map<String, Object>>() {
                            });
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read fixture " + path, e);
                    }
                })
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list UI message fixtures", e);
        }
    }

    private static Path findFixtureDir() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("test-fixtures").resolve("ui-message");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate test-fixtures/ui-message");
    }
}
