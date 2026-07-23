package run.halo.aifoundation.provider.support.openai;

/**
 * Standard OpenAI Chat Completions dialect whose function arguments are appendable fragments.
 */
public final class OpenAiChatCompletionsStreamDialect implements StreamDialect {

    @Override
    public ArgumentDelta normalizeArguments(int index, String accumulatedArguments,
        String providerArguments) {
        return ArgumentDelta.append(providerArguments);
    }
}
