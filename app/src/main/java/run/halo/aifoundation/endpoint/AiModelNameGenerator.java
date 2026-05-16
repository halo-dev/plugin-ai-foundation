package run.halo.aifoundation.endpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AiModelNameGenerator {

    private static final int MAX_NAME_LENGTH = 63;
    private static final int HASH_LENGTH = 8;

    public static String generate(String providerName, String modelId) {
        return generate(providerName, modelId, 0);
    }

    public static String generate(String providerName, String modelId, int attempt) {
        var prefix = normalizePart(providerName) + "-" + normalizePart(modelId);
        var suffix = hash(providerName + "\n" + modelId + "\n" + attempt);
        var maxPrefixLength = MAX_NAME_LENGTH - HASH_LENGTH - 1;
        if (prefix.length() > maxPrefixLength) {
            prefix = trimDash(prefix.substring(0, maxPrefixLength));
        }
        if (prefix.isBlank()) {
            prefix = "model";
        }
        return prefix + "-" + suffix;
    }

    private static String normalizePart(String value) {
        var normalized = value.toLowerCase()
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-");
        normalized = trimDash(normalized);
        return normalized.isBlank() ? "model" : normalized;
    }

    private static String trimDash(String value) {
        return value.replaceAll("^-|-$", "");
    }

    private static String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
