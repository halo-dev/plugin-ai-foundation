package run.halo.aifoundation.decision;

import java.beans.Transient;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationToken;

/** One shared textual or JSON state and independently evaluated named questions. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequest {
    /** Text, a JSON object, or a JSON array; media assets are not supported. */
    private Object state;
    private Map<String, DecisionQuestion> questions;
    /** Local audit metadata, never sent to the provider. */
    private Map<String, Object> metadata;
    private transient CancellationToken cancellationToken;
    private transient GenerationTimeouts timeouts;

    @Transient
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    @Transient
    public GenerationTimeouts getTimeouts() {
        return timeouts;
    }
}
