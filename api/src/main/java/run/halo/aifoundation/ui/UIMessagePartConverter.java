package run.halo.aifoundation.ui;

import java.util.List;
import run.halo.aifoundation.message.ModelMessagePart;

/**
 * Converts an application-specific UI message part into provider-neutral model content.
 *
 * @param <M> message metadata type
 */
@FunctionalInterface
public interface UIMessagePartConverter<M> {
    /**
     * Converts a UI message part to model content.
     *
     * @param part part to convert
     * @param context conversion context
     * @return model message parts, or an empty list to skip the part
     */
    List<ModelMessagePart> convert(UIMessagePart part, UIMessageConversionContext<M> context);
}
