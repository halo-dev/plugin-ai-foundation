package run.halo.aifoundation.service.observation;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import reactor.core.Exceptions;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.exception.EmbeddingCancelledException;
import run.halo.aifoundation.exception.EmbeddingTimeoutException;
import run.halo.aifoundation.exception.RerankCancelledException;
import run.halo.aifoundation.exception.RerankTimeoutException;

public record UsageError(String type, String code) {

    private static final int MAX_VALUE_LENGTH = 96;

    public static UsageError from(Throwable error) {
        if (error == null) {
            return null;
        }
        if (isTimeout(error)) {
            return new UsageError("TIMEOUT", null);
        }
        var unwrapped = Exceptions.unwrap(error);
        var type = sanitize(unwrapped.getClass().getSimpleName()).toUpperCase(Locale.ROOT);
        return new UsageError(type, null);
    }

    static UsageStatus failureStatus(Throwable error) {
        if (isTimeout(error)) {
            return UsageStatus.TIMED_OUT;
        }
        if (isCancellation(error)) {
            return UsageStatus.CANCELLED;
        }
        return UsageStatus.FAILED;
    }

    static boolean isTimeout(Throwable error) {
        return matchesCause(error, UsageError::isTimeoutType);
    }

    private static boolean isTimeoutType(Throwable error) {
        return switch (error) {
            case AiGenerationTimeoutException ignored -> true;
            case EmbeddingTimeoutException ignored -> true;
            case RerankTimeoutException ignored -> true;
            case TimeoutException ignored -> true;
            case SocketTimeoutException ignored -> true;
            case HttpTimeoutException ignored -> true;
            default -> false;
        };
    }

    static boolean isCancellation(Throwable error) {
        return matchesCause(error, UsageError::isCancellationType);
    }

    private static boolean isCancellationType(Throwable error) {
        return switch (error) {
            case CancellationException ignored -> true;
            case AiGenerationCancelledException ignored -> true;
            case EmbeddingCancelledException ignored -> true;
            case RerankCancelledException ignored -> true;
            default -> false;
        };
    }

    private static boolean matchesCause(Throwable error, Predicate<Throwable> matches) {
        if (error == null) {
            return false;
        }
        var current = Exceptions.unwrap(error);
        for (int depth = 0; depth < 16; depth++) {
            if (current == null) {
                return false;
            }
            if (matches.test(current)) {
                return true;
            }
            var cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
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
