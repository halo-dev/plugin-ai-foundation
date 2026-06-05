package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Raised when UI message validation fails.
 */
public class InvalidUIMessageException extends IllegalArgumentException {
    private final List<UIMessageValidationIssue> issues;

    /**
     * Creates an exception from validation issues.
     *
     * @param issues validation issues
     */
    public InvalidUIMessageException(List<UIMessageValidationIssue> issues) {
        super("Invalid UI messages: " + issues.size() + " issue(s)");
        this.issues = List.copyOf(issues);
    }

    /**
     * Returns validation issues that caused the exception.
     *
     * @return immutable validation issues
     */
    public List<UIMessageValidationIssue> issues() {
        return issues;
    }
}
