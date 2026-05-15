package run.halo.aifoundation.provider;

import static run.halo.app.extension.ExtensionUtil.addFinalizers;
import static run.halo.app.extension.ExtensionUtil.removeFinalizers;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.app.extension.index.query.Queries;

@Slf4j
@Component
public class AiProviderReconciler implements Reconciler<Reconciler.Request> {

    static final String FINALIZER_NAME = "aifoundation.halo.run/cascade-delete-models";

    private final ExtensionClient client;

    public AiProviderReconciler(ExtensionClient client) {
        this.client = client;
    }

    @Override
    public Result reconcile(Request request) {
        client.fetch(AiProvider.class, request.name()).ifPresent(provider -> {
            if (ExtensionUtil.isDeleted(provider)) {
                if (removeFinalizers(provider.getMetadata(), Set.of(FINALIZER_NAME))) {
                    deleteAssociatedModels(request.name());
                    client.update(provider);
                    log.info("Cascade deleted models for provider: {}", request.name());
                }
                return;
            }
            addFinalizers(provider.getMetadata(), Set.of(FINALIZER_NAME));
            client.update(provider);
        });
        return Result.doNotRetry();
    }

    private void deleteAssociatedModels(String providerName) {
        var listOptions = ListOptions.builder()
            .fieldQuery(Queries.equal("spec.providerName", providerName))
            .build();
        var models = client.listAll(AiModel.class, listOptions, Sort.unsorted());
        for (AiModel model : models) {
            try {
                client.delete(model);
                log.debug("Deleted model {} for provider {}",
                    model.getMetadata().getName(), providerName);
            } catch (Exception e) {
                log.error("Failed to delete model {} for provider {}: {}",
                    model.getMetadata().getName(), providerName, e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AiProvider()).build();
    }
}
