package run.halo.aifoundation.provider.protocol.responses;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Provider-profile normalization result for a non-standard Responses output item. */
public record ResponsesProviderOutput(
    List<String> text,
    List<String> reasoning,
    List<JsonNode> sources,
    List<JsonNode> files,
    boolean preserveItem
) {
    public ResponsesProviderOutput {
        text = text == null ? List.of() : List.copyOf(text);
        reasoning = reasoning == null ? List.of() : List.copyOf(reasoning);
        sources = sources == null ? List.of() : List.copyOf(sources);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static ResponsesProviderOutput preserved() {
        return new ResponsesProviderOutput(List.of(), List.of(), List.of(), List.of(), true);
    }
}
