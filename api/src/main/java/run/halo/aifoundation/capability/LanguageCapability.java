package run.halo.aifoundation.capability;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fine-grained capability values for language models.
 *
 * <p>Boolean values are tri-state: {@code true}, {@code false}, or {@code null} for unknown.
 * Unknown semantic capabilities are treated as unsupported when a request actually requires
 * them. Lists are coverage declarations, not business upload policy; consumer plugins still
 * decide which files their users may choose.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageCapability {

    /**
     * Whether image parts can be sent in user or assistant message history.
     */
    private Boolean imageInput;

    /**
     * Whether non-image file parts can be sent in user or assistant message history.
     */
    private Boolean fileInput;

    /**
     * Whether saved assistant reasoning parts can be replayed as model history.
     */
    private Boolean reasoningHistory;

    /**
     * Media type patterns accepted by language media input, such as {@code image/*} or
     * {@code application/pdf}. A supported range must cover a required range to satisfy a
     * capability requirement.
     */
    private List<String> inputMediaTypes;

    /**
     * Supported media source kinds for language input. URL support means the provider can consume
     * the URL natively; AI Foundation does not download URLs as a fallback.
     */
    private List<InputSource> inputSources;

    /**
     * Creates a snapshot whose language capabilities are all unknown.
     *
     * @return an unknown language capability snapshot
     */
    public static LanguageCapability unknown() {
        return new LanguageCapability();
    }
}
