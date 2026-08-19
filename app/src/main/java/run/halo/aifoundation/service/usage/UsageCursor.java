package run.halo.aifoundation.service.usage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

record UsageCursor(Instant startedAt, String id, String filterHash) {

    static String encode(Instant startedAt, String id, UsageQuery query) {
        var value = startedAt.toEpochMilli() + "\n" + id + "\n" + hash(query);
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static UsageCursor decode(String encoded, UsageQuery query) {
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(encoded),
                StandardCharsets.UTF_8);
            var parts = decoded.split("\\n", -1);
            if (parts.length != 3 || !parts[2].equals(hash(query))) {
                throw new IllegalArgumentException("Cursor does not match the current filters");
            }
            return new UsageCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), parts[1],
                parts[2]);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid cursor", error);
        }
    }

    private static String hash(UsageQuery query) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var canonical = new StringBuilder();
            append(canonical, query.from());
            append(canonical, query.to());
            append(canonical, query.callerPlugin());
            append(canonical, query.feature());
            append(canonical, query.providerName());
            append(canonical, query.modelName());
            append(canonical, query.modelType());
            append(canonical, query.operation());
            append(canonical, query.status());
            append(canonical, query.usageQuality());
            append(canonical, query.resolution());
            var bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void append(StringBuilder target, Object value) {
        if (value == null) {
            target.append("-:");
            return;
        }
        var text = value.toString();
        target.append(text.length()).append(':').append(text);
    }
}
