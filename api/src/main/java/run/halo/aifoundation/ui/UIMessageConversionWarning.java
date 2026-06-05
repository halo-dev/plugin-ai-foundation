package run.halo.aifoundation.ui;

/**
 * Diagnostic produced while converting UI messages into model messages.
 *
 * @param messageId related UI message id, if available
 * @param role related UI message role, if available
 * @param partType related part type, if available
 * @param partId related part id, if available
 * @param code stable diagnostic code
 * @param message human-readable diagnostic text
 */
public record UIMessageConversionWarning(String messageId, String role, String partType,
                                         String partId, String code, String message) {
}
