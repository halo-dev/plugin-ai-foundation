package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persisted message exchanged with a frontend chat UI.
 *
 * <p>A UI message keeps interface-facing parts such as text, custom data, tool
 * results, files, sources, and reasoning. It is the object callers can store
 * and send back on the next chat request.
 *
 * @param id stable message identifier
 * @param role conversation role represented by the message
 * @param parts persisted UI message parts in display order
 * @param metadata caller-defined message-level metadata
 */
public record UIMessage<M>(String id, UIMessageRole role, List<UIMessagePart> parts,
                           M metadata) {

    public UIMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(role, "role must not be null");
        parts = List.copyOf(Objects.requireNonNull(parts, "parts must not be null"));
    }

    /**
     * Creates an assistant message without typed metadata.
     *
     * @param id stable assistant message id
     * @param parts persisted assistant parts
     * @return assistant UI message
     */
    public static UIMessage<Void> assistant(String id, List<UIMessagePart> parts) {
        return new UIMessage<>(id, UIMessageRole.ASSISTANT, parts, null);
    }

    /**
     * Returns all parts with the given protocol type.
     *
     * @param type value from {@link UIMessageChunkType} or a compatible custom type
     * @return matching parts in message order
     */
    public List<UIMessagePart> parts(String type) {
        return parts.stream()
            .filter(part -> type.equals(part.type()))
            .toList();
    }

    /**
     * Finds one part by protocol type and part id.
     *
     * @param type part protocol type
     * @param id part identifier, such as a text id, tool call id, or data name
     * @return matching part when present
     */
    public Optional<UIMessagePart> part(String type, String id) {
        return parts.stream()
            .filter(part -> type.equals(part.type()))
            .filter(part -> id.equals(partId(part)))
            .findFirst();
    }

    /**
     * Returns persisted text parts.
     *
     * @return text parts in message order
     */
    public List<TextPart> textParts() {
        return parts.stream()
            .filter(TextPart.class::isInstance)
            .map(TextPart.class::cast)
            .toList();
    }

    /**
     * Concatenates all persisted text parts.
     *
     * @return assistant or user text visible to the caller
     */
    public String text() {
        return textParts().stream()
            .map(TextPart::text)
            .reduce("", String::concat);
    }

    /**
     * Returns persisted custom data parts.
     *
     * @return data parts in message order
     */
    public List<DataPart> dataParts() {
        return parts.stream()
            .filter(DataPart.class::isInstance)
            .map(DataPart.class::cast)
            .toList();
    }

    /**
     * Finds the first persisted data part by name.
     *
     * @param name data part name
     * @return matching data part when present
     */
    public Optional<DataPart> dataPart(String name) {
        return dataParts().stream()
            .filter(part -> name.equals(part.name()))
            .findFirst();
    }

    /**
     * Reads a named data part as a specific Java type.
     *
     * @param name data part name
     * @param type expected data value type
     * @param <T> expected data value type
     * @return converted data value when the part exists
     * @throws ClassCastException when the stored data is not assignable to {@code type}
     */
    public <T> Optional<T> data(String name, Class<T> type) {
        return dataPart(name).map(part -> part.dataAs(type));
    }

    /**
     * Returns a copy of this message with replacement parts.
     *
     * @param parts new persisted parts
     * @return message with unchanged id, role, and metadata
     */
    public UIMessage<M> withParts(List<UIMessagePart> parts) {
        return new UIMessage<>(id, role, parts, metadata);
    }

    /**
     * Returns a copy of this message with replacement metadata.
     *
     * @param metadata new message-level metadata
     * @param <N> new metadata type
     * @return message with unchanged id, role, and parts
     */
    public <N> UIMessage<N> withMetadata(N metadata) {
        return new UIMessage<>(id, role, parts, metadata);
    }

    private static String partId(UIMessagePart part) {
        if (part instanceof TextPart value) {
            return value.id();
        }
        if (part instanceof ReasoningPart value) {
            return value.id();
        }
        if (part instanceof SourceUrlPart value) {
            return value.sourceId();
        }
        if (part instanceof FilePart value) {
            return value.fileId();
        }
        if (part instanceof ToolPart value) {
            return value.toolCallId();
        }
        if (part instanceof DataPart value) {
            return value.id();
        }
        return null;
    }
}
