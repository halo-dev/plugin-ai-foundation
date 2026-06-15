package run.halo.aifoundation.ui;

/**
 * Lifecycle chunk describing the start of one generation step.
 *
 * <p>Step start chunks are not persisted into {@link UIMessage#parts()} by the stream reader.
 *
 * @param stepIndex step index
 */
public record StartStepChunk(Integer stepIndex) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.START_STEP;
    }
}
