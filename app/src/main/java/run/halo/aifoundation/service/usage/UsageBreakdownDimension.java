package run.halo.aifoundation.service.usage;

import java.util.Locale;

/**
 * Grouping dimension for usage breakdown and dimension-split trend queries. The enum name
 * maps to a column shared by ai_calls and ai_usage_daily, so both raw and archived sources
 * can answer grouped queries.
 */
public enum UsageBreakdownDimension {
    MODEL("model_name"),
    PROVIDER("provider_name"),
    CALLER_PLUGIN("caller_plugin_name");

    private final String column;

    UsageBreakdownDimension(String column) {
        this.column = column;
    }

    String column() {
        return column;
    }

    /** Accepts camelCase or snake_case spellings such as "model" or "callerPlugin". */
    public static UsageBreakdownDimension fromText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "dimension must be one of model, provider, callerPlugin");
        }
        var normalized = value.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return UsageBreakdownDimension.valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                "dimension must be one of model, provider, callerPlugin", error);
        }
    }
}
