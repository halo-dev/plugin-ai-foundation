package run.halo.aifoundation.provider.support;

/** Shared URI normalization used by provider transports. */
public final class ProviderUris {

    private ProviderUris() {
    }

    /**
     * Removes every trailing slash without changing the rest of the value.
     *
     * @param value URI text, or {@code null}
     * @return the value without trailing slashes
     */
    public static String withoutTrailingSlashes(String value) {
        if (value == null) {
            return null;
        }
        var end = value.length();
        while (end > 0) {
            if (value.charAt(end - 1) != '/') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    /** Removes one exact trailing endpoint path after normalizing trailing slashes. */
    public static String withoutTrailingPath(String value, String path) {
        var normalized = withoutTrailingSlashes(value);
        if (normalized == null) {
            return null;
        }
        if (path == null) {
            return normalized;
        }
        var suffix = path.startsWith("/") ? path : "/" + path;
        if (!normalized.endsWith(suffix)) {
            return normalized;
        }
        return normalized.substring(0, normalized.length() - suffix.length());
    }
}
