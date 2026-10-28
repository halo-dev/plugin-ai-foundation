package run.halo.aifoundation.provider.protocol.systemone;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.decision.DecisionAnswer;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionQuestionType;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.decision.DecisionUsage;
import run.halo.aifoundation.provider.support.ProviderDecisionClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

/** Shared System One wire protocol for direct providers and gateways. */
public class SystemOneDecisionClient implements ProviderDecisionClient {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON =
        new ParameterizedTypeReference<>() { };

    private final String providerType;
    private final URI endpoint;
    private final Map<String, String> headers;
    private final String apiKey;
    private final String model;
    private final WebClient client;

    public SystemOneDecisionClient(String providerType, String baseUrl, String apiKey, String model,
        Map<String, String> headers, WebClient.Builder builder) {
        this.providerType = providerType;
        this.endpoint = URI.create(ProviderUris.withoutTrailingPath(baseUrl, "/v1") + "/v1/systemone");
        this.headers = Map.copyOf(headers);
        this.apiKey = apiKey;
        this.model = model;
        this.client = builder.build();
    }

    @Override
    public Mono<DecisionResponse> decide(DecisionRequest request) {
        return Mono.defer(() -> {
            var questions = new LinkedHashMap<String, Object>();
            request.getQuestions().forEach((id, question) -> questions.put(id, encode(question)));
            var body = Map.of("model", model, "state", request.getState(), "questions", questions);
            var diagnostics = ProviderDiagnostics.create(providerType, "decision");
            diagnostics.request(endpoint.toString(), body, false);
            return client.post().uri(endpoint).headers(requestHeaders -> {
                    headers.forEach(requestHeaders::set);
                    requestHeaders.setBearerAuth(apiKey);
                })
                .bodyValue(body).exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return ProviderHttpResponseSupport.errorMono(response, providerType,
                            "decision", diagnostics);
                    }
                    diagnostics.responseStatus(response.statusCode().value());
                    return response.bodyToMono(JSON).map(SystemOneDecisionClient::decode);
                });
        });
    }

    private static Map<String, Object> encode(DecisionQuestion question) {
        var body = new LinkedHashMap<String, Object>();
        body.put("instructions", question.getInstructions());
        switch (question.getType()) {
            case YES_NO -> {
                body.put("type", "noul");
                var criteria = new LinkedHashMap<String, String>();
                if (question.getYesCriteria() != null) {
                    criteria.put("true", question.getYesCriteria());
                }
                if (question.getNoCriteria() != null) {
                    criteria.put("false", question.getNoCriteria());
                }
                if (!criteria.isEmpty()) {
                    body.put("criteria", criteria);
                }
            }
            case CHOICE -> {
                if (question.getOptions().size() > 255) {
                    throw new IllegalArgumentException("System One supports at most 255 choice options");
                }
                body.put("type", "choice");
                body.put("criteria", question.getOptions());
            }
            case SCORE -> {
                if (question.getLevels().size() > 10) {
                    throw new IllegalArgumentException("System One supports at most 10 score levels");
                }
                body.put("type", "score");
                body.put("criteria", question.getLevels());
            }
        }
        return body;
    }

    private static DecisionResponse decode(Map<String, Object> body) {
        var answers = new LinkedHashMap<String, DecisionAnswer>();
        object(body.get("answers")).forEach((id, raw) -> answers.put(id, answer(object(raw))));
        DecisionUsage usage = null;
        if (body.get("usage") != null) {
            var raw = object(body.get("usage"));
            usage = new DecisionUsage(tokens(raw.get("input_tokens")), tokens(raw.get("output_tokens")));
        }
        return new DecisionResponse(string(body.get("model")), answers, usage);
    }

    private static DecisionAnswer answer(Map<String, Object> raw) {
        var answer = DecisionAnswer.builder();
        switch (string(raw.get("type"))) {
            case "noul" -> answer.type(DecisionQuestionType.YES_NO).probability(number(raw.get("noul")));
            case "choice" -> answer.type(DecisionQuestionType.CHOICE)
                .choice(string(raw.get("choice")))
                .probabilities(probabilities(raw.get("probabilities")))
                .confidence(number(raw.get("confidence")));
            case "score" -> {
                var legend = new LinkedHashMap<String, String>();
                object(raw.get("legend")).forEach((key, value) -> legend.put(key, string(value)));
                answer.type(DecisionQuestionType.SCORE).score(number(raw.get("score")))
                    .probabilities(probabilities(raw.get("probabilities")))
                    .confidence(number(raw.get("confidence"))).legend(legend);
            }
            default -> throw new IllegalStateException("Unsupported System One answer type");
        }
        return answer.build();
    }

    private static Map<String, Double> probabilities(Object raw) {
        var result = new LinkedHashMap<String, Double>();
        object(raw).forEach((key, value) -> result.put(key, number(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) {
            throw new IllegalStateException("Expected an object in System One response");
        }
        return (Map<String, Object>) map;
    }

    private static String string(Object raw) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Expected a non-empty string in System One response");
        }
        return value;
    }

    private static Double number(Object raw) {
        if (!(raw instanceof Number value) || !Double.isFinite(value.doubleValue())) {
            throw new IllegalStateException("Expected a finite number in System One response");
        }
        return value.doubleValue();
    }

    private static Integer tokens(Object raw) {
        if (raw == null) {
            return null;
        }
        var count = number(raw);
        if (count < 0 || count > Integer.MAX_VALUE || count != Math.rint(count)) {
            throw new IllegalStateException("Invalid token count in System One response");
        }
        return count.intValue();
    }
}
