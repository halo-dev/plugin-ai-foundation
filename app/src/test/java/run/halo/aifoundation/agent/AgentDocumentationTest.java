package run.halo.aifoundation.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDocumentationTest {

    @Test
    void bilingualGuidesNavigationAndSkillMapReferenceThePublishedAgentSurface()
        throws IOException {
        var root = repositoryRoot();
        var chineseGuide = read(root, "dev/zh-CN/sdk-core/agents.md");
        var englishGuide = read(root, "dev/en/sdk-core/agents.md");
        for (var type : List.of("Agent", "AgentOptions", "AgentCall", "PreparedAgentCall",
            "ToolCallFailureKind", "UIMessageChatHandlers.streamAgent")) {
            assertThat(chineseGuide).contains(type);
            assertThat(englishGuide).contains(type);
        }
        assertThat(read(root, "dev/zh-CN/sdk-core/README.md")).contains("./agents.md");
        assertThat(read(root, "dev/en/sdk-core/README.md")).contains("./agents.md");
        assertThat(read(root, "dev/zh-CN/sdk-core/api-reference.md"))
            .contains("AgentCallPrepareContext", "ToolCallFailureKind");
        assertThat(read(root, "dev/en/sdk-core/api-reference.md"))
            .contains("AgentCallPrepareContext", "ToolCallFailureKind");
        assertThat(read(root, "skills/use-ai-foundation-sdk/references/sdk-map.md"))
            .contains("sdk-core/agents.md", "aifoundation/agent/");
    }

    @Test
    void everyDocumentedAgentTopLevelTypeHasPublicSource() {
        var root = repositoryRoot();
        for (var type : List.of("Agent", "AgentOptions", "AgentCall", "AgentCallValidator",
            "AgentCallPrepare", "AgentCallPrepareContext", "PreparedAgentCall",
            "AgentCallPhase", "AgentCallException")) {
            assertThat(root.resolve("api/src/main/java/run/halo/aifoundation/agent/"
                + type + ".java"))
                .as("published source for %s", type)
                .isRegularFile();
        }
    }

    private String read(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath));
    }

    private Path repositoryRoot() {
        var current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        assertThat(current).as("repository root").isNotNull();
        return current;
    }
}
