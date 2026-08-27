package run.halo.aifoundation.provider.transport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

/**
 * Incrementally decodes the SSE wire format without assuming network-buffer boundaries.
 */
public final class ProviderSseDecoder {

    private ProviderSseDecoder() {
    }

    public static Flux<ProviderSseEvent> decode(Flux<DataBuffer> source) {
        return Flux.defer(() -> {
            var state = new DecoderState();
            return source.concatMapIterable(state::accept)
                .concatWith(Flux.defer(() -> Flux.fromIterable(state.finish())))
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        });
    }

    private static final class DecoderState {

        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final StringBuilder data = new StringBuilder();
        private String event;
        private String lastEventId;
        private Long retryMillis;
        private boolean pendingCarriageReturn;

        List<ProviderSseEvent> accept(DataBuffer buffer) {
            try {
                var events = new ArrayList<ProviderSseEvent>();
                while (buffer.readableByteCount() > 0) {
                    acceptByte(buffer.read(), events);
                }
                return events;
            } finally {
                DataBufferUtils.release(buffer);
            }
        }

        private void acceptByte(byte value, List<ProviderSseEvent> events) {
            if (pendingCarriageReturn) {
                processLine(events);
                pendingCarriageReturn = false;
                if (value == '\n') {
                    return;
                }
            }
            if (value == '\r') {
                pendingCarriageReturn = true;
                return;
            }
            if (value == '\n') {
                processLine(events);
                return;
            }
            line.write(value);
        }

        List<ProviderSseEvent> finish() {
            var events = new ArrayList<ProviderSseEvent>();
            if (pendingCarriageReturn) {
                processLine(events);
                pendingCarriageReturn = false;
            }
            if (line.size() > 0) {
                processLine(events);
            }
            dispatch(events);
            return events;
        }

        private void processLine(List<ProviderSseEvent> events) {
            var bytes = line.toByteArray();
            line.reset();
            var length = bytes.length;
            if (length == 0) {
                dispatch(events);
                return;
            }
            var value = new String(bytes, 0, length, StandardCharsets.UTF_8);
            if (value.startsWith(":")) {
                return;
            }
            var separator = value.indexOf(':');
            var field = separator >= 0 ? value.substring(0, separator) : value;
            var fieldValue = separator >= 0 ? value.substring(separator + 1) : "";
            if (fieldValue.startsWith(" ")) {
                fieldValue = fieldValue.substring(1);
            }
            switch (field) {
                case "data" -> data.append(fieldValue).append('\n');
                case "event" -> event = fieldValue;
                case "id" -> {
                    if (fieldValue.indexOf('\0') < 0) {
                        lastEventId = fieldValue;
                    }
                }
                case "retry" -> retryMillis = parseRetry(fieldValue);
                default -> {
                    // Unknown SSE fields are intentionally ignored.
                }
            }
        }

        private void dispatch(List<ProviderSseEvent> events) {
            if (data.isEmpty()) {
                event = null;
                retryMillis = null;
                return;
            }
            data.setLength(data.length() - 1);
            events.add(new ProviderSseEvent(
                event == null || event.isEmpty() ? "message" : event,
                data.toString(), lastEventId, retryMillis));
            data.setLength(0);
            event = null;
            retryMillis = null;
        }

        private Long parseRetry(String value) {
            if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
                return null;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
