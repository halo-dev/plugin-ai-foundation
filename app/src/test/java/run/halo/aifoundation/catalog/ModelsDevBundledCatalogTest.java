package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class ModelsDevBundledCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant FETCHED_AT = Instant.parse("2026-10-20T04:05:06Z");

    @Test
    void packagedReleaseSnapshotMatchesItsManifestAndTheProductionParser() throws Exception {
        var snapshot = ModelsDevBundledCatalog.load();
        assertThat(snapshot).as("models.dev release snapshot must be packaged").isNotNull();
        assertThat(snapshot.catalog().providers().get("openai")).isNotEmpty();
        assertThat(snapshot.catalog().providers().keySet())
            .allMatch(ModelsDevProviderIdentity.catalogIds()::contains);
        try (var metadata = ModelsDevBundledCatalog.class.getResourceAsStream("/models-dev/metadata.json");
             var compressed = ModelsDevBundledCatalog.class.getResourceAsStream("/models-dev/api-v2.json.gz")) {
            assertThat(metadata).isNotNull();
            assertThat(compressed).isNotNull();
            var manifest = MAPPER.readTree(metadata);
            final byte[] raw;
            try (var gzip = new GZIPInputStream(compressed)) {
                raw = gzip.readAllBytes();
            }
            assertThat(manifest.path("schemaVersion").intValue()).isEqualTo(2);
            assertThat(manifest.path("sourceUrl").asText()).isEqualTo(ModelsDevCatalogService.SOURCE_URL);
            assertThat(manifest.path("fetchedAt").asText()).endsWith("Z");
            assertThat(snapshot.fetchedAt()).isEqualTo(Instant.parse(manifest.path("fetchedAt").asText()));
            assertThat(manifest.path("sha256").asText()).isEqualToIgnoringCase(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var parsed = ModelsDevCatalog.parse(MAPPER.readTree(raw))
                .retainProviders(ModelsDevProviderIdentity.catalogIds());
            assertThat(snapshot.catalog()).isEqualTo(parsed);
        }
    }

    @Test
    void verifiesRawBytesBeforeProjectingAndPreservesOriginalFetchTime() throws Exception {
        var source = (ObjectNode) MAPPER.readTree(ModelsDevCatalogServiceTest.CATALOG);
        var unknown = source.deepCopy().path("openai");
        ((ObjectNode) unknown).put("id", "unreviewed-provider");
        source.set("unreviewed-provider", unknown);
        var raw = source.toString().getBytes(StandardCharsets.UTF_8);
        var snapshot = read(gzip(raw), metadata(raw));
        assertThat(snapshot.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(snapshot.legacy()).isFalse();
        assertThat(snapshot.etag()).isNull();
        assertThat(snapshot.catalog().providers()).containsOnlyKeys("openai");
        assertThat(snapshot.catalog().find("openai", "exact-model").tools()).isTrue();
        assertThat(snapshot.catalog().find("openai", "exact-model").model().has("cost")).isFalse();
    }

    @Test
    void rejectsMismatchedHashesCorruptCompressionAndIncompleteGzip() throws Exception {
        var raw = ModelsDevCatalogServiceTest.CATALOG.getBytes(StandardCharsets.UTF_8);
        var metadata = metadata(raw);
        metadata.put("sha256", "0".repeat(64));
        assertThatThrownBy(() -> read(gzip(raw), metadata))
            .isInstanceOf(IOException.class).hasMessageContaining("digest mismatch");
        assertThatThrownBy(() -> read(raw, metadata(raw))).isInstanceOf(IOException.class);
        var compressed = gzip(raw);
        assertThatThrownBy(() -> read(Arrays.copyOf(compressed, compressed.length - 1), metadata(raw)))
            .isInstanceOf(IOException.class);
    }

    @Test
    void rejectsUnknownVersionsWrongSourcesAndInvalidTimestamps() throws Exception {
        var raw = ModelsDevCatalogServiceTest.CATALOG.getBytes(StandardCharsets.UTF_8);
        var compressed = gzip(raw);
        for (var metadata : java.util.List.of(
            metadata(raw).put("schemaVersion", 3),
            metadata(raw).put("schemaVersion", "2"),
            metadata(raw).put("sourceUrl", "https://models.dev/api.json"),
            metadata(raw).put("sourceUrl", "https://other.example/catalog.json"),
            metadata(raw).put("fetchedAt", "not-a-timestampZ"),
            metadata(raw).put("fetchedAt", "2026-10-20T04:05:06+01:00"),
            metadata(raw).put("sha256", "incomplete"))) {
            assertThatThrownBy(() -> read(compressed, metadata)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void limitsDecompressedBytesRatherThanCompressedFileSize() throws Exception {
        var raw = new byte[ModelsDevCatalogService.MAX_BYTES + 1];
        Arrays.fill(raw, (byte) ' ');
        var compressed = gzip(raw);
        assertThat(compressed.length).isLessThan(32 * 1024);
        assertThatThrownBy(() -> read(compressed, metadata(raw)))
            .isInstanceOf(IOException.class).hasMessageContaining("too large");
    }

    @Test
    void doesNotAcceptAValidHashAsProofOfValidCatalogStructure() throws Exception {
        var raw = "{\"openai\":{\"id\":\"another-provider\",\"models\":{}}}"
            .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> read(gzip(raw), metadata(raw)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ModelsDevCatalogService.Snapshot read(byte[] compressed, ObjectNode metadata) throws IOException {
        return ModelsDevBundledCatalog.read(new ByteArrayInputStream(compressed),
            new ByteArrayInputStream(MAPPER.writeValueAsBytes(metadata)));
    }

    private ObjectNode metadata(byte[] raw) throws Exception {
        var metadata = MAPPER.createObjectNode();
        metadata.put("schemaVersion", 2);
        metadata.put("sourceUrl", ModelsDevCatalogService.SOURCE_URL);
        metadata.put("fetchedAt", FETCHED_AT.toString());
        metadata.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        return metadata;
    }

    private byte[] gzip(byte[] raw) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(raw);
        }
        return bytes.toByteArray();
    }
}