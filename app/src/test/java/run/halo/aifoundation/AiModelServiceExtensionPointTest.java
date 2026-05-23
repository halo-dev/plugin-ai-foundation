package run.halo.aifoundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pf4j.ExtensionPoint;
import org.yaml.snakeyaml.Yaml;
import run.halo.aifoundation.service.AiModelServiceImpl;

class AiModelServiceExtensionPointTest {

    @Test
    void aiModelServiceIsExtensionPoint() {
        assertThat(ExtensionPoint.class).isAssignableFrom(AiModelService.class);
    }

    @Test
    void extensionResourcesReferenceAiModelServiceAndImplementation() {
        var docs = loadExtensionResourceDocuments();

        var extensionPointDefinition = docs.stream()
            .filter(doc -> "ExtensionPointDefinition".equals(doc.get("kind")))
            .findFirst()
            .orElseThrow();
        assertThat(extensionPointDefinition)
            .extracting(doc -> ((Map<?, ?>) doc.get("metadata")).get("name"))
            .isEqualTo("ai-model-service");
        assertThat(extensionPointDefinition)
            .extracting(doc -> ((Map<?, ?>) doc.get("spec")).get("className"))
            .isEqualTo(AiModelService.class.getName());
        assertThat(extensionPointDefinition)
            .extracting(doc -> ((Map<?, ?>) doc.get("spec")).get("type"))
            .isEqualTo("SINGLETON");

        var extensionDefinition = docs.stream()
            .filter(doc -> "ExtensionDefinition".equals(doc.get("kind")))
            .findFirst()
            .orElseThrow();
        assertThat(extensionDefinition)
            .extracting(doc -> ((Map<?, ?>) doc.get("metadata")).get("name"))
            .isEqualTo("ai-foundation-ai-model-service");
        assertThat(extensionDefinition)
            .extracting(doc -> ((Map<?, ?>) doc.get("spec")).get("className"))
            .isEqualTo(AiModelServiceImpl.class.getName());
        assertThat(extensionDefinition)
            .extracting(doc -> ((Map<?, ?>) doc.get("spec")).get("extensionPointName"))
            .isEqualTo("ai-model-service");
    }

    private static ArrayList<Map<String, Object>> loadExtensionResourceDocuments() {
        var resourceName = "extensions/ai-model-service-extension-point.yaml";
        var resource = AiModelServiceExtensionPointTest.class.getClassLoader()
            .getResourceAsStream(resourceName);
        assertThat(resource).as(resourceName).isNotNull();

        var docs = new ArrayList<Map<String, Object>>();
        var yaml = new Yaml();
        for (var document : yaml.loadAll(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            assertThat(document).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            var map = (Map<String, Object>) document;
            docs.add(map);
        }
        return docs;
    }
}
