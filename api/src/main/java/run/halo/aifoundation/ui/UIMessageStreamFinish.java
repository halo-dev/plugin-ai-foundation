package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Complete terminal context for a created UI message stream.
 *
 * @param messages original conversation with the response message appended or replaced
 * @param responseMessage final aggregated assistant response message
 * @param isContinuation whether the response replaced the last assistant message
 * @param terminal terminal finish, error, or abort state
 */
public record UIMessageStreamFinish<M>(List<UIMessage<M>> messages,
                                       UIMessage<M> responseMessage,
                                       boolean isContinuation,
                                       UIMessageStreamTerminal terminal) {
    public UIMessageStreamFinish {
        messages = List.copyOf(messages);
    }
}
