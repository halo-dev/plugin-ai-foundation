package run.halo.aifoundation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumerDocumentationTest {

    private static final List<String> REQUIRED_HEADINGS = List.of(
        "## 快速开始",
        "## 获取服务",
        "## 常用类型",
        "## 生成文本",
        "## 流式文本",
        "## 结构化输出",
        "## 工具调用",
        "## 设置",
        "## 嵌入",
        "## 能力支持说明",
        "## 错误和告警",
        "## 测试和排查",
        "## 高级 Provider Options"
    );

    private static final List<String> PUBLIC_TYPES = List.of(
        "AiModelService",
        "LanguageModel",
        "GenerateTextRequest",
        "GenerateTextResult",
        "StreamTextResult",
        "PreparedStep",
        "StepContext",
        "StopCondition",
        "ReasoningOptions",
        "GenerationTimeouts",
        "CancellationSource",
        "ModelMessage",
        "ModelMessagePart",
        "OutputSpec",
        "JsonSchema",
        "ToolDefinition",
        "ToolExecutionContext",
        "ToolCallRepairCallback",
        "ToolCallRepairContext",
        "ToolCallRepairResult",
        "ToolApprovalPolicy",
        "ToolApprovalRequest",
        "ToolApprovalResponse",
        "ToolChoice",
        "ProviderOptions",
        "EmbeddingModel",
        "EmbeddingRequest",
        "EmbeddingResponse",
        "EmbeddingUtils",
        "StructuredOutputValidationException"
    );

    @Test
    void devGuideKeepsCallerWorkflowHeadings() throws IOException {
        var guide = Files.readString(repoRoot().resolve("dev/dev.md"));

        assertThat(guide).doesNotContain("AI " + "SDK");
        assertThat(guide).doesNotContain("app/src/main/java");
        assertThat(guide).doesNotContain("ProviderClientCache");
        assertThat(guide).doesNotContain("SecretResolver");
        assertThat(guide).contains(REQUIRED_HEADINGS.toArray(String[]::new));
        assertThat(guide).contains(
            "getResponseMessages()",
            "conversationMessages.addAll(result.getResponseMessages())",
            "messages.addAll(first.getResponseMessages())",
            "messages.addAll(second.getResponseMessages())",
            "external-tool-pending",
            "tool-call-repaired",
            "tool-call-repair-failed",
            "ToolCallRepairContext",
            "ModelMessagePart.toolResult",
            "ModelMessagePart.toolError",
            "tool-approval-response",
            "ToolDefinition.strict(true)",
            "inputExamples",
            "context.getCancellationToken()",
            "原始 step index",
            "textStream()` 只包含回答文本 delta",
            "只发送修复后的 `tool-call`",
            "不会产生合成的 `tool-result` 或 `tool-error`"
        );
    }

    @Test
    void documentedPublicTypesExistInApiModule() {
        var apiSource = repoRoot().resolve("api/src/main/java/run/halo/aifoundation");

        for (var type : PUBLIC_TYPES) {
            assertThat(apiSource.resolve(type + ".java").toFile().exists()
                    || apiSource.resolve("chat/" + type + ".java").toFile().exists()
                    || apiSource.resolve("control/" + type + ".java").toFile().exists()
                    || apiSource.resolve("embedding/" + type + ".java").toFile().exists()
                    || apiSource.resolve("exception/" + type + ".java").toFile().exists()
                    || apiSource.resolve("message/" + type + ".java").toFile().exists()
                    || apiSource.resolve("options/" + type + ".java").toFile().exists()
                    || apiSource.resolve("part/" + type + ".java").toFile().exists()
                    || apiSource.resolve("schema/" + type + ".java").toFile().exists()
                    || apiSource.resolve("tool/" + type + ".java").toFile().exists())
                .as(type + " should exist in api module")
                .isTrue();
        }
    }

    private Path repoRoot() {
        var current = Path.of("").toAbsolutePath();
        if (Files.exists(current.resolve("dev/dev.md"))) {
            return current;
        }
        return current.getParent();
    }
}
