package run.halo.aifoundation.provider.protocol.responses;

import java.util.Objects;

public record StandardResponsesProfile(String providerType, String adapterType)
    implements ResponsesProfile {

    public StandardResponsesProfile {
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(adapterType, "adapterType must not be null");
    }
}
