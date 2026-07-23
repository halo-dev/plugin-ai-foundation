package run.halo.aifoundation.service.language.stream;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * Ordered app-internal provider stream event.
 */
public sealed interface ProviderStreamPart permits ProviderStreamPart.ChatResponsePart,
    ProviderStreamPart.ToolInputStartPart, ProviderStreamPart.ToolInputDeltaPart,
    ProviderStreamPart.ToolInputEndPart {

    record ChatResponsePart(ChatResponse response) implements ProviderStreamPart {
    }

    record ToolInputStartPart(int index, String toolCallId, String toolName)
        implements ProviderStreamPart {
    }

    record ToolInputDeltaPart(int index, String inputTextDelta) implements ProviderStreamPart {
    }

    record ToolInputEndPart(int index) implements ProviderStreamPart {
    }
}
