package run.halo.aifoundation.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Immutable typed input and operational controls for one agent invocation.
 *
 * <p>Use either a prompt or model messages, but not both. Agent-owned policy such as tools,
 * instructions, output, and stop conditions is intentionally not exposed here.
 *
 * @param <O> call options type
 */
@Value
public class AgentCall<O> {
    String prompt;
    List<ModelMessage> messages;
    O options;
    Map<String, Object> metadata;
    Map<String, Object> context;
    Map<String, String> headers;
    CancellationToken cancellationToken;
    GenerationTimeouts timeouts;
    List<GenerationLifecycle> lifecycle;
    List<LanguageModelMiddleware> middleware;

    @Builder
    private AgentCall(String prompt, List<ModelMessage> messages, O options,
        Map<String, Object> metadata, Map<String, Object> context, Map<String, String> headers,
        CancellationToken cancellationToken, GenerationTimeouts timeouts,
        List<GenerationLifecycle> lifecycle, List<LanguageModelMiddleware> middleware) {
        this.prompt = prompt;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.options = options;
        this.metadata = immutableMap(metadata);
        this.context = immutableMap(context);
        this.headers = immutableStringMap(headers);
        this.cancellationToken = cancellationToken;
        this.timeouts = timeouts;
        this.lifecycle = lifecycle == null ? List.of() : List.copyOf(lifecycle);
        this.middleware = middleware == null ? List.of() : List.copyOf(middleware);
    }

    /**
     * Creates a no-options prompt call.
     */
    public static AgentCall<Void> prompt(String prompt) {
        return AgentCall.<Void>builder().prompt(prompt).build();
    }

    /**
     * Creates a typed prompt call.
     */
    public static <O> AgentCall<O> prompt(String prompt, O options) {
        return AgentCall.<O>builder().prompt(prompt).options(options).build();
    }

    /**
     * Creates a no-options message call.
     */
    public static AgentCall<Void> messages(List<ModelMessage> messages) {
        return AgentCall.<Void>builder().messages(messages).build();
    }

    /**
     * Creates a typed message call.
     */
    public static <O> AgentCall<O> messages(List<ModelMessage> messages, O options) {
        return AgentCall.<O>builder().messages(messages).options(options).build();
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source) {
        return immutableMap(source);
    }
}
