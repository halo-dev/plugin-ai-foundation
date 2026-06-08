package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Result of validating persisted UI messages.
 *
 * @param messages immutable validated message input
 * @param issues validation issues found in the input
 */
public record UIMessageValidationResult<M>(List<UIMessage<M>> messages,
                                           List<UIMessageValidationIssue> issues) {
    public UIMessageValidationResult {
        messages = List.copyOf(messages);
        issues = List.copyOf(issues);
    }

    /**
     * Returns whether no validation issues were found.
     *
     * @return {@code true} when {@link #issues()} is empty
     */
    public boolean isValid() {
        return issues.isEmpty();
    }
}
