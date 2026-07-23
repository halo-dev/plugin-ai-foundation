package run.halo.aifoundation.provider.support.openai;

/**
 * Dialect for providers that stream cumulative argument snapshots rather than appendable text.
 */
public final class CumulativeToolInputStreamDialect implements StreamDialect {

    @Override
    public ArgumentDelta normalizeArguments(int index, String accumulatedArguments,
        String providerArguments) {
        var previous = accumulatedArguments != null ? accumulatedArguments : "";
        var current = providerArguments != null ? providerArguments : "";
        if (!current.startsWith(previous)) {
            return ArgumentDelta.unreliable();
        }
        return ArgumentDelta.append(current.substring(previous.length()));
    }
}
