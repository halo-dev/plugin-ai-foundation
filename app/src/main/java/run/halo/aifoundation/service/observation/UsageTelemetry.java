package run.halo.aifoundation.service.observation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class UsageTelemetry {

    private UsageTelemetry() {
    }

    public static <T> T safely(java.util.function.Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException | LinkageError error) {
            return fallback;
        }
    }

    public static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            log.warn("Failed to observe AI usage; model execution is unaffected");
        }
    }
}
