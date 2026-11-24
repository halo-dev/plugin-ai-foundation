package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Batch model sync response with one result per requested operation.")
public record ModelSyncResponse(String providerName, List<ModelSyncResult> results) {}
