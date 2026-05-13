package run.halo.aifoundation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatChunk {
    private ChunkType type;
    private String content;
    private boolean last;
    private String finishReason;
    private Usage usage;
}
