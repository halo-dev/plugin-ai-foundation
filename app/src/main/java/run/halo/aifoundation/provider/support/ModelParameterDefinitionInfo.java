package run.halo.aifoundation.provider.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelParameterDefinitionInfo {
    private String parameter;
    private ModelType modelType;
    private ModelParameterDomain domain;
    private String field;
    private String displayName;
    private String description;
    private boolean common;
}
