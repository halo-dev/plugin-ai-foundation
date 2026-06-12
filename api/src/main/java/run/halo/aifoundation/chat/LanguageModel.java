package run.halo.aifoundation.chat;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;

/**
 * Provider-neutral Java SDK for text generation.
 *
 * <p>Implementations are resolved from {@link AiModelService} and hide the underlying Spring AI
 * provider details. Use {@link GenerateTextRequest} for tools, structured output, streaming,
 * lifecycle callbacks, cancellation, timeouts, and provider options.
 */
public interface LanguageModel {

    /**
     * Generates text for a single-turn user prompt.
     */
    Mono<GenerateTextResult> generateText(String prompt);

    /**
     * Generates text from a full provider-neutral request.
     */
    Mono<GenerateTextResult> generateText(GenerateTextRequest request);

    /**
     * Starts a text stream. The returned result exposes both full protocol events and convenient
     * derived streams such as text deltas and final aggregate result.
     */
    StreamTextResult streamText(GenerateTextRequest request);

    /**
     * Returns read-only capability metadata for this resolved model.
     *
     * <p>The default is conservative so custom implementations can opt in to provider-specific
     * behavior only when they know it is supported.
     *
     * @return model capabilities
     */
    default LanguageModelCapabilities capabilities() {
        return LanguageModelCapabilities.defaults();
    }
}
