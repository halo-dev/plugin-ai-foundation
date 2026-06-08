package run.halo.aifoundation.ui;

/**
 * Stream chunk carrying application data for the UI.
 *
 * <p>Non-transient data is aggregated into {@link DataPart}. Transient data is delivered to the
 * stream consumer but is not persisted into {@link UIMessage#parts()}.
 *
 * @param name stable data name used to replace persisted data parts
 * @param data application payload
 * @param transientData whether the payload is display-only for the current stream
 */
public record DataChunk(String name, Object data, boolean transientData)
    implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.DATA;
    }
}
