package run.halo.aifoundation.provider.support.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;

class ImageParameterMappingMergerTest {

    @Test
    void preservesNativeDefaultsWhenNoPortableParameterWasApplied() {
        var body = new LinkedHashMap<String, Object>();
        body.put("size", "2048x2048");
        body.put("quality", "hd");

        var result = ImageParameterMappingMerger.merge(body, new ParameterMappingTarget());

        assertThat(result)
            .containsEntry("size", "2048x2048")
            .containsEntry("quality", "hd");
    }

    @Test
    void replacesOnlyTheNativeDefaultOwnedByAppliedPortableParameter() {
        var body = new LinkedHashMap<String, Object>();
        body.put("n", 4);
        body.put("size", "2048x2048");
        body.put("quality", "hd");
        var target = new ParameterMappingTarget();
        target.root().put("batch_size", 2);
        target.recordAppliedParameter(ModelParameter.IMAGE_COUNT);

        var result = ImageParameterMappingMerger.merge(body, target);

        assertThat(result)
            .doesNotContainKey("n")
            .containsEntry("batch_size", 2)
            .containsEntry("size", "2048x2048")
            .containsEntry("quality", "hd");
    }

    @Test
    void replacesAliasesAcrossRootAndParametersWithoutDroppingOtherDefaults() {
        var body = new LinkedHashMap<String, Object>();
        body.put("size", "1024x1024");
        body.put("width", 1024);
        body.put("parameters", Map.of(
            "size", "1024*1024",
            "seed", 7,
            "watermark", false));
        var target = new ParameterMappingTarget();
        target.parameters().put("output_resolution", "2048*2048");
        target.recordAppliedParameter(ModelParameter.IMAGE_SIZE);

        var result = ImageParameterMappingMerger.merge(body, target);

        assertThat(result).doesNotContainKeys("size", "width");
        assertThat(parameters(result))
            .doesNotContainKey("size")
            .containsEntry("output_resolution", "2048*2048")
            .containsEntry("seed", 7)
            .containsEntry("watermark", false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters(Map<String, Object> body) {
        return (Map<String, Object>) body.get("parameters");
    }
}
