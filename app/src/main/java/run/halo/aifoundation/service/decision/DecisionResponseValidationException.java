package run.halo.aifoundation.service.decision;

import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.decision.DecisionUsage;

/** Keeps reported usage when a decoded decision response fails semantic validation. */
public final class DecisionResponseValidationException extends IllegalStateException {
    private final String responseModel;
    private final DecisionUsage usage;

    public DecisionResponseValidationException(IllegalStateException cause,
        DecisionResponse response) {
        super(cause.getMessage(), cause);
        this.responseModel = response.getModel();
        this.usage = response.getUsage();
    }

    public String getResponseModel() {
        return responseModel;
    }

    public DecisionUsage getUsage() {
        return usage;
    }
}
