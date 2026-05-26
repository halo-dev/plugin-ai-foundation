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
public class ModelMessagePart {
    public static final String TYPE_TEXT = "text";

    private String type;
    private String text;
    private Map<String, Object> providerOptions;

    public static ModelMessagePart text(String text) {
        return new ModelMessagePart(TYPE_TEXT, text, null);
    }
}
