package run.halo.aifoundation.service.audit;

import java.util.List;

public interface CallerPluginObservationRegistry {

    void record(CallerPluginInfo caller);

    List<CallerPluginInfo> list();
}
