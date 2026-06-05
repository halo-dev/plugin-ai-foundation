package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Validates typed UI message metadata.
 *
 * @param <M> message metadata type
 */
@FunctionalInterface
public interface UIMessageMetadataValidator<M> {
    /**
     * Validates message metadata.
     *
     * @param message message being validated
     * @param metadata typed metadata from the message
     * @param context validation context
     * @return validation issues, or an empty list when valid
     */
    List<UIMessageValidationIssue> validate(UIMessage<M> message, M metadata,
        UIMessageValidationContext<M> context);
}
