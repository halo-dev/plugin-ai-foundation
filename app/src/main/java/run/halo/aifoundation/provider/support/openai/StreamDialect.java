package run.halo.aifoundation.provider.support.openai;

/**
 * Normalizes provider-specific tool argument chunk semantics into appendable deltas.
 */
public interface StreamDialect {

    ArgumentDelta normalizeArguments(int index, String accumulatedArguments,
        String providerArguments);

    record ArgumentDelta(String delta, boolean reliable) {

        public static ArgumentDelta append(String delta) {
            return new ArgumentDelta(delta, true);
        }

        public static ArgumentDelta unreliable() {
            return new ArgumentDelta(null, false);
        }
    }
}
