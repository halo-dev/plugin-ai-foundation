package run.halo.aifoundation.provider.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ProviderSseDecoderTest {

    private final NettyDataBufferFactory buffers =
        new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);

    @Test
    void decodesFragmentedUtf8CrlfAndMultiLineEvents() {
        var payload = "event: delta\r\nid: 42\r\nretry: 1500\r\n"
            + "data: 你\r\ndata: 好\r\n\r\n"
            + ": heartbeat\r"
            + "data: final\r\r";
        var bytes = payload.getBytes(StandardCharsets.UTF_8);
        var firstChineseByte = indexOf(bytes, "你".getBytes(StandardCharsets.UTF_8)[0]);

        var chunks = Flux.just(
            wrap(Arrays.copyOfRange(bytes, 0, firstChineseByte + 1)),
            wrap(Arrays.copyOfRange(bytes, firstChineseByte + 1, firstChineseByte + 2)),
            wrap(Arrays.copyOfRange(bytes, firstChineseByte + 2, bytes.length - 1)),
            wrap(Arrays.copyOfRange(bytes, bytes.length - 1, bytes.length)));

        StepVerifier.create(ProviderSseDecoder.decode(chunks))
            .expectNext(new ProviderSseEvent("delta", "你\n好", "42", 1500L))
            .expectNext(new ProviderSseEvent("message", "final", "42", null))
            .verifyComplete();
    }

    @Test
    void flushesFinalEventWithoutTerminatingBlankLineAndIgnoresInvalidFields() {
        var payload = "ignored: value\nid: invalid\0id\nretry: later\ndata:test";

        StepVerifier.create(ProviderSseDecoder.decode(Flux.just(wrap(payload))))
            .expectNext(new ProviderSseEvent("message", "test", null, null))
            .verifyComplete();
    }

    @Test
    void releasesConsumedBufferWhenDownstreamCancels() {
        var first = wrap("data: first\n\n");
        var second = wrap("data: second\n\n");

        StepVerifier.create(ProviderSseDecoder.decode(Flux.just(first, second)))
            .expectNext(new ProviderSseEvent("message", "first", null, null))
            .thenCancel()
            .verify();

        assertThat(nativeRefCount(first)).isZero();
        releaseIfNecessary(second);
    }

    private DataBuffer wrap(String value) {
        return wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private DataBuffer wrap(byte[] value) {
        return buffers.wrap(value);
    }

    private int nativeRefCount(DataBuffer buffer) {
        return ((NettyDataBuffer) buffer).getNativeBuffer().refCnt();
    }

    private void releaseIfNecessary(DataBuffer buffer) {
        if (nativeRefCount(buffer) > 0) {
            DataBufferUtils.release(buffer);
        }
    }

    private int indexOf(byte[] values, byte expected) {
        for (var index = 0; index < values.length; index++) {
            if (values[index] == expected) {
                return index;
            }
        }
        throw new IllegalArgumentException("Byte not found");
    }
}
