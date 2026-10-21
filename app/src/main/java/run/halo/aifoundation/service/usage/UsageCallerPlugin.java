package run.halo.aifoundation.service.usage;

/**
 * A caller plugin recorded in the usage statistics database.
 *
 * <p>Version and detection source come from the plugin's most recent recorded call.
 */
public record UsageCallerPlugin(
    String name,
    String version,
    String detectionSource
) {
}
