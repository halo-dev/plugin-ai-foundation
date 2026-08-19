package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.usage.UsageCallDetail;
import run.halo.aifoundation.service.usage.UsageCallPage;
import run.halo.aifoundation.service.usage.UsageHealth;
import run.halo.aifoundation.service.usage.UsageFeature;
import run.halo.aifoundation.service.usage.UsageQuality;
import run.halo.aifoundation.service.usage.UsageQuery;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.aifoundation.service.usage.UsageStatus;
import run.halo.aifoundation.service.usage.UsageSummary;
import run.halo.aifoundation.service.usage.UsageTrendPoint;
import run.halo.aifoundation.service.usage.UsageTrendResolution;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class UsageStatisticsConsoleEndpoint implements CustomEndpoint {

    private static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final Duration MAX_RANGE = Duration.ofDays(3660);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private final UsageStatisticsService service;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "console.api.aifoundation.halo.run/v1alpha1/UsageStatistics";
        return route()
            .GET("usage-statistics/summary", this::summary,
                builder -> filters(builder.operationId("GetAiUsageSummary").tag(tag))
                    .description("Get filtered AI call and token totals.")
                    .response(responseBuilder().implementation(UsageSummary.class)))
            .GET("usage-statistics/trends", this::trends,
                builder -> filters(builder.operationId("ListAiUsageTrends").tag(tag))
                    .description("List filtered UTC usage trend buckets.")
                    .parameter(parameterBuilder().name("resolution").in(ParameterIn.QUERY)
                        .description("Optional trend bucket resolution: DAY or HOUR. HOUR applies "
                            + "only to detail-backed intervals; archived intervals remain DAY.")
                        .implementation(String.class))
                    .response(responseBuilder().implementationArray(UsageTrendPoint.class)))
            .GET("usage-statistics/calls", this::calls,
                builder -> filters(builder.operationId("ListAiUsageCalls").tag(tag))
                    .description("List logical AI calls using cursor pagination.")
                    .parameter(parameterBuilder().name("size").in(ParameterIn.QUERY)
                        .description("Page size from 1 to 200.").implementation(Integer.class))
                    .parameter(parameterBuilder().name("cursor").in(ParameterIn.QUERY)
                        .description("Opaque cursor returned by the previous page.")
                        .implementation(String.class))
                    .response(responseBuilder().implementation(UsageCallPage.class)))
            .GET("usage-statistics/calls/{id}", this::call,
                builder -> builder.operationId("GetAiUsageCall").tag(tag)
                    .description("Get a logical AI call and its physical executions.")
                    .parameter(parameterBuilder().name("id").in(ParameterIn.PATH)
                        .required(true).implementation(String.class))
                    .response(responseBuilder().implementation(UsageCallDetail.class)))
            .GET("usage-statistics/health", this::health,
                builder -> builder.operationId("GetAiUsageStatisticsHealth").tag(tag)
                    .description("Get statistics availability and completeness indicators.")
                    .response(responseBuilder().implementation(UsageHealth.class)))
            .POST("usage-statistics/reset", this::reset,
                builder -> builder.operationId("ResetAiUsageStatistics").tag(tag)
                    .description("Delete all usage statistics. Requires confirmation RESET.")
                    .requestBody(requestBodyBuilder().required(true)
                        .implementation(ResetRequest.class))
                    .response(responseBuilder().implementation(ResetResponse.class)))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> summary(ServerRequest request) {
        return query(request, null).flatMap(service::summary)
            .flatMap(ServerResponse.ok()::bodyValue)
            .onErrorResume(IllegalArgumentException.class, this::badRequest)
            .onErrorResume(IllegalStateException.class, this::unavailable);
    }

    private Mono<ServerResponse> trends(ServerRequest request) {
        return query(request, text(request, "resolution"))
            .flatMap(service::trends).flatMap(ServerResponse.ok()::bodyValue)
            .onErrorResume(IllegalArgumentException.class, this::badRequest)
            .onErrorResume(IllegalStateException.class, this::unavailable);
    }

    private Mono<ServerResponse> calls(ServerRequest request) {
        return query(request, null).flatMap(value -> service.listCalls(value, pageSize(request),
                request.queryParam("cursor").orElse(null)))
            .flatMap(ServerResponse.ok()::bodyValue)
            .onErrorResume(IllegalArgumentException.class, this::badRequest)
            .onErrorResume(IllegalStateException.class, this::unavailable);
    }

    private Mono<ServerResponse> call(ServerRequest request) {
        return service.getCall(request.pathVariable("id"))
            .flatMap(result -> result.<Mono<ServerResponse>>map(
                    value -> ServerResponse.ok().bodyValue(value))
                .orElseGet(() -> ServerResponse.notFound().build()))
            .onErrorResume(IllegalStateException.class, this::unavailable);
    }

    private Mono<ServerResponse> health(ServerRequest request) {
        return ServerResponse.ok().bodyValue(service.health());
    }

    private Mono<ServerResponse> reset(ServerRequest request) {
        return request.bodyToMono(ResetRequest.class)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("request body is required")))
            .flatMap(body -> service.reset(body.confirmation()))
            .flatMap(epoch -> ServerResponse.ok().bodyValue(new ResetResponse(epoch)))
            .onErrorResume(IllegalArgumentException.class, this::badRequest)
            .onErrorResume(IllegalStateException.class, this::unavailable);
    }

    private Mono<UsageQuery> query(ServerRequest request, String resolution) {
        return Mono.fromCallable(() -> {
            var now = Instant.now();
            var defaultTo = now.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
            var to = instant(request, "to", defaultTo);
            var from = instant(request, "from", to.minus(DEFAULT_RANGE));
            if (!from.isBefore(to)) {
                throw new IllegalArgumentException("from must be before to");
            }
            if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
                throw new IllegalArgumentException("date range must not exceed 3660 days");
            }
            var feature = text(request, "feature");
            if (feature != null && !UsageFeature.isValid(feature)) {
                throw new IllegalArgumentException("feature must match " + UsageFeature.FORMAT);
            }
            return new UsageQuery(from, to, text(request, "callerPlugin"),
                feature, text(request, "providerName"),
                text(request, "modelName"), text(request, "modelType"),
                text(request, "operation"), enumValue(request, "status", UsageStatus.class),
                enumValue(request, "usageQuality", UsageQuality.class),
                resolution == null ? null : trendResolution(resolution));
        });
    }

    private static org.springdoc.core.fn.builders.operation.Builder filters(
        org.springdoc.core.fn.builders.operation.Builder builder) {
        builder.parameter(parameterBuilder().name("from").in(ParameterIn.QUERY)
                .description("Inclusive ISO-8601 instant; defaults to 30 days before to.")
                .implementation(Instant.class))
            .parameter(parameterBuilder().name("to").in(ParameterIn.QUERY)
                .description("Exclusive ISO-8601 instant; defaults to now.")
                .implementation(Instant.class));
        for (var name : new String[] {"callerPlugin", "feature", "providerName", "modelName",
            "modelType", "operation", "status", "usageQuality"}) {
            builder.parameter(parameterBuilder().name(name).in(ParameterIn.QUERY)
                .implementation(String.class));
        }
        return builder;
    }

    private static Instant instant(ServerRequest request, String name, Instant fallback) {
        var value = text(request, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 instant", error);
        }
    }

    private static int pageSize(ServerRequest request) {
        var value = text(request, "size");
        if (value == null) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            var size = Integer.parseInt(value);
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
            }
            return size;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("size must be an integer", error);
        }
    }

    private static String text(ServerRequest request, String name) {
        return request.queryParam(name).map(String::trim).filter(value -> !value.isEmpty())
            .orElse(null);
    }

    private static <E extends Enum<E>> E enumValue(ServerRequest request, String name,
        Class<E> type) {
        var value = text(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid " + name + ": " + value, error);
        }
    }

    private static UsageTrendResolution trendResolution(String value) {
        try {
            return UsageTrendResolution.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("resolution must be DAY or HOUR", error);
        }
    }

    private Mono<ServerResponse> badRequest(Throwable error) {
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .bodyValue(new ErrorResponse(error.getMessage()));
    }

    private Mono<ServerResponse> unavailable(Throwable error) {
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .bodyValue(new ErrorResponse(error.getMessage()));
    }

    public record ResetRequest(String confirmation) {
    }

    public record ResetResponse(long epoch) {
    }

    public record ErrorResponse(String message) {
    }
}
