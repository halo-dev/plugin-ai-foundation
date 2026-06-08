package run.halo.aifoundation.ui;

import java.util.List;
import run.halo.aifoundation.message.ModelMessagePart;

/**
 * Converts a named UI data part into provider-neutral model content.
 *
 * @param <M> message metadata type
 */
@FunctionalInterface
public interface UIMessageDataConverter<M> {
    /**
     * Converts a data part to model content.
     *
     * @param part data part to convert
     * @param context conversion context
     * @return model message parts, or an empty list to skip the part
     */
    List<ModelMessagePart> convert(DataPart part, UIMessageConversionContext<M> context);
}
