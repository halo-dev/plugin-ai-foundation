package run.halo.aifoundation.provider.support;

import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.GenerateTextRequest;

/**
 * Builds provider-specific chat options for tool-calling requests.
 */
@FunctionalInterface
public interface ToolCallingChatOptionsFactory {

    ChatOptions build(GenerateTextRequest request, List<ToolCallback> toolCallbacks,
        Set<String> toolNames);
}
