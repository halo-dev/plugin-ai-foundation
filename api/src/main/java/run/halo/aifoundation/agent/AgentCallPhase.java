package run.halo.aifoundation.agent;

/**
 * Phase in which an agent call failed before model execution.
 */
public enum AgentCallPhase {
    /** Typed call options or call input were invalid. */
    VALIDATION,
    /** Asynchronous call preparation failed or returned invalid state. */
    PREPARATION
}
