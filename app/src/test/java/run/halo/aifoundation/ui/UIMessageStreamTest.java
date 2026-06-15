package run.halo.aifoundation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;
import tools.jackson.databind.json.JsonMapper;

class UIMessageStreamTest {

    @Test
    void factoriesExposeStableTypeDiscriminators() {
        assertThat(UIMessageChunks.textDelta("text-1", "hello").type())
            .isEqualTo(UIMessageChunkType.TEXT_DELTA);
        assertThat(UIMessageChunks.data("sources", List.of("a")).type())
            .isEqualTo("data-sources");
        assertThat(UIMessageChunks.transientData("status", "retrieving"))
            .extracting(DataChunk::transientData)
            .isEqualTo(true);
        assertThat(UIMessageChunks.finish(FinishReason.STOP, "stop", null).type())
            .isEqualTo(UIMessageChunkType.FINISH);
        assertThat(UIMessageChunks.messageMetadata(Map.of("status", "ready")).type())
            .isEqualTo(UIMessageChunkType.MESSAGE_METADATA);
    }

    @Test
    void writerPreservesWriteAndMergeOrderAndTextIds() {
        var chunks = UIMessageStreams.create("msg-1", writer -> {
            writer.writeData("status", "preparing", true);
            writer.writeText("hello");
            writer.merge(Flux.just(UIMessageChunks.textDelta("merged", "world")));
        }).chunks().collectList().block();

        assertThat(chunks).hasSize(5);
        assertThat(chunks.get(0)).isEqualTo(UIMessageChunks.data("status", "preparing", true));
        assertThat(chunks.get(1)).isEqualTo(UIMessageChunks.textStart("msg-1-text-1"));
        assertThat(chunks.get(2)).isEqualTo(UIMessageChunks.textDelta("msg-1-text-1", "hello"));
        assertThat(chunks.get(3)).isEqualTo(UIMessageChunks.textEnd("msg-1-text-1"));
        assertThat(chunks.get(4)).isEqualTo(UIMessageChunks.textDelta("merged", "world"));
    }

    @Test
    void writerCanWriteMessageMetadata() {
        var chunks = UIMessageStreams.create(writer -> writer.writeMessageMetadata(
            Map.of("status", "thinking"))).chunks().collectList().block();

        assertThat(chunks).containsExactly(UIMessageChunks.messageMetadata(
            Map.of("status", "thinking")));
    }

    @Test
    void cancellationHelperExposesTokenAndManualCancellation() {
        var cancellation = UIMessageCancellations.create();

        assertThat(cancellation.isCancellationRequested()).isFalse();
        assertThat(cancellation.token().isCancellationRequested()).isFalse();

        cancellation.cancel();

        assertThat(cancellation.isCancellationRequested()).isTrue();
        assertThat(cancellation.token().isCancellationRequested()).isTrue();
    }

    @Test
    void cancellationHelperCancelsOnlyWhenFluxSubscriberCancels() {
        var cancellation = UIMessageCancellations.create();

        StepVerifier.create(cancellation.cancelWhenSubscriberCancels(Flux.never()))
            .thenCancel()
            .verify();

        assertThat(cancellation.isCancellationRequested()).isTrue();

        var complete = UIMessageCancellations.create();
        StepVerifier.create(complete.cancelWhenSubscriberCancels(Flux.just("done")))
            .expectNext("done")
            .verifyComplete();
        assertThat(complete.isCancellationRequested()).isFalse();

        var failed = UIMessageCancellations.create();
        StepVerifier.create(failed.cancelWhenSubscriberCancels(Flux.error(
                new IllegalStateException("boom"))))
            .expectErrorMessage("boom")
            .verify();
        assertThat(failed.isCancellationRequested()).isFalse();
    }

