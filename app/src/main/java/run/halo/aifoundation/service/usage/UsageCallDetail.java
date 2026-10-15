package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.UsageExecutionRecord;

import java.util.List;

public record UsageCallDetail(UsageCallItem call, List<UsageExecutionRecord> executions) {
}
