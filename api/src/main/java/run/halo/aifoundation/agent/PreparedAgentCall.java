package run.halo.aifoundation.agent;

import java.util.Objects;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;

/**
 * Effective model and request produced by one-time agent call preparation.
 */
@Value
public class PreparedAgentCall {
    LanguageModel model;
    GenerateTextRequest request;

    public PreparedAgentCall(LanguageModel model, GenerateTextRequest request) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.request = Objects.requireNonNull(request, "request must not be null");
    }
}
