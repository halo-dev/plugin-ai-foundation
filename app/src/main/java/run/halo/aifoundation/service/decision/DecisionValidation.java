package run.halo.aifoundation.service.decision;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import run.halo.aifoundation.capability.DecisionCapability;
import run.halo.aifoundation.decision.DecisionAnswer;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;

/** Validates neutral request/response semantics independently of any provider. */
public final class DecisionValidation {
    private DecisionValidation() {
    }

    public static void request(DecisionRequest request) {
        require(request != null, "Decision request is required");
        var state = request.getState();
        require(state instanceof String || state instanceof Map<?, ?> || state instanceof List<?>,
            "Decision state must be text, a JSON object, or a JSON array");
        validateJson(state, 0);
        require(!(state instanceof String text) || !text.isBlank(), "Decision state is required");
        require(request.getQuestions() != null && !request.getQuestions().isEmpty(),
            "At least one decision question is required");
        request.getQuestions().forEach((id, question) -> {
            require(id != null && !id.isBlank(), "Question IDs must not be blank");
            question(question);
        });
        var timeout = request.getTimeouts() == null ? null
            : request.getTimeouts().getTotalTimeout();
        require(timeout == null || (!timeout.isZero() && !timeout.isNegative()),
            "Decision timeout must be positive");
    }

    private static void validateJson(Object value, int depth) {
        require(depth <= 32, "Decision state exceeds the supported JSON nesting depth");
        if (value == null || value instanceof String || value instanceof Boolean) {
            return;
        }
        if (value instanceof Number number) {
            require(Double.isFinite(number.doubleValue()), "State numbers must be finite");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                require(key instanceof String, "State object keys must be strings");
                validateJson(child, depth + 1);
            });
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child -> validateJson(child, depth + 1));
            return;
        }
        throw new IllegalArgumentException("Decision state must contain only JSON values");
    }

    public static void capabilities(DecisionRequest request,
        DecisionCapability capabilities) {
        require(capabilities != null && capabilities.getQuestionTypes() != null,
            "Decision capabilities are unavailable");
        require(request.getState() instanceof String || Boolean.TRUE.equals(capabilities.getStructuredState()),
            "This decision model does not support structured state");
        request.getQuestions().values().forEach(question -> {
            require(capabilities.getQuestionTypes().contains(question.getType()),
                "This decision model does not support question type " + question.getType());
            if (question.getOptions() != null && capabilities.getMaxChoiceOptions() != null) {
                require(question.getOptions().size() <= capabilities.getMaxChoiceOptions(),
                    "Too many choice options; maximum is " + capabilities.getMaxChoiceOptions());
            }
            if (question.getLevels() != null && capabilities.getMaxScoreLevels() != null) {
                require(question.getLevels().size() <= capabilities.getMaxScoreLevels(),
                    "Too many score levels; maximum is " + capabilities.getMaxScoreLevels());
            }
        });
    }

    private static void question(DecisionQuestion question) {
        require(question != null && question.getType() != null, "Question type is required");
        require(question.getInstructions() != null && !question.getInstructions().isBlank(),
            "Question instructions are required");
        switch (question.getType()) {
            case YES_NO -> require(question.getOptions() == null && question.getLevels() == null,
                "Yes/no questions do not accept options or levels");
            case CHOICE -> {
                require(question.getOptions() != null && question.getOptions().size() >= 2,
                    "Choice questions require at least two options");
                question.getOptions().forEach((key, description) -> {
                    require(key != null && !key.isBlank(), "Choice option IDs must not be blank");
                    require(description != null, "Choice option descriptions must not be null");
                });
                require(question.getLevels() == null, "Choice questions do not accept levels");
                noBooleanCriteria(question);
            }
            case SCORE -> {
                require(question.getLevels() != null && question.getLevels().size() >= 2,
                    "Score questions require at least two ordered levels");
                question.getLevels().forEach(level -> require(level != null && !level.isBlank(),
                    "Score level descriptions must not be blank"));
                require(question.getOptions() == null, "Score questions do not accept options");
                noBooleanCriteria(question);
            }
        }
    }

    private static void noBooleanCriteria(DecisionQuestion question) {
        require(question.getYesCriteria() == null && question.getNoCriteria() == null,
            "Only yes/no questions accept yesCriteria or noCriteria");
    }

    public static void response(DecisionRequest request, DecisionResponse response) {
        valid(response != null && response.getModel() != null && !response.getModel().isBlank(),
            "Missing decision response model");
        valid(response.getAnswers() != null
                && response.getAnswers().keySet().equals(request.getQuestions().keySet()),
            "Decision response question IDs do not match the request");
        request.getQuestions().forEach((id, question) -> {
            var answer = response.getAnswers().get(id);
            valid(answer != null && answer.getType() == question.getType(),
                "Decision response type does not match question " + id);
            switch (question.getType()) {
                case YES_NO -> probability(answer.getProbability());
                case CHOICE -> {
                    distribution(answer, question.getOptions().keySet());
                    valid(answer.getChoice() != null
                            && question.getOptions().containsKey(answer.getChoice()),
                        "Decision response selected an unknown option");
                }
                case SCORE -> {
                    var keys = IntStream.range(0, question.getLevels().size())
                        .mapToObj(Integer::toString).collect(Collectors.toCollection(LinkedHashSet::new));
                    distribution(answer, keys);
                    var score = answer.getScore();
                    valid(score != null && Double.isFinite(score) && score >= 0
                            && score <= question.getLevels().size() - 1,
                        "Decision score is outside the requested levels");
                    valid(answer.getLegend() != null && answer.getLegend().keySet().equals(keys)
                            && answer.getLegend().values().stream().allMatch(v -> v != null),
                        "Decision score legend does not match the requested levels");
                }
            }
        });
    }

    private static void distribution(DecisionAnswer answer, Set<String> keys) {
        var probabilities = answer.getProbabilities();
        valid(probabilities != null && probabilities.keySet().equals(keys),
            "Decision probabilities do not match the requested options");
        probabilities.values().forEach(DecisionValidation::probability);
        valid(Math.abs(probabilities.values().stream().mapToDouble(Double::doubleValue).sum() - 1)
            <= 0.001, "Decision probabilities must sum to one");
        probability(answer.getConfidence());
    }

    private static void probability(Double value) {
        valid(value != null && Double.isFinite(value) && value >= 0 && value <= 1,
            "Decision probabilities and confidence must be finite values between zero and one");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void valid(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
