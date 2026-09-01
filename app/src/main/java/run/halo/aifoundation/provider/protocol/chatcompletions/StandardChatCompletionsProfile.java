package run.halo.aifoundation.provider.protocol.chatcompletions;

import java.util.Objects;

/**
 * Immutable profile for providers that use standard delta-based Chat Completions streaming.
 */
public record StandardChatCompletionsProfile(String providerType, String adapterType,
                                             StreamDialect toolInputStreamDialect)
    implements ChatCompletionsProfile {

    public StandardChatCompletionsProfile {
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(adapterType, "adapterType must not be null");
        Objects.requireNonNull(toolInputStreamDialect,
            "toolInputStreamDialect must not be null");
    }

    public StandardChatCompletionsProfile(String providerType, String adapterType) {
        this(providerType, adapterType, new DeltaToolInputStreamDialect());
    }
}
