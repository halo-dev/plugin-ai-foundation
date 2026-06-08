package run.halo.aifoundation.ui;

/**
 * Controls how accumulated UI reasoning parts are converted back into model context.
 */
public enum UIReasoningConversion {
    /**
     * Resolve reasoning conversion automatically at the caller boundary.
     *
     * <p>{@link UIMessageChatHandlers} resolves this value from the selected language model
     * capabilities. Direct converter calls treat it as {@link #PRESERVE_PROVIDER_STATE}.
     */
    AUTO,
    /**
     * Drop reasoning parts from model context.
     */
    DROP,
    /**
     * Preserve reasoning text and provider metadata as model reasoning context.
     */
    PRESERVE_PROVIDER_STATE,
    /**
     * Convert reasoning text into plain model text context.
     */
    INCLUDE_TEXT_AS_CONTEXT,
    /**
     * Preserve reasoning state and fail when a reasoning part has neither text nor
     * provider metadata.
     */
    STRICT
}
