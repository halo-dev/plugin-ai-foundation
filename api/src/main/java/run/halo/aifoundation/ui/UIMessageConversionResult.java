package run.halo.aifoundation.ui;

import java.util.List;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Full result of converting UI messages into model messages.
 *
 * @param messages provider-neutral model messages
 * @param warnings non-fatal conversion diagnostics
 */
public record UIMessageConversionResult(List<ModelMessage> messages,
                                        List<UIMessageConversionWarning> warnings) {
    public UIMessageConversionResult {
        messages = List.copyOf(messages);
        warnings = List.copyOf(warnings);
    }
}
