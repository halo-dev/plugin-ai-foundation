package run.halo.aifoundation.provider.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DefaultParameterMappingInfo {
    private String mode;
    private String template;
}
