package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelsDevSnapshotTaskTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SNAPSHOT_NAME = "api-v2.json.gz";
    private static final String METADATA_NAME = "metadata.json";
    private static final String VALID_CATALOG = """
        {"openai":{"id":"openai","models":{
          "chat":{"id":"chat","type":"chat","capabilities":{
            "tools":{"supported":false},"structured_output":true,
            "reasoning":{"supported":false}},
            "modalities":{"input":["text"],"output":["text"]}},
          "nullable":{"id":"nullable","type":"chat","capabilities":{
            "tools":{"supported":null},"structured_output":null,
            "reasoning":{"supported":null}},
            "modalities":{"input":null,"output":null}},
          "image":{"id":"image","type":"image",
            "modalities":{"input":["text"],"output":["image/png"]}},
          "embedding":{"id":"embedding","type":"embedding",
            "modalities":{"input":["text"],"output":["embedding"]}},
          "rerank":{"id":"rerank","type":"reranking"},
          "future":{"id":"future","type":"future-model-type",
            "modalities":{"input":42},"capabilities":{"structured_output":"unknown"}},
          "non-text":{"id":"non-text","type":"chat",
            "modalities":{"input":42,"output":["image/png"]},
            "capabilities":{"tools":{"supported":"unknown"}}}
        }}}
        """;

    @TempDir
    static Path testKitDirectory;

    @TempDir
    Path projectDirectory;

    @BeforeEach
    void prepareProject() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'models-dev-snapshot-test'");
        Files.writeString(projectDirectory.resolve("build.gradle"), "apply from: 'models-dev-snapshot.gradle'");
        copyResource("/gradle/models-dev-snapshot.gradle", projectDirectory.resolve("models-dev-snapshot.gradle"));
        Files.createDirectories(outputDirectory());
    }

    @Test
    void validMixedCatalogCanBeUpdatedVerifiedAndLoadedByRuntime() throws Exception {
        Files.writeString(inputFile(), VALID_CATALOG);

        var result =
                runner("updateModelsDevSnapshot", "verifyModelsDevSnapshot").build();

        assertThat(result.task(":updateModelsDevSnapshot").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":verifyModelsDevSnapshot").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        try (var compressed = Files.newInputStream(outputDirectory().resolve(SNAPSHOT_NAME));
                var metadata = Files.newInputStream(outputDirectory().resolve(METADATA_NAME))) {
            var catalog = ModelsDevBundledCatalog.read(compressed, metadata).catalog();
            assertThat(catalog.providers().get("openai").keySet())
                    .containsExactlyInAnyOrder("chat", "nullable", "image", "embedding", "rerank");
            assertThat(catalog.find("openai", "chat").tools()).isFalse();
            assertThat(catalog.find("openai", "chat").structuredOutput()).isTrue();
            assertThat(catalog.find("openai", "nullable").tools()).isNull();
        }
    }

    @Test
    void invalidConsumedFieldsNeverReplaceTheExistingSnapshot() throws Exception {
        copyBundledSnapshot();
        var snapshotBefore = Files.readAllBytes(outputDirectory().resolve(SNAPSHOT_NAME));
        var metadataBefore = Files.readAllBytes(outputDirectory().resolve(METADATA_NAME));
        var invalidFields = List.of(
                new InvalidField("capabilities.tools.supported", "false"),
                new InvalidField("capabilities.structured_output", "true"),
                new InvalidField("capabilities.reasoning.supported", 1),
                new InvalidField("modalities.input", "text"),
                new InvalidField("modalities.output", "text"),
                new InvalidField("modalities.input", List.of("text", 1)),
                new InvalidField("modalities.output", List.of("text", false)));

        for (var invalid : invalidFields) {
            Files.writeString(inputFile(), catalogWithInvalidField(invalid));

            var result = runner("updateModelsDevSnapshot").buildAndFail();

            assertThat(result.getOutput()).contains("openai/chat." + invalid.path());
            assertPreserved(snapshotBefore, metadataBefore);
        }
    }

    @Test
    void catalogWithOnlyFilteredChatModelsCannotReplaceTheExistingSnapshot() throws Exception {
        copyBundledSnapshot();
        var snapshotBefore = Files.readAllBytes(outputDirectory().resolve(SNAPSHOT_NAME));
        var metadataBefore = Files.readAllBytes(outputDirectory().resolve(METADATA_NAME));
        Files.writeString(inputFile(), """
            {"openai":{"id":"openai","models":{"image-chat":{
              "id":"image-chat","type":"chat","modalities":{"output":["image/png"]}
            }}}}
            """);

        var result = runner("updateModelsDevSnapshot").buildAndFail();

        assertThat(result.getOutput()).contains("Catalog must contain at least one usable openai chat model");
        assertPreserved(snapshotBefore, metadataBefore);
    }

    @Test
    void matchingDigestDoesNotMakeInvalidRuntimeFieldsPassVerification() throws Exception {
        // Object keys are sorted so this also satisfies the CLI's canonical JSON requirement.
        var raw = ("{\"openai\":{\"id\":\"openai\",\"models\":{\"chat\":{"
                        + "\"capabilities\":{\"tools\":{\"supported\":\"false\"}},"
                        + "\"id\":\"chat\",\"type\":\"chat\"}}}}")
                .getBytes(StandardCharsets.UTF_8);
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(raw);
        }
        Files.write(outputDirectory().resolve(SNAPSHOT_NAME), compressed.toByteArray());
        var metadata = MAPPER.createObjectNode()
                .put("schemaVersion", 2)
                .put("sourceUrl", ModelsDevCatalogService.SOURCE_URL)
                .put("fetchedAt", "2026-01-01T00:00:00Z")
                .put("providerCount", 1)
                .put("modelCount", 1)
                .put(
                        "sha256",
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Files.writeString(outputDirectory().resolve(METADATA_NAME), metadata.toString());

        var result = runner("verifyModelsDevSnapshot").buildAndFail();

        assertThat(result.getOutput())
                .contains("openai/chat.capabilities.tools.supported")
                .contains("must be a boolean or null");
    }

    private String catalogWithInvalidField(InvalidField invalid) {
        var catalog = MAPPER.createObjectNode();
        var provider = catalog.putObject("openai").put("id", "openai");
        var model =
                provider.putObject("models").putObject("chat").put("id", "chat").put("type", "chat");
        var fields = invalid.path().split("\\.");
        var parent = model;
        for (int index = 0; index < fields.length - 1; index++) {
            parent = parent.putObject(fields[index]);
        }
        parent.set(fields[fields.length - 1], MAPPER.valueToTree(invalid.value()));
        return catalog.toString();
    }

    private void copyBundledSnapshot() throws IOException {
        copyResource("/models-dev/" + SNAPSHOT_NAME, outputDirectory().resolve(SNAPSHOT_NAME));
        copyResource("/models-dev/" + METADATA_NAME, outputDirectory().resolve(METADATA_NAME));
    }

    private void copyResource(String name, Path destination) throws IOException {
        try (var resource = ModelsDevSnapshotTaskTest.class.getResourceAsStream(name)) {
            assertThat(resource).as("Packaged test resource %s", name).isNotNull();
            Files.copy(resource, destination);
        }
    }

    private void assertPreserved(byte[] snapshotBefore, byte[] metadataBefore) throws IOException {
        assertThat(Arrays.equals(
                        snapshotBefore, Files.readAllBytes(outputDirectory().resolve(SNAPSHOT_NAME))))
                .as("The previous compressed snapshot must be preserved")
                .isTrue();
        assertThat(Arrays.equals(
                        metadataBefore, Files.readAllBytes(outputDirectory().resolve(METADATA_NAME))))
                .as("The previous metadata must be preserved")
                .isTrue();
    }

    private GradleRunner runner(String... tasks) {
        var arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of(
                "--offline",
                "--stacktrace",
                "--console=plain",
                "--max-workers=2",
                "--no-build-cache",
                "--no-configuration-cache",
                "-Dorg.gradle.jvmargs=-Xmx256m -XX:MaxMetaspaceSize=256m",
                "-PmodelsDevSnapshotInput=input.json",
                "-PmodelsDevSnapshotOutputDir=output"));
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withTestKitDir(testKitDirectory.toFile())
                .withArguments(arguments);
    }

    private Path inputFile() {
        return projectDirectory.resolve("input.json");
    }

    private Path outputDirectory() {
        return projectDirectory.resolve("output");
    }

    private record InvalidField(String path, Object value) {}
}
