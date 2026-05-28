package run.halo.aifoundation;

/**
 * Raised when a caller asks for a default model slot that has not been configured.
 */
public class DefaultModelNotConfiguredException extends AiFoundationException {

    public DefaultModelNotConfiguredException(String slotName) {
        super("Default AI model slot is not configured: " + slotName);
    }
}
