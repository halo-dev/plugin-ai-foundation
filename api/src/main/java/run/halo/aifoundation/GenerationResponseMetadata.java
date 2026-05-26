package run.halo.aifoundation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResponseMetadata {
    private String id;
    private String model;
    private Instant timestamp;
    private List<ModelMessage> messages;
    private Map<String, List<String>> headers;
    private Object body;
    private Map<String, Object> metadata;
}
