package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Batch model sync request.")
public record ModelSyncRequest(List<ModelSyncOperation> operations) {}
