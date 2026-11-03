package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Validates the release snapshot before making it available as an offline fallback. */
final class ModelsDevBundledCatalog {

    private static final String CATALOG_RESOURCE = "/models-dev/api-v2.json.gz";
    private static final String METADATA_RESOURCE = "/models-dev/metadata.json";
    private static final int MAX_METADATA_BYTES = 16 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModelsDevBundledCatalog() {
    }

    static ModelsDevCatalogService.Snapshot load() {
        try (var catalog = ModelsDevBundledCatalog.class.getResourceAsStream(CATALOG_RESOURCE);
             var metadata = ModelsDevBundledCatalog.class.getResourceAsStream(METADATA_RESOURCE)) {
            if (catalog == null && metadata == null) {
                return null;
            }
            if (catalog == null || metadata == null) {
                throw new IOException("Incomplete bundled models.dev catalog");
            }
            return read(catalog, metadata);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Invalid bundled models.dev catalog", failure);
        }
    }

    static ModelsDevCatalogService.Snapshot read(InputStream compressed, InputStream metadata)
        throws IOException {
        var metadataBytes = metadata.readNBytes(MAX_METADATA_BYTES + 1);
        if (metadataBytes.length > MAX_METADATA_BYTES) {
            throw new IOException("Bundled models.dev metadata too large");
        }
        var manifest = MAPPER.readTree(metadataBytes);
        if (manifest == null || !manifest.isObject()
            || !manifest.path("schemaVersion").isIntegralNumber()
            || !manifest.path("schemaVersion").canConvertToInt()
            || manifest.path("schemaVersion").intValue() != 2
            || !ModelsDevCatalogService.SOURCE_URL.equals(manifest.path("sourceUrl").asText())) {
            throw new IOException("Unsupported bundled models.dev metadata");
        }
        var timestamp = manifest.path("fetchedAt").asText();
        if (!timestamp.endsWith("Z")) {
            throw new IOException("Bundled models.dev timestamp must be UTC");
        }
        final Instant fetchedAt;
        try {
            fetchedAt = Instant.parse(timestamp);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid bundled models.dev timestamp", failure);
        }
        var expectedHash = manifest.path("sha256").asText();
        if (!expectedHash.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Invalid bundled models.dev digest");
        }
        final byte[] json;
        try (var gzip = new GZIPInputStream(compressed)) {
            json = gzip.readNBytes(ModelsDevCatalogService.MAX_BYTES + 1);
        }
        if (json.length > ModelsDevCatalogService.MAX_BYTES) {
            throw new IOException("Bundled models.dev catalog too large");
        }
        if (!expectedHash.equalsIgnoreCase(sha256(json))) {
            throw new IOException("Bundled models.dev digest mismatch");
        }
        var catalog = ModelsDevCatalog.parse(MAPPER.readTree(json))
            .retainProviders(ModelsDevProviderIdentity.catalogIds());
        return new ModelsDevCatalogService.Snapshot(catalog, fetchedAt, null, false);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}