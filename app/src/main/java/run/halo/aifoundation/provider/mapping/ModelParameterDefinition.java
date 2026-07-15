package run.halo.aifoundation.provider.mapping;

import java.util.Objects;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.support.ModelParameterDomain;
import run.halo.aifoundation.provider.support.ModelType;

public final class ModelParameterDefinition {

    private final ModelParameter parameter;
    private final ModelParameterDomain domain;
    private final String field;
    private final String displayName;
    private final String description;
    private final boolean common;
    private final SelectionAccessor accessor;

    ModelParameterDefinition(ModelParameter parameter, ModelParameterDomain domain, String field,
        String displayName, String description, boolean common,
        SelectionAccessor accessor) {
        this.parameter = Objects.requireNonNull(parameter);
        this.domain = Objects.requireNonNull(domain);
        this.field = Objects.requireNonNull(field);
        this.displayName = Objects.requireNonNull(displayName);
        this.description = Objects.requireNonNull(description);
        this.common = common;
        this.accessor = Objects.requireNonNull(accessor);
    }

    public ModelParameter parameter() {
        return parameter;
    }

    public ModelType modelType() {
        return domain.getModelType();
    }

    public ModelParameterDomain domain() {
        return domain;
    }

    public String field() {
        return field;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean common() {
        return common;
    }

    public ModelParameterMappings.Selection read(ModelParameterMappings mappings) {
        return mappings == null ? null : accessor.read(mappings);
    }

    public void write(ModelParameterMappings mappings,
        ModelParameterMappings.Selection selection) {
        accessor.write(Objects.requireNonNull(mappings), selection);
    }

    interface SelectionAccessor {
        ModelParameterMappings.Selection read(ModelParameterMappings mappings);

        void write(ModelParameterMappings mappings, ModelParameterMappings.Selection selection);
    }
}
