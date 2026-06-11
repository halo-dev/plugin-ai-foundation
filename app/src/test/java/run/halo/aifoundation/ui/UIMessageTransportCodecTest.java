package run.halo.aifoundation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UIMessageTransportCodecTest {

    record Metadata(String chatId) {
    }

    @Test
    void decodesAndEncodesAllBuiltInPartTypes() {
        var parts = List.of(
            UIMessageParts.text("text-1", "hello"),
            UIMessageParts.reasoning("reasoning-1", "thinking", Map.of("opaque", "state")),
            UIMessageParts.data("notice", Map.of("level", "info")),
            UIMessageParts.sourceUrl("source-1", "https://halo.run", "Halo", Map.of()),
            UIMessageParts.file("file-1", "https://example.com/a.txt", "a.txt", "text/plain",
                null, Map.of("provider", "meta")),
            UIMessageParts.tool("call-1", "weather", ToolPartState.OUTPUT_AVAILABLE,
                Map.of("city", "Hangzhou"), null, Map.of("temp", 20), null, null, Map.of()),
            UIMessageParts.tool("call-2", "search", ToolPartState.OUTPUT_ERROR,
                Map.of("q", "Halo"), null, null, "failed", null, Map.of()),
            UIMessageParts.tool("call-3", "pay", ToolPartState.APPROVAL_REQUESTED,
                Map.of("amount", 1), null, null, null, new ToolApproval("approval-1", null,
                    null), Map.of("approval", "meta"))
        );

        for (var part : parts) {
            var encoded = UIMessageTransportCodec.partToMap(part);
            assertThat(encoded).containsEntry("type", part.type());
            assertThat(encoded).doesNotContainValue(null);
            var decoded = UIMessageTransportCodec.partFromMap(encoded);
            assertThat(decoded).isEqualTo(part);
        }
    }

    @Test
    void decodesAndEncodesChatRequestWithMapMetadata() {
        var requestMap = Map.<String, Object>of(
            "id", "chat-1",
            "trigger", "regenerate-message",
            "messageId", "assistant-1",
            "messages", List.of(Map.of(
                "id", "assistant-1",
                "role", "assistant",
                "metadata", Map.of("chatId", "chat-1"),
                "parts", List.of(Map.of(
                    "type", "tool-pay",
                    "toolCallId", "call-1",
                    "toolName", "pay",
                    "state", "output-error",
                    "errorText", "Denied",
                    "approval", Map.of("id", "approval-1", "approved", false)
                ))
            ))
        );

        var request = UIMessageTransportCodec.chatRequestFromMap(requestMap);

        assertThat(request.id()).isEqualTo("chat-1");
        assertThat(request.trigger()).isEqualTo(UIMessageChatTrigger.REGENERATE_MESSAGE);
        assertThat(request.messageId()).isEqualTo("assistant-1");
        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().getFirst().metadata()).isEqualTo(Map.of("chatId", "chat-1"));
        assertThat(request.messages().getFirst().parts().getFirst())
            .isInstanceOf(ToolPart.class);

        var encoded = UIMessageTransportCodec.chatRequestToMap(request);
        assertThat(encoded).containsEntry("trigger", "regenerate-message");
        assertThat(encoded).containsKey("messages");
    }

    @Test
    void supportsTypedMetadataMapper() {
        var message = UIMessageTransportCodec.messageFromMap(Map.of(
            "id", "user-1",
            "role", "USER",
            "metadata", Map.of("chatId", "chat-1"),
            "parts", List.of(Map.of("type", "text", "id", "text-1", "text", "hello"))
        ), raw -> new Metadata((String) ((Map<?, ?>) raw).get("chatId")));

        assertThat(message.metadata()).isEqualTo(new Metadata("chat-1"));
        assertThat(UIMessageTransportCodec.messageToMap(message)).containsEntry("role", "user");
    }

    @Test
    void defaultsMissingTriggerToSubmitMessage() {
        var request = UIMessageTransportCodec.chatRequestFromMap(Map.of(
            "id", "chat-1",
            "messages", List.of(Map.of(
                "id", "user-1",
                "role", "user",
                "parts", List.of(Map.of("type", "text", "id", "text-1", "text", "hello"))
            ))
        ));

        assertThat(request.trigger()).isEqualTo(UIMessageChatTrigger.SUBMIT_MESSAGE);
    }

    @Test
    void rejectsInvalidTransportValues() {
        assertThatThrownBy(() -> UIMessageTransportCodec.partFromMap(Map.of("type", "unknown")))
            .isInstanceOf(InvalidUIMessageException.class);
        assertThatThrownBy(() -> UIMessageTransportCodec.messageFromMap(Map.of(
            "id", "m1", "role", "tool", "parts", List.of()
        ))).isInstanceOf(InvalidUIMessageException.class);
        assertThatThrownBy(() -> UIMessageTransportCodec.chatRequestFromMap(Map.of(
            "id", "chat-1", "trigger", "bad", "messages", List.of()
        ))).isInstanceOf(InvalidUIMessageException.class);
        assertThatThrownBy(() -> UIMessageTransportCodec.chatRequestFromMap(Map.of(
            "id", "chat-1", "messages", "not-list"
        ))).isInstanceOf(InvalidUIMessageException.class);
    }
}
