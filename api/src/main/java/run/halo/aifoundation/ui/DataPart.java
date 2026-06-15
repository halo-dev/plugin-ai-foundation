package run.halo.aifoundation.ui;

/**
 * Persisted application data attached to a UI message.
 *
 * @param type dynamic data type
 * @param id stable data id
 * @param name stable data name
 * @param data persisted application payload
 * @param transientData whether the part originated from stream-only data
 */
public record DataPart(String type, String id, String name, Object data, boolean transientData)
    implements UIMessagePart {

    public DataPart {
        UIMessageDynamicNames.requireDataType(type, name);
    }

    /**
     * Creates a persisted dynamic data part using the name as the initial id.
     *
     * @param name stable data name
     * @param data persisted application payload
     */
    public DataPart(String name, Object data) {
        this(UIMessageDynamicNames.dataType(name), name, name, data, false);
    }

    /**
     * Creates a persisted dynamic data part.
     *
     * @param id stable data id
     * @param name stable data name
     * @param data persisted application payload
     */
    public DataPart(String id, String name, Object data) {
        this(UIMessageDynamicNames.dataType(name), id, name, data, false);
    }

    /**
     * Cast the data payload to the expected type.
     *
     * @param type expected payload class
     * @return the payload cast to {@code type}
     * @param <T> payload type
     * @throws ClassCastException when the payload is not assignable to {@code type}
     */
    public <T> T dataAs(Class<T> type) {
        return type.cast(data);
    }
}
