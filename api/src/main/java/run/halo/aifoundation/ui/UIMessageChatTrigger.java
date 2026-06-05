package run.halo.aifoundation.ui;

import java.util.Locale;

/**
 * Chat transport trigger describing how the caller wants to process messages.
 */
public enum UIMessageChatTrigger {
    /**
     * A user submitted a new message.
     */
    SUBMIT_MESSAGE("submit-message"),
    /**
     * A caller requests regeneration of an existing assistant message.
     */
    REGENERATE_MESSAGE("regenerate-message");

    private final String value;

    UIMessageChatTrigger(String value) {
        this.value = value;
    }

    /**
     * Returns the transport value used by chat request payloads.
     *
     * @return lower-case trigger value
     */
    public String value() {
        return value;
    }

    /**
     * Parses a trigger value from transport input.
     *
     * @param value trigger value such as {@code submit-message}
     * @return parsed trigger
     * @throws IllegalArgumentException when the value is blank or unsupported
     */
    public static UIMessageChatTrigger fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("trigger must not be blank");
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        for (var trigger : values()) {
            if (trigger.value.equals(normalized)
                || trigger.name().toLowerCase(Locale.ROOT).equals(normalized.replace('-', '_'))) {
                return trigger;
            }
        }
        throw new IllegalArgumentException("Unsupported UI message chat trigger: " + value);
    }
}
