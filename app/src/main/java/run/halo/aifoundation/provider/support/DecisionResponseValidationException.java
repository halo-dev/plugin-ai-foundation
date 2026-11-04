package run.halo.aifoundation.provider.support;

import run.halo.aifoundation.decision.DecisionUsage;

/** Keeps valid provider metadata when a decision response fails validation. */
public final class DecisionResponseValidationException extends IllegalStateException {
    private final String responseModel;
    private final DecisionUsage usage;

    public DecisionResponseValidationException(IllegalStateException cause,
        String responseModel, DecisionUsage usage) {
        super(cause.getMessage(), cause);
        this.responseModel = responseModel;
        this.usage = usage;
    }

    public String getResponseModel() {
        return responseModel;
    }

    public DecisionUsage getUsage() {
        return usage;
    }
}