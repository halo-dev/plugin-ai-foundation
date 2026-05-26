package run.halo.aifoundation;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMessage {
    private ModelMessageRole role;
    private List<ModelMessagePart> content;

    public static ModelMessage system(String text) {
        return of(ModelMessageRole.SYSTEM, text);
    }

    public static ModelMessage user(String text) {
        return of(ModelMessageRole.USER, text);
    }

    public static ModelMessage assistant(String text) {
        return of(ModelMessageRole.ASSISTANT, text);
    }

    public static ModelMessage tool(List<ModelMessagePart> content) {
        return new ModelMessage(ModelMessageRole.TOOL, content);
    }

    private static ModelMessage of(ModelMessageRole role, String text) {
        return new ModelMessage(role, List.of(ModelMessagePart.text(text)));
    }
}
