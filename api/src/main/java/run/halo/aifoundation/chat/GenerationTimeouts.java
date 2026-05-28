package run.halo.aifoundation.chat;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * Provider-neutral timeout settings for one generation or embedding request.
 *
 * <p>{@link #totalTimeout} bounds the whole call, {@link #stepTimeout} bounds each provider model
 * invocation, and {@link #toolTimeout} bounds each server-side tool executor call.
 *
 * <pre>{@code
 * var request = GenerateTextRequest.builder()
 *     .prompt("Use a tool if needed")
 *     .timeouts(GenerationTimeouts.builder()
 *         .totalTimeout(Duration.ofMinutes(2))
 *         .stepTimeout(Duration.ofSeconds(45))
 *         .toolTimeout(Duration.ofSeconds(10))
 *         .build())
 *     .build();
 * }</pre>
 */
@Value
@Builder
@AllArgsConstructor
public class GenerationTimeouts {
    /**
     * Maximum duration for the whole generation request, including all steps and tool execution.
     */
    Duration totalTimeout;
    /**
     * Maximum duration for a single provider generation step.
     */
    Duration stepTimeout;
    /**
     * Maximum duration for a single server-side tool execution.
     */
    Duration toolTimeout;

    public static GenerationTimeouts total(Duration timeout) {
        return GenerationTimeouts.builder().totalTimeout(timeout).build();
    }
}
