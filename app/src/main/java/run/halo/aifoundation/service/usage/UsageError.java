package run.halo.aifoundation.service.usage;

import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import reactor.core.Exceptions;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.EmbeddingCancelledException;
import run.halo.aifoundation.exception.RerankCancelledException;

public record UsageError(String type, String code) {

    private static final int MAX_VALUE_LENGTH = 96;

    public static UsageError from(Throwable error) {
        if (error == null) {
            return null;
        }
        var unwrapped = Exceptions.unwrap(error);
        var type = isTimeout(unwrapped)
            ? "TIMEOUT" : sanitize(unwrapped.getClass().getSimpleName()).toUpperCase(Locale.ROOT);
        return new UsageError(type, null);
    }

    static boolean isTimeout(Throwable error) {
        if (error == null) {
            return false;
        }
        for (var current = Exceptions.unwrap(error); current != null;
            current = current.getCause()) {
            if (current instanceof TimeoutException
                || current instanceof java.net.SocketTimeoutException
                || current instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            if (current == current.getCause()) {
                break;
            }
        }
        return false;
    }

    static boolean isCancellation(Throwable error) {
        if (error == null) {
            return false;
        }
        for (var current = Exceptions.unwrap(error); current != null;
            current = current.getCause()) {
            if (current instanceof CancellationException
                || current instanceof AiGenerationCancelledException
                || current instanceof EmbeddingCancelledException
                || current instanceof RerankCancelledException) {
                return true;
            }
            if (current == current.getCause()) {
                break;
            }
        }
        return false;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        var sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), MAX_VALUE_LENGTH));
    }
}
