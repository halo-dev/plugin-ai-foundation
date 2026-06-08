package run.halo.aifoundation.ui;

import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.LanguageModelUsage;

/**
 * Terminal state collected while reading a UI message stream.
 *
 * @param finishReason provider finish reason when available
 * @param usage token usage when available
 * @param aborted whether the stream ended because of cancellation
 * @param errorText caller-facing error text when the stream ended with an error
 */
public record UIMessageStreamTerminal(FinishReason finishReason, LanguageModelUsage usage,
                                      boolean aborted, String errorText) {

    /**
     * Creates an empty terminal state before a terminal chunk is observed.
     *
     * @return empty terminal state
     */
    public static UIMessageStreamTerminal empty() {
        return new UIMessageStreamTerminal(null, null, false, null);
    }

    /**
     * Returns a copy with successful finish details.
     *
     * @param finishReason provider finish reason
     * @param usage token usage
     * @return updated terminal state
     */
    public UIMessageStreamTerminal withFinish(FinishReason finishReason,
        LanguageModelUsage usage) {
        return new UIMessageStreamTerminal(finishReason, usage, aborted, errorText);
    }

    /**
     * Returns a copy with abort state.
     *
     * @param aborted whether the stream was aborted
     * @return updated terminal state
     */
    public UIMessageStreamTerminal withAborted(boolean aborted) {
        return new UIMessageStreamTerminal(finishReason, usage, aborted, errorText);
    }

    /**
     * Returns a copy with caller-facing error text.
     *
     * @param errorText error text
     * @return updated terminal state
     */
    public UIMessageStreamTerminal withErrorText(String errorText) {
        return new UIMessageStreamTerminal(finishReason, usage, aborted, errorText);
    }
}
