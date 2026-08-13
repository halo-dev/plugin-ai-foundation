package run.halo.aifoundation.agent;

/**
 * Validates typed options before an agent call is prepared.
 *
 * @param <O> call options type
 */
@FunctionalInterface
public interface AgentCallValidator<O> {

    /**
     * Validates the supplied options. Throw an exception to reject the call.
     */
    void validate(O options);
}
