package run.halo.aifoundation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;
import org.junit.jupiter.api.Test;

class UIMessageConversionValidationTest {

    record Metadata(String chatId) {
    }

    @Test
    void convertsRolesAndTextParts() {
        var messages = UIMessageConverters.toModelMessages(List.of(
            new UIMessage<>("sys", UIMessageRole.SYSTEM,
                List.of(UIMessageParts.text("sys-text", "system")), new Metadata("chat")),
            new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("user-text", "hello")), new Metadata("chat")),
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT,
                List.of(UIMessageParts.text("assistant-text", "hi")), new Metadata("chat"))
        ));

        assertThat(messages).hasSize(3);
        assertThat(messages).extracting("role")
            .containsExactly(ModelMessageRole.SYSTEM, ModelMessageRole.USER,
                ModelMessageRole.ASSISTANT);
        assertThat(messages.get(0).getContent().getFirst().getText()).isEqualTo("system");
        assertThat(messages.get(1).getContent().getFirst().getText()).isEqualTo("hello");
        assertThat(messages.get(2).getContent().getFirst().getText()).isEqualTo("hi");
    }

    @Test
    void convertsToolCallsResultsAndErrors() {
        var result = UIMessageConverters.convertToModelMessages(List.of(
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.text("text", "checking"),
                UIMessageParts.tool("call-1", "weather", ToolPartState.OUTPUT_AVAILABLE,
                    Map.of("city", "Hangzhou"), null, Map.of("temp", 20), null, null,
                    Map.of()),
                UIMessageParts.tool("call-2", "search", ToolPartState.OUTPUT_ERROR,
                    Map.of("q", "Halo"), null, null, "failed", null, Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.messages()).hasSize(4);
        assertThat(result.messages().get(0).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(0).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TEXT, PartType.TOOL_CALL);
        assertThat(result.messages().get(1).getRole()).isEqualTo(ModelMessageRole.TOOL);
        assertThat(result.messages().get(1).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_RESULT);
        assertThat(result.messages().get(1).getContent().getFirst().getResult())
            .isEqualTo(Map.of("temp", 20));
        assertThat(result.messages().get(2).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(2).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_CALL);
        assertThat(result.messages().get(3).getRole()).isEqualTo(ModelMessageRole.TOOL);
        assertThat(result.messages().get(3).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_ERROR);
    }

    @Test
    void convertsDynamicTerminalToolsAndSkipsPendingTools() {
        var result = UIMessageConverters.convertToModelMessages(List.of(
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.text("text", "checking"),
                UIMessageParts.tool("call-1", "weather", ToolPartState.INPUT_AVAILABLE,
                    Map.of("city", "Hangzhou"), null, null, null, null, Map.of()),
                UIMessageParts.tool("call-2", "search", ToolPartState.OUTPUT_AVAILABLE,
                    Map.of("q", "Halo"), null, Map.of("result", "Halo"), null, null, Map.of()),
                UIMessageParts.tool("call-3", "pay", ToolPartState.OUTPUT_ERROR,
                    Map.of("amount", 1), null, null, "Denied", new ToolApproval(
                        "approval-1", false, "Denied by user"), Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(result.warnings()).extracting(UIMessageConversionWarning::code)
            .containsExactly("tool.pending-skipped");
        assertThat(result.messages()).hasSize(4);
        assertThat(result.messages().get(0).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(0).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TEXT, PartType.TOOL_CALL);
        assertThat(result.messages().get(1).getRole()).isEqualTo(ModelMessageRole.TOOL);
        assertThat(result.messages().get(1).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_RESULT);
        assertThat(result.messages().get(1).getContent().get(0).getResult())
            .isEqualTo(Map.of("result", "Halo"));
        assertThat(result.messages().get(2).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(2).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_CALL);
        assertThat(result.messages().get(3).getRole()).isEqualTo(ModelMessageRole.TOOL);
        assertThat(result.messages().get(3).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_ERROR);
        assertThat(result.messages().get(3).getContent().getFirst().getErrorText())
            .isEqualTo("Denied");
    }

    @Test
    void convertsApprovedToolApprovalWithOriginalToolCall() {
        var result = UIMessageConverters.convertToModelMessages(List.of(
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.tool("call-1", "halo_test_info", ToolPartState.INPUT_AVAILABLE,
                    Map.of("query", "hello"), null, null, null,
                    new ToolApproval("approval-1", true, "approved"), Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(0).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_CALL, PartType.TOOL_APPROVAL_REQUEST);
        assertThat(result.messages().get(1).getRole()).isEqualTo(ModelMessageRole.TOOL);
        assertThat(result.messages().get(1).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_APPROVAL_RESPONSE);
    }

    @Test
    void warnsForSkippedUiOnlyPartsAndEmptyMessages() {
        var result = UIMessageConverters.convertToModelMessages(List.of(
            new UIMessage<>("ui-only", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.data("status", "loading"),
                UIMessageParts.sourceUrl("source-1", "https://example.com", "Example", Map.of()),
                UIMessageParts.file("file-1", "data", "a.txt", "text/plain", "hello", Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(result.messages()).isEmpty();
        assertThat(result.warnings()).extracting(UIMessageConversionWarning::code)
            .contains(
                "data.converter-missing",
                "source.skipped",
                "file.skipped",
                "message.empty-after-conversion"
            );
    }

    @Test
    void convertsTerminalToolWithBoundaryOrder() {
        var result = UIMessageConverters.convertToModelMessages(List.of(
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.reasoning("reasoning-1", "Need payment approval.",
                    Map.of("signature", "opaque")),
                UIMessageParts.tool("call-1", "pay", ToolPartState.OUTPUT_AVAILABLE,
                    Map.of("amount", 1), null, Map.of("ok", true),
                    null, new ToolApproval("approval-1", true, "Approved"),
                    Map.of("provider", "meta")),
                UIMessageParts.text("final", "done")
            ), new Metadata("chat"))
        ));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.messages()).hasSize(3);
        assertThat(result.messages().get(0).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(0).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.REASONING, PartType.TOOL_CALL);
        assertThat(result.messages().get(0).getContent().getFirst().getProviderOptions())
            .isEqualTo(Map.of("signature", "opaque"));
        assertThat(result.messages().get(1).getRole()).isEqualTo(ModelMessageRole.TOOL);
        assertThat(result.messages().get(1).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TOOL_RESULT);
        assertThat(result.messages().get(2).getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
        assertThat(result.messages().get(2).getContent()).extracting(ModelMessagePart::getType)
            .containsExactly(PartType.TEXT);
    }

    @Test
    void failsForStrictUnsupportedAndEmptyPolicies() {
        var dataOnly = List.of(new UIMessage<>("data", UIMessageRole.USER,
            List.of(UIMessageParts.data("status", "loading")), new Metadata("chat")));

        assertThatThrownBy(() -> UIMessageConverters.toModelMessages(dataOnly, options -> options
            .unsupportedPartPolicy(UnsupportedUIMessagePartPolicy.FAIL)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not converted");

        assertThatThrownBy(() -> UIMessageConverters.toModelMessages(dataOnly, options -> options
            .unsupportedPartPolicy(UnsupportedUIMessagePartPolicy.IGNORE)
            .emptyMessagePolicy(EmptyUIMessagePolicy.FAIL)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("produced no model content");
    }

    @Test
    void appliesNamedDataAndCustomPartConverters() {
        var result = UIMessageConverters.convertToModelMessages(List.of(
            new UIMessage<>("user", UIMessageRole.USER, List.of(
                UIMessageParts.data("postDraft", Map.of("title", "Hello")),
                UIMessageParts.sourceUrl("source-1", "https://halo.run", "Halo", Map.of())
            ), new Metadata("chat-1"))
        ), options -> options
            .dataConverter("postDraft", (part, context) -> List.of(ModelMessagePart.text(
                "Draft: " + ((Map<?, ?>) part.data()).get("title")
            )))
            .partConverter((part, context) -> {
                if (part instanceof SourceUrlPart source) {
                    return List.of(ModelMessagePart.text(
                        "Source for " + context.metadata().chatId() + ": " + source.url()));
                }
                return List.of();
            }));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().getContent()).extracting(ModelMessagePart::getText)
            .containsExactly("Draft: Hello", "Source for chat-1: https://halo.run");
    }

    @Test
    void preservesReasoningByDefaultButCanIncludeTextExplicitly() {
        var messages = List.of(new UIMessage<>("assistant", UIMessageRole.ASSISTANT,
            List.of(UIMessageParts.reasoning("reasoning-1", "hidden", Map.of("opaque", "state"))),
            new Metadata("chat")));

        var defaultResult = UIMessageConverters.convertToModelMessages(messages);
        assertThat(defaultResult.warnings()).isEmpty();
        assertThat(defaultResult.messages()).hasSize(1);
        assertThat(defaultResult.messages().getFirst().getContent().getFirst().getType())
            .isEqualTo(PartType.REASONING);
        assertThat(defaultResult.messages().getFirst().getContent().getFirst().getProviderOptions())
            .isEqualTo(Map.of("opaque", "state"));

        var included = UIMessageConverters.toModelMessages(messages, options -> options
            .reasoningConversion(UIReasoningConversion.INCLUDE_TEXT_AS_CONTEXT));
        assertThat(included).hasSize(1);
        assertThat(included.getFirst().getContent().getFirst().getText()).isEqualTo("hidden");
    }

    @Test
    void handlesEmptyReasoningByConversionPolicy() {
        var messages = List.of(new UIMessage<>("assistant", UIMessageRole.ASSISTANT,
            List.of(UIMessageParts.reasoning("reasoning-1", "", Map.of())),
            new Metadata("chat")));

        var defaultResult = UIMessageConverters.convertToModelMessages(messages);
        assertThat(defaultResult.messages()).isEmpty();
        assertThat(defaultResult.warnings()).extracting(UIMessageConversionWarning::code)
            .contains("reasoning.empty-skipped", "message.empty-after-conversion");

        var dropped = UIMessageConverters.convertToModelMessages(messages,
            options -> options.reasoningConversion(UIReasoningConversion.DROP));
        assertThat(dropped.warnings()).extracting(UIMessageConversionWarning::code)
            .contains("message.empty-after-conversion")
            .doesNotContain("reasoning.empty-skipped");

        assertThatThrownBy(() -> UIMessageConverters.toModelMessages(messages,
            options -> options.reasoningConversion(UIReasoningConversion.STRICT)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reasoning part must include text or provider metadata");
    }

    @Test
    void validatesRequiredMessageAndPartFields() {
        var result = UIMessageValidators.safeValidate(List.of(
            new UIMessage<>("", UIMessageRole.USER, List.of(
                UIMessageParts.text("", "hello"),
                new DataPart("data-status", "", "status", "value", false)
            ), new Metadata("chat"))
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).extracting(UIMessageValidationIssue::code)
            .contains("message.id.required", "part.id.required",
                "part.data.id.required");
        assertThatThrownBy(() -> UIMessageValidators.validate(result.messages()))
            .isInstanceOf(InvalidUIMessageException.class);
    }

    @Test
    void validatesDuplicateFinalToolOutputs() {
        var result = UIMessageValidators.safeValidate(List.of(
            new UIMessage<>("m1", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.tool("call-1", "weather", ToolPartState.OUTPUT_AVAILABLE,
                    Map.of("city", "Hangzhou"), null, "sunny", null, null, Map.of()),
                UIMessageParts.tool("call-1", "weather", ToolPartState.OUTPUT_ERROR,
                    Map.of("city", "Hangzhou"), null, null, "failed", null, Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(result.issues()).extracting(UIMessageValidationIssue::code)
            .contains("tool.result.duplicate");
    }

    @Test
    void validatesApprovalRequestedAndDeniedToolStates() {
        var valid = UIMessageValidators.safeValidate(List.of(
            new UIMessage<>("m1", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.tool("call-1", "pay", ToolPartState.APPROVAL_REQUESTED,
                    Map.of("amount", 1), null, null, null,
                    new ToolApproval("approval-1", null, null), Map.of()),
                UIMessageParts.tool("call-2", "pay", ToolPartState.OUTPUT_ERROR,
                    Map.of("amount", 2), null, null, "Denied",
                    new ToolApproval("approval-2", false, "Denied"), Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(valid.isValid()).isTrue();
        assertThat(valid.issues()).isEmpty();
    }

    @Test
    void rejectsInvalidDynamicToolFields() {
        var result = UIMessageValidators.safeValidate(List.of(
            new UIMessage<>("m1", UIMessageRole.ASSISTANT, List.of(
                new ToolPart("tool-pay", "", "pay", ToolPartState.OUTPUT_ERROR,
                    Map.of("amount", 1), null, null, "", null, Map.of()),
                new ToolPart("tool-search", "call-2", "search", null,
                    Map.of("q", "Halo"), null, null, null, null, Map.of())
            ), new Metadata("chat"))
        ));

        assertThat(result.issues()).extracting(UIMessageValidationIssue::code)
            .contains(
                "part.tool.id.required",
                "part.tool.error-text.required",
                "part.tool.state.required"
            );
    }

    @Test
    void runsValidatorHooksAndCapturesExceptionsWithoutMutation() {
        var originalParts = new ArrayList<UIMessagePart>();
        originalParts.add(UIMessageParts.data("postDraft", Map.of("title", "")));
        var message = new UIMessage<>("m1", UIMessageRole.USER, originalParts,
            new Metadata("chat"));

        var result = UIMessageValidators.safeValidate(List.of(message), options -> options
            .metadataValidator((current, metadata, context) -> List.of(
                new UIMessageValidationIssue(current.id(), current.role().name(), null, null,
                    "metadata.invalid", metadata.chatId())))
            .dataValidator("postDraft", (current, part, context) -> List.of(
                new UIMessageValidationIssue(current.id(), current.role().name(), part.type(),
                    part.name(), "part.data.invalid", "draft title is required")))
            .toolValidator((current, part, context) -> {
                throw new IllegalStateException("tool validator failed");
            }));

        assertThat(result.issues()).extracting(UIMessageValidationIssue::code)
            .contains("metadata.invalid", "part.data.invalid");
        assertThat(result.messages().getFirst().parts()).containsExactlyElementsOf(originalParts);

        var toolResult = UIMessageValidators.safeValidate(List.of(
            new UIMessage<>("m2", UIMessageRole.ASSISTANT,
                List.of(UIMessageParts.tool("call-1", "weather", ToolPartState.INPUT_AVAILABLE,
                    Map.of(), null, null, null, null, Map.of())),
                new Metadata("chat"))
        ), options -> options.toolValidator((current, part, context) -> {
            throw new IllegalStateException("tool validator failed");
        }));

        assertThat(toolResult.issues()).extracting(UIMessageValidationIssue::code)
            .contains("validator.exception");
    }

    @Test
    void runsNamedDynamicToolValidators() {
        var result = UIMessageValidators.safeValidate(List.of(
            new UIMessage<>("m1", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.tool("call-1", "weather", ToolPartState.OUTPUT_AVAILABLE,
                    Map.of("city", ""), null, Map.of("temp", 20), null, null, Map.of()),
                UIMessageParts.tool("call-2", "search", ToolPartState.OUTPUT_AVAILABLE,
                    Map.of("q", "Halo"), null, Map.of("result", "Halo"), null, null, Map.of())
            ), new Metadata("chat"))
        ), options -> options.toolValidator("weather", (current, part, context) -> {
            var tool = (ToolPart) part;
            var input = (Map<?, ?>) tool.input();
            return input.get("city").toString().isBlank()
                ? List.of(new UIMessageValidationIssue(current.id(), current.role().name(),
                    tool.type(), tool.toolCallId(), "tool.weather.city.required",
                    "Weather tool city is required"))
                : List.of();
        }));

        assertThat(result.issues()).extracting(UIMessageValidationIssue::code)
            .contains("tool.weather.city.required")
            .doesNotContain("validator.exception");
    }
}
