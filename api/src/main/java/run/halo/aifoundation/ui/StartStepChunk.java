package run.halo.aifoundation.ui;

/**
 * Lifecycle chunk describing the start of one generation step.
 *
 * <p>The stream reader persists this lifecycle event as a marker-only {@link StepStartPart}.
 * The invocation-local index remains stream diagnostics and is not copied to the persisted part.
 *
 * @param stepIndex step index
 */
public record StartStepChunk(Integer stepIndex) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.START_STEP;
    }
}
