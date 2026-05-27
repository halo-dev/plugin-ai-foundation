package run.halo.aifoundation;

/**
 * Decides whether text generation should execute another model step.
 *
 * <p>The callback is evaluated after a step has finished and after any tool calls from that step
 * have been inspected. Returning {@code true} allows Halo to continue with another model
 * invocation; returning {@code false} ends the generation.
 *
 * <pre>{@code
 * GenerateTextRequest request = GenerateTextRequest.builder()
 *     .prompt("Use tools when needed")
 *     .tools(List.of(weatherTool))
 *     .stopWhen(StopCondition.stepCountIs(3))
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface StopCondition {

    /**
     * Returns whether another model step should be started.
     *
     * @param context immutable state from the completed step
     * @return {@code true} to continue, {@code false} to stop
     */
    boolean shouldContinue(StepContext context);

    /**
     * Continues while there are successful tool results and the maximum number of model steps has
     * not been reached.
     */
    static StopCondition stepCountIs(int stepCount) {
        if (stepCount < 1) {
            throw new IllegalArgumentException("stepCount must be greater than or equal to 1");
        }
        return context -> context.getStepIndex() + 1 < stepCount;
    }

    /**
     * Continues only when the completed step produced at least one tool call, all executed tool
     * calls succeeded, and the maximum number of model steps has not been reached.
     */
    static StopCondition toolCalls(int stepCount) {
        if (stepCount < 1) {
            throw new IllegalArgumentException("stepCount must be greater than or equal to 1");
        }
        return context -> context.getStepIndex() + 1 < stepCount
            && context.getStep() != null
            && context.getStep().getToolCalls() != null
            && !context.getStep().getToolCalls().isEmpty()
            && context.getStep().getToolResults() != null
            && !context.getStep().getToolResults().isEmpty()
            && (context.getStep().getToolErrors() == null
                || context.getStep().getToolErrors().isEmpty());
    }
}
