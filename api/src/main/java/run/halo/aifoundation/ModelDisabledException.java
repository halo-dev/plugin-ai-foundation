package run.halo.aifoundation;

public class ModelDisabledException extends AiFoundationException {

    public ModelDisabledException(String modelName) {
        super("AI model is disabled: " + modelName);
    }
}
