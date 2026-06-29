package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;

public record ModelOptionUnavailableDetail(
    @Schema(description = "Capability path that did not match")
    String path,
    @Schema(description = "Expected safe value")
    Object expected,
    @Schema(description = "Actual safe value")
    Object actual
) {
}
