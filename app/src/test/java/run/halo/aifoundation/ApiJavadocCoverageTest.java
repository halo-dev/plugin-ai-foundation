package run.halo.aifoundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class ApiJavadocCoverageTest {

    private static final Path API_SOURCES =
        Path.of("..", "api", "src", "main", "java", "run", "halo", "aifoundation");

    private static final List<DocumentedProperties> CHANGED_PROPERTIES = List.of(
        properties("chat/GenerateTextRequest.java", "GenerateTextRequest",
            "minP", "repetitionPenalty", "logprobs", "topLogprobs", "parallelToolCalls"),
        properties("chat/PreparedStep.java", "PreparedStep",
            "minP", "repetitionPenalty", "logprobs", "topLogprobs", "parallelToolCalls"),
        properties("chat/StepContext.java", "StepContext",
            "maxOutputTokens", "temperature", "topP", "topK", "minP", "presencePenalty",
            "frequencyPenalty", "repetitionPenalty", "logprobs", "topLogprobs",
            "parallelToolCalls", "stopSequences"),
        properties("image/GenerateImageRequest.java", "GenerateImageRequest", "negativePrompt"),
        properties("message/ModelMessagePart.java", "ModelMessagePart", "providerMetadata")
    );

    @Test
    void publicPropertiesAddedOrRenamedByThisChangeHaveJavadoc() throws Exception {
        var violations = findMissingJavadocs();

        assertThat(violations)
            .as("Public API properties added or renamed by this change must have Javadoc")
            .isEmpty();
    }

    private static List<String> findMissingJavadocs() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK compiler is required for the API Javadoc audit").isNotNull();

        var sourceRoot = API_SOURCES.toAbsolutePath().normalize();
        var paths = CHANGED_PROPERTIES.stream()
            .map(target -> sourceRoot.resolve(target.file()))
            .toList();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null,
            StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, files, null,
                List.of("-proc:none", "--release", "21"), null,
                files.getJavaFileObjectsFromPaths(paths));
            var units = task.parse();
            var docTrees = DocTrees.instance(task);
            var violations = new ArrayList<String>();
            var found = new HashSet<String>();
            for (var unit : units) {
                new ChangedPropertyScanner(docTrees, unit, violations, found).scan(unit, null);
            }
            CHANGED_PROPERTIES.forEach(target -> target.fields().forEach(field -> {
                var key = key(target.className(), field);
                if (!found.contains(key)) {
                    violations.add(target.file() + " missing declaration " + field);
                }
            }));
            return violations;
        }
    }

    private static final class ChangedPropertyScanner extends TreePathScanner<Void, Void> {

        private final DocTrees docTrees;
        private final CompilationUnitTree unit;
        private final List<String> violations;
        private final Set<String> found;

        private ChangedPropertyScanner(DocTrees docTrees, CompilationUnitTree unit,
            List<String> violations, Set<String> found) {
            this.docTrees = docTrees;
            this.unit = unit;
            this.violations = violations;
            this.found = found;
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            var parent = getCurrentPath().getParentPath();
            if (parent == null || !(parent.getLeaf() instanceof ClassTree type)) {
                return super.visitVariable(tree, unused);
            }
            var className = type.getSimpleName().toString();
            var fieldName = tree.getName().toString();
            var target = CHANGED_PROPERTIES.stream()
                .filter(candidate -> candidate.className().equals(className))
                .filter(candidate -> candidate.fields().contains(fieldName))
                .findFirst();
            if (target.isEmpty()) {
                return super.visitVariable(tree, unused);
            }

            found.add(key(className, fieldName));
            var comment = docTrees.getDocCommentTree(getCurrentPath());
            if (comment == null || comment.getFullBody().isEmpty()
                && comment.getBlockTags().isEmpty()) {
                long position = docTrees.getSourcePositions().getStartPosition(unit, tree);
                long line = position >= 0 ? unit.getLineMap().getLineNumber(position) : -1;
                violations.add(target.orElseThrow().file() + ":" + line + " " + fieldName);
            }
            return super.visitVariable(tree, unused);
        }
    }

    private static DocumentedProperties properties(String file, String className,
        String... fields) {
        return new DocumentedProperties(file, className, Set.of(fields));
    }

    private static String key(String className, String field) {
        return className + "#" + field;
    }

    private record DocumentedProperties(String file, String className, Set<String> fields) {
    }
}
