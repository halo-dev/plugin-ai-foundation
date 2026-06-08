package run.halo.aifoundation.ui;

/**
 * Persisted application data attached to a UI message.
 *
 * @param name stable data name
 * @param data persisted application payload
 */
public record DataPart(String name, Object data) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.DATA;
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
