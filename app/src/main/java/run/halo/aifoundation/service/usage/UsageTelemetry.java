package run.halo.aifoundation.service.usage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class UsageTelemetry {

    private UsageTelemetry() {
    }

    public static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            log.warn("Failed to observe AI usage; model execution is unaffected", error);
        }
    }
}
