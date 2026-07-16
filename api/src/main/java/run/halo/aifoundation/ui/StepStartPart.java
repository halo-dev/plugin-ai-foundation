package run.halo.aifoundation.ui;

/**
 * Marker persisted at the start of one assistant generation step.
 *
 * <p>The marker's position in {@link UIMessage#parts()} defines the boundary. It intentionally
 * carries no invocation-local step index because indexes restart for each model invocation.
 */
public record StepStartPart() implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.STEP_START;
    }
}
