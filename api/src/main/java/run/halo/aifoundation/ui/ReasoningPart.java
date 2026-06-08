package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted visible reasoning text in a UI message.
 *
 * <p>Provider-specific opaque reasoning state is stored in {@code providerMetadata} and is not
 * converted back to model input by default.
 *
 * @param id stable reasoning block identifier
 * @param text accumulated visible reasoning text
 * @param providerMetadata provider-specific reasoning metadata
 */
public record ReasoningPart(String id, String text, Map<String, Object> providerMetadata)
    implements UIMessagePart {

    @Override
    public String type() {
        return UIMessageChunkType.REASONING;
    }
}
