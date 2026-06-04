package run.halo.aifoundation.service.language;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LanguageModelRuntimeSupport {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public void checkCancellation(GenerateTextRequest request) {
        if (request != null && request.getCancellationToken() != null
            && request.getCancellationToken().isCancellationRequested()) {
            throw new AiGenerationCancelledException("Generation was cancelled");
        }
    }

    public <T> Mono<T> withToolTimeout(Mono<T> mono, GenerateTextRequest request) {
        var timeout = toolTimeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("tool", timeout, error));
    }

    public String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value != null ? value : Map.of());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to serialize JSON value", e);
        }
    }

    private Duration toolTimeout(GenerateTextRequest request) {
        return request != null && request.getTimeouts() != null
            ? request.getTimeouts().getToolTimeout()
            : null;
    }
}
