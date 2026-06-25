package run.halo.aifoundation.service.capability;

import java.util.List;

/**
 * Result of checking a model capability snapshot against a requirement.
 */
public record CapabilityMatchResult(boolean matched, List<CapabilityMatchIssue> issues) {

    public static CapabilityMatchResult success() {
        return new CapabilityMatchResult(true, List.of());
    }

    public static CapabilityMatchResult unmatched(List<CapabilityMatchIssue> issues) {
        return new CapabilityMatchResult(false, List.copyOf(issues));
    }
}
