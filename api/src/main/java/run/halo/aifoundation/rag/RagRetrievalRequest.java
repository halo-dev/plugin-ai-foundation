package run.halo.aifoundation.rag;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Request passed to a caller-provided RAG retriever.
 */
@Value
@Builder
public class RagRetrievalRequest {

    String query;

    GenerateTextRequest generationRequest;

    List<ModelMessage> messages;

    Integer maxResults;

    Double minScore;

    Map<String, Object> metadata;

    Map<String, Object> context;

    Map<String, Object> options;
}
