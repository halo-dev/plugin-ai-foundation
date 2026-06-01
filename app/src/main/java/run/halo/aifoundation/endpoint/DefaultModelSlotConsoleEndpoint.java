package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.setting.DefaultModelSlotStore;
import run.halo.aifoundation.setting.DefaultModelSlots;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class DefaultModelSlotConsoleEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final DefaultModelSlotStore defaultModelSlotStore;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/DefaultModelSlot";
        return route()
            .GET("default-model-slots", this::getDefaultModelSlots,
                builder -> builder.operationId("GetDefaultModelSlots")
                    .description("Get AI Foundation default model slots.")
                    .tag(tag)
                    .response(responseBuilder().implementation(DefaultModelSlots.class))
            )
            .PUT("default-model-slots", this::updateDefaultModelSlots,
                builder -> builder.operationId("UpdateDefaultModelSlots")
                    .description("Update AI Foundation default model slots.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(DefaultModelSlots.class))
                    .response(responseBuilder().implementation(DefaultModelSlots.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> getDefaultModelSlots(ServerRequest request) {
        return defaultModelSlotStore.get()
            .flatMap(slots -> ServerResponse.ok().bodyValue(slots));
    }

    private Mono<ServerResponse> updateDefaultModelSlots(ServerRequest request) {
        return request.bodyToMono(DefaultModelSlots.class)
            .flatMap(slots -> validateSlots(slots)
                .then(Mono.defer(() -> defaultModelSlotStore.save(slots)))
            )
            .flatMap(saved -> ServerResponse.ok().bodyValue(saved));
    }

    private Mono<Void> validateSlots(DefaultModelSlots slots) {
        return Flux.concat(List.of(
                validateSlot("languageModelName", slots.getLanguageModelName(), ModelType.LANGUAGE),
                validateSlot("embeddingModelName", slots.getEmbeddingModelName(), ModelType.EMBEDDING),
                validateSlot("rerankModelName", slots.getRerankModelName(), ModelType.RERANK),
                validateSlot("imageGenerationModelName", slots.getImageGenerationModelName(),
                    ModelType.IMAGE_GENERATION)
            ))
            .then();
    }

    private Mono<Void> validateSlot(String slotName, String modelName, ModelType expectedType) {
        if (modelName == null || modelName.isBlank()) {
            return Mono.empty();
        }
        return client.fetch(AiModel.class, modelName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Default slot '" + slotName + "' references missing model: " + modelName)))
            .flatMap(model -> {
                if (!model.getSpec().isEnabled()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Default slot '" + slotName + "' references disabled model: "
                            + modelName));
                }
                var actualType = model.getSpec().getModelType();
                if (actualType != expectedType) {
                    var actualValue = actualType != null ? actualType.getValue() : "null";
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Default slot '" + slotName + "' requires modelType '"
                            + expectedType.getValue() + "' but model '" + modelName
                            + "' has modelType '" + actualValue + "'"));
                }
                var providerName = model.getSpec().getProviderName();
                return client.fetch(AiProvider.class, providerName)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Default slot '" + slotName + "' references model '" + modelName
                            + "' with missing provider: " + providerName)))
                    .flatMap(provider -> {
                        if (!provider.getSpec().isEnabled()) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Default slot '" + slotName + "' references model '"
                                    + modelName + "' with disabled provider: " + providerName));
                        }
                        return Mono.<Void>empty();
                    });
            });
    }

}
