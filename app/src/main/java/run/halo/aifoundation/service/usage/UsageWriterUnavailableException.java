package run.halo.aifoundation.service.usage;

/** The writer cannot be reused after a failed transaction rollback. */
final class UsageWriterUnavailableException extends IllegalStateException {

    UsageWriterUnavailableException(Throwable cause) {
        super("AI usage statistics writer is unavailable", cause);
    }
}
