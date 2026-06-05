package run.halo.aifoundation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

class UIMessageStreamReaderTest {

    record Metadata(String chatId) {
    }

    record StringMetadata(String value) {
    }

    @Test
    void uiMessageExposesQueryCopyHelpersAndTypedMetadata() {
        var message = new UIMessage<>("msg-1", UIMessageRole.ASSISTANT, List.of(
            UIMessageParts.text("text-1", "hello "),
            UIMessageParts.text("text-2", "world"),
            UIMessageParts.data("status", "done")
        ), new Metadata("chat-1"));

        assertThat(message.metadata().chatId()).isEqualTo("chat-1");
        assertThat(message.text()).isEqualTo("hello world");
        assertThat(message.parts(UIMessageChunkType.TEXT)).hasSize(2);
        assertThat(message.part(UIMessageChunkType.TEXT, "text-1")).contains(
            UIMessageParts.text("text-1", "hello "));
        assertThat(message.data("status", String.class)).contains("done");
        assertThat(message.withParts(List.of(UIMessageParts.text("text-3", "new"))).text())
            .isEqualTo("new");
        assertThat(message.withMetadata(new Metadata("chat-2")).metadata().chatId())
            .isEqualTo("chat-2");
    }

    @Test
    void defaultJsonMapperSerializesMessagePartTypeDiscriminators() throws Exception {
        var mapper = JsonMapper.builder().build();
        var message = new UIMessage<>("msg-1", UIMessageRole.ASSISTANT, List.of(
            UIMessageParts.text("text-1", "hello"),
            UIMessageParts.data("status", "done")
        ), Map.of("chatId", "chat-1"));

        var json = mapper.writeValueAsString(message);

        assertThat(json)
            .contains("\"type\":\"text\"")
            .contains("\"type\":\"data\"")
            .contains("\"metadata\":{\"chatId\":\"chat-1\"}");
    }

