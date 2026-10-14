package run.halo.aifoundation.tool.builtin;

import java.math.BigDecimal;
import java.util.Map;

final class BuiltInToolInputs {
    private BuiltInToolInputs() {
    }

    static String requiredString(Map<String, Object> input, String key, int maxLength) {
        var value = input.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException(key + " must be nonblank and at most "
                + maxLength + " characters");
        }
        return text.strip();
    }

    static int limit(Map<String, Object> input) {
        var value = input.get("limit");
        if (value == null) {
            return 5;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("limit must be an integer between 1 and 10");
        }
        try {
            int limit = new BigDecimal(number.toString()).intValueExact();
            if (limit >= 1 && limit <= 10) {
                return limit;
            }
        } catch (NumberFormatException | ArithmeticException ignored) {
            // Keep one stable validation error for non-finite and fractional numbers.
        }
        throw new IllegalArgumentException("limit must be an integer between 1 and 10");
    }

    static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
