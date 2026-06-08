package run.halo.aifoundation.ui;

/**
 * One validation issue found in a persisted UI message conversation.
 *
 * @param messageId related UI message id, if available
 * @param role related UI message role, if available
 * @param partType related part type, if available
 * @param partId related part id, if available
 * @param code stable validation code
 * @param message human-readable validation message
 */
public record UIMessageValidationIssue(String messageId, String role, String partType,
                                       String partId, String code, String message) {
}
