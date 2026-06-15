package run.halo.aifoundation.service.audit;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCallerPluginObservationRegistry implements CallerPluginObservationRegistry {

    private final ConcurrentHashMap<String, CallerPluginInfo> callers = new ConcurrentHashMap<>();

    @Override
    public void record(CallerPluginInfo caller) {
        if (caller == null || !caller.isDetected() || caller.getPluginName() == null) {
            return;
        }
        callers.put(caller.getPluginName(), caller);
    }

    @Override
    public List<CallerPluginInfo> list() {
        return callers.values().stream()
            .sorted(Comparator.comparing(CallerPluginInfo::getPluginName))
            .toList();
    }
}
