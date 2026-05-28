package run.halo.aifoundation.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token usage reported by a language model provider.
 *
 * <p>Not all providers return every field. Use nullable-safe handling and prefer
 * {@link GenerateTextResult#getTotalUsage()} when a request can perform multiple tool-calling
 * steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageModelUsage {
    /**
     * Number of prompt/input tokens reported by the provider.
     */
    private Integer inputTokens;
    /**
     * Number of generated output tokens reported by the provider.
     */
    private Integer outputTokens;
    /**
     * Number of reasoning tokens reported by providers that expose reasoning usage.
     */
    private Integer reasoningTokens;
    /**
     * Total token count reported by the provider.
     */
    private Integer totalTokens;
    /**
     * Raw provider usage object, retained for provider-specific diagnostics.
     */
    private Object raw;
}
