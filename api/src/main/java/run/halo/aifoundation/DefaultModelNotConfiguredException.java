package run.halo.aifoundation;

public class DefaultModelNotConfiguredException extends AiFoundationException {

    public DefaultModelNotConfiguredException(String slotName) {
        super("Default AI model slot is not configured: " + slotName);
    }
}
