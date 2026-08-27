package run.halo.aifoundation.provider.support;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Classifies URI-like references by their leading protocol marker.
 *
 * <p>The policy deliberately does not parse or validate a URI. Provider protocols sometimes use
 * opaque references such as {@code data:} and {@code ms://}; validation remains the responsibility
 * of the provider that owns the wire contract.
 */
public final class UriReferencePolicy {

    private final List<String> allowedPrefixes;

    private UriReferencePolicy(List<String> allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes;
    }

    public static UriReferencePolicy allowing(String... prefixes) {
        if (prefixes == null) {
            throw new IllegalArgumentException("At least one URI reference prefix is required");
        }
        if (prefixes.length == 0) {
            throw new IllegalArgumentException("At least one URI reference prefix is required");
        }
        var normalized = Arrays.stream(prefixes)
            .map(UriReferencePolicy::normalizePrefix)
            .distinct()
            .toList();
        return new UriReferencePolicy(normalized);
    }

    public boolean allows(String reference) {
        if (reference == null) {
            return false;
        }
        for (var prefix : allowedPrefixes) {
            if (reference.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("URI reference prefix must not be null");
        }
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("URI reference prefix must not be blank");
        }
        return prefix.toLowerCase(Locale.ROOT);
    }
}
