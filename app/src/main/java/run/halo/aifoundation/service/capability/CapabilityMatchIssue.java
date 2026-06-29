package run.halo.aifoundation.service.capability;

/**
 * One missing or incompatible capability condition.
 */
public record CapabilityMatchIssue(String path, Object expected, Object actual) {
}
