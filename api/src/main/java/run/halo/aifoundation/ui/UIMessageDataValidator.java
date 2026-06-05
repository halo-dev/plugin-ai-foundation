package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Validates a named UI data part.
 *
 * @param <M> message metadata type
 */
@FunctionalInterface
public interface UIMessageDataValidator<M> {
    /**
     * Validates a data part.
     *
     * @param message message containing the data part
     * @param part data part being validated
     * @param context validation context
     * @return validation issues, or an empty list when valid
     */
    List<UIMessageValidationIssue> validate(UIMessage<M> message, DataPart part,
        UIMessageValidationContext<M> context);
}
