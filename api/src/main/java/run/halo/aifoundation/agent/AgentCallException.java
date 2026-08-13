package run.halo.aifoundation.agent;

import run.halo.aifoundation.exception.AiFoundationException;

/**
 * Stable pre-provider failure raised while validating or preparing an agent call.
 */
public class AgentCallException extends AiFoundationException {
    private final AgentCallPhase phase;

    public AgentCallException(AgentCallPhase phase, String message) {
        super(message);
        this.phase = phase;
    }

    public AgentCallException(AgentCallPhase phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    public AgentCallPhase getPhase() {
        return phase;
    }
}
