package run.halo.aifoundation.service.usage;

import java.util.List;

public record UsageCallDetail(UsageCallItem call, List<UsageExecutionRecord> executions) {
}
