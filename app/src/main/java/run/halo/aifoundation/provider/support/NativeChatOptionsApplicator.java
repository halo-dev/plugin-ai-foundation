package run.halo.aifoundation.provider.support;

import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;

/** Applies administrator-owned model options to one provider's chat options. */
@FunctionalInterface
public interface NativeChatOptionsApplicator {

    ChatOptions apply(ChatOptions options, Map<String, Object> nativeOptions);
}
