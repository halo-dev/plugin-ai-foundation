package run.halo.aifoundation.service.usage;

import java.util.List;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;

public record UsageCallDetail(UsageCallItem call, List<UsageExecutionRecord> executions) {}
