package run.halo.aifoundation.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ApiVersion;

@Slf4j
@ApiVersion("console.api.aifoundation.halo.run/v1alpha1")
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
public class ModelConsoleEndpoint {

    private final ReactiveExtensionClient client;

    @GetMapping
    public Mono<ListResult<AiModel>> list() {
        return client.list(AiModel.class, null, null)
            .collectList()
            .map(models -> new ListResult<>(models.size(), models.size(), 1, models));
    }

    @GetMapping("/{name}")
    public Mono<AiModel> get(@PathVariable("name") String name) {
        return client.fetch(AiModel.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: " + name)));
    }

    @PostMapping
    public Mono<AiModel> create(@RequestBody AiModel model) {
        return validateModel(model, null)
            .then(Mono.defer(() -> {
                if (model.getMetadata() == null) {
                    model.setMetadata(new Metadata());
                }
                return client.create(model);
            }));
    }

    @PutMapping("/{name}")
    public Mono<AiModel> update(@PathVariable("name") String name, @RequestBody AiModel model) {
        return validateModel(model, name)
            .then(client.fetch(AiModel.class, name)
                .switchIfEmpty(Mono.error(
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: " + name)))
                .flatMap(existing -> {
                    existing.setSpec(model.getSpec());
                    return client.update(existing);
                }));
    }

    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable("name") String name) {
        return client.fetch(AiModel.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: " + name)))
            .flatMap(client::delete)
            .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    private Mono<Void> validateModel(AiModel model, String excludeName) {
        if (model.getSpec() == null) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model spec is required"));
        }
        var providerName = model.getSpec().getProviderName();
        var modelId = model.getSpec().getModelId();

        if (providerName == null || providerName.isBlank()) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerName is required"));
        }
        if (modelId == null || modelId.isBlank()) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelId is required"));
        }

        return client.fetch(AiProvider.class, providerName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Provider not found: " + providerName)))
            .then(client.list(AiModel.class,
                    existing -> providerName.equals(existing.getSpec().getProviderName())
                        && modelId.equals(existing.getSpec().getModelId())
                        && (excludeName == null || !excludeName.equals(
                        existing.getMetadata().getName())),
                    null)
                .hasElements()
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.<Void>error(new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A model with providerName='" + providerName
                                + "' and modelId='" + modelId + "' already exists"));
                    }
                    return Mono.empty();
                }));
    }
}