    @Test
    void cancellationHelperCancelsOnlyWhenMonoSubscriberCancels() {
        var cancellation = UIMessageCancellations.create();

        StepVerifier.create(cancellation.cancelWhenSubscriberCancels(Mono.never()))
            .thenCancel()
            .verify();

        assertThat(cancellation.isCancellationRequested()).isTrue();

        var complete = UIMessageCancellations.create();
        StepVerifier.create(complete.cancelWhenSubscriberCancels(Mono.just("done")))
            .expectNext("done")
            .verifyComplete();
        assertThat(complete.isCancellationRequested()).isFalse();

        var failed = UIMessageCancellations.create();
        StepVerifier.create(failed.cancelWhenSubscriberCancels(Mono.error(
                new IllegalStateException("boom"))))
            .expectErrorMessage("boom")
            .verify();
        assertThat(failed.isCancellationRequested()).isFalse();
    }

    @Test
    void writerConvertsMergedStreamErrorsAndCallsFinishHandler() {
        var finish = new AtomicReference<UIMessageStreamFinish>();

        var chunks = UIMessageStreams.createWithOptions(options -> options
            .messageId("msg-1")
            .onError(error -> "safe error")
            .onFinish(finish::set)
            .execute(writer -> writer.merge(Flux.error(new IllegalStateException("boom")))))
            .chunks()
            .collectList()
            .block();

        assertThat(chunks).containsExactly(UIMessageChunks.error("safe error"));
        assertThat(finish.get().responseMessage().id()).isEqualTo("msg-1");
        assertThat(finish.get().responseMessage().parts()).isEmpty();
        assertThat(finish.get().terminal().errorText()).isEqualTo("safe error");
        assertThat(finish.get().isContinuation()).isFalse();
    }

    @Test
    void writerMapsCancellationExceptionToAbortWithoutCallingErrorHandler() {
        var finish = new AtomicReference<UIMessageStreamFinish>();
        var errorHandlerCalls = new AtomicReference<Throwable>();

        var chunks = UIMessageStreams.createWithOptions(options -> options
            .messageId("msg-1")
            .onError(error -> {
                errorHandlerCalls.set(error);
                return "safe error";
            })
            .onFinish(finish::set)
            .execute(writer -> writer.merge(Flux.concat(
                Flux.just(UIMessageChunks.textDelta("text-1", "partial")),
                Flux.error(new AiGenerationCancelledException("cancelled"))
            ))))
            .chunks()
            .collectList()
            .block();

        assertThat(chunks).containsExactly(
            UIMessageChunks.textDelta("text-1", "partial"),
            UIMessageChunks.abort()
        );
        assertThat(errorHandlerCalls).hasValue(null);
        assertThat(finish.get().responseMessage().text()).isEqualTo("partial");
        assertThat(finish.get().terminal().aborted()).isTrue();
        assertThat(finish.get().terminal().errorText()).isNull();
    }

    @Test
    void writerMapsCancelledTokenFailureToAbort() {
        var source = new CancellationSource();
        source.cancel();

        var chunks = UIMessageStreams.createWithOptions(options -> options
            .cancellationToken(source.token())
            .execute(writer -> writer.merge(Flux.error(new IllegalStateException("closed")))))
            .chunks()
            .collectList()
            .block();

        assertThat(chunks).containsExactly(UIMessageChunks.abort());
    }

    @Test
    void writerKeepsNonCancellationErrorsAsErrors() {
        var chunks = UIMessageStreams.createWithOptions(options -> options
            .onError(error -> "safe error")
            .execute(writer -> writer.merge(Flux.error(new IllegalStateException("boom")))))
            .chunks()
            .collectList()
            .block();

        assertThat(chunks).containsExactly(UIMessageChunks.error("safe error"));
    }

    @Test
    void writerEmitsOnlyOneTerminalChunk() {
        var finishBeforeCancel = UIMessageStreams.create(writer -> {
            writer.write(UIMessageChunks.finish(null, null, null));
            writer.merge(Flux.error(new AiGenerationCancelledException("cancelled")));
        }).chunks().collectList().block();

        var abortBeforeFinish = UIMessageStreams.create(writer -> {
            writer.write(UIMessageChunks.abort());
            writer.write(UIMessageChunks.finish(null, null, null));
        }).chunks().collectList().block();

        var errorBeforeCancel = UIMessageStreams.create(writer -> {
            writer.write(UIMessageChunks.error("failed"));
            writer.merge(Flux.error(new AiGenerationCancelledException("cancelled")));
        }).chunks().collectList().block();

        assertThat(finishBeforeCancel).containsExactly(UIMessageChunks.finish(null, null, null));
        assertThat(abortBeforeFinish).containsExactly(UIMessageChunks.abort());
        assertThat(errorBeforeCancel).containsExactly(UIMessageChunks.error("failed"));
    }

