package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.tool.AiToolServiceImpl;
import run.halo.aifoundation.tool.ToolDescriptor;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class ToolConsoleEndpoint implements CustomEndpoint {
    private final AiToolServiceImpl toolService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/Tool";
        return route()
            .GET("tools", this::listTools,
                builder -> builder.operationId("ListTools")
                    .description("List tools contributed by enabled plugins without executors.")
                    .tag(tag)
                    .response(responseBuilder().implementationArray(ToolDescriptor.class)))
            .PUT("tools/{pluginName}/{localName}/state", this::updateToolState,
                builder -> builder.operationId("UpdateToolState").tag(tag)
                    .description("Enable or disable a shared tool for all consumers.")
                    .parameter(parameterBuilder().name("pluginName").in(ParameterIn.PATH)
                        .implementation(String.class).required(true))
                    .parameter(parameterBuilder().name("localName").in(ParameterIn.PATH)
                        .implementation(String.class).required(true))
                    .requestBody(requestBodyBuilder().required(true)
                        .implementation(ToolEnabledRequest.class))
                    .response(responseBuilder().implementation(ToolDescriptor.class)))
            .GET("tools/{pluginName}/{localName}/configuration", this::getToolConfiguration,
                builder -> builder.operationId("GetToolConfiguration").tag(tag)
                    .description("Get provider-owned configuration values without credentials.")
                    .parameter(parameterBuilder().name("pluginName").in(ParameterIn.PATH)
                        .implementation(String.class).required(true))
                    .parameter(parameterBuilder().name("localName").in(ParameterIn.PATH)
                        .implementation(String.class).required(true))
                    .response(responseBuilder().implementation(ToolConfig.class)))
            .PUT("tools/{pluginName}/{localName}/configuration", this::updateToolConfiguration,
                builder -> builder.operationId("UpdateToolConfiguration").tag(tag)
                    .description("Validate and save provider-owned tool configuration.")
                    .parameter(parameterBuilder().name("pluginName").in(ParameterIn.PATH)
                        .implementation(String.class).required(true))
                    .parameter(parameterBuilder().name("localName").in(ParameterIn.PATH)
                        .implementation(String.class).required(true))
                    .requestBody(requestBodyBuilder().required(true).implementation(ToolConfig.class))
                    .response(responseBuilder().implementation(ToolConfig.class)))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listTools(ServerRequest request) {
        return toolService.listTools().collectList().flatMap(ServerResponse.ok()::bodyValue);
    }

    private Mono<ServerResponse> updateToolState(ServerRequest request) {
        return request.bodyToMono(ToolEnabledRequest.class)
            .switchIfEmpty(Mono.error(badRequest("Tool enabled state is required")))
            .flatMap(body -> {
                if (body.enabled() == null) {
                    return Mono.error(badRequest("Tool enabled state is required"));
                }
                return toolService.updateEnabled(id(request), body.enabled());
            })
            .onErrorMap(IllegalArgumentException.class, error -> badRequest(error.getMessage()))
            .flatMap(ServerResponse.ok()::bodyValue);
    }

    private Mono<ServerResponse> getToolConfiguration(ServerRequest request) {
        return toolService.getConfiguration(id(request)).map(ToolConfig::new)
            .onErrorMap(IllegalArgumentException.class, error -> badRequest(error.getMessage()))
            .flatMap(ServerResponse.ok()::bodyValue);
    }

    private Mono<ServerResponse> updateToolConfiguration(ServerRequest request) {
        return request.bodyToMono(ToolConfig.class)
            .switchIfEmpty(Mono.error(badRequest("Tool configuration is required")))
            .flatMap(body -> toolService.updateConfiguration(id(request), body.values()))
            .map(ToolConfig::new)
            .onErrorMap(IllegalArgumentException.class, error -> badRequest(error.getMessage()))
            .flatMap(ServerResponse.ok()::bodyValue);
    }

    private static String id(ServerRequest request) {
        return request.pathVariable("pluginName") + "/" + request.pathVariable("localName");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ToolEnabledRequest(Boolean enabled) { }

    public record ToolConfig(Map<String, Object> values) { }
}
