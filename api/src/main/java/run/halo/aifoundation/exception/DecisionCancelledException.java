package run.halo.aifoundation.exception;

/** Raised when a decision request is cancelled through its cancellation token. */
public class DecisionCancelledException extends AiFoundationException {
    public DecisionCancelledException() {
        super("Decision inference was cancelled");
    }
}
