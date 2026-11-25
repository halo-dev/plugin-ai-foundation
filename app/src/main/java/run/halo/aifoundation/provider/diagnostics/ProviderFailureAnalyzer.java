package run.halo.aifoundation.provider.diagnostics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.exception.EmbeddingTimeoutException;
import run.halo.aifoundation.exception.IncompatibleModelTypeException;
import run.halo.aifoundation.exception.ModelDisabledException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.exception.ProviderApiException;
import run.halo.aifoundation.exception.ProviderDisabledException;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;
import run.halo.aifoundation.provider.transport.ProviderHttpException;

/** Maps provider failures to stable categories and credential-safe summaries. */
@Component
public class ProviderFailureAnalyzer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_SUMMARY_LENGTH = 300;
    private static final int MAX_DETAIL_LENGTH = 1_024;
    private static final int MAX_CAUSE_DEPTH = 16;

    public ProviderFailureAnalysis analyze(Throwable error) {
        if (error == null) {
            return new ProviderFailureAnalysis(ProviderFailureCategory.UNKNOWN, null, "Unknown provider failure", null);
        }
        var causes = causes(error);
        var httpFailure = causes.stream()
                .map(this::asHttpFailure)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (httpFailure != null) {
            return fromHttpFailure(httpFailure);
        }
        var category = categoryFor(causes);
        var summary = bounded(redact(summaryFor(causes.getFirst())), MAX_SUMMARY_LENGTH);
        var detail = bounded(redact(detailFor(causes)), MAX_DETAIL_LENGTH);
        return new ProviderFailureAnalysis(category, null, summary, detail);
    }

    private List<Throwable> causes(Throwable error) {
        var causes = new ArrayList<Throwable>();
        var current = Exceptions.unwrap(error);
        for (int depth = 0; depth < MAX_CAUSE_DEPTH && current != null; depth++) {
            causes.add(current);
            var cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return causes;
    }

    private HttpFailure asHttpFailure(Throwable error) {
        if (error instanceof ProviderHttpException providerError) {
            return new HttpFailure(
                    providerError.getStatusCode(),
                    providerError.getOperation(),
                    providerError.getProviderType(),
                    providerError.getResponseBody());
        }
        if (error instanceof WebClientResponseException responseError) {
            return new HttpFailure(
                    responseError.getStatusCode().value(), null, null, responseError.getResponseBodyAsString());
        }
        if (error instanceof ProviderApiException providerError) {
            return new HttpFailure(providerError.getStatusCode(), null, providerError.getProviderType(), null);
        }
        return null;
    }

    private ProviderFailureAnalysis fromHttpFailure(HttpFailure failure) {
        var category = categoryForStatus(failure.status());
        var summary = new StringBuilder("HTTP ").append(failure.status());
        var reason = HttpStatus.resolve(failure.status());
        if (reason != null
                && reason.getReasonPhrase() != null
                && !reason.getReasonPhrase().isBlank()) {
            summary.append(' ').append(reason.getReasonPhrase());
        }
        var providerMessage = extractErrorMessage(failure.body());
        if (providerMessage != null && !providerMessage.isBlank()) {
            summary.append(": ").append(providerMessage);
        }
        return new ProviderFailureAnalysis(
                category,
                failure.status(),
                bounded(redact(summary.toString()), MAX_SUMMARY_LENGTH),
                bounded(redact(failure.body()), MAX_DETAIL_LENGTH));
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var root = OBJECT_MAPPER.readTree(body);
            for (var candidate : List.of(root.path("error").path("message"), root.path("message"), root.path("msg"))) {
                if (candidate.isTextual() && !candidate.asText().isBlank()) {
                    return candidate.asText();
                }
            }
            if (root.path("error").isTextual() && !root.path("error").asText().isBlank()) {
                return root.path("error").asText();
            }
            return null;
        } catch (JsonProcessingException ignored) {
            return body.trim();
        }
    }

    private ProviderFailureCategory categoryForStatus(int status) {
        if (status == 401 || status == 403) {
            return ProviderFailureCategory.AUTHENTICATION;
        }
        if (status == 408 || status == 504) {
            return ProviderFailureCategory.NETWORK;
        }
        if (status == 429) {
            return ProviderFailureCategory.RATE_LIMITED;
        }
        if (status == 405 || status == 501) {
            return ProviderFailureCategory.CAPABILITY_UNSUPPORTED;
        }
        if (status == 400 || status == 404 || status == 409 || status == 422) {
            return ProviderFailureCategory.CONFIGURATION;
        }
        if (status >= 500) {
            return ProviderFailureCategory.PROVIDER_ERROR;
        }
        return ProviderFailureCategory.UNKNOWN;
    }

    private ProviderFailureCategory categoryFor(List<Throwable> causes) {
        for (var cause : causes) {
            if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof SSLException) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof ConnectException || cause instanceof NoRouteToHostException) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof WebClientRequestException
                    || cause instanceof io.netty.handler.timeout.TimeoutException) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof AiGenerationTimeoutException || cause instanceof EmbeddingTimeoutException) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof UnsupportedModelCapabilityException
                    || cause instanceof IncompatibleModelTypeException) {
                return ProviderFailureCategory.CAPABILITY_UNSUPPORTED;
            }
            if (cause instanceof ModelNotFoundException
                    || cause instanceof ModelDisabledException
                    || cause instanceof ProviderDisabledException) {
                return ProviderFailureCategory.CONFIGURATION;
            }
            if (cause instanceof IOException && !(cause instanceof JsonProcessingException)) {
                return ProviderFailureCategory.NETWORK;
            }
            if (cause instanceof IllegalArgumentException) {
                return ProviderFailureCategory.CONFIGURATION;
            }
        }
        return ProviderFailureCategory.UNKNOWN;
    }

    private String summaryFor(Throwable cause) {
        var message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return cause.getClass().getSimpleName();
    }

    private String detailFor(List<Throwable> causes) {
        var first = causes.getFirst();
        var root = causes.getLast();
        var detail = new StringBuilder();
        var message = first.getMessage();
        if (message != null && !message.isBlank()) {
            detail.append(first.getClass().getSimpleName()).append(": ").append(message);
        } else {
            detail.append(first.getClass().getSimpleName());
        }
        if (root != first) {
            detail.append("; rootCause=").append(root.getClass().getSimpleName());
            if (root.getMessage() != null && !root.getMessage().isBlank()) {
                detail.append(": ").append(root.getMessage());
            }
        }
        return detail.toString();
    }

    private String redact(String value) {
        return AiFoundationDiagnostics.redactSensitiveText(value);
    }

    private String bounded(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        var suffix = "…";
        return value.substring(0, maxLength - suffix.length()) + suffix;
    }

    private record HttpFailure(int status, String operation, String providerType, String body) {}
}
