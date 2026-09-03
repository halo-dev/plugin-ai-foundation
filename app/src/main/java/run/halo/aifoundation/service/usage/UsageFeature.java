package run.halo.aifoundation.service.usage;

import java.util.Map;
import java.util.regex.Pattern;

public final class UsageFeature {

    public static final String METADATA_KEY = "aifoundation.halo.run/feature";
    public static final String FORMAT = "[a-z0-9._-]{1,64}";
    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    private UsageFeature() {
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    public static String fromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        var value = metadata.get(METADATA_KEY);
        return value instanceof String text && isValid(text) ? text : null;
    }
}
