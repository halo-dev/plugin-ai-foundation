package run.halo.aifoundation.chat;

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request-scoped reasoning preference for text generation.
 *
 * <p>The setting describes the caller's intent. A provider adapter decides whether the selected
 * provider can honor that intent and how it should be translated to native request parameters.
 * When the adapter cannot apply an explicit setting, generation fails before a provider call is
 * made.
 *
 * <pre>{@code
 * GenerateTextRequest request = GenerateTextRequest.builder()
 *     .prompt("Summarize this comment thread.")
 *     .reasoning(ReasoningOptions.disabled())
 *     .build();
 * }</pre>
 *
 * <p>Do not combine an explicit typed reasoning setting with raw provider-native reasoning keys
 * in {@link GenerateTextRequest#getProviderOptions()}. Use the typed setting for normal SDK usage,
 * and raw provider options only when a provider-specific parameter is not modeled yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasoningOptions {

    /**
     * Reasoning mode requested for this generation call.
     */
    private Mode mode;

    /**
     * Optional effort level. Provider adapters may reject levels they do not support.
     */
    private Effort effort;

    /**
     * Use the provider and model default behavior. This is equivalent to leaving
     * {@link GenerateTextRequest#getReasoning()} unset.
     *
     * @return reasoning options that do not add provider-native reasoning controls
     */
    public static ReasoningOptions providerDefault() {
        return ReasoningOptions.builder()
            .mode(Mode.DEFAULT)
            .build();
    }

    /**
     * Request reasoning when the selected provider adapter supports an explicit enable switch.
     *
     * @return reasoning options with {@link Mode#ENABLED}
     */
    public static ReasoningOptions enabled() {
        return ReasoningOptions.builder()
            .mode(Mode.ENABLED)
            .build();
    }

    /**
     * Request non-reasoning behavior when the selected provider adapter supports an explicit
     * disable switch. This is useful for latency-sensitive requests.
     *
     * @return reasoning options with {@link Mode#DISABLED}
     */
    public static ReasoningOptions disabled() {
        return ReasoningOptions.builder()
            .mode(Mode.DISABLED)
            .build();
    }

    /**
     * Request a provider-supported reasoning effort level.
     *
     * @param effort requested effort level
     * @return reasoning options with the requested effort
     * @throws NullPointerException when {@code effort} is null
     */
    public static ReasoningOptions effort(Effort effort) {
        return ReasoningOptions.builder()
            .mode(Mode.ENABLED)
            .effort(Objects.requireNonNull(effort, "effort must not be null"))
            .build();
    }

    /**
     * Whether this value asks the provider adapter to apply an explicit reasoning control.
     *
     * @return true when mode is enabled, mode is disabled, or an effort is set
     */
    public boolean isExplicit() {
        return effort != null || mode == Mode.ENABLED || mode == Mode.DISABLED;
    }

    /**
     * Request-scoped reasoning mode.
     */
    public enum Mode {
        /**
         * Leave reasoning behavior to the provider and selected model.
         */
        DEFAULT,

        /**
         * Ask the provider adapter to enable reasoning when it has a native mapping.
         */
        ENABLED,

        /**
         * Ask the provider adapter to disable reasoning when it has a native mapping.
         */
        DISABLED
    }

    /**
     * Portable effort intent for providers that expose effort-style reasoning controls.
     */
    public enum Effort {
        /**
         * Lower reasoning effort where supported.
         */
        LOW,

        /**
         * Balanced reasoning effort where supported.
         */
        MEDIUM,

        /**
         * Higher reasoning effort where supported.
         */
        HIGH
    }
}
