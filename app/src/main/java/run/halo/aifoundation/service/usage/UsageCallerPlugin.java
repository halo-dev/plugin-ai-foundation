package run.halo.aifoundation.service.usage;

/**
 * A caller plugin recorded in the usage statistics database.
 *
 * <p>{@code version} and {@code detectionSource} are the values recorded on the plugin's most
 * recent call. They are used when the plugin resource is no longer available; the Console
 * endpoint prefers the metadata of the installed plugin.
 */
public record UsageCallerPlugin(
    String name,
    String version,
    String detectionSource
) {
}
