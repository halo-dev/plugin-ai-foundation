package run.halo.aifoundation;

/**
 * Prepares request overrides before a model step starts.
 *
 * <p>The callback receives immutable step context and returns a {@link PreparedStep} with settings
 * for the next model invocation. Return {@code null} or {@link PreparedStep#empty()} to keep the
 * current request settings.
 *
 * <pre>{@code
 * GenerateTextRequest request = GenerateTextRequest.builder()
 *     .prompt("Plan and answer")
 *     .tools(List.of(planTool, answerTool))
 *     .prepareStep(context -> context.getStepIndex() == 0
 *         ? PreparedStep.builder().activeTools(List.of("plan")).build()
 *         : PreparedStep.builder().activeTools(List.of("answer")).build())
 *     .stopWhen(StopCondition.stepCountIs(2))
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface PrepareStepCallback {

    /**
     * Returns step-scoped request overrides.
     */
    PreparedStep prepare(StepContext context);
}
