package run.halo.aifoundation.provider.support;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import run.halo.aifoundation.extension.ModelParameterMappings;

@Data
@Builder
public class ParameterMappingTemplateInfo {
    private String id;
    private String displayName;
    private String description;
    private String defaultField;
    private String parameter;
    private ModelType modelType;
    private List<AdapterType> adapterTypes;
    private String configurationType;
    private ModelParameterMappings.ReasoningMapping defaultReasoningMapping;
}
