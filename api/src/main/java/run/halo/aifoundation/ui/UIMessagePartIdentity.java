package run.halo.aifoundation.ui;

import java.util.Objects;

/**
 * Stable identity used when reducing stream chunks into persisted UI message parts.
 *
 * @param type part type
 * @param id part id within that type
 */
public record UIMessagePartIdentity(String type, String id) {

    public UIMessagePartIdentity {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Computes identity for a persisted part.
     *
     * @param part UI message part
     * @return stable identity
     */
    public static UIMessagePartIdentity of(UIMessagePart part) {
        return switch (part) {
            case TextPart value -> new UIMessagePartIdentity(value.type(), value.id());
            case ReasoningPart value -> new UIMessagePartIdentity(value.type(), value.id());
            case DataPart value -> new UIMessagePartIdentity(value.type(), value.id());
            case SourceUrlPart value -> new UIMessagePartIdentity(value.type(), value.sourceId());
            case SourceDocumentPart value -> new UIMessagePartIdentity(value.type(),
                value.sourceId());
            case FilePart value -> new UIMessagePartIdentity(value.type(), value.fileId());
            case ToolPart value -> new UIMessagePartIdentity(value.type(), value.toolCallId());
        };
    }
}
