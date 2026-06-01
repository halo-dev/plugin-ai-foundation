package run.halo.aifoundation.message;

import run.halo.aifoundation.part.PartType;

/**
 * Convenience message part for plain text input.
 *
 * <p>This is equivalent to {@link ModelMessagePart#text(String)} and exists for callers that prefer
 * a concrete Java type:
 *
 * <pre>{@code
 * ModelMessage message = ModelMessage.user(List.of(new TextPart("Hello")));
 * }</pre>
 */
public class TextPart extends ModelMessagePart {

    public TextPart() {
        setType(PartType.TEXT);
    }

    public TextPart(String text) {
        setType(PartType.TEXT);
        setText(text);
    }
}
