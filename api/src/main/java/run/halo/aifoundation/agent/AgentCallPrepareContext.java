package run.halo.aifoundation.agent;

import java.util.Objects;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;

/**
 * Request-scoped context for one-time asynchronous agent call preparation.
 *
 * @param <O> call options type
 */
@Value
public class AgentCallPrepareContext<O> {
    AgentCall<O> call;
    O options;
    LanguageModel baseModel;
    GenerateTextRequest.GenerateTextRequestBuilder requestBuilder;

    public AgentCallPrepareContext(AgentCall<O> call, O options, LanguageModel baseModel,
        GenerateTextRequest.GenerateTextRequestBuilder requestBuilder) {
        this.call = Objects.requireNonNull(call, "call must not be null");
        this.options = options;
        this.baseModel = Objects.requireNonNull(baseModel, "baseModel must not be null");
        this.requestBuilder = Objects.requireNonNull(requestBuilder,
            "requestBuilder must not be null");
    }

    /**
     * Builds a prepared call using the agent's base model.
     */
    public PreparedAgentCall prepared() {
        return prepared(baseModel);
    }

    /**
     * Builds a prepared call using a model selected for this invocation only.
     */
    public PreparedAgentCall prepared(LanguageModel model) {
        return new PreparedAgentCall(model, requestBuilder.build());
    }
}
