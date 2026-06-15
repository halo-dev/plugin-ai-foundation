package run.halo.aifoundation.service.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallerPluginAuditRecorder {

    private final CallerPluginResolver callerPluginResolver;
    private final CallerPluginObservationRegistry callerPluginObservationRegistry;

    public void recordModelResolution(ModelCallContext context) {
        recordCurrentCaller();
    }

    public void recordModelInvocation(ModelCallContext context, String operation) {
        recordCurrentCaller();
    }

    private void recordCurrentCaller() {
        var caller = callerPluginResolver.resolveCurrentCallerSnapshot();
        if (!caller.isDetected() || caller.getPluginName() == null) {
            return;
        }
        callerPluginResolver.resolveCurrentCaller()
            .doOnNext(callerPluginObservationRegistry::record)
            .subscribe(
                ignored -> {
                },
                error -> log.debug("Failed to record caller plugin information.", error)
            );
    }
}
