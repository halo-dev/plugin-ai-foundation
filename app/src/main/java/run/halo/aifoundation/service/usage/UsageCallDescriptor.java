package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.ModelCallContext;

public record UsageCallDescriptor(
    ModelCallContext context,
    String operation,
    boolean streaming,
    String feature,
    CallerPluginInfo caller
) {
}
