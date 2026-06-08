package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Validates UI tool parts.
 *
 * @param <M> message metadata type
 */
@FunctionalInterface
public interface UIMessageToolValidator<M> {
    /**
     * Validates a tool-related part.
     *
     * @param message message containing the tool part
     * @param part tool-related part being validated
     * @param context validation context
     * @return validation issues, or an empty list when valid
     */
    List<UIMessageValidationIssue> validate(UIMessage<M> message, UIMessagePart part,
        UIMessageValidationContext<M> context);
}