    @Test
    void streamTextResultMapsFullStreamToUiChunksAndSkipsRawDiagnostics() {
        var stream = new StreamTextResult(Flux.just(
            TextStreamPart.start("msg-1"),
            TextStreamPart.startStep(0),
            TextStreamPart.textStart("text-1"),
            TextStreamPart.textDelta("text-1", "hello"),
            TextStreamPart.textEnd("text-1"),
            TextStreamPart.reasoningStart("reasoning-1"),
            TextStreamPart.reasoningDelta("reasoning-1", "thinking", Map.of("k", "v")),
            TextStreamPart.reasoningEnd("reasoning-1"),
            TextStreamPart.source(GenerationContentPart.source("source-1",
                "https://example.com", "Example", Map.of("rank", 1))),
            TextStreamPart.file(GenerationContentPart.file("file-1", "https://example.com/a.txt",
                "a.txt", "text/plain", "abc", Map.of("size", 3))),
            TextStreamPart.toolInputStart("input-1", "call-1", "weather"),
            TextStreamPart.toolInputDelta("input-1", "call-1", "weather", "{\"city\""),
            TextStreamPart.toolCall(ToolCall.builder()
                .toolCallId("call-1")
                .toolName("weather")
                .input(Map.of("city", "Hangzhou"))
                .providerMetadata(Map.of("provider", "test"))
                .build()),
            TextStreamPart.toolResult(ToolResult.builder()
                .toolCallId("call-1")
                .toolName("weather")
                .result(Map.of("temperature", 20))
                .providerMetadata(Map.of("provider", "test"))
                .build()),
            TextStreamPart.toolError(ToolError.builder()
                .toolCallId("call-2")
                .toolName("weather")
                .errorText("failed")
                .providerMetadata(Map.of("provider", "test"))
                .build()),
            TextStreamPart.toolApprovalRequest(ToolApprovalRequest.builder()
                .approvalId("approval-1")
                .toolCallId("call-3")
                .toolName("payment")
                .input(Map.of("amount", 100))
                .stepIndex(0)
                .providerMetadata(Map.of("provider", "test"))
                .build()),
            TextStreamPart.toolApprovalResponse(ToolApprovalResponse.builder()
                .approvalId("approval-1")
                .toolCallId("call-3")
                .toolName("payment")
                .approved(false)
                .reason("not allowed")
                .providerMetadata(Map.of("provider", "test"))
                .build()),
            TextStreamPart.finishStep(0, FinishReason.STOP, "stop", null, List.of(), null,
                null, Map.of("provider", "test")),
            TextStreamPart.raw(Map.of("debug", true)),
            TextStreamPart.finish(FinishReason.STOP, "stop", null),
            TextStreamPart.error("model failed"),
            TextStreamPart.builder().type("abort").build()
        ), Flux.empty(), Flux.empty(), Flux.empty(), Mono.empty(), Mono.empty());

        var chunks = stream.toUIMessageStream().chunks().collectList().block();

        assertThat(chunks)
            .extracting(UIMessageChunk::type)
            .containsExactly(
                UIMessageChunkType.START,
                UIMessageChunkType.START_STEP,
                UIMessageChunkType.TEXT_START,
                UIMessageChunkType.TEXT_DELTA,
                UIMessageChunkType.TEXT_END,
                UIMessageChunkType.REASONING_START,
                UIMessageChunkType.REASONING_DELTA,
                UIMessageChunkType.REASONING_END,
                UIMessageChunkType.SOURCE_URL,
                UIMessageChunkType.FILE,
                UIMessageChunkType.TOOL_INPUT_START,
                UIMessageChunkType.TOOL_INPUT_DELTA,
                UIMessageChunkType.TOOL_INPUT_AVAILABLE,
                UIMessageChunkType.TOOL_OUTPUT_AVAILABLE,
                UIMessageChunkType.TOOL_OUTPUT_ERROR,
                UIMessageChunkType.TOOL_APPROVAL_REQUEST,
                UIMessageChunkType.TOOL_APPROVAL_RESPONSE,
                UIMessageChunkType.FINISH_STEP,
                UIMessageChunkType.FINISH,
                UIMessageChunkType.ERROR,
                UIMessageChunkType.ABORT
            );
        assertThat(chunks).noneMatch(chunk -> chunk.type().equals(
            UIMessageChunkType.MESSAGE_METADATA));
        assertThat((StartChunk) chunks.getFirst()).extracting(StartChunk::messageMetadata)
            .isNull();
        assertThat((FinishChunk) chunks.get(18)).extracting(FinishChunk::messageMetadata)
            .isNull();
    }

