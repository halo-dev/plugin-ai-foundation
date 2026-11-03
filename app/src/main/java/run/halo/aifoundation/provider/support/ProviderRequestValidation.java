package run.halo.aifoundation.provider.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** Internal request validation invoked after protocol and provider wire normalization. */
public final class ProviderRequestValidation {

    private static final String CONTEXT_KEY = ProviderRequestValidation.class.getName();

    private ProviderRequestValidation() {
    }

    public static ChatOptions attach(ChatOptions options, Consumer<Map<String, Object>> validator) {
        if (!(options instanceof ToolCallingChatOptions toolOptions)
            || !(options.mutate() instanceof ToolCallingChatOptions.Builder<?> builder)) {
            return options;
        }
        var context = new LinkedHashMap<String, Object>();
        if (toolOptions.getToolContext() != null) {
            context.putAll(toolOptions.getToolContext());
        }
        context.put(CONTEXT_KEY, new RequestValidator(validator));
        return builder.toolContext(context).build();
    }

    public static void validate(ToolCallingChatOptions options, Map<String, Object> body) {
        var context = options.getToolContext();
        if (context != null && context.get(CONTEXT_KEY) instanceof RequestValidator validator) {
            validator.callback().accept(body);
        }
    }

    private record RequestValidator(Consumer<Map<String, Object>> callback) {
    }
}