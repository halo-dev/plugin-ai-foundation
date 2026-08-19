package run.halo.aifoundation.service.usage;

import java.util.List;

public record UsageCallPage(List<UsageCallItem> items, String nextCursor) {
}
