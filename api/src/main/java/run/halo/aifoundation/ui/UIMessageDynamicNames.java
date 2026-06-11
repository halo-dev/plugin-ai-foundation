package run.halo.aifoundation.ui;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validation and formatting helpers for dynamic UI message part names.
 */
public final class UIMessageDynamicNames {

    private static final Pattern DYNAMIC_IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

    private UIMessageDynamicNames() {
    }

    /**
     * Creates a dynamic data type.
     *
     * @param name data name
     * @return dynamic data type
     */
    public static String dataType(String name) {
        requireName(name, DYNAMIC_IDENTIFIER, "data name");
        return "data-" + name;
    }

    /**
     * Creates a dynamic tool type.
     *
     * @param toolName tool name
     * @return dynamic tool type
     */
    public static String toolType(String toolName) {
        requireName(toolName, DYNAMIC_IDENTIFIER, "tool name");
        return "tool-" + toolName;
    }

    /**
     * Validates that a type matches a data name.
     *
     * @param type dynamic type
     * @param name data name
     */
    public static void requireDataType(String type, String name) {
        requireMatchingType(type, dataType(name), "data type");
    }

    /**
     * Validates that a type matches a tool name.
     *
     * @param type dynamic type
     * @param toolName tool name
     */
    public static void requireToolType(String type, String toolName) {
        requireMatchingType(type, toolType(toolName), "tool type");
    }

    static void requireSimpleName(String value, String label) {
        requireName(value, DYNAMIC_IDENTIFIER, label);
    }

    private static void requireName(String value, Pattern pattern, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a simple identifier: " + value);
        }
    }

    private static void requireMatchingType(String actual, String expected, String label) {
        if (!Objects.equals(actual, expected)) {
            throw new IllegalArgumentException(label + " must be " + expected + " but was " + actual);
        }
    }
}
