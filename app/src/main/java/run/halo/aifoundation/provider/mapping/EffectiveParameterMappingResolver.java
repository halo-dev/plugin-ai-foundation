package run.halo.aifoundation.provider.mapping;

import java.util.EnumMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.service.model.ModelResolution;

@Component
public class EffectiveParameterMappingResolver {

    private final ModelParameterCatalog catalog;

    public EffectiveParameterMappingResolver() {
        this(new ModelParameterCatalog());
    }

    @Autowired
    public EffectiveParameterMappingResolver(ModelParameterCatalog catalog) {
        this.catalog = catalog;
    }

    public EffectiveParameterMappings resolve(ModelResolution resolution) {
        var modelType = resolution.model().getSpec().getModelType();
        var values = new EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping>(
            ModelParameter.class);
        resolution.providerType().getDefaultParameterMappings().forEach((parameter, mapping) -> {
            if (catalog.definition(parameter).modelType() == modelType) {
                values.put(parameter, new EffectiveParameterMappings.EffectiveMapping(
                    mapping.mode(), mapping.template(), null, null,
                    EffectiveParameterMappings.Source.BUILT_IN));
            }
        });
        for (var definition : catalog.definitionsFor(List.of(modelType))) {
            if (!values.containsKey(definition.parameter())) {
                values.put(definition.parameter(),
                    unsupported(EffectiveParameterMappings.Source.BUILT_IN));
            }
        }
        overlay(values, resolution.provider().getSpec().getParameterMappings(),
            EffectiveParameterMappings.Source.PROVIDER, modelType);
        overlay(values, resolution.model().getSpec().getParameterMappings(),
            EffectiveParameterMappings.Source.MODEL, modelType);
        return new EffectiveParameterMappings(values);
    }

    private void overlay(
        EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping> target,
        ModelParameterMappings mappings, EffectiveParameterMappings.Source source,
        run.halo.aifoundation.provider.support.ModelType modelType) {
        for (var entry : catalog.selections(mappings)) {
            if (entry.definition().modelType() != modelType
                || entry.selection().getMode() == null
                || entry.selection().getMode() == ModelParameterMappings.Mode.INHERIT) {
                continue;
            }
            var selection = entry.selection();
            target.put(entry.parameter(), new EffectiveParameterMappings.EffectiveMapping(
                selection.getMode(), selection.getTemplate(), selection.getField(),
                selection.getReasoningMapping(),
                source));
        }
    }

    private EffectiveParameterMappings.EffectiveMapping unsupported(
        EffectiveParameterMappings.Source source) {
        return new EffectiveParameterMappings.EffectiveMapping(
            ModelParameterMappings.Mode.UNSUPPORTED, null, null, null, source);
    }
}
