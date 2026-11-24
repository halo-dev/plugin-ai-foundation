package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;
import run.halo.aifoundation.provider.support.ModelType;

/**
 * A saved model that the provider did not return during discovery. The model is reported for operator awareness and is
 * never deleted automatically.
 */
@Schema(description = "A saved model that the provider no longer lists during discovery.")
public record MissingRemoteModelItem(
        String modelName,
        String modelId,
        @Nullable String displayName,
        @Nullable ModelType modelType,
        boolean enabled) {}
