package run.halo.aifoundation.service.usage;

import java.util.List;

public record UsageBreakdown(
    List<UsageBreakdownItem> items
) {
}
