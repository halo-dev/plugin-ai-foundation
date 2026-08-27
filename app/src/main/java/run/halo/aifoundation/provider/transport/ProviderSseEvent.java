package run.halo.aifoundation.provider.transport;

/**
 * One decoded Server-Sent Event.
 */
public record ProviderSseEvent(String event, String data, String id, Long retryMillis) {
}
