package run.halo.aifoundation.ui;

/**
 * Persisted accumulated text block in a UI message.
 *
 * @param id stable text block identifier
 * @param text accumulated text
 */
public record TextPart(String id, String text) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.TEXT;
    }
}
