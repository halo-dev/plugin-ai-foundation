package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Factory methods for persisted UI message parts.
 */
public final class UIMessageParts {

    private UIMessageParts() {
    }

    /**
     * Creates a persisted text part.
     *
     * @param id text part id
     * @param text text content
     * @return text part
     */
    public static TextPart text(String id, String text) {
        return new TextPart(id, text);
    }

    /**
     * Creates a persisted reasoning part.
     *
     * @param id reasoning part id
     * @param text reasoning text
     * @param providerMetadata provider-specific reasoning metadata
     * @return reasoning part
     */
    public static ReasoningPart reasoning(String id, String text,
        Map<String, Object> providerMetadata) {
        return new ReasoningPart(id, text, providerMetadata);
    }

    /**
     * Creates a persisted custom data part.
     *
     * @param name data part name
     * @param data data payload
     * @return data part
     */
    public static DataPart data(String name, Object data) {
        return new DataPart(name, data);
    }

    /**
     * Creates a persisted dynamic data part.
     *
     * @param id data part id
     * @param name data part name
     * @param data data payload
     * @return data part
     */
    public static DataPart data(String id, String name, Object data) {
        return new DataPart(id, name, data);
    }

    /**
     * Creates a persisted source URL part.
     *
     * @param sourceId source id
     * @param url source URL
     * @param title optional source title
     * @param providerMetadata provider-specific metadata
     * @return source URL part
     */
    public static SourceUrlPart sourceUrl(String sourceId, String url, String title,
        Map<String, Object> providerMetadata) {
        return new SourceUrlPart(sourceId, url, title, providerMetadata);
    }

    /**
     * Creates a persisted document source part.
     *
     * @param sourceId source id
     * @param mediaType document media type
     * @param title display title
     * @param filename optional filename
     * @param providerMetadata provider-specific metadata
     * @return document source part
     */
    public static SourceDocumentPart sourceDocument(String sourceId, String mediaType,
        String title, String filename, Map<String, Object> providerMetadata) {
        return new SourceDocumentPart(sourceId, mediaType, title, filename, providerMetadata);
    }

    /**
     * Creates a persisted file part.
     *
     * @param fileId file id
     * @param url file URL
     * @param title optional file title
     * @param mediaType optional media type
     * @param data optional inline data
     * @param providerMetadata provider-specific metadata
     * @return file part
     */
    public static FilePart file(String fileId, String url, String title, String mediaType,
        Object data, Map<String, Object> providerMetadata) {
        return new FilePart(fileId, url, title, mediaType, data, providerMetadata);
    }

    /**
     * Creates a persisted dynamic tool part.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param state tool lifecycle state
     * @param input tool input
     * @param inputText streamed input text
     * @param output tool output
     * @param errorText safe tool error text
     * @param approval approval metadata
     * @param providerMetadata provider-specific metadata
     * @return dynamic tool part
     */
    public static ToolPart tool(String toolCallId, String toolName, ToolPartState state,
        Object input, String inputText, Object output, String errorText, ToolApproval approval,
        Map<String, Object> providerMetadata) {
        return new ToolPart(ToolPart.typeFor(toolName), toolCallId, toolName, state, input,
            inputText, output, errorText, approval, providerMetadata);
    }

}
