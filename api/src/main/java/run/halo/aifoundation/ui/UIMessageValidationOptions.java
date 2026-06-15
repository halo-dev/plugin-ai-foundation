package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Options for validating persisted UI messages.
 *
 * @param <M> message metadata type
 */
public final class UIMessageValidationOptions<M> {
    private final List<UIMessageMetadataValidator<M>> metadataValidators = new ArrayList<>();
    private final Map<String, List<UIMessageDataValidator<M>>> dataValidators =
        new LinkedHashMap<>();
    private final Map<String, List<UIMessageToolValidator<M>>> namedToolValidators =
        new LinkedHashMap<>();
    private final List<UIMessageToolValidator<M>> toolValidators = new ArrayList<>();

    /**
     * Adds a message-level metadata validator.
     *
     * @param validator metadata validator
     * @return this options object
     */
    public UIMessageValidationOptions<M> metadataValidator(
        UIMessageMetadataValidator<M> validator) {
        metadataValidators.add(Objects.requireNonNull(validator, "validator must not be null"));
        return this;
    }

    /**
     * Adds a validator for a named {@link DataPart}.
     *
     * @param name data part name
     * @param validator data validator
     * @return this options object
     */
    public UIMessageValidationOptions<M> dataValidator(String name,
        UIMessageDataValidator<M> validator) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("data validator name must not be blank");
        }
        dataValidators.computeIfAbsent(name, ignored -> new ArrayList<>())
            .add(Objects.requireNonNull(validator, "validator must not be null"));
        return this;
    }

    /**
     * Adds a validator for tool-related parts.
     *
     * @param validator tool validator
     * @return this options object
     */
    public UIMessageValidationOptions<M> toolValidator(UIMessageToolValidator<M> validator) {
        toolValidators.add(Objects.requireNonNull(validator, "validator must not be null"));
        return this;
    }

    /**
     * Adds a validator for a named dynamic {@link ToolPart}.
     *
     * @param toolName tool name
     * @param validator tool validator
     * @return this options object
     */
    public UIMessageValidationOptions<M> toolValidator(String toolName,
        UIMessageToolValidator<M> validator) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("tool validator name must not be blank");
        }
        namedToolValidators.computeIfAbsent(toolName, ignored -> new ArrayList<>())
            .add(Objects.requireNonNull(validator, "validator must not be null"));
        return this;
    }

    List<UIMessageMetadataValidator<M>> metadataValidators() {
        return metadataValidators;
    }

    Map<String, List<UIMessageDataValidator<M>>> dataValidators() {
        return dataValidators;
    }

    List<UIMessageToolValidator<M>> toolValidators() {
        return toolValidators;
    }

    Map<String, List<UIMessageToolValidator<M>>> namedToolValidators() {
        return namedToolValidators;
    }
}
