package run.halo.aifoundation.catalog;

import java.time.Instant;
import java.util.List;

/** Provenance of fields supplemented during discovery, retained when the model is imported. */
public record ModelCatalogMatch(String providerId, String modelId, Instant fetchedAt,
                                List<String> fields) {
}
