package run.halo.aifoundation;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationContentPart {
    public static final String TYPE_TEXT = "text";

    private String type;
    private String text;
    private Map<String, Object> metadata;

    public static GenerationContentPart text(String text) {
        return GenerationContentPart.builder()
            .type(TYPE_TEXT)
            .text(text)
            .build();
    }
}
