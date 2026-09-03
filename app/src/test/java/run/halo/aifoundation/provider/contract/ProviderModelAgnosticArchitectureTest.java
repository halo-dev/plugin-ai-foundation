package run.halo.aifoundation.provider.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProviderModelAgnosticArchitectureTest {

    private static final Pattern MODEL_IDENTIFIER_INSPECTION = Pattern.compile(
        "(?i)(?:model(?:Id|Name)?|normalized(?:Model|Id))\\s*\\.\\s*"
            + "(?:startsWith|endsWith|contains|matches|toLowerCase|toUpperCase)\\s*\\(");

    private static final Pattern MODEL_CATALOG_LITERAL = Pattern.compile(
        "(?i)(?:kimi-k\\d|minimax-m\\d|mimo-v\\d|glm-\\d|cogview|qwen(?:[-/]|\\d)|"
            + "deepseek-v\\d|gpt-\\d|gpt-oss|text-embedding-\\d|tao-8k|"
            + "black-forest-labs|flux(?:-\\d|\\d)|imagen-\\d|dall-e|bge-reranker|z-image)");

    @Test
    void productionProvidersDoNotEmbedOrInspectModelCatalogs() throws IOException {
        var violations = new ArrayList<String>();
        try (var files = Files.walk(providerSourceRoot())) {
            files.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> inspect(path, violations));
        }

        assertThat(violations)
            .as("Provider behavior must be selected by adapter, explicit mappings, or remote "
                + "structured metadata—not by model identifier text")
            .isEmpty();
    }

    private void inspect(Path path, List<String> violations) {
        try {
            var source = Files.readString(path);
            recordViolation(path, source, MODEL_IDENTIFIER_INSPECTION,
                "inspects model identifier text", violations);
            recordViolation(path, source, MODEL_CATALOG_LITERAL,
                "contains a model catalog literal", violations);
            if (source.contains("inferModelType(")) {
                violations.add(relative(path) + ": infers a model type");
            }
            if (inspectsPortableReasoning(path, source)) {
                violations.add(relative(path)
                    + ": translates portable reasoning outside model mappings");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to inspect " + path, error);
        }
    }

    private boolean inspectsPortableReasoning(Path path, String source) {
        var relativePath = relative(path);
        if (relativePath.startsWith("mapping/")) {
            return false;
        }
        if (relativePath.equals("support/ReasoningControlOptions.java")) {
            return false;
        }
        return source.contains("getReasoning(");
    }

    private void recordViolation(Path path, String source, Pattern pattern, String message,
        List<String> violations) {
        var matcher = pattern.matcher(source);
        if (matcher.find()) {
            violations.add(relative(path) + ": " + message + " ('" + matcher.group() + "')");
        }
    }

    private Path providerSourceRoot() {
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        var moduleSource = workingDirectory.resolve("src/main/java/run/halo/aifoundation/provider");
        if (Files.isDirectory(moduleSource)) {
            return moduleSource;
        }
        return workingDirectory.resolve("app/src/main/java/run/halo/aifoundation/provider");
    }

    private String relative(Path path) {
        return providerSourceRoot().relativize(path).toString();
    }
}