    @Test
    void responseCarriesHeadersAndEncodesSseFramesWithCallerSerializer() {
        var response = new UIMessageStreamResponse(
            UIMessageStreams.create(writer -> writer.writeText("text-1", "hello")),
            chunk -> "{\"type\":\"" + chunk.type() + "\"}");

        assertThat(response.headers())
            .containsEntry(UIMessageStreamResponse.PROTOCOL_HEADER,
                UIMessageStreamResponse.PROTOCOL_VERSION);
        assertThat(response.stream().collectList().block()).hasSize(3);
        assertThat(response.body().collectList().block())
            .containsExactly(
                "data: {\"type\":\"text-start\"}\n\n",
                "data: {\"type\":\"text-delta\"}\n\n",
                "data: {\"type\":\"text-end\"}\n\n",
                "data: [DONE]\n\n"
            );
    }

    @Test
    void defaultJsonMapperSerializesChunkTypeDiscriminator() throws Exception {
        var mapper = JsonMapper.builder().build();

        assertThat(mapper.writeValueAsString(UIMessageChunks.textDelta("text-1", "hello")))
            .contains("\"type\":\"text-delta\"")
            .contains("\"id\":\"text-1\"")
            .contains("\"delta\":\"hello\"");
        assertThat(mapper.writeValueAsString(UIMessageChunks.data("status", "ready", true)))
            .contains("\"type\":\"data-status\"")
            .contains("\"transientData\":true");
        assertThat(mapper.writeValueAsString(UIMessageChunks.start("msg-1",
                Map.of("status", "start"))))
            .contains("\"type\":\"start\"")
            .contains("\"messageMetadata\":{\"status\":\"start\"}");
        assertThat(mapper.writeValueAsString(UIMessageChunks.messageMetadata(
                Map.of("status", "middle"))))
            .contains("\"type\":\"message-metadata\"")
            .contains("\"messageMetadata\":{\"status\":\"middle\"}");
        assertThat(mapper.writeValueAsString(UIMessageChunks.finish(null, null, null,
                Map.of("status", "done"))))
            .contains("\"type\":\"finish\"")
            .contains("\"messageMetadata\":{\"status\":\"done\"}");
    }

    @Test
    void responseWithoutSerializerExposesStreamButBodyFails() {
        var response = new UIMessageStreamResponse(
            UIMessageStreams.create(writer -> writer.writeText("hello")));

        assertThat(response.stream().collectList().block()).hasSize(3);
        assertThatThrownBy(() -> response.body().collectList().block())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires a chunk serializer");
    }

    @Test
    void serializerFailuresAreNotMaskedAsUiErrors() {
        var response = new UIMessageStreamResponse(
            UIMessageStreams.create(writer -> writer.writeText("hello")),
            chunk -> {
                throw new IllegalStateException("cannot encode");
            });

        assertThatThrownBy(() -> response.body().collectList().block())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot encode");
    }
}
