package run.halo.aifoundation.endpoint;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ApiVersion;

@Slf4j
@ApiVersion("console.api.aifoundation.halo.run/v1alpha1")
@RestController
@RequestMapping("/providers")
@RequiredArgsConstructor
public class ProviderConsoleEndpoint {

    private final ReactiveExtensionClient client;

    @GetMapping
    public Flux<AiProvider> list() {
        return client.listAll(AiProvider.class, new ListOptions(), Sort.unsorted());
    }

    @GetMapping("/{name}")
    public Mono<AiProvider> get(@PathVariable("name") String name) {
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Provider not found: " + name)));
    }

    @PostMapping
    public Mono<AiProvider> create(@RequestBody AiProvider provider) {
        var providerType = provider.getSpec() != null ? provider.getSpec().getProviderType() : null;
        if (providerType == null || !AiProvider.SUPPORTED_PROVIDER_TYPES.contains(providerType)) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Unsupported provider type: " + providerType
                    + ". Supported types: " + AiProvider.SUPPORTED_PROVIDER_TYPES));
        }
        if (provider.getMetadata() == null) {
            provider.setMetadata(new Metadata());
        }
        if (provider.getStatus() == null) {
            provider.setStatus(new AiProvider.AiProviderStatus());
        }
        return client.create(provider);
    }

    @PutMapping("/{name}")
    public Mono<AiProvider> update(@PathVariable("name") String name,
        @RequestBody AiProvider provider) {
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Provider not found: " + name)))
            .flatMap(existing -> {
                existing.setSpec(provider.getSpec());
                return client.update(existing);
            });
    }

    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable("name") String name) {
        return client.list(AiModel.class,
                model -> name.equals(model.getSpec().getProviderName()), null)
            .hasElements()
            .flatMap(hasModels -> {
                if (Boolean.TRUE.equals(hasModels)) {
                    return Mono.<ResponseEntity<Void>>error(
                        new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Cannot delete provider '" + name
                                + "': it has associated AI models. "
                                + "Please delete all models first."));
                }
                return client.fetch(AiProvider.class, name)
                    .switchIfEmpty(Mono.error(
                        new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND,
                            "Provider not found: " + name)))
                    .flatMap(client::delete)
                    .thenReturn(ResponseEntity.<Void>noContent().build());
            });
    }
}
