package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * One field-level difference between a discovered model and its saved counterpart.
 *
 * @param field stable field key understood by the Console, such as {@code displayName} or
 *     {@code capabilities.language.imageInput}
 * @param localValue saved value rendered as text, or {@code null} when absent
 * @param remoteValue discovered value rendered as text, or {@code null} when absent
 * @param applied whether a sync update would write the remote value; administrator-owned fields and capability domains
 *     with a manual source are never applied
 */
@Schema(description = "A field-level difference between discovered and saved model configuration.")
public record ModelSyncDifference(
        String field, @Nullable String localValue, @Nullable String remoteValue, boolean applied) {}
