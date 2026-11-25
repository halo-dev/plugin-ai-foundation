package run.halo.aifoundation.provider.diagnostics;

/** Result of analysing one provider failure. */
public record ProviderFailureAnalysis(
        ProviderFailureCategory category, Integer httpStatus, String summary, String detail) {}
