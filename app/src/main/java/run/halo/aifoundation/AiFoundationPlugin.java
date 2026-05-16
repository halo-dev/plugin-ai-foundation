package run.halo.aifoundation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Slf4j
@Component
public class AiFoundationPlugin extends BasePlugin {

    @Autowired
    private SchemeManager schemeManager;

    public AiFoundationPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        schemeManager.register(AiProvider.class);
        schemeManager.register(AiModel.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<AiModel, String>single("spec.providerName", String.class)
                .indexFunc(model -> model.getSpec().getProviderName()));
            indexSpecs.add(IndexSpecs.<AiModel, String>single("spec.modelId", String.class)
                .indexFunc(model -> model.getSpec().getModelId()));
        });
        log.info("AI Foundation plugin started, registered AiProvider and AiModel extensions.");
    }

    @Override
    public void stop() {
        schemeManager.unregister(schemeManager.get(AiProvider.class));
        schemeManager.unregister(schemeManager.get(AiModel.class));
        log.info("AI Foundation plugin stopped, unregistered extensions.");
    }
}