    @Test
    void readerAccumulatesTextReasoningAndEmitsVisibleSnapshotsOnly() {
        var result = UIMessageStreamReader.read(new UIMessageStream(Flux.just(
            UIMessageChunks.start("msg-1"),
            UIMessageChunks.textStart("text-1"),
            UIMessageChunks.textDelta("text-1", "hel"),
            UIMessageChunks.textDelta("text-1", "lo"),
            UIMessageChunks.textEnd("text-1"),
            UIMessageChunks.reasoningDelta("reasoning-1", "think", Map.of("k", "v")),
            UIMessageChunks.finish(null, null, null)
        )));

        var messages = result.messages().collectList().block();
        var response = result.responseMessage().block();

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).parts()).containsExactly(UIMessageParts.text("text-1", ""));
        assertThat(messages.get(1).text()).isEqualTo("hel");
        assertThat(messages.get(2).text()).isEqualTo("hello");
        assertThat(response.id()).isEqualTo("msg-1");
        assertThat(response.parts()).containsExactly(
            UIMessageParts.text("text-1", "hello"),
            UIMessageParts.reasoning("reasoning-1", "think", Map.of("k", "v"))
        );
    }

    @Test
    void readerReturnsEmptyResponseMessageWhenNoVisibleParts() {
        var result = UIMessageStreamReader.<Metadata>read(options -> options
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.start("msg-empty"),
                UIMessageChunks.finish(null, null, null)
            )))
            .metadataSupplier(() -> new Metadata("chat-1")));

        assertThat(result.messages().collectList().block()).isEmpty();
        assertThat(result.responseMessage().block())
            .isEqualTo(new UIMessage<>("msg-empty", UIMessageRole.ASSISTANT, List.of(),
                new Metadata("chat-1")));
    }

    @Test
    void readerReplacesStablePartsById() {
        var result = UIMessageStreamReader.read(new UIMessageStream(Flux.just(
            UIMessageChunks.sourceUrl("source-1", "https://old.example", "Old", Map.of()),
            UIMessageChunks.sourceUrl("source-1", "https://new.example", "New", Map.of()),
            UIMessageChunks.file("file-1", "old", "old.txt", "text/plain", "old", Map.of()),
            UIMessageChunks.file("file-1", "new", "new.txt", "text/plain", "new", Map.of()),
            UIMessageChunks.toolCall("call-1", "weather", Map.of("city", "A"), Map.of()),
            UIMessageChunks.toolCall("call-1", "weather", Map.of("city", "B"), Map.of()),
            UIMessageChunks.toolResult("call-1", "weather", "old", Map.of()),
            UIMessageChunks.toolResult("call-1", "weather", "new", Map.of()),
            UIMessageChunks.toolError("call-2", "weather", "old", Map.of()),
            UIMessageChunks.toolError("call-2", "weather", "new", Map.of()),
            UIMessageChunks.toolApprovalRequest("approval-1", "call-3", "pay",
                Map.of("amount", 1), 0, Map.of()),
            UIMessageChunks.toolApprovalRequest("approval-1", "call-3", "pay",
                Map.of("amount", 2), 0, Map.of()),
            UIMessageChunks.data("status", "old"),
            UIMessageChunks.data("status", "new")
        )));

        var response = result.responseMessage().block();

        assertThat(response.parts()).containsExactly(
            UIMessageParts.sourceUrl("source-1", "https://new.example", "New", Map.of()),
            UIMessageParts.file("file-1", "new", "new.txt", "text/plain", "new", Map.of()),
            UIMessageParts.toolCall("call-1", "weather", Map.of("city", "B"), Map.of()),
            UIMessageParts.toolResult("call-1", "weather", "new", Map.of()),
            UIMessageParts.toolError("call-2", "weather", "new", Map.of()),
            UIMessageParts.toolApprovalRequest("approval-1", "call-3", "pay",
                Map.of("amount", 2), 0, Map.of()),
            UIMessageParts.data("status", "new")
        );
    }

    @Test
    void readerExcludesTransientToolInputAndLifecycleChunks() {
        var result = UIMessageStreamReader.read(new UIMessageStream(Flux.just(
            UIMessageChunks.start("msg-1"),
            UIMessageChunks.transientData("status", "retrieving"),
            UIMessageChunks.toolInputStart("input-1", "call-1", "weather"),
            UIMessageChunks.toolInputDelta("input-1", "call-1", "weather", "{\"city\""),
            UIMessageChunks.finishStep(0, null, null, null, List.of(), null, null, Map.of()),
            UIMessageChunks.error("failed"),
            UIMessageChunks.abort(),
            UIMessageChunks.finish(null, null, null)
        )));

        assertThat(result.messages().collectList().block()).isEmpty();
        assertThat(result.responseMessage().block().parts()).isEmpty();
        assertThat(result.finish().block())
            .isEqualTo(new UIMessageStreamTerminal(null, null, true, "failed"));
    }

    @Test
    void readerHonorsIdPriorityMetadataSupplierAndImmutableSnapshots() {
        var metadataCalls = new AtomicInteger();
        var existing = new UIMessage<>("existing", UIMessageRole.ASSISTANT,
            List.of(UIMessageParts.text("text-0", "old")), new Metadata("existing-chat"));
        var result = UIMessageStreamReader.<Metadata>read(options -> options
            .message(existing)
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.start("ignored"),
                UIMessageChunks.textDelta("text-1", "new")
            )))
            .messageIdGenerator(() -> "generated")
            .metadataSupplier(() -> {
                metadataCalls.incrementAndGet();
                return new Metadata("new-chat");
            }));

        var messages = result.messages().collectList().block();
        var response = result.responseMessage().block();

        assertThat(response.id()).isEqualTo("existing");
        assertThat(response.metadata()).isEqualTo(new Metadata("existing-chat"));
        assertThat(metadataCalls).hasValue(0);
        assertThat(messages.get(0).parts()).hasSize(2);
        assertThat(existing.parts()).containsExactly(UIMessageParts.text("text-0", "old"));
    }

    @Test
    void readerUsesGeneratedIdAndMetadataSupplierOnce() {
        var metadataCalls = new AtomicInteger();
        var result = UIMessageStreamReader.read(options -> options
            .stream(new UIMessageStream(Flux.just(UIMessageChunks.textDelta("text-1", "hello"))))
            .messageIdGenerator(() -> "generated")
            .metadataSupplier(() -> {
                metadataCalls.incrementAndGet();
                return new Metadata("chat-1");
            }));

        assertThat(result.responseMessage().block())
            .isEqualTo(new UIMessage<>("generated", UIMessageRole.ASSISTANT,
                List.of(UIMessageParts.text("text-1", "hello")), new Metadata("chat-1")));
        assertThat(metadataCalls).hasValue(1);
    }

    @Test
    void readerAggregatesStartMessageMetadataAndEmitsSnapshot() {
        var result = UIMessageStreamReader.read(options -> options
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.start("msg-1", Map.of("model", "test-model"))
            )))
            .metadataSupplier(() -> Map.of("chatId", "chat-1")));

        var messages = result.messages().collectList().block();
        var response = result.responseMessage().block();

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().id()).isEqualTo("msg-1");
        assertThat(messages.getFirst().metadata()).isEqualTo(Map.of(
            "chatId", "chat-1",
            "model", "test-model"
        ));
        assertThat(response.metadata()).isEqualTo(messages.getFirst().metadata());
        assertThat(response.parts()).isEmpty();
    }

    @Test
    void readerAggregatesMessageMetadataChunksAndSkipsNullOrUnchangedSnapshots() {
        var result = UIMessageStreamReader.read(options -> options
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.messageMetadata(Map.of("status", "thinking")),
                UIMessageChunks.messageMetadata(null),
                UIMessageChunks.messageMetadata(Map.of("status", "thinking")),
                UIMessageChunks.messageMetadata(Map.of("status", "done"))
            )))
            .messageIdGenerator(() -> "generated")
            .metadataSupplier(() -> Map.of("chatId", "chat-1")));

        var messages = result.messages().collectList().block();
        var response = result.responseMessage().block();

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().metadata()).isEqualTo(Map.of(
            "chatId", "chat-1",
            "status", "thinking"
        ));
        assertThat(messages.get(1).metadata()).isEqualTo(Map.of(
            "chatId", "chat-1",
            "status", "done"
        ));
        assertThat(response.id()).isEqualTo("generated");
        assertThat(response.metadata()).isEqualTo(messages.get(1).metadata());
        assertThat(response.parts()).isEmpty();
    }

    @Test
    void readerAggregatesFinishMessageMetadataIntoFinalResponseMessage() {
        var result = UIMessageStreamReader.read(options -> options
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.textDelta("text-1", "hello"),
                UIMessageChunks.finish(null, null, null, Map.of("status", "done"))
            )))
            .metadataSupplier(() -> Map.of("status", "streaming")));

        var messages = result.messages().collectList().block();
        var response = result.responseMessage().block();

        assertThat(messages).hasSize(2);
        assertThat(messages.getLast().metadata()).isEqualTo(Map.of("status", "done"));
        assertThat(response.metadata()).isEqualTo(Map.of("status", "done"));
        assertThat(response.text()).isEqualTo("hello");
    }

    @Test
    void readerMergesMetadataIntoExistingMessageMetadata() {
        Map<String, Object> metadata = Map.of("chatId", "chat-1");
        var existing = new UIMessage<>("existing", UIMessageRole.ASSISTANT,
            List.of(UIMessageParts.text("text-0", "old")), metadata);
        var result = UIMessageStreamReader.<Map<String, Object>>read(options -> options
            .message(existing)
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.messageMetadata(Map.of("status", "updated"))
            )))
            .metadataSupplier(() -> Map.of("chatId", "ignored")));

        var response = result.responseMessage().block();

        assertThat(response.id()).isEqualTo("existing");
        assertThat(response.metadata()).isEqualTo(Map.of(
            "chatId", "chat-1",
            "status", "updated"
        ));
    }

    @Test
    void readerReplacesNonMapMetadataByDefault() {
        var result = UIMessageStreamReader.<StringMetadata>read(options -> options
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.messageMetadata(new StringMetadata("updated"))
            )))
            .metadataSupplier(() -> new StringMetadata("initial")));

        var response = result.responseMessage().block();

        assertThat(response.metadata()).isEqualTo(new StringMetadata("updated"));
        assertThat(response.parts()).isEmpty();
    }

    @Test
    void readerUsesCustomMetadataMerger() {
        var result = UIMessageStreamReader.<StringMetadata>read(options -> options
            .stream(new UIMessageStream(Flux.just(
                UIMessageChunks.messageMetadata("step-1"),
                UIMessageChunks.messageMetadata("step-2")
            )))
            .metadataSupplier(() -> new StringMetadata("base"))
            .metadataMerger((current, update) -> new StringMetadata(
                current.value() + "/" + update)));

        var messages = result.messages().collectList().block();
        var response = result.responseMessage().block();

        assertThat(messages).extracting(UIMessage::metadata).containsExactly(
            new StringMetadata("base/step-1"),
            new StringMetadata("base/step-1/step-2")
        );
        assertThat(response.metadata()).isEqualTo(new StringMetadata("base/step-1/step-2"));
    }

    @Test
    void createWithOptionsUsesCustomMetadataMergerForFinishAggregation() {
        var captured = new AtomicReference<UIMessageStreamFinish<StringMetadata>>();

        UIMessageStreams.<StringMetadata>createWithOptions(options -> options
            .metadataSupplier(() -> new StringMetadata("base"))
            .metadataMerger((current, update) -> new StringMetadata(
                current.value() + "+" + ((StringMetadata) update).value()))
            .onFinish(captured::set)
            .execute(writer -> writer.writeMessageMetadata(new StringMetadata("update"))))
            .chunks()
            .collectList()
            .block();

        assertThat(captured.get().responseMessage().metadata())
            .isEqualTo(new StringMetadata("base+update"));
        assertThat(captured.get().responseMessage().parts()).isEmpty();
    }

    @Test
    void readerHandlesProtocolAndReadErrors() {
        var protocol = UIMessageStreamReader.read(new UIMessageStream(Flux.just(
            UIMessageChunks.error("model failed")
        )));
        assertThat(protocol.finish().block().errorText()).isEqualTo("model failed");

        var captured = new AtomicReference<Throwable>();
        var recoverable = UIMessageStreamReader.read(options -> options
            .stream(new UIMessageStream(Flux.error(new IllegalStateException("boom"))))
            .onError(captured::set)
            .errorHandler(error -> "safe read error"));
        assertThat(recoverable.finish().block().errorText()).isEqualTo("safe read error");
        assertThat(recoverable.responseMessage().block().parts()).isEmpty();
        assertThat(captured.get()).isInstanceOf(IllegalStateException.class);

        var terminating = UIMessageStreamReader.read(options -> options
            .stream(new UIMessageStream(Flux.error(new IllegalStateException("fatal"))))
            .terminateOnError(true));
        assertThatThrownBy(() -> terminating.responseMessage().block())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fatal");
    }

    @Test
    void createWithOptionsEmitsFullFinishForAppendAndContinuation() {
        var user = new UIMessage<>("user-1", UIMessageRole.USER,
            List.of(UIMessageParts.text("user-text", "hi")), new Metadata("chat-1"));
        var appended = new AtomicReference<UIMessageStreamFinish<Metadata>>();

        UIMessageStreams.<Metadata>createWithOptions(options -> options
            .originalMessages(List.of(user))
            .metadataSupplier(() -> new Metadata("chat-1"))
            .onFinish(appended::set)
            .execute(writer -> writer.writeText("assistant")))
            .chunks()
            .collectList()
            .block();

        assertThat(appended.get().isContinuation()).isFalse();
        assertThat(appended.get().messages()).hasSize(2);
        assertThat(appended.get().responseMessage().text()).isEqualTo("assistant");

        var assistant = appended.get().responseMessage();
        var continued = new AtomicReference<UIMessageStreamFinish<Metadata>>();
        UIMessageStreams.<Metadata>createWithOptions(options -> options
            .originalMessages(List.of(user, assistant))
            .message(assistant)
            .onFinish(continued::set)
            .execute(writer -> writer.writeText("more")))
            .chunks()
            .collectList()
            .block();

        assertThat(continued.get().isContinuation()).isTrue();
        assertThat(continued.get().messages()).hasSize(2);
        assertThat(continued.get().messages().getLast().id()).isEqualTo(assistant.id());
        assertThat(continued.get().messages().getLast().text()).isEqualTo("assistantmore");
    }
}
