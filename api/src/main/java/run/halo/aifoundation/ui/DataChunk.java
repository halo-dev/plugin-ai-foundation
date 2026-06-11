package run.halo.aifoundation.ui;

/**
 * Stream chunk carrying application data for the UI.
 *
 * <p>Non-transient data is aggregated into {@link DataPart}. Transient data is delivered to the
 * stream consumer but is not persisted into {@link UIMessage#parts()}.
 *
 * @param type dynamic data type
 * @param id stable data id
 * @param name stable data name used to replace persisted data parts
 * @param data application payload
 * @param transientData whether the payload is display-only for the current stream
 */
public record DataChunk(String type, String id, String name, Object data, boolean transientData)
    implements UIMessageChunk {

    public DataChunk {
        UIMessageDynamicNames.requireDataType(type, name);
    }

    /**
     * Creates a dynamic data chunk using the name as the initial id.
     *
     * @param name data name
     * @param data data payload
     * @param transientData whether the payload is stream-only
     */
    public DataChunk(String name, Object data, boolean transientData) {
        this(UIMessageDynamicNames.dataType(name), name, name, data, transientData);
    }

    /**
     * Creates a dynamic data chunk type for the given name.
     *
     * @param name data name
     * @return dynamic type
     */
    public static String typeFor(String name) {
        return UIMessageDynamicNames.dataType(name);
    }
}
