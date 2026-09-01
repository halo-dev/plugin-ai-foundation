package run.halo.aifoundation.provider.protocol.responses;

import java.util.Map;

public sealed interface ResponsesStreamPart {

    record TextDelta(String itemId, String delta) implements ResponsesStreamPart {
    }

    record ReasoningDelta(String itemId, String delta) implements ResponsesStreamPart {
    }

    record ToolInputStart(int outputIndex, String itemId, String callId, String name)
        implements ResponsesStreamPart {
    }

    record ToolInputDelta(int outputIndex, String itemId, String callId, String delta)
        implements ResponsesStreamPart {
    }

    record ToolInputEnd(int outputIndex, String itemId, String callId, String name,
                        String arguments) implements ResponsesStreamPart {
    }

    record Source(Map<String, Object> source) implements ResponsesStreamPart {
        public Source {
            source = Map.copyOf(source);
        }
    }

    record File(Map<String, Object> file) implements ResponsesStreamPart {
        public File {
            file = Map.copyOf(file);
        }
    }

    record Completed(ResponsesResult result) implements ResponsesStreamPart {
    }

    record Unknown(String eventType, Map<String, Object> providerMetadata)
        implements ResponsesStreamPart {
        public Unknown {
            providerMetadata = Map.copyOf(providerMetadata);
        }
    }
}
