package run.halo.aifoundation.rag;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Safe RAG lifecycle event payload.
 */
@Value
@Builder
public class RagLifecycleEvent {

    String stage;

    String query;

    Integer sourceCount;

    Integer contextCharacters;

    String warningCode;

    String errorType;

    String errorMessage;

    Map<String, Object> metadata;

    Map<String, Object> context;
}
